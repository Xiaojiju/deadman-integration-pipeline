package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;

/**
 * 单个产品功能的读写 Option 一次加载结果，避免列表路径反复查四张表。
 */
public record FunctionOptionBundle(
        List<WriteFieldOption> writeFields,
        List<WriteFieldOption> readFields,
        List<ValueOption> writeValueOptions,
        List<ValueOption> readValueOptions
) {

    public static FunctionOptionBundle empty() {
        return new FunctionOptionBundle(List.of(), List.of(), List.of(), List.of());
    }

    public FunctionOptionBundle {
        writeFields = writeFields == null ? List.of() : List.copyOf(writeFields);
        readFields = readFields == null ? List.of() : List.copyOf(readFields);
        writeValueOptions = writeValueOptions == null ? List.of() : List.copyOf(writeValueOptions);
        readValueOptions = readValueOptions == null ? List.of() : List.copyOf(readValueOptions);
    }
}
