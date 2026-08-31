package com.mtfm.gateway.catalog.id;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 雪花主键生成（MyBatis-Plus {@link IdWorker}）。
 *
 * <p>返回十进制字符串，兼容现有 {@code VARCHAR} 主键列与 {@code String} 实体字段。
 */
public final class SnowflakeIds {

    private SnowflakeIds() {
    }

    /** 下一个雪花 ID（字符串）。 */
    public static String next() {
        return IdWorker.getIdStr();
    }
}
