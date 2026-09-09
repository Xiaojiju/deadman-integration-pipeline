package com.mtfm.gateway.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 通道探针请求。
 */
public record ChannelProbeRequestBody(
        @NotBlank(message = "productId 不能为空")
        String productId
) {
}
