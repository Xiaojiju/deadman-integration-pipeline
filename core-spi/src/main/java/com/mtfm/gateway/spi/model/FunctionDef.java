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
 * @param functionId        功能 ID
 * @param accessType        访问类型（READ / WRITE）
 * @param accessPermission  读写权限码，与 {@link AccessPermission} 对齐
 * @param optionSchema      产品侧 option 树（兼容旧投影）
 * @param properties        EAV 属性列表（优先于 optionSchema）
 * @param writeAccessType   VALUE / STRUCT
 * @param writeValueOptions VALUE 模式写选项
 * @param writeFields       STRUCT 模式写字段
 * @param readFields        READ 功能字段
 * @param readValueOptions  读值映射
 * @param payloadEncoding   南向载荷编码，默认 JSON
 * @param reply             MQTT 应答约定；空则不等待设备回包
 * @param schedule          定时下发约定
 * @param scale             入站换算约定
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
        ReplySpec reply,
        ScheduleSpec schedule,
        ScaleSpec scale
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
        schedule = schedule == null ? ScheduleSpec.disabled() : schedule;
    }

    /**
     * MQTT 应答：订阅 slot、回包关联号 path、超时毫秒。
     */
    public record ReplySpec(String topicSlot, String correlationPath, Integer timeoutMs) {
        public ReplySpec {
            topicSlot = blankToNull(topicSlot);
            correlationPath = blankToNull(correlationPath);
        }

        public static ReplySpec of(String topicSlot, String correlationPath, Integer timeoutMs) {
            if (blankToNull(topicSlot) == null && blankToNull(correlationPath) == null && timeoutMs == null) {
                return null;
            }
            return new ReplySpec(topicSlot, correlationPath, timeoutMs);
        }
    }

    /**
     * 产品级定时下发。
     */
    public record ScheduleSpec(Long intervalMs, boolean enabled) {
        public static ScheduleSpec disabled() {
            return new ScheduleSpec(null, false);
        }

        public static ScheduleSpec of(Long intervalMs, boolean enabled) {
            if (intervalMs == null && !enabled) {
                return disabled();
            }
            return new ScheduleSpec(intervalMs, enabled);
        }
    }

    /**
     * 入站换算：add / subtract / multiply / divide。
     */
    public record ScaleSpec(String op, String operand) {
        public ScaleSpec {
            op = blankToNull(op);
            operand = blankToNull(operand);
        }

        public static ScaleSpec of(String op, String operand) {
            if (blankToNull(op) == null && blankToNull(operand) == null) {
                return null;
            }
            return new ScaleSpec(op, operand);
        }
    }

    public static FunctionDef of(String functionId, String accessType) {
        return builder(functionId).accessType(accessType).build();
    }

    public static Builder builder(String functionId) {
        return new Builder(functionId);
    }

    public boolean awaitsReply() {
        return replyTopicSlot() != null;
    }

    public String replyTopicSlot() {
        return reply == null ? null : reply.topicSlot();
    }

    public String correlationPath() {
        return reply == null ? null : reply.correlationPath();
    }

    public Integer replyTimeoutMs() {
        return reply == null ? null : reply.timeoutMs();
    }

    public Long scheduleIntervalMs() {
        return schedule == null ? null : schedule.intervalMs();
    }

    public boolean scheduleEnabled() {
        return schedule != null && schedule.enabled();
    }

    public String scaleOp() {
        return scale == null ? null : scale.op();
    }

    public String scaleOperand() {
        return scale == null ? null : scale.operand();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {
        private final String functionId;
        private String accessType = "WRITE";
        private int accessPermission = AccessPermission.WRITE.code();
        private Option optionSchema;
        private List<PropertyItem> properties = List.of();
        private ValueAccessType writeAccessType = ValueAccessType.VALUE;
        private List<ValueOption> writeValueOptions = List.of();
        private List<WriteFieldOption> writeFields = List.of();
        private List<WriteFieldOption> readFields = List.of();
        private List<ValueOption> readValueOptions = List.of();
        private PayloadEncoding payloadEncoding = PayloadEncoding.JSON;
        private ReplySpec reply;
        private ScheduleSpec schedule = ScheduleSpec.disabled();
        private ScaleSpec scale;

        private Builder(String functionId) {
            this.functionId = functionId;
        }

        public Builder accessType(String accessType) {
            this.accessType = accessType;
            return this;
        }

        public Builder accessPermission(int accessPermission) {
            this.accessPermission = accessPermission;
            return this;
        }

        public Builder optionSchema(Option optionSchema) {
            this.optionSchema = optionSchema;
            return this;
        }

        public Builder properties(List<PropertyItem> properties) {
            this.properties = properties;
            return this;
        }

        public Builder writeAccessType(ValueAccessType writeAccessType) {
            this.writeAccessType = writeAccessType;
            return this;
        }

        public Builder writeValueOptions(List<ValueOption> writeValueOptions) {
            this.writeValueOptions = writeValueOptions;
            return this;
        }

        public Builder writeFields(List<WriteFieldOption> writeFields) {
            this.writeFields = writeFields;
            return this;
        }

        public Builder readFields(List<WriteFieldOption> readFields) {
            this.readFields = readFields;
            return this;
        }

        public Builder readValueOptions(List<ValueOption> readValueOptions) {
            this.readValueOptions = readValueOptions;
            return this;
        }

        public Builder payloadEncoding(PayloadEncoding payloadEncoding) {
            this.payloadEncoding = payloadEncoding;
            return this;
        }

        public Builder reply(ReplySpec reply) {
            this.reply = reply;
            return this;
        }

        public Builder schedule(ScheduleSpec schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder scale(ScaleSpec scale) {
            this.scale = scale;
            return this;
        }

        public FunctionDef build() {
            return new FunctionDef(
                    functionId,
                    accessType,
                    accessPermission,
                    optionSchema,
                    properties,
                    writeAccessType,
                    writeValueOptions,
                    writeFields,
                    readFields,
                    readValueOptions,
                    payloadEncoding,
                    reply,
                    schedule,
                    scale);
        }
    }
}
