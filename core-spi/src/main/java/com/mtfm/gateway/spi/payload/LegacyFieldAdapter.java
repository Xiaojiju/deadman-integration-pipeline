package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 扁平 writeFields / writeValueOptions ↔ FieldNode 树与 VALUE 映射。 */
public final class LegacyFieldAdapter {

    private LegacyFieldAdapter() {
    }

    /** 扁平写字段 → object 根 FieldNode；点分 path 还原嵌套，数字段视为数组下标。 */
    public static FieldNode fromWriteFields(List<WriteFieldOption> fields) {
        MutableNode root = MutableNode.object("root");
        if (fields != null) {
            for (WriteFieldOption field : fields) {
                String path = field.field();
                if (path == null || path.isBlank()) {
                    continue;
                }
                insertPath(root, path.split("\\."), field);
            }
        }
        return root.freeze();
    }

    /**
     * 优先从字段 options（MAPPED 叶子）推导 VALUE 映射；否则回落功能级 writeValueOptions。
     */
    public static List<ValueMapping> fromFieldAndValueOptions(
            List<WriteFieldOption> fields, List<ValueOption> valueOptions) {
        record GroupKey(String callerField, String mappingValue) {
        }
        Map<GroupKey, List<FieldPatch>> byKey = new LinkedHashMap<>();
        Map<GroupKey, String> descriptions = new LinkedHashMap<>();
        if (fields != null) {
            for (WriteFieldOption field : fields) {
                if (field.options() == null || field.options().isEmpty()) {
                    continue;
                }
                String callerField = field.callerField() == null || field.callerField().isBlank()
                        ? CommandAssembler.CALLER_VALUE_KEY
                        : field.callerField();
                for (ValueOption option : field.options()) {
                    String mappingValue = option.mappingValue() != null && !option.mappingValue().isBlank()
                            ? option.mappingValue()
                            : option.optionValue();
                    GroupKey key = new GroupKey(callerField, mappingValue);
                    byKey.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(new FieldPatch(field.field(), option.optionValue()));
                    descriptions.putIfAbsent(key, option.description());
                }
            }
        }
        if (!byKey.isEmpty()) {
            List<ValueMapping> result = new ArrayList<>();
            byKey.forEach((key, patches) -> result.add(
                    ValueMapping.patch(
                            key.callerField(),
                            key.mappingValue(),
                            descriptions.getOrDefault(key, ""),
                            patches)));
            return List.copyOf(result);
        }
        WriteFieldOption mapped = firstMapped(fields);
        String target = mapped != null ? mapped.field() : guessMappedOrFirst(fields);
        String callerField = mapped != null && mapped.callerField() != null && !mapped.callerField().isBlank()
                ? mapped.callerField()
                : CommandAssembler.CALLER_VALUE_KEY;
        return fromWriteValueOptions(valueOptions, target, callerField);
    }

    /** writeValueOptions → VALUE 映射（optionValue 即协议值，patch 到目标 path）。 */
    public static List<ValueMapping> fromWriteValueOptions(
            List<ValueOption> options, String targetField) {
        return fromWriteValueOptions(options, targetField, CommandAssembler.CALLER_VALUE_KEY);
    }

