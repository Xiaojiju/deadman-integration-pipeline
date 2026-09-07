package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.model.ExecutionResult;

import java.util.List;

public record ActionGroupExecutionView(
        String groupId,
        String code,
        String kind,
        String source,
        List<ExecutionResult> items
) {
}
