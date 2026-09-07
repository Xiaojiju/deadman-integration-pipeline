package com.mtfm.gateway.catalog.dto;

import java.util.Map;

/**
 * 场景触发器写入请求，嵌在 {@link ActionGroupWriteRequest} 中。
 */
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
