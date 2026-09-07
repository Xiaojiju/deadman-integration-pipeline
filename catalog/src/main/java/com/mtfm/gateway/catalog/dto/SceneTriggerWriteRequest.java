package com.mtfm.gateway.catalog.dto;

import java.util.Map;

public record SceneTriggerWriteRequest(
        String mode,
        String listenDeviceCode,
        String listenFunctionId,
        Map<String, Object> listenMatch,
        String timerKind,
        String timerAt,
        String cronExpr,
        String timezone,
        Boolean enabled
) {
}
