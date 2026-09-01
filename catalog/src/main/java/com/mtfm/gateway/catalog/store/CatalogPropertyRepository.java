package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.ChannelPropertyEntity;
import com.mtfm.gateway.catalog.entity.DeviceFieldOverrideEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity;
import com.mtfm.gateway.catalog.entity.DeviceTopicOverrideEntity;
import com.mtfm.gateway.catalog.entity.EndpointPropertyEntity;
import com.mtfm.gateway.catalog.entity.FunctionPropertyEntity;
import com.mtfm.gateway.catalog.entity.ReadFieldEntity;
import com.mtfm.gateway.catalog.entity.ReadFieldValueOptionEntity;
import com.mtfm.gateway.catalog.entity.ReadValueOptionEntity;
import com.mtfm.gateway.catalog.entity.WriteOptionEntity;
import com.mtfm.gateway.catalog.entity.WriteValueOptionEntity;
import com.mtfm.gateway.catalog.mapper.ChannelPropertyMapper;
import com.mtfm.gateway.catalog.mapper.DeviceFieldOverrideMapper;
import com.mtfm.gateway.catalog.mapper.DeviceFunctionOverrideMapper;
import com.mtfm.gateway.catalog.mapper.DeviceTopicOverrideMapper;
import com.mtfm.gateway.catalog.mapper.EndpointPropertyMapper;
import com.mtfm.gateway.catalog.mapper.FunctionPropertyMapper;
import com.mtfm.gateway.catalog.mapper.ReadFieldMapper;
import com.mtfm.gateway.catalog.mapper.ReadFieldValueOptionMapper;
import com.mtfm.gateway.catalog.mapper.ReadValueOptionMapper;
import com.mtfm.gateway.catalog.mapper.WriteOptionMapper;
import com.mtfm.gateway.catalog.mapper.WriteValueOptionMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EAV 属性与读写 Option 的批量替换仓储（删旧插新，对齐旧 property）。
 */
@Repository
public class CatalogPropertyRepository {

    private final ChannelPropertyMapper channelProperties;
    private final EndpointPropertyMapper endpointProperties;
    private final FunctionPropertyMapper functionProperties;
    private final DeviceFunctionOverrideMapper deviceOverrides;
    private final WriteOptionMapper writeOptions;
    private final WriteValueOptionMapper writeValueOptions;
    private final ReadValueOptionMapper readValueOptions;
    private final ReadFieldMapper readFields;
    private final ReadFieldValueOptionMapper readFieldValueOptions;
    private final DeviceFieldOverrideMapper deviceFieldOverrides;
    private final DeviceTopicOverrideMapper deviceTopicOverrides;

    public CatalogPropertyRepository(
            ChannelPropertyMapper channelProperties,
            EndpointPropertyMapper endpointProperties,
            FunctionPropertyMapper functionProperties,
            DeviceFunctionOverrideMapper deviceOverrides,
            WriteOptionMapper writeOptions,
            WriteValueOptionMapper writeValueOptions,
            ReadValueOptionMapper readValueOptions,
            ReadFieldMapper readFields,
            ReadFieldValueOptionMapper readFieldValueOptions,
            DeviceFieldOverrideMapper deviceFieldOverrides,
            DeviceTopicOverrideMapper deviceTopicOverrides) {
        this.channelProperties = channelProperties;
        this.endpointProperties = endpointProperties;
        this.functionProperties = functionProperties;
        this.deviceOverrides = deviceOverrides;
        this.writeOptions = writeOptions;
        this.writeValueOptions = writeValueOptions;
        this.readValueOptions = readValueOptions;
        this.readFields = readFields;
        this.readFieldValueOptions = readFieldValueOptions;
        this.deviceFieldOverrides = deviceFieldOverrides;
        this.deviceTopicOverrides = deviceTopicOverrides;
    }

    // ——— Channel ———

    public List<PropertyItem> listChannelProperties(String channelId) {
        return channelProperties.selectList(new QueryWrapper<ChannelPropertyEntity>()
                        .eq("channel_id", channelId)
                        .orderByAsc("attribute"))
                .stream()
                .map(CatalogPropertyRepository::toItem)
                .toList();
    }

