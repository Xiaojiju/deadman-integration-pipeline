package com.mtfm.gateway.catalog.dto;

import java.util.Map;

public record ActionMemberView(
        String id,
        String deviceCode,
        String functionId,
        Map<String, Object> arguments,
        int sortIndex
) {
}
