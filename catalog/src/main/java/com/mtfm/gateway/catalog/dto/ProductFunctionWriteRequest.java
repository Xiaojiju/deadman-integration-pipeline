package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;

/**
 * 创建/更新产品功能请求。
 *
 * @param functionId        功能业务 ID（FIXED 时必须在能力模板列表内）
 * @param accessType        访问类型 READ / WRITE
 * @param accessPermission  权限位
 * @param capabilityType    南向能力类型
 * @param writeAccessType   VALUE / STRUCT（可由 payloadMode 代替）
 * @param properties        FIXED 能力默认参数（EAV）
 * @param writeValueOptions VALUE 枚举（也可落在 writeFields.options）
 * @param writeFields       扁平写字段（path / source / constant）
 * @param readFields        READ 扁平字段
 * @param readValueOptions  读值映射枚举（可选）
 * @param sortIndex         排序
 * @param description       功能说明
 * @param payloadMode       VALUE / STRUCT
 * @param payloadEncoding   JSON / HEX / BINARY
 * @param replyTopicSlot    MQTT 应答订阅 slot 或完整 topic
 * @param correlationPath   回包关联号字段
 * @param correlationCommandPath 下发载荷中与回包关联的字段；$deviceCode 表示设备编码
 * @param resultPath        回包成败字段
 * @param replyTimeoutMs    等待应答毫秒
 * @param scheduleIntervalMs 定时下发间隔毫秒
 * @param scheduleEnabled   是否启用定时下发
 */
public record ProductFunctionWriteRequest(
        String functionId,
        String accessType,
        Integer accessPermission,
        String capabilityType,
        String writeAccessType,
        List<PropertyItem> properties,
        List<ValueOption> writeValueOptions,
        List<WriteFieldOption> writeFields,
        List<WriteFieldOption> readFields,
        List<ValueOption> readValueOptions,
        Integer sortIndex,
        String description,
        String publishTopicSlot,
        String subscribeTopicSlot,
        String payloadMode,
        String payloadEncoding,
        String replyTopicSlot,
        String correlationPath,
        String correlationCommandPath,
        String resultPath,
        Integer replyTimeoutMs,
        Long scheduleIntervalMs,
        Boolean scheduleEnabled) {

    public ProductFunctionWriteRequest(
            String functionId,
            String accessType,
            Integer accessPermission,
            String capabilityType,
            String writeAccessType,
            List<PropertyItem> properties,
            List<ValueOption> writeValueOptions,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> readValueOptions,
            Integer sortIndex,
            String description,
            String publishTopicSlot,
            String subscribeTopicSlot,
            String payloadMode,
            String payloadEncoding) {
        this(functionId, accessType, accessPermission, capabilityType, writeAccessType, properties,
                writeValueOptions, writeFields, readFields, readValueOptions, sortIndex, description,
                publishTopicSlot, subscribeTopicSlot, payloadMode, payloadEncoding,
                null, null, null, null, null, null, null);
    }
}
