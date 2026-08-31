package com.mtfm.gateway.spi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 开放属性袋：仅用于命令参数、遥测点、目录 schema。禁止放连接句柄。
 *
 * @param values 属性键值；构造时拷贝为不可变 Map
 */
public record Attributes(Map<String, Object> values) {

    public Attributes {
        values = Maps.copyObjectMap(values);
    }

    /** 返回空属性袋。 */
    public static Attributes empty() {
        return new Attributes(Map.of());
    }

    /** 从 Map 创建属性袋。 */
    public static Attributes from(Map<String, ?> values) {
        return new Attributes(Maps.copyObjectMap(values));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /**
     * 返回追加/覆盖单键后的新实例。
     */
    public Attributes with(String key, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(values);
        next.put(key, value);
        return new Attributes(next);
    }
}
