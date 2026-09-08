package com.mtfm.gateway.catalog.store.support;

import com.mtfm.gateway.catalog.entity.AbstractPropertyEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;

import java.util.List;
import java.util.Map;

/**
 * 配置属性的唯一编解码入口：JSON 袋、运行时 Map、EAV 行都经这里互转。
 *
 * <p>使用示例：
 * <pre>{@code
 * List<PropertyItem> items = PropertyCodec.fromMap(values);
 * Map<String, Object> runtime = PropertyCodec.toMap(items);
 * PropertyCodec.applyItem(row, items.get(0));
 * }</pre>
 */
public final class PropertyCodec {

    private PropertyCodec() {
    }

    public static Map<String, Object> readJson(String json) {
        return JsonMaps.readMap(json);
    }

    public static String writeJson(Object value) {
        return JsonMaps.write(value);
    }

    public static List<PropertyItem> fromMap(Map<String, ?> values) {
        return PropertySchemas.fromValueMap(values);
    }

    public static Map<String, Object> toMap(List<PropertyItem> items) {
        return PropertySchemas.toValueMap(items);
    }

    public static List<PropertyItem> fromJson(String json) {
        return fromMap(readJson(json));
    }

    public static PropertyItem toItem(AbstractPropertyEntity row) {
        return EavPropertySupport.toItem(row);
    }

    public static void applyItem(AbstractPropertyEntity row, PropertyItem item) {
        EavPropertySupport.applyItem(row, item);
    }
}
