package com.mtfm.gateway.catalog.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 通用分页结果。
 *
 * @param items      当前页数据
 * @param total      总条数
 * @param page       当前页（从 1 起）
 * @param size       每页大小
 * @param totalPages 总页数
 */
public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {

    public static <T> PageResult<T> of(IPage<T> pageData) {
        return new PageResult<>(
                pageData.getRecords(),
                pageData.getTotal(),
                (int) pageData.getCurrent(),
                (int) pageData.getSize(),
                (int) Math.max(pageData.getPages(), 0)
        );
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}
