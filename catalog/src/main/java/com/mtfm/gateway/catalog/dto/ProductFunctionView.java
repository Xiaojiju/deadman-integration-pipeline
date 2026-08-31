package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Map;

/**
 * 产品功能视图
 * ProductFunctionView：产品功能视图，包含产品功能的所有信息。
 * 
 * @param id                主键
 * @param productId         产品主键
 * @param functionId        功能主键
 * @param description       描述
 * @param accessType        访问类型
 * @param accessPermission  访问权限
 * @param capabilityType    能力类型
 * @param writeAccessType   写访问类型
 * @param properties        属性
 * @param writeValueOptions 写值选项
 * @param writeFields       写字段
 * @param readValueOptions  读值选项
 * @param protocolMapping   协议映射
 * @param sortIndex         排序索引
 */
public record ProductFunctionView(
        String id,
        String productId,
        String functionId,
        String description,
        String accessType,
        Integer accessPermission,
        String capabilityType,
        String writeAccessType,
        List<PropertyItem> properties,
        List<ValueOption> writeValueOptions,
        List<WriteFieldOption> writeFields,
        List<ValueOption> readValueOptions,
        Map<String, Object> protocolMapping,
        Integer sortIndex) {
}
