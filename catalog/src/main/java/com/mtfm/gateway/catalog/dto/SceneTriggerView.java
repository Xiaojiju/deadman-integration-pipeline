package com.mtfm.gateway.catalog.dto;

import java.util.Map;

/**
 * 场景触发器视图：LISTEN 或 TIMER 模式的触发条件。
 */
public record SceneTriggerView(
        String id,
        String mode,
        String listenDeviceCode,
        String listenFunctionId,
        Map<String, Object> listenMatch,
        String timerKind,
        String timerAt,
        String cronExpr,
        String timezone,
        boolean enabled
) {
}
