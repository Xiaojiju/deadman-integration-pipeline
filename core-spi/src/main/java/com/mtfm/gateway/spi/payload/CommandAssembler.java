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
            Map<String, Object> deviceFieldOverrides,
            String requestId
    ) {

        public Request {
            payloadMode = payloadMode == null ? PayloadMode.STRUCT : payloadMode;
            valueMappings = valueMappings == null ? List.of() : List.copyOf(valueMappings);
            callerArguments = callerArguments == null ? Map.of() : Map.copyOf(callerArguments);
            deviceFieldOverrides = deviceFieldOverrides == null ? Map.of() : Map.copyOf(deviceFieldOverrides);
            requestId = requestId == null || requestId.isBlank() ? null : requestId.trim();
        }

        public Request(
                PayloadMode payloadMode,
                FieldNode structRoot,
                List<ValueMapping> valueMappings,
                Map<String, Object> callerArguments,
                Map<String, Object> deviceFieldOverrides) {
            this(payloadMode, structRoot, valueMappings, callerArguments, deviceFieldOverrides, null);
        }
    }

    /**
     * 按映射上的 callerField 取调用方参数。
     * {@code value}（默认）仍兼容 {@code lock} / {@code command.lock} / 单键兜底。
     */
    public static Object resolveCallerValue(Map<String, Object> caller, String callerField) {
        if (caller == null || caller.isEmpty()) {
            return null;
        }
        String field = (callerField == null || callerField.isBlank()) ? CALLER_VALUE_KEY : callerField.trim();
        Object direct = caller.get(field);
        if (direct != null) {
            return direct;
        }
        if (!CALLER_VALUE_KEY.equals(field)) {
            return null;
        }
        Object extracted = extractCallerValue(caller);
        if (extracted != null) {
            return extracted;
        }
        if (caller.size() == 1) {
            return caller.values().iterator().next();
        }
        return null;
    }

    /** @deprecated 使用 {@link #resolveCallerValue(Map, String)} */
    public static Map<String, Object> normalizeCallerArguments(PayloadMode mode, Map<String, Object> caller) {
        if (caller == null || caller.isEmpty()) {
            return Map.of();
        }
        if (mode != PayloadMode.VALUE) {
            return Map.copyOf(caller);
        }
        Object extracted = resolveCallerValue(caller, CALLER_VALUE_KEY);
        if (extracted == null || caller.containsKey(CALLER_VALUE_KEY)) {
            return Map.copyOf(caller);
        }
        Map<String, Object> next = new LinkedHashMap<>(caller);
        next.put(CALLER_VALUE_KEY, extracted);
        return Map.copyOf(next);
    }

    private static Object extractCallerValue(Map<String, Object> caller) {
        Object lock = caller.get("lock");
        if (lock != null) {
            return lock;
        }
        Object command = caller.get("command");
        if (command instanceof Map<?, ?> map) {
            Object nested = map.get("lock");
            if (nested != null) {
                return nested;
            }
        }
        return null;
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
                FieldTreePaths.setFlat(flat, leaf.path(), JsonLiterals.coerceConstant(node.constant(), node.isArray()));
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
            Object caller = callerValueForLeaf(request.callerArguments(), node, leaf.path());
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
                        flat, leaf.path(),
                        FieldValueGenerators.generate(node.valueGenerator(), node.type(), request.requestId()));
            }
        }

        return unwrapRoot(FieldTreePaths.toNested(request.structRoot(), flat));
    }

    private static Map<String, Object> unwrapRoot(Map<String, Object> nested) {
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
        for (ValueMapping mapping : request.valueMappings()) {
            if (mapping.target() != MappingTarget.FILL_ROOT) {
                continue;
            }
            Object raw = resolveCallerValue(request.callerArguments(), mapping.callerField());
            if (raw == null || !mappingMatches(mapping, raw)) {
                continue;
            }
            if (mapping.rootValue() instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                return new LinkedHashMap<>(cast);
            }
            return Map.of("_value", mapping.rootValue());
        }
        return null;
    }

    private static void applyValuePatches(Request request, Map<String, Object> flat) {
        LinkedHashMap<String, Boolean> applied = new LinkedHashMap<>();
        for (ValueMapping mapping : request.valueMappings()) {
            if (mapping.target() == MappingTarget.FILL_ROOT) {
                continue;
            }
            String callerField = mapping.callerField();
            if (applied.containsKey(callerField)) {
                continue;
            }
            Object raw = resolveCallerValue(request.callerArguments(), callerField);
            if (raw == null) {
                continue;
            }
            if (!mappingMatches(mapping, raw)) {
                continue;
            }
            for (FieldPatch patch : mapping.patches()) {
                FieldTreePaths.setFlat(flat, patch.path(), patch.value());
            }
            applied.put(callerField, Boolean.TRUE);
        }
    }

    private static Object callerValueForLeaf(Map<String, Object> caller, FieldNode node, String path) {
        if (caller == null || caller.isEmpty()) {
            return null;
        }
        if (node.callerField() != null && !node.callerField().isBlank()) {
            Object named = caller.get(node.callerField());
            if (named != null) {
                return named;
            }
        }
        Object byPath = caller.get(path);
        if (byPath != null) {
            return byPath;
        }
        return caller.get(node.name());
    }

    private static boolean mappingMatches(ValueMapping mapping, Object raw) {
        return mapping.mappingValue().equals(String.valueOf(raw));
    }

    private static List<FieldTreePaths.LeafBinding> leavesOf(FieldNode root) {
        return FieldTreePaths.collectLeaves(root, null);
    }
}
