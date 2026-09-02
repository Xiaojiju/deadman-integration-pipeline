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
    /** UI 采值约束（FieldFormat wire）。 */
    private String format;
    /** 平台值生成器 wire code。 */
    private String valueGenerator;
    /** 值来源：caller / platform / device / constant / mapped。 */
    private String fieldSource;
    /** source=constant 时的固定值。 */
    private String constantValue;
    /** MAPPED 时调用方传入的字段名。 */
    private String callerField;
    /** 协议字段顺序，按配置传入顺序保存。 */
    private Integer sortIndex;
    /** HEX/BINARY 字段占用字节数。 */
    private Integer byteLength;
    /** 字节序 big / little。 */
    private String byteOrder;

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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getValueGenerator() {
        return valueGenerator;
    }

    public void setValueGenerator(String valueGenerator) {
        this.valueGenerator = valueGenerator;
    }

    public String getFieldSource() {
        return fieldSource;
    }

    public void setFieldSource(String fieldSource) {
        this.fieldSource = fieldSource;
    }

    public String getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(String constantValue) {
        this.constantValue = constantValue;
    }

    public String getCallerField() {
        return callerField;
    }

    public void setCallerField(String callerField) {
        this.callerField = callerField;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
    }

    public Integer getByteLength() {
        return byteLength;
    }

    public void setByteLength(Integer byteLength) {
        this.byteLength = byteLength;
    }

    public String getByteOrder() {
        return byteOrder;
    }

    public void setByteOrder(String byteOrder) {
        this.byteOrder = byteOrder;
    }
}
