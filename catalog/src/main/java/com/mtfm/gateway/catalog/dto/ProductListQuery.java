package com.mtfm.gateway.catalog.dto;

/**
 * 产品分页筛选。
 */
public record ProductListQuery(
        String name,
        String code,
        String productTypeId
) {
}
