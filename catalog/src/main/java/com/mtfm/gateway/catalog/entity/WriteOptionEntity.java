package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gw_write_option")
public class WriteOptionEntity {

    @TableId
    private String id;
    private String productFunctionId;
    private String field;
    private String description;
    private String accessDataType;
    private String transformDataType;
    private Boolean ignoreRequest;

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

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Boolean getIgnoreRequest() {
        return ignoreRequest;
    }

    public void setIgnoreRequest(Boolean ignoreRequest) {
        this.ignoreRequest = ignoreRequest;
    }
}
