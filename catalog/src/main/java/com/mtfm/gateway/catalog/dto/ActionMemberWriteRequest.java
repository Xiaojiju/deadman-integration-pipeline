package com.mtfm.gateway.catalog.dto;

import java.util.List;
import java.util.Map;

public record ActionMemberWriteRequest(
        String deviceCode,
        String functionId,
        Map<String, Object> arguments,
        Integer sortIndex
) {
}
