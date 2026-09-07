package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * READ 功能下的单个读字段定义，结构与 {@link WriteOptionEntity} 对齐。
 */
@TableName("gw_read_field")
public class ReadFieldEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属产品功能主键，对应 {@link ProductFunctionEntity#id}。 */
    private String productFunctionId;

    /** 协议字段名或 path。 */
    private String field;

    /** 字段说明，可空。 */
    private String description;

    /** 访问层数据类型 wire code。 */
    private String accessDataType;

    /** 传输层数据类型 wire code。 */
    private String transformDataType;

    /** 是否忽略调用方入参。 */
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
    /** 字节序 big / little。 */
    private String byteOrder;

    /** 入站换算运算符。 */
    private String scaleOp;

    /** 入站换算操作数。 */
    private String scaleOperand;

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

    public String getScaleOp() {
        return scaleOp;
    }

    public void setScaleOp(String scaleOp) {
        this.scaleOp = scaleOp;
    }

    public String getScaleOperand() {
        return scaleOperand;
    }

    public void setScaleOperand(String scaleOperand) {
        this.scaleOperand = scaleOperand;
    }
}
