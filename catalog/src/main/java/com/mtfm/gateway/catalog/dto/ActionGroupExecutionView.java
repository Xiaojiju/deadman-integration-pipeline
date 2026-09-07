package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.model.ExecutionResult;

import java.util.List;

/**
 * 动作组执行结果：各成员指令的 {@link ExecutionResult} 列表。
 */
public record ActionGroupExecutionView(
        String groupId,
        String code,
        String kind,
        String source,
        List<ExecutionResult> items
) {
}
