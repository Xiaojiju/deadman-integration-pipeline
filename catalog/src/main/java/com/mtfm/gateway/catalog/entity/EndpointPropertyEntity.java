package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gw_endpoint_property")
public class EndpointPropertyEntity extends AbstractPropertyEntity {

    @TableId
    private String id;
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
