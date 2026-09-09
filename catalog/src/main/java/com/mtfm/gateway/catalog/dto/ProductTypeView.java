package com.mtfm.gateway.catalog.dto;

import java.time.Instant;

/**
 * 产品类型对外视图。
 */
public record ProductTypeView(
        String id,
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
