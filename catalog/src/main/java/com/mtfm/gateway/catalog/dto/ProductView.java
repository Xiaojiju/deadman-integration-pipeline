package com.mtfm.gateway.catalog.dto;

import java.time.Instant;

/**
 * 产品对外视图。不暴露 MyBatis 实体。
 */
public record ProductView(
        String id,
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
