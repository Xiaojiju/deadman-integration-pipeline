package com.mtfm.gateway.catalog.dto;

/**
 * 设备分页筛选。online：true / false / unknown。
 */
public record DeviceListQuery(
        String name,
        String deviceCode,
        String online,
        String productTypeId
) {
}
