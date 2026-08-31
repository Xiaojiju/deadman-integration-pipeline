package com.mtfm.gateway.catalog.dto;

/**
 * 创建/更新产品请求。
 *
 * @param code                 产品编码（创建必填；更新时忽略，编码不可改）
 * @param name                 显示名称
 * @param description          说明
 * @param seedCapabilityType   可选：创建后按该能力预置模板一键挂载功能（FIXED 能力常用）
 */
public record ProductWriteRequest(
        String code,
        String name,
        String description,
        String seedCapabilityType
) {
}
