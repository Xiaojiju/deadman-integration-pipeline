package com.mtfm.gateway.catalog.dto;

import java.util.List;

/**
 * 批量加载设备。deviceCodes 为空则加载全部已启用且未 load 的设备。
 */
public record DeviceLoadBatchRequest(List<String> deviceCodes) {
}
