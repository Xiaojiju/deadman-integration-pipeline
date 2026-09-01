package com.mtfm.gateway.spi.payload;

/**
 * VALUE 映射对字段树的一条 patch。
 *
 * @param path  点分路径，如 command 或 user.body.height
 * @param value patch 值
 */
public record FieldPatch(String path, Object value) {

    public FieldPatch {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
    }
}
