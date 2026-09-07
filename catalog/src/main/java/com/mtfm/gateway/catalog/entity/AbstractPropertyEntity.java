package com.mtfm.gateway.catalog.entity;

/**
 * EAV 属性行公共字段（不含主键，主键由子类声明）。
 */
public abstract class AbstractPropertyEntity {

    /** 属性名，与能力 schema 字段 key 对齐。 */
    private String attribute;

    /** 属性值（明文或加密后存储，取决于 dataType）。 */
    private String attributeValue;

    /** 值类型 wire code，如 {@code string} / {@code secret}。 */
    private String dataType;

    /** 属性说明，可空。 */
    private String description;

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