    public void replaceChannelProperties(String channelId, List<PropertyItem> items) {
        channelProperties.delete(new QueryWrapper<ChannelPropertyEntity>().eq("channel_id", channelId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            ChannelPropertyEntity row = new ChannelPropertyEntity();
            row.setId(SnowflakeIds.next());
            row.setChannelId(channelId);
            applyItem(row, item);
            channelProperties.insert(row);
        }
    }

    public void deleteChannelProperties(String channelId) {
        channelProperties.delete(new QueryWrapper<ChannelPropertyEntity>().eq("channel_id", channelId));
    }

    // ——— Endpoint ———

    public List<PropertyItem> listEndpointProperties(String endpointId) {
        return endpointProperties.selectList(new QueryWrapper<EndpointPropertyEntity>()
                        .eq("endpoint_id", endpointId)
                        .orderByAsc("attribute"))
                .stream()
                .map(CatalogPropertyRepository::toItem)
                .toList();
    }

    public void replaceEndpointProperties(String endpointId, List<PropertyItem> items) {
        endpointProperties.delete(new QueryWrapper<EndpointPropertyEntity>().eq("endpoint_id", endpointId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            EndpointPropertyEntity row = new EndpointPropertyEntity();
            row.setId(SnowflakeIds.next());
            row.setEndpointId(endpointId);
            applyItem(row, item);
            endpointProperties.insert(row);
        }
    }

    public void deleteEndpointProperties(String endpointId) {
        endpointProperties.delete(new QueryWrapper<EndpointPropertyEntity>().eq("endpoint_id", endpointId));
    }

    // ——— Function property ———

    public List<PropertyItem> listFunctionProperties(String productFunctionId) {
        return functionProperties.selectList(new QueryWrapper<FunctionPropertyEntity>()
                        .eq("product_function_id", productFunctionId)
                        .orderByAsc("attribute"))
                .stream()
                .map(CatalogPropertyRepository::toItem)
                .toList();
    }

    public void replaceFunctionProperties(String productFunctionId, List<PropertyItem> items) {
        functionProperties.delete(new QueryWrapper<FunctionPropertyEntity>()
                .eq("product_function_id", productFunctionId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            FunctionPropertyEntity row = new FunctionPropertyEntity();
            row.setId(SnowflakeIds.next());
            row.setProductFunctionId(productFunctionId);
            applyItem(row, item);
            functionProperties.insert(row);
        }
    }

    public void deleteFunctionProperties(String productFunctionId) {
        functionProperties.delete(new QueryWrapper<FunctionPropertyEntity>()
                .eq("product_function_id", productFunctionId));
    }

    // ——— Device function override ———

    public List<PropertyItem> listDeviceOverrides(String deviceId, String functionId) {
        return deviceOverrides.selectList(new QueryWrapper<DeviceFunctionOverrideEntity>()
                        .eq("device_id", deviceId)
                        .eq("function_id", functionId)
                        .orderByAsc("attribute"))
                .stream()
                .map(CatalogPropertyRepository::toItem)
                .toList();
    }

    public List<DeviceFunctionOverrideEntity> listAllDeviceOverrides(String deviceId) {
        return deviceOverrides.selectList(new QueryWrapper<DeviceFunctionOverrideEntity>()
                .eq("device_id", deviceId)
                .orderByAsc("function_id")
                .orderByAsc("attribute"));
    }

    public void replaceDeviceOverrides(String deviceId, String functionId, List<PropertyItem> items) {
        deviceOverrides.delete(new QueryWrapper<DeviceFunctionOverrideEntity>()
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
            applyItem(row, item);
            deviceOverrides.insert(row);
        }
    }

    /**
     * 按 functionId 分组批量替换设备覆盖；传入的 map key 为 functionId。
     */
    public void replaceAllDeviceOverrides(String deviceId, java.util.Map<String, List<PropertyItem>> byFunction) {
        deviceOverrides.delete(new QueryWrapper<DeviceFunctionOverrideEntity>().eq("device_id", deviceId));
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
                applyItem(row, item);
                deviceOverrides.insert(row);
            }
        });
    }

