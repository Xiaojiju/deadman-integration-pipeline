package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备端点 EAV 属性行，一条记录对应地址 schema 中的一个字段。
 */
@TableName("gw_endpoint_property")
public class EndpointPropertyEntity extends AbstractPropertyEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属端点主键，对应 {@link DeviceEndpointEntity#id}。 */
    private String endpointId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }
}
