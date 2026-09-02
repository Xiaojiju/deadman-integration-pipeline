package com.mtfm.gateway.catalog.store.support;

import com.mtfm.gateway.catalog.entity.AbstractPropertyEntity;
import com.mtfm.gateway.spi.property.PropertyItem;

/** EAV 属性行的通用转换。 */
public final class EavPropertySupport {

    private EavPropertySupport() {
    }

    public static PropertyItem toItem(AbstractPropertyEntity row) {
        return new PropertyItem(row.getAttribute(), row.getAttributeValue(), row.getDataType(), row.getDescription());
    }

    public static void applyItem(AbstractPropertyEntity row, PropertyItem item) {
        row.setAttribute(item.attribute());
        row.setAttributeValue(item.attributeValue());
        row.setDataType(item.dataType());
        row.setDescription(item.description());
    }
}