    public static List<ValueMapping> fromWriteValueOptions(
            List<ValueOption> options, String targetField, String callerField) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        String field = (targetField == null || targetField.isBlank()) ? "command" : targetField;
        String caller = (callerField == null || callerField.isBlank())
                ? CommandAssembler.CALLER_VALUE_KEY
                : callerField;
        List<ValueMapping> mappings = new ArrayList<>();
        for (ValueOption option : options) {
            String mv = option.mappingValue() != null ? option.mappingValue() : option.optionValue();
            mappings.add(ValueMapping.patch(
                    caller,
                    mv,
                    option.description(),
                    List.of(new FieldPatch(field, option.optionValue()))));
        }
        return List.copyOf(mappings);
    }

    /** 字段树叶子 → 扁平 write/read fields。 */
    public static List<WriteFieldOption> toWriteFields(FieldNode root) {
        if (root == null) {
            return List.of();
        }
        List<WriteFieldOption> result = new ArrayList<>();
        collectFields(root, null, result);
        return List.copyOf(result);
    }

    /** VALUE 映射 → 旧 writeValueOptions。 */
    public static List<ValueOption> toWriteValueOptions(List<ValueMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        List<ValueOption> result = new ArrayList<>();
        for (ValueMapping mapping : mappings) {
            Object option = mapping.mappingValue();
            if (!mapping.patches().isEmpty() && mapping.patches().get(0).value() != null) {
                option = mapping.patches().get(0).value();
            } else if (mapping.rootValue() != null) {
                option = mapping.rootValue();
            }
            result.add(new ValueOption(
                    String.valueOf(option),
                    mapping.mappingValue(),
                    mapping.description(),
                    "string",
                    "string",
                    Boolean.FALSE));
        }
        return List.copyOf(result);
    }

    public static PayloadMode payloadModeFromWriteAccess(String writeAccessType, List<ValueOption> valueOptions) {
        if ("VALUE".equalsIgnoreCase(writeAccessType) && valueOptions != null && !valueOptions.isEmpty()) {
            return PayloadMode.VALUE;
        }
        return PayloadMode.STRUCT;
    }

    private static void insertPath(MutableNode parent, String[] parts, WriteFieldOption field) {
        insertPath(parent, List.of(parts), field);
    }

    private static void insertPath(MutableNode parent, List<String> parts, WriteFieldOption field) {
        String head = parts.get(0);
        if (head.matches("\\d+") && !"array".equalsIgnoreCase(parent.type)) {
            parent.type = "array";
        }
        MutableNode child = findChild(parent, head);
        if (child == null) {
            child = MutableNode.leaf(head);
            parent.children.add(child);
        }
        if (parts.size() == 1) {
            applyLeaf(child, field);
            return;
        }
        String next = parts.get(1);
        if (next.matches("\\d+")) {
            if (child.children.isEmpty() && isScalarType(child.type)) {
                child.type = "array";
            }
        } else if (child.children.isEmpty() && isScalarType(child.type)) {
            child.type = "object";
        }
        insertPath(child, parts.subList(1, parts.size()), field);
    }

    private static void applyLeaf(MutableNode node, WriteFieldOption field) {
        FieldSource source = FieldSource.from(field.source());
        List<String> choices = field.options() == null
                ? List.of()
                : field.options().stream().map(ValueOption::optionValue).toList();
        String type = field.transformDataType();
        if (type == null || type.isBlank()) {
            type = field.accessDataType();
        }
        if (type == null || type.isBlank()) {
            type = "string";
        }
        if (!choices.isEmpty() && source != FieldSource.MAPPED && isScalarType(type)) {
            type = "select";
        }
        node.type = type;
        node.format = field.format();
        node.source = source;
        node.valueGenerator = field.valueGenerator();
        node.constant = field.constant();
        node.choices = new ArrayList<>(choices);
        node.description = field.description() == null ? "" : field.description();
        node.byteLength = field.byteLength();
        node.byteOrder = field.byteOrder();
    }

    private static MutableNode findChild(MutableNode parent, String name) {
        for (MutableNode child : parent.children) {
            if (name.equals(child.name)) {
                return child;
            }
        }
        return null;
    }

    private static void collectFields(FieldNode node, String prefix, List<WriteFieldOption> out) {
        boolean root = "root".equals(node.name()) && (prefix == null || prefix.isBlank());
        if (root && !node.children().isEmpty()) {
            for (FieldNode child : node.children()) {
                collectFields(child, child.name(), out);
            }
            return;
        }
        boolean container = !node.children().isEmpty() && (node.isObject() || node.isArray());
        if (container) {
            for (FieldNode child : node.children()) {
                String path = prefix == null || prefix.isBlank()
                        ? child.name()
                        : prefix + "." + child.name();
                collectFields(child, path, out);
            }
            return;
        }
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        List<ValueOption> options = node.choices() == null || node.choices().isEmpty()
                ? List.of()
                : node.choices().stream().map(choice -> ValueOption.of(choice, choice)).toList();
        boolean ignore = node.source() != FieldSource.CALLER;
        out.add(new WriteFieldOption(
                prefix,
                node.description(),
                leafType(node.type()),
                leafType(node.type()),
                ignore,
                options,
                node.format(),
                node.valueGenerator(),
                node.source() == null ? null : node.source().wire(),
                constantToString(node.constant()),
                null,
                node.byteLength(),
                node.byteOrder()));
    }

    private static String leafType(String type) {
        if (type == null || type.isBlank() || "select".equalsIgnoreCase(type)) {
            return "string";
        }
        return type;
    }

    private static String constantToString(Object constant) {
        if (constant == null) {
            return null;
        }
        if (constant instanceof String text) {
            return text;
        }
        return String.valueOf(constant);
    }

    private static boolean isScalarType(String type) {
        if (type == null || type.isBlank()) {
            return true;
        }
        return !"object".equalsIgnoreCase(type)
                && !"json".equalsIgnoreCase(type)
                && !"array".equalsIgnoreCase(type);
    }

    private static WriteFieldOption firstMapped(List<WriteFieldOption> fields) {
        if (fields == null) {
            return null;
        }
        for (WriteFieldOption field : fields) {
            if (FieldSource.MAPPED == FieldSource.from(field.source())) {
                return field;
            }
        }
        return null;
    }

    private static String guessMappedOrFirst(List<WriteFieldOption> fields) {
        if (fields == null || fields.isEmpty()) {
            return "command";
        }
        for (WriteFieldOption field : fields) {
            if (FieldSource.MAPPED == FieldSource.from(field.source())) {
                return field.field();
            }
        }
        if (fields.size() == 1) {
            return fields.get(0).field();
        }
        return "command";
    }

    private static final class MutableNode {
        private String name;
        private String type;
        private String format = "none";
        private FieldSource source = FieldSource.CALLER;
        private String valueGenerator;
        private Object constant;
        private final List<MutableNode> children = new ArrayList<>();
        private List<String> choices = new ArrayList<>();
        private String description = "";
        private Integer byteLength;
        private String byteOrder;

        private static MutableNode object(String name) {
            MutableNode node = new MutableNode();
            node.name = name;
            node.type = "object";
            return node;
        }

        private static MutableNode leaf(String name) {
            MutableNode node = new MutableNode();
            node.name = name;
            node.type = "string";
            return node;
        }

        private FieldNode freeze() {
            return new FieldNode(
                    name,
                    type,
                    format,
                    source,
                    valueGenerator,
                    constant,
                    children.stream().map(MutableNode::freeze).toList(),
                    null,
                    List.copyOf(choices),
                    description,
                    byteLength,
                    byteOrder);
        }
    }
}
