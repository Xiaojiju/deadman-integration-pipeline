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
        String payloadEncoding) {
}
