package com.mtfm.gateway.catalog.dto;

import java.util.Map;

/**
 * 动作组成员视图：组内一条待执行指令及其参数。
 */
public record ActionMemberView(
        String id,
        String deviceCode,
        String functionId,
        Map<String, Object> arguments,
        int sortIndex
) {
}
