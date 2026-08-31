package com.mtfm.gateway.spi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 可序列化传输头。禁止放入连接、Channel 等运行时句柄。
 *
 * @param values 头键值；构造时拷贝为不可变 Map
 */
public record MessageHeaders(Map<String, String> values) {

    public MessageHeaders {
        values = Maps.copyStringMap(values);
    }

    /** 返回空传输头。 */
    public static MessageHeaders empty() {
        return new MessageHeaders(Map.of());
    }

    /** 从 Map 创建传输头。 */
    public static MessageHeaders from(Map<String, String> values) {
        return new MessageHeaders(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** 返回追加/覆盖单键后的新传输头。 */
    public MessageHeaders with(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(values);
        next.put(key, value);
        return new MessageHeaders(next);
    }
}
