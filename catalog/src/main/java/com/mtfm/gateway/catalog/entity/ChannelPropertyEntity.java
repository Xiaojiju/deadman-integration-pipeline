package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 通道 EAV 属性行，一条记录对应连接参数 schema 中的一个字段。
 */
@TableName("gw_channel_property")
public class ChannelPropertyEntity extends AbstractPropertyEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属通道主键，对应 {@link ChannelEntity#id}。 */
    private String channelId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }
}
