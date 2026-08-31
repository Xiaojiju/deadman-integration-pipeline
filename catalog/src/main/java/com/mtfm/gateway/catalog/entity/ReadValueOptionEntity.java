package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gw_read_value_option")
public class ReadValueOptionEntity {

    @TableId
    private String id;
    private String productFunctionId;
    private String description;
    private String optionValue;
    private String mappingValue;
    private String accessDataType;
    private String transformDataType;
    private Boolean isDefault;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductFunctionId() {
        return productFunctionId;
    }

    public void setProductFunctionId(String productFunctionId) {
        this.productFunctionId = productFunctionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }

    public String getMappingValue() {
        return mappingValue;
    }

    public void setMappingValue(String mappingValue) {
        this.mappingValue = mappingValue;
    }

    public String getAccessDataType() {
        return accessDataType;
    }

    public void setAccessDataType(String accessDataType) {
        this.accessDataType = accessDataType;
    }

    public String getTransformDataType() {
        return transformDataType;
    }

    public void setTransformDataType(String transformDataType) {
        this.transformDataType = transformDataType;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
}
