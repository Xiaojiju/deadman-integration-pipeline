package com.mtfm.gateway.spi.payload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 点分 path 与嵌套 Map 互转。 */
final class FieldTreePaths {

    private FieldTreePaths() {
    }

    static void setFlat(Map<String, Object> flat, String path, Object value) {
        flat.put(path, value);
    }

    static Map<String, Object> toNested(Map<String, Object> flat) {
        if (flat.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            putNested(root, entry.getKey(), entry.getValue());
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void putNested(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(part, created);
                current = created;
            } else {
                current = (Map<String, Object>) next;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    /** 从字段树收集叶子 path 与节点定义。 */
    static List<LeafBinding> collectLeaves(FieldNode node, String prefix) {
        List<LeafBinding> result = new ArrayList<>();
        collectLeavesInternal(node, prefix, result);
        return List.copyOf(result);
    }

    private static void collectLeavesInternal(FieldNode node, String prefix, List<LeafBinding> out) {
        if (node.isObject() && !node.children().isEmpty()) {
            for (FieldNode child : node.children()) {
                String nextPrefix = prefix == null || prefix.isBlank()
                        ? child.name()
                        : prefix + "." + child.name();
                if (child.isObject() && !child.children().isEmpty()) {
                    collectLeavesInternal(child, prefix.isBlank() ? child.name() : prefix + "." + child.name(), out);
                } else if (child.isArray()) {
                    out.add(new LeafBinding(nextPrefix, child));
                } else {
                    out.add(new LeafBinding(nextPrefix, child));
                }
            }
            return;
        }
        if ("root".equals(node.name())) {
            return;
        }
        String path = prefix == null || prefix.isBlank() ? node.name() : prefix;
        out.add(new LeafBinding(path, node));
    }

    record LeafBinding(String path, FieldNode node) {
    }
}