    public void deleteDeviceOverrides(String deviceId) {
        deviceOverrides.delete(new QueryWrapper<DeviceFunctionOverrideEntity>().eq("device_id", deviceId));
        deviceFieldOverrides.delete(new QueryWrapper<DeviceFieldOverrideEntity>().eq("device_id", deviceId));
        deviceTopicOverrides.delete(new QueryWrapper<DeviceTopicOverrideEntity>().eq("device_id", deviceId));
    }

    public Map<String, Object> listDeviceFieldOverrides(String deviceId, String functionId) {
        return deviceFieldOverrides.selectList(new QueryWrapper<DeviceFieldOverrideEntity>()
                        .eq("device_id", deviceId)
                        .eq("function_id", functionId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        DeviceFieldOverrideEntity::getFieldPath,
                        row -> parseJsonValue(row.getFieldValue()),
                        (a, b) -> b,
                        java.util.LinkedHashMap::new));
    }

    public Map<String, String> listDeviceTopicOverrides(String deviceId, String functionId) {
        return deviceTopicOverrides.selectList(new QueryWrapper<DeviceTopicOverrideEntity>()
                        .eq("device_id", deviceId)
                        .eq("function_id", functionId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        DeviceTopicOverrideEntity::getTopicSlot,
                        DeviceTopicOverrideEntity::getTopicValue,
                        (a, b) -> b,
                        java.util.LinkedHashMap::new));
    }

