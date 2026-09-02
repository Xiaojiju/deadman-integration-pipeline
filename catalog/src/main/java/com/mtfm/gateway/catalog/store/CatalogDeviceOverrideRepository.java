package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.DeviceFieldOverrideEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity;
import com.mtfm.gateway.catalog.entity.DeviceTopicOverrideEntity;
import com.mtfm.gateway.catalog.mapper.DeviceFieldOverrideMapper;
import com.mtfm.gateway.catalog.mapper.DeviceFunctionOverrideMapper;
import com.mtfm.gateway.catalog.mapper.DeviceTopicOverrideMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.EavPropertySupport;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class CatalogDeviceOverrideRepository {

    private final DeviceFunctionOverrideMapper functionOverrides;
    private final DeviceFieldOverrideMapper fieldOverrides;
    private final DeviceTopicOverrideMapper topicOverrides;

    public CatalogDeviceOverrideRepository(
            DeviceFunctionOverrideMapper functionOverrides,
            DeviceFieldOverrideMapper fieldOverrides,
            DeviceTopicOverrideMapper topicOverrides) {
        this.functionOverrides = functionOverrides;
        this.fieldOverrides = fieldOverrides;
        this.topicOverrides = topicOverrides;
    }

    public List<PropertyItem> listFunctionOverrides(String deviceId, String functionId) {
        return functionOverrides.selectList(new QueryWrapper<DeviceFunctionOverrideEntity>()
                        .eq("device_id", deviceId)
                        .eq("function_id", functionId)
                        .orderByAsc("attribute"))
                .stream()
                .map(EavPropertySupport::toItem)
                .toList();
    }

    public List<DeviceFunctionOverrideEntity> listAllFunctionOverrides(String deviceId) {
        return functionOverrides.selectList(new QueryWrapper<DeviceFunctionOverrideEntity>()
                .eq("device_id", deviceId)
                .orderByAsc("function_id")
                .orderByAsc("attribute"));
    }

    public void replaceFunctionOverrides(String deviceId, String functionId, List<PropertyItem> items) {
        functionOverrides.delete(new QueryWrapper<DeviceFunctionOverrideEntity>()
                .eq("device_id", deviceId)
                .eq("function_id", functionId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            DeviceFunctionOverrideEntity row = new DeviceFunctionOverrideEntity();
            row.setId(SnowflakeIds.next());
            row.setDeviceId(deviceId);
            row.setFunctionId(functionId);
            EavPropertySupport.applyItem(row, item);
            functionOverrides.insert(row);
        }
    }

    public void replaceAllFunctionOverrides(String deviceId, Map<String, List<PropertyItem>> byFunction) {
        functionOverrides.delete(new QueryWrapper<DeviceFunctionOverrideEntity>().eq("device_id", deviceId));
        if (byFunction == null || byFunction.isEmpty()) {
            return;
        }
        byFunction.forEach((functionId, items) -> {
            if (items == null) {
                return;
            }
            for (PropertyItem item : items) {
                DeviceFunctionOverrideEntity row = new DeviceFunctionOverrideEntity();
                row.setId(SnowflakeIds.next());
                row.setDeviceId(deviceId);
                row.setFunctionId(functionId);
                EavPropertySupport.applyItem(row, item);
                functionOverrides.insert(row);
            }
        });
    }

    public void deleteAll(String deviceId) {
        functionOverrides.delete(new QueryWrapper<DeviceFunctionOverrideEntity>().eq("device_id", deviceId));
        fieldOverrides.delete(new QueryWrapper<DeviceFieldOverrideEntity>().eq("device_id", deviceId));
        topicOverrides.delete(new QueryWrapper<DeviceTopicOverrideEntity>().eq("device_id", deviceId));
    }

    public Map<String, Object> listFieldOverrides(String deviceId, String functionId) {
        return fieldOverrides.selectList(new QueryWrapper<DeviceFieldOverrideEntity>()
                        .eq("device_id", deviceId)
                        .eq("function_id", functionId))
                .stream()
                .collect(Collectors.toMap(
                        DeviceFieldOverrideEntity::getFieldPath,
                        row -> parseJsonValue(row.getFieldValue()),
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    public Map<String, String> listTopicOverrides(String deviceId, String functionId) {
        return topicOverrides.selectList(new QueryWrapper<DeviceTopicOverrideEntity>()
                        .eq("device_id", deviceId)
                        .eq("function_id", functionId))
                .stream()
                .collect(Collectors.toMap(
                        DeviceTopicOverrideEntity::getTopicSlot,
                        DeviceTopicOverrideEntity::getTopicValue,
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    public void replaceFieldOverrides(String deviceId, String functionId, Map<String, Object> overrides) {
        fieldOverrides.delete(new QueryWrapper<DeviceFieldOverrideEntity>()
                .eq("device_id", deviceId)
                .eq("function_id", functionId));
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        overrides.forEach((path, value) -> {
            if (path == null || path.isBlank() || value == null) {
                return;
            }
            DeviceFieldOverrideEntity row = new DeviceFieldOverrideEntity();
            row.setId(SnowflakeIds.next());
            row.setDeviceId(deviceId);
            row.setFunctionId(functionId);
            row.setFieldPath(path.trim());
            row.setFieldValue(com.mtfm.gateway.catalog.json.JsonMaps.write(value));
            fieldOverrides.insert(row);
        });
    }

    public void replaceTopicOverrides(String deviceId, String functionId, Map<String, String> overrides) {
        topicOverrides.delete(new QueryWrapper<DeviceTopicOverrideEntity>()
                .eq("device_id", deviceId)
                .eq("function_id", functionId));
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        overrides.forEach((slot, topic) -> {
            if (slot == null || slot.isBlank() || topic == null || topic.isBlank()) {
                return;
            }
            DeviceTopicOverrideEntity row = new DeviceTopicOverrideEntity();
            row.setId(SnowflakeIds.next());
            row.setDeviceId(deviceId);
            row.setFunctionId(functionId);
            row.setTopicSlot(slot.trim());
            row.setTopicValue(topic.trim());
            topicOverrides.insert(row);
        });
    }

    private static Object parseJsonValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed, Object.class);
            } catch (Exception ignored) {
                return raw;
            }
        }
        return raw;
    }
}
