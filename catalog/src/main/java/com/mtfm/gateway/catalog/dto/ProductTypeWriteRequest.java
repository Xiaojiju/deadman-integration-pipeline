package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建/更新产品类型。
 */
public record ProductTypeWriteRequest(
        @NotBlank(groups = CreateOp.class, message = "类型 code 不能为空")
        String code,
        @NotBlank(groups = CreateOp.class, message = "类型 name 不能为空")
        String name,
        String description
) {
}
