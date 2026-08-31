package com.mtfm.gateway.catalog.entity;

/**
 * EAV 属性行公共字段（不含主键，主键由子类声明）。
 */
public abstract class AbstractPropertyEntity {

    private String attribute;
    private String attributeValue;
    private String dataType;
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
