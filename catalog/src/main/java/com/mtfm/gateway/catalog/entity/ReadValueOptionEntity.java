package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * READ 功能 VALUE 模式下的枚举/映射选项。
 */
@TableName("gw_read_value_option")
public class ReadValueOptionEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属产品功能主键，对应 {@link ProductFunctionEntity#id}。 */
    private String productFunctionId;

    /** 选项说明。 */
    private String description;

    /** 协议侧原始值。 */
    private String optionValue;

    /** 映射后的平台/调用方值。 */
    private String mappingValue;

    /** 访问层数据类型 wire code。 */
    private String accessDataType;

    /** 传输层数据类型 wire code。 */
    private String transformDataType;

    /** 是否为默认选项。 */
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
