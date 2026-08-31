package com.mtfm.gateway.spi.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 插件/阶段轨迹条目，记录 Normalize 或插件链中的处理节点。
 *
 * @param name 阶段或插件名
 * @param note 说明
 * @param at   记录时间
 */
public record TraceEntry(String name, String note, Instant at) {

    public TraceEntry {
        Objects.requireNonNull(name, "name");
        note = note == null ? "" : note;
        at = at == null ? Instant.now() : at;
    }

    /** 创建当前时刻的轨迹条目。 */
    public static TraceEntry of(String name, String note) {
        return new TraceEntry(name, note, Instant.now());
    }
}
