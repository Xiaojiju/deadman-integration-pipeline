package com.mtfm.gateway.spi.model;

import com.mtfm.gateway.spi.option.Option;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
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
 * @param payloadEncoding    南向载荷编码，默认 JSON
 * @param replyTopicSlot     MQTT 应答订阅 slot，空则不等待设备回包
 * @param correlationPath    回包中关联号字段 path
 * @param resultPath         已废弃；成败由 {@code readFields} 应答取值判定
 * @param replyTimeoutMs     等待设备应答毫秒数
 * @param scheduleIntervalMs 定时下发间隔（阶段 C）
 * @param scheduleEnabled    是否启用定时下发（阶段 C）
 * @param scaleOp            入站换算运算符 add/subtract/multiply/divide
 * @param scaleOperand       入站换算操作数
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
        List<ValueOption> readValueOptions,
        PayloadEncoding payloadEncoding,
        String replyTopicSlot,
        String correlationPath,
        String resultPath,
        Integer replyTimeoutMs,
        Long scheduleIntervalMs,
        boolean scheduleEnabled,
        String scaleOp,
        String scaleOperand
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
        payloadEncoding = payloadEncoding == null ? PayloadEncoding.JSON : payloadEncoding;
        replyTopicSlot = blankToNull(replyTopicSlot);
        correlationPath = blankToNull(correlationPath);
        resultPath = blankToNull(resultPath);
    }

    /** 兼容旧 17 参构造（无换算）。 */
    public FunctionDef(
            String functionId,
            String accessType,
            int accessPermission,
            Option optionSchema,
            List<PropertyItem> properties,
            ValueAccessType writeAccessType,
            List<ValueOption> writeValueOptions,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> readValueOptions,
            PayloadEncoding payloadEncoding,
            String replyTopicSlot,
            String correlationPath,
            String resultPath,
            Integer replyTimeoutMs,
            Long scheduleIntervalMs,
            boolean scheduleEnabled) {
        this(functionId, accessType, accessPermission, optionSchema, properties, writeAccessType,
                writeValueOptions, writeFields, readFields, readValueOptions, payloadEncoding,
                replyTopicSlot, correlationPath, resultPath, replyTimeoutMs, scheduleIntervalMs,
                scheduleEnabled, null, null);
    }

    /** 兼容旧 11 参构造（无应答/调度字段）。 */
    public FunctionDef(
            String functionId,
            String accessType,
            int accessPermission,
            Option optionSchema,
            List<PropertyItem> properties,
            ValueAccessType writeAccessType,
            List<ValueOption> writeValueOptions,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> readValueOptions,
            PayloadEncoding payloadEncoding) {
        this(functionId, accessType, accessPermission, optionSchema, properties, writeAccessType,
                writeValueOptions, writeFields, readFields, readValueOptions, payloadEncoding,
                null, null, null, null, null, false, null, null);
    }

    /** 兼容旧 10 参构造（无 payloadEncoding）。 */
    public FunctionDef(
            String functionId,
            String accessType,
            int accessPermission,
            Option optionSchema,
            List<PropertyItem> properties,
            ValueAccessType writeAccessType,
            List<ValueOption> writeValueOptions,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> readValueOptions) {
        this(functionId, accessType, accessPermission, optionSchema, properties, writeAccessType,
                writeValueOptions, writeFields, readFields, readValueOptions, PayloadEncoding.JSON);
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

    public boolean awaitsReply() {
        return replyTopicSlot != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
