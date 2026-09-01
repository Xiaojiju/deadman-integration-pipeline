package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.FieldValueGenerators;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按字段树 + VALUE 映射 + 设备覆盖 组装协议 payload（不可逆、单向）。
 */
public final class CommandAssembler {

    /** 调用方 VALUE 模式简值键名。 */
    public static final String CALLER_VALUE_KEY = "value";

    private CommandAssembler() {
    }

    public record Request(
            PayloadMode payloadMode,
            FieldNode structRoot,
            List<ValueMapping> valueMappings,
            Map<String, Object> callerArguments,
            Map<String, Object> deviceFieldOverrides
    ) {

        public Request {
            payloadMode = payloadMode == null ? PayloadMode.STRUCT : payloadMode;
            valueMappings = valueMappings == null ? List.of() : List.copyOf(valueMappings);
            callerArguments = callerArguments == null ? Map.of() : Map.copyOf(callerArguments);
            deviceFieldOverrides = deviceFieldOverrides == null ? Map.of() : Map.copyOf(deviceFieldOverrides);
        }
    }

    public static Map<String, Object> assemble(Request request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.structRoot(), "structRoot");

        if (request.payloadMode() == PayloadMode.VALUE) {
            Map<String, Object> valueResult = assembleValueMode(request);
            if (valueResult != null) {
                return valueResult;
            }
        }

        Map<String, Object> flat = new LinkedHashMap<>();
        List<FieldTreePaths.LeafBinding> leaves = leavesOf(request.structRoot());

        for (FieldTreePaths.LeafBinding leaf : leaves) {
            FieldNode node = leaf.node();
            if (node.source() == FieldSource.CONSTANT && node.constant() != null) {
                FieldTreePaths.setFlat(flat, leaf.path(), node.constant());
            }
        }

        if (request.payloadMode() == PayloadMode.VALUE) {
            applyValuePatches(request, flat);
        }

        for (FieldTreePaths.LeafBinding leaf : leaves) {
            FieldNode node = leaf.node();
            if (node.source() != FieldSource.CALLER) {
                continue;
            }
            Object caller = request.callerArguments().get(leaf.path());
            if (caller == null) {
                caller = request.callerArguments().get(node.name());
            }
            if (caller != null) {
                FieldTreePaths.setFlat(flat, leaf.path(), caller);
            }
        }

        for (FieldTreePaths.LeafBinding leaf : leaves) {
            FieldNode node = leaf.node();
            if (node.source() != FieldSource.DEVICE) {
                continue;
            }
            Object device = request.deviceFieldOverrides().get(leaf.path());
            if (device == null) {
                device = request.deviceFieldOverrides().get(node.name());
            }
            if (device != null) {
                FieldTreePaths.setFlat(flat, leaf.path(), device);
            }
        }

        for (FieldTreePaths.LeafBinding leaf : leaves) {
            FieldNode node = leaf.node();
            if (node.source() == FieldSource.PLATFORM
                    || FieldValueGenerators.isPlatformGenerated(node.valueGenerator())) {
                FieldTreePaths.setFlat(
                        flat, leaf.path(), FieldValueGenerators.generate(node.valueGenerator()));
            }
        }

        Map<String, Object> nested = FieldTreePaths.toNested(flat);
        if (nested.size() == 1 && nested.containsKey("root")) {
            Object inner = nested.get("root");
            if (inner instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                return cast;
            }
        }
        return nested;
    }

    private static Map<String, Object> assembleValueMode(Request request) {
        Object raw = request.callerArguments().get(CALLER_VALUE_KEY);
        if (raw == null) {
            return null;
        }
        String key = String.valueOf(raw);
        for (ValueMapping mapping : request.valueMappings()) {
            if (!mapping.mappingValue().equals(key)) {
                continue;
            }
            if (mapping.target() == MappingTarget.FILL_ROOT) {
                if (mapping.rootValue() instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    return new LinkedHashMap<>(cast);
                }
                return Map.of("_value", mapping.rootValue());
            }
            break;
        }
        return null;
    }

    private static void applyValuePatches(Request request, Map<String, Object> flat) {
        Object raw = request.callerArguments().get(CALLER_VALUE_KEY);
        if (raw == null) {
            return;
        }
        String key = String.valueOf(raw);
        for (ValueMapping mapping : request.valueMappings()) {
            if (!mapping.mappingValue().equals(key)) {
                continue;
            }
            if (mapping.target() == MappingTarget.FILL_ROOT) {
                return;
            }
            for (FieldPatch patch : mapping.patches()) {
                FieldTreePaths.setFlat(flat, patch.path(), patch.value());
            }
            return;
        }
    }

    private static List<FieldTreePaths.LeafBinding> leavesOf(FieldNode root) {
        if ("root".equals(root.name()) && root.isObject()) {
            List<FieldTreePaths.LeafBinding> merged = new java.util.ArrayList<>();
            for (FieldNode child : root.children()) {
                if (child.isObject() && !child.children().isEmpty()) {
                    merged.addAll(FieldTreePaths.collectLeaves(child, child.name()));
                } else {
                    merged.add(new FieldTreePaths.LeafBinding(child.name(), child));
                }
            }
            return List.copyOf(merged);
        }
        return FieldTreePaths.collectLeaves(root, null);
    }
}
