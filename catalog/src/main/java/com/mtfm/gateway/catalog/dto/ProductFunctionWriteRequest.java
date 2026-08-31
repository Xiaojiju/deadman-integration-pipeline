package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Map;

/**
 * 创建/更新产品功能请求。
 *
 * @param functionId        功能业务 ID（FIXED 时必须在能力模板列表内）
 * @param accessType        访问类型 READ / WRITE
 * @param accessPermission  权限位
 * @param capabilityType    南向能力类型
 * @param writeAccessType   VALUE / STRUCT
 * @param properties        功能默认参数（EAV）：键值落在 gw_function_property，
 *                          运行时与设备覆盖合并后作为指令参数；
 *                          有模板时 attribute 应对齐 SchemaField.name（如 command、target）
 * @param parameters        兼容旧 Map 入参，等价于 properties
 * @param writeValueOptions VALUE 写下发枚举（如 open/close）；FIXED 时取值须来自模板 choices
 * @param writeFields       STRUCT 写字段树
 * @param readValueOptions  读映射枚举；FIXED 时取值须来自模板规定（若有）
 * @param protocolMapping   协议层映射（寄存器 offset 等），与业务参数分离
 * @param sortIndex         排序
 * @param description       功能说明
 */
public record ProductFunctionWriteRequest(
        String functionId,
        String accessType,
        Integer accessPermission,
        String capabilityType,
        String writeAccessType,
        List<PropertyItem> properties,
        Map<String, Object> parameters,
        List<ValueOption> writeValueOptions,
        List<WriteFieldOption> writeFields,
        List<ValueOption> readValueOptions,
        Map<String, Object> protocolMapping,
        Integer sortIndex,
        String description) {
}
