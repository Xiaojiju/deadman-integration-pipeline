package com.mtfm.gateway.spi.option;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Option} 的取值形态：标量、多级对象、多级数组。
 *
 * <p>使用示例：
 * <pre>{@code
 * OptionValue.Scalar s = OptionValue.scalar("hello");
 * OptionValue.ObjectNode obj = OptionValue.object(Map.of("key", Option.scalar("v")));
 * OptionValue.ArrayNode arr = OptionValue.array(List.of(Option.scalar("a"), Option.scalar("b")));
 * }</pre>
 */
public sealed interface OptionValue {

    /** 标量字符串值。 */
    record Scalar(String raw) implements OptionValue {
        public Scalar {
            raw = raw == null ? "" : raw;
        }
    }

    /** 对象节点，字段名为 key，值为子 Option。 */
    record ObjectNode(Map<String, Option> fields) implements OptionValue {
        public ObjectNode {
            fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
        }
    }

    /** 数组节点，元素为子 Option。 */
    record ArrayNode(List<Option> items) implements OptionValue {
        public ArrayNode {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** 创建标量值。 */
    static Scalar scalar(String raw) {
        return new Scalar(raw);
    }

    /** 创建对象节点。 */
    static ObjectNode object(Map<String, Option> fields) {
        return new ObjectNode(fields);
    }

    /** 创建数组节点。 */
    static ArrayNode array(List<Option> items) {
        return new ArrayNode(items);
    }
}
