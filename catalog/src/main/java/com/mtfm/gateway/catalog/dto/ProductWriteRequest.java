package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建/更新产品请求。
 *
 * <pre>{@code
 * POST /catalog/products
 * {"code":"door","name":"门锁","productTypeId":"ACCESS_CONTROL","seedCapabilityType":"HIKVISION_ENTRANCE"}
 * }</pre>
 *
 * @param code               产品编码（创建必填；更新忽略，编码不可改）
 * @param name               显示名称（创建必填）
 * @param description        说明
 * @param productTypeId      产品类型 id 或 code（创建必填；更新忽略）
 * @param seedCapabilityType 可选：创建后按该能力预置模板挂载功能（FIXED 常用）
 */
public record ProductWriteRequest(
        @NotBlank(groups = CreateOp.class, message = "产品 code 不能为空")
        String code,
        @NotBlank(groups = CreateOp.class, message = "产品 name 不能为空")
        String name,
        String description,
        @NotBlank(groups = CreateOp.class, message = "产品类型不能为空")
        String productTypeId,
        String seedCapabilityType
) {
}
