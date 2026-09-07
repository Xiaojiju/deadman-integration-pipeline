package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 产品功能 EAV 属性行，一条记录对应功能 option schema 中的一个字段。
 */
@TableName("gw_function_property")
public class FunctionPropertyEntity extends AbstractPropertyEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属产品功能主键，对应 {@link ProductFunctionEntity#id}。 */
    private String productFunctionId;

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
}