    public void replaceDeviceFieldOverrides(String deviceId, String functionId, Map<String, Object> overrides) {
        deviceFieldOverrides.delete(new QueryWrapper<DeviceFieldOverrideEntity>()
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
            deviceFieldOverrides.insert(row);
        });
    }

    public void replaceDeviceTopicOverrides(String deviceId, String functionId, Map<String, String> overrides) {
        deviceTopicOverrides.delete(new QueryWrapper<DeviceTopicOverrideEntity>()
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
            deviceTopicOverrides.insert(row);
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

    // ——— Write / Read options ———

    public List<ValueOption> listWriteValueOptions(String productFunctionId) {
        return writeValueOptions.selectList(new QueryWrapper<WriteValueOptionEntity>()
                        .eq("parent_id", productFunctionId)
                        .orderByAsc("option_value"))
                .stream()
                .map(CatalogPropertyRepository::toValueOption)
                .toList();
    }

    public List<WriteFieldOption> listWriteFields(String productFunctionId) {
        List<WriteOptionEntity> fields = writeOptions.selectList(new QueryWrapper<WriteOptionEntity>()
                .eq("product_function_id", productFunctionId)
                .orderByAsc("field"));
        List<WriteFieldOption> result = new ArrayList<>();
        for (WriteOptionEntity field : fields) {
            List<ValueOption> options = writeValueOptions.selectList(new QueryWrapper<WriteValueOptionEntity>()
                            .eq("parent_id", field.getId())
                            .orderByAsc("option_value"))
                    .stream()
                    .map(CatalogPropertyRepository::toValueOption)
                    .toList();
            result.add(new WriteFieldOption(
                    field.getField(),
                    field.getDescription(),
                    field.getAccessDataType(),
                    field.getTransformDataType(),
                    Boolean.TRUE.equals(field.getIgnoreRequest()),
                    options,
                    field.getFormat(),
                    field.getValueGenerator()));
        }
        return List.copyOf(result);
    }

    public List<WriteFieldOption> listReadFields(String productFunctionId) {
        List<ReadFieldEntity> fields = readFields.selectList(new QueryWrapper<ReadFieldEntity>()
                .eq("product_function_id", productFunctionId)
                .orderByAsc("field"));
        List<WriteFieldOption> result = new ArrayList<>();
        for (ReadFieldEntity field : fields) {
            List<ValueOption> options = readFieldValueOptions.selectList(new QueryWrapper<ReadFieldValueOptionEntity>()
                            .eq("parent_id", field.getId())
                            .orderByAsc("option_value"))
                    .stream()
                    .map(CatalogPropertyRepository::toReadFieldValueOption)
                    .toList();
            result.add(new WriteFieldOption(
                    field.getField(),
                    field.getDescription(),
                    field.getAccessDataType(),
                    field.getTransformDataType(),
                    Boolean.TRUE.equals(field.getIgnoreRequest()),
                    options,
                    field.getFormat(),
                    field.getValueGenerator()));
        }
        return List.copyOf(result);
    }

    public List<ValueOption> listReadValueOptions(String productFunctionId) {
        return readValueOptions.selectList(new QueryWrapper<ReadValueOptionEntity>()
                        .eq("product_function_id", productFunctionId)
                        .orderByAsc("option_value"))
                .stream()
                .map(CatalogPropertyRepository::toReadValueOption)
                .toList();
    }

    /**
     * 替换功能的写选项树。VALUE 模式写 writeValueOptions；STRUCT 模式写 writeFields。
     */
    public void replaceWriteOptions(
            String productFunctionId,
            ValueAccessType accessType,
            List<ValueOption> valueOptions,
            List<WriteFieldOption> writeFields) {
        deleteWriteOptions(productFunctionId);
        ValueAccessType mode = accessType == null ? ValueAccessType.VALUE : accessType;
        if (mode == ValueAccessType.STRUCT) {
            if (writeFields == null) {
                return;
            }
            for (WriteFieldOption field : writeFields) {
                WriteOptionEntity row = new WriteOptionEntity();
                row.setId(SnowflakeIds.next());
                row.setProductFunctionId(productFunctionId);
                row.setField(field.field());
                row.setDescription(field.description());
                row.setAccessDataType(field.accessDataType());
                row.setTransformDataType(field.transformDataType());
                row.setIgnoreRequest(field.ignoreRequest());
                row.setFormat(field.format());
                row.setValueGenerator(field.valueGenerator());
                writeOptions.insert(row);
                insertWriteValueOptions(row.getId(), field.options());
            }
            return;
        }
        insertWriteValueOptions(productFunctionId, valueOptions);
    }

    public void replaceReadFields(String productFunctionId, List<WriteFieldOption> readFieldsList) {
        deleteReadFields(productFunctionId);
        if (readFieldsList == null || readFieldsList.isEmpty()) {
            return;
        }
        for (WriteFieldOption field : readFieldsList) {
            ReadFieldEntity row = new ReadFieldEntity();
            row.setId(SnowflakeIds.next());
            row.setProductFunctionId(productFunctionId);
            row.setField(field.field());
            row.setDescription(field.description());
            row.setAccessDataType(field.accessDataType());
            row.setTransformDataType(field.transformDataType());
            row.setIgnoreRequest(field.ignoreRequest());
            row.setFormat(field.format());
            row.setValueGenerator(field.valueGenerator());
            readFields.insert(row);
            insertReadFieldValueOptions(row.getId(), field.options());
        }
    }

    public void replaceReadValueOptions(String productFunctionId, List<ValueOption> options) {
        readValueOptions.delete(new QueryWrapper<ReadValueOptionEntity>()
                .eq("product_function_id", productFunctionId));
        if (options == null || options.isEmpty()) {
            return;
        }
        for (ValueOption option : options) {
            ReadValueOptionEntity row = new ReadValueOptionEntity();
            row.setId(SnowflakeIds.next());
            row.setProductFunctionId(productFunctionId);
            applyValueOption(row, option);
            readValueOptions.insert(row);
        }
    }

    public void deleteWriteOptions(String productFunctionId) {
        List<WriteOptionEntity> fields = writeOptions.selectList(new QueryWrapper<WriteOptionEntity>()
                .eq("product_function_id", productFunctionId));
        for (WriteOptionEntity field : fields) {
            writeValueOptions.delete(new QueryWrapper<WriteValueOptionEntity>().eq("parent_id", field.getId()));
        }
        writeOptions.delete(new QueryWrapper<WriteOptionEntity>().eq("product_function_id", productFunctionId));
        writeValueOptions.delete(new QueryWrapper<WriteValueOptionEntity>().eq("parent_id", productFunctionId));
    }

    public void deleteReadValueOptions(String productFunctionId) {
        readValueOptions.delete(new QueryWrapper<ReadValueOptionEntity>()
                .eq("product_function_id", productFunctionId));
    }

    public void deleteReadFields(String productFunctionId) {
        List<ReadFieldEntity> fields = readFields.selectList(new QueryWrapper<ReadFieldEntity>()
                .eq("product_function_id", productFunctionId));
        for (ReadFieldEntity field : fields) {
            readFieldValueOptions.delete(new QueryWrapper<ReadFieldValueOptionEntity>()
                    .eq("parent_id", field.getId()));
        }
        readFields.delete(new QueryWrapper<ReadFieldEntity>().eq("product_function_id", productFunctionId));
    }

    /** 删除产品功能关联的全部 EAV / Option。 */
    public void deleteAllForProductFunction(String productFunctionId) {
        deleteFunctionProperties(productFunctionId);
        deleteWriteOptions(productFunctionId);
        deleteReadFields(productFunctionId);
        deleteReadValueOptions(productFunctionId);
    }

    private void insertReadFieldValueOptions(String parentId, List<ValueOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        for (ValueOption option : options) {
            ReadFieldValueOptionEntity row = new ReadFieldValueOptionEntity();
            row.setId(SnowflakeIds.next());
            row.setParentId(parentId);
            row.setDescription(option.description());
            row.setOptionValue(option.optionValue());
            row.setMappingValue(option.mappingValue());
            row.setAccessDataType(option.accessDataType());
            row.setTransformDataType(option.transformDataType());
            row.setIsDefault(option.isDefault());
            readFieldValueOptions.insert(row);
        }
    }

    private void insertWriteValueOptions(String parentId, List<ValueOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        for (ValueOption option : options) {
            WriteValueOptionEntity row = new WriteValueOptionEntity();
            row.setId(SnowflakeIds.next());
            row.setParentId(parentId);
            row.setDescription(option.description());
            row.setOptionValue(option.optionValue());
            row.setMappingValue(option.mappingValue());
            row.setAccessDataType(option.accessDataType());
            row.setTransformDataType(option.transformDataType());
            row.setIsDefault(option.isDefault());
            writeValueOptions.insert(row);
        }
    }

    private static void applyItem(com.mtfm.gateway.catalog.entity.AbstractPropertyEntity row, PropertyItem item) {
        row.setAttribute(item.attribute());
        row.setAttributeValue(item.attributeValue());
        row.setDataType(item.dataType());
        row.setDescription(item.description());
    }

    private static void applyValueOption(ReadValueOptionEntity row, ValueOption option) {
        row.setDescription(option.description());
        row.setOptionValue(option.optionValue());
        row.setMappingValue(option.mappingValue());
        row.setAccessDataType(option.accessDataType());
        row.setTransformDataType(option.transformDataType());
        row.setIsDefault(option.isDefault());
    }

    private static PropertyItem toItem(com.mtfm.gateway.catalog.entity.AbstractPropertyEntity row) {
        return new PropertyItem(row.getAttribute(), row.getAttributeValue(), row.getDataType(), row.getDescription());
    }

    private static ValueOption toValueOption(WriteValueOptionEntity row) {
        return new ValueOption(
                row.getOptionValue(),
                row.getMappingValue(),
                row.getDescription(),
                row.getAccessDataType(),
                row.getTransformDataType(),
                row.getIsDefault());
    }

    private static ValueOption toReadValueOption(ReadValueOptionEntity row) {
        return new ValueOption(
                row.getOptionValue(),
                row.getMappingValue(),
                row.getDescription(),
                row.getAccessDataType(),
                row.getTransformDataType(),
                row.getIsDefault());
    }

    private static ValueOption toReadFieldValueOption(ReadFieldValueOptionEntity row) {
        return new ValueOption(
                row.getOptionValue(),
                row.getMappingValue(),
                row.getDescription(),
                row.getAccessDataType(),
                row.getTransformDataType(),
                row.getIsDefault());
    }
}
