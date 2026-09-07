package com.mtfm.gateway.catalog.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 动作组成员。
 */
public record ActionMemberWriteRequest(
        @NotBlank(message = "deviceCode 不能为空")
        String deviceCode,
        @NotBlank(message = "functionId 不能为空")
        String functionId,
        Map<String, Object> arguments,
        Integer sortIndex
) {
}
