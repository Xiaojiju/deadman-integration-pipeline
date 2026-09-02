package com.mtfm.gateway.spi.payload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 点分 path 与嵌套 Map / List 互转；数字段视为数组下标（如 {@code params.0}）。 */
final class FieldTreePaths {

    private FieldTreePaths() {
    }

    static void setFlat(Map<String, Object> flat, String path, Object value) {
        flat.put(path, value);
    }

    /** 从嵌套 Map / List 按点分 path 取值（数字段视为数组下标）。 */
    static Object getNested(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        if (!path.contains(".")) {
            return root.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            current = getChild(current, part);
            if (current == null) {
                return null;
            }
        }
        return current;
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

    /**
     * 按字段树子节点顺序还原嵌套结构，保证协议 JSON 键序与配置顺序一致。
     * 树中未出现的 path（如整段 array patch）追加在末尾。
     */
    static Map<String, Object> toNested(FieldNode tree, Map<String, Object> flat) {
        if (flat == null || flat.isEmpty()) {
            return Map.of();
        }
        if (tree == null) {
            return toNested(flat);
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (LeafBinding leaf : collectLeaves(tree, null)) {
            if (!flat.containsKey(leaf.path())) {
                continue;
            }
            putNested(nested, leaf.path(), flat.get(leaf.path()));
            emitted.add(leaf.path());
        }
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            if (emitted.contains(entry.getKey())) {
                continue;
            }
            putNested(nested, entry.getKey(), entry.getValue());
        }
        return nested;
    }

    private static void putNested(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            return;
        }
        String[] parts = path.split("\\.");
        Object container = root;
        for (int i = 0; i < parts.length - 1; i++) {
            container = ensureChild(container, parts[i], isIndex(parts[i + 1]));
        }
        setChild(container, parts[parts.length - 1], value);
    }

    /** 从字段树收集叶子 path 与节点定义。object / 带元素定义的 array 都会展开。 */
    static List<LeafBinding> collectLeaves(FieldNode node, String prefix) {
        List<LeafBinding> result = new ArrayList<>();
        collectLeavesInternal(node, prefix, result);
        return List.copyOf(result);
    }

    private static void collectLeavesInternal(FieldNode node, String prefix, List<LeafBinding> out) {
        if (isContainer(node)) {
            for (FieldNode child : node.children()) {
                String nextPrefix = join(prefix, child.name());
                if (isContainer(child)) {
                    collectLeavesInternal(child, nextPrefix, out);
                } else {
                    out.add(new LeafBinding(nextPrefix, child));
                }
            }
            return;
        }
        if ("root".equals(node.name()) && (prefix == null || prefix.isBlank())) {
            return;
        }
        String path = prefix == null || prefix.isBlank() ? node.name() : prefix;
        out.add(new LeafBinding(path, node));
    }

    private static boolean isContainer(FieldNode node) {
        return !node.children().isEmpty() && (node.isObject() || node.isArray());
    }

    private static String join(String prefix, String name) {
        if (prefix == null || prefix.isBlank()) {
            return name;
        }
        return prefix + "." + name;
    }

    private static boolean isIndex(String part) {
        if (part == null || part.isEmpty()) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            if (!Character.isDigit(part.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Object ensureChild(Object container, String key, boolean childIsList) {
        Object existing = getChild(container, key);
        if (childIsList) {
            if (!(existing instanceof List<?>)) {
                existing = new ArrayList<>();
                setChild(container, key, existing);
            }
        } else if (!(existing instanceof Map<?, ?>)) {
            existing = new LinkedHashMap<>();
            setChild(container, key, existing);
        }
        return existing;
    }

    private static Object getChild(Object container, String key) {
        if (container instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (container instanceof List<?> list && isIndex(key)) {
            int idx = Integer.parseInt(key);
            if (idx >= 0 && idx < list.size()) {
                return list.get(idx);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void setChild(Object container, String key, Object value) {
        if (container instanceof Map<?, ?>) {
            ((Map<String, Object>) container).put(key, value);
            return;
        }
        if (container instanceof List<?> && isIndex(key)) {
            List<Object> list = (List<Object>) container;
            int idx = Integer.parseInt(key);
            while (list.size() <= idx) {
                list.add(null);
            }
            list.set(idx, value);
            return;
        }
        throw new IllegalArgumentException("无法写入 path 段: " + key);
    }

    record LeafBinding(String path, FieldNode node) {
    }
}
