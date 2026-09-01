package com.mtfm.gateway.spi.model;

import com.mtfm.gateway.spi.option.Option;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;

/**
 * 功能目录项。描述设备有什么功能，不是流水线上的活节点。
 *
 * @param functionId         功能 ID
 * @param accessType         访问类型（READ / WRITE）
 * @param accessPermission   读写权限码，与 {@link AccessPermission} 对齐
 * @param optionSchema       产品侧 option 树（兼容旧投影）
 * @param properties         EAV 属性列表（优先于 optionSchema）
 * @param writeAccessType    VALUE / STRUCT
 * @param writeValueOptions  VALUE 模式写选项
 * @param writeFields        STRUCT 模式写字段
 * @param readFields         READ 功能字段
 * @param readValueOptions   读值映射
 */
public record FunctionDef(
        String functionId,
        String accessType,
        int accessPermission,
        Option optionSchema,
        List<PropertyItem> properties,
        ValueAccessType writeAccessType,
        List<ValueOption> writeValueOptions,
        List<WriteFieldOption> writeFields,
        List<WriteFieldOption> readFields,
        List<ValueOption> readValueOptions
) {

    public FunctionDef {
        if (functionId == null || functionId.isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        if (accessType == null || accessType.isBlank()) {
            accessType = "WRITE";
        }
        properties = properties == null ? List.of() : List.copyOf(properties);
        writeAccessType = writeAccessType == null ? ValueAccessType.VALUE : writeAccessType;
        writeValueOptions = writeValueOptions == null ? List.of() : List.copyOf(writeValueOptions);
        writeFields = writeFields == null ? List.of() : List.copyOf(writeFields);
        readFields = readFields == null ? List.of() : List.copyOf(readFields);
        readValueOptions = readValueOptions == null ? List.of() : List.copyOf(readValueOptions);
    }

    /** 兼容旧四参构造。 */
    public FunctionDef(String functionId, String accessType, int accessPermission, Option optionSchema) {
        this(functionId, accessType, accessPermission, optionSchema, List.of(), ValueAccessType.VALUE,
                List.of(), List.of(), List.of(), List.of());
    }

    /** 兼容旧九参构造（无 readFields）。 */
    public FunctionDef(
            String functionId,
            String accessType,
            int accessPermission,
            Option optionSchema,
            List<PropertyItem> properties,
            ValueAccessType writeAccessType,
            List<ValueOption> writeValueOptions,
            List<WriteFieldOption> writeFields,
            List<ValueOption> readValueOptions) {
        this(functionId, accessType, accessPermission, optionSchema, properties, writeAccessType,
                writeValueOptions, writeFields, List.of(), readValueOptions);
    }

    /** 仅含 functionId 与 accessType 的简化构造。 */
    public FunctionDef(String functionId, String accessType) {
        this(functionId, accessType, AccessPermission.WRITE.code(), null);
    }
}
