package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaValidator;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通道 connection / 端点 address 校验，以及通道视图密码脱敏。
 */
final class CatalogConnectionSupport {

    static final String SECRET_MASK = "••••";

    private CatalogConnectionSupport() {
    }

    static void validateConnection(CapabilityDescriptor descriptor, List<PropertyItem> items) {
        Map<String, Object> values = PropertySchemas.toValueMap(items);
        SchemaValidator.require(descriptor.connectionSchema(), values, "通道 connection");
        requireModbusTransportFields(descriptor.capabilityType(), values);
    }

    static void validateAddress(CapabilityDescriptor descriptor, List<PropertyItem> items) {
        Map<String, Object> values = PropertySchemas.toValueMap(items);
        SchemaValidator.require(descriptor.addressSchema(), values, "端点 address");
        requireModbusAddressFields(descriptor.capabilityType(), values);
    }

    /**
     * MODBUS：TCP 必填 host，RTU 必填 serialPort。schema 里两者都是可选，避免互相卡住。
     * 同时校验 port / baudRate / dataBits / stopBits 范围。
     */
    static void requireModbusTransportFields(String capabilityType, Map<String, Object> values) {
        if (capabilityType == null || !"MODBUS".equalsIgnoreCase(capabilityType)) {
            return;
        }
        Map<String, Object> safe = values == null ? Map.of() : values;
        String transport = String.valueOf(safe.getOrDefault("transport", "TCP"));
        if ("RTU".equalsIgnoreCase(transport)) {
            Object serial = safe.get("serialPort");
            if (serial == null || String.valueOf(serial).isBlank()) {
                throw new IllegalArgumentException("通道 connection 缺少必填字段: serialPort");
            }
        } else {
            Object host = safe.get("host");
            if (host == null || String.valueOf(host).isBlank()) {
                throw new IllegalArgumentException("通道 connection 缺少必填字段: host");
            }
        }
        requireIntRange(safe.get("port"), 1, 65535, "通道 connection port");
        requirePositiveInt(safe.get("baudRate"), "通道 connection baudRate");
        requireIntRange(safe.get("dataBits"), 5, 8, "通道 connection dataBits");
        requireIntRange(safe.get("stopBits"), 1, 2, "通道 connection stopBits");
    }

    static void requireModbusAddressFields(String capabilityType, Map<String, Object> values) {
        if (capabilityType == null || !"MODBUS".equalsIgnoreCase(capabilityType)) {
            return;
        }
        Map<String, Object> safe = values == null ? Map.of() : values;
        requireIntRange(safe.get("slaveId"), 1, 247, "端点 address slaveId");
    }

    static List<PropertyItem> redactSecrets(List<PropertyItem> items, List<SchemaField> schema) {
        if (items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        Set<String> secretNames = secretFieldNames(schema);
        List<PropertyItem> redacted = new ArrayList<>();
        for (PropertyItem item : items) {
            if (isSecretAttribute(item.attribute(), secretNames)
                    && item.attributeValue() != null
                    && !item.attributeValue().isBlank()) {
                redacted.add(new PropertyItem(item.attribute(), SECRET_MASK, item.dataType(), item.description()));
            } else {
                redacted.add(item);
            }
        }
        return List.copyOf(redacted);
    }

    static List<PropertyItem> restoreSecrets(
            List<PropertyItem> incoming, List<PropertyItem> existing, List<SchemaField> schema) {
        List<PropertyItem> incomingItems = incoming == null ? List.of() : incoming;
        Map<String, PropertyItem> existingByName = new LinkedHashMap<>();
        for (PropertyItem item : existing == null ? List.<PropertyItem>of() : existing) {
            existingByName.put(item.attribute(), item);
        }
        Set<String> secretNames = secretFieldNames(schema);
        List<PropertyItem> restored = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PropertyItem item : incomingItems) {
            seen.add(item.attribute());
            if (isSecretAttribute(item.attribute(), secretNames) && isMaskedOrBlank(item.attributeValue())) {
                PropertyItem previous = existingByName.get(item.attribute());
                restored.add(previous != null ? previous : item);
            } else {
                restored.add(item);
            }
        }
        for (String name : secretNames) {
            if (!seen.contains(name) && existingByName.containsKey(name)) {
                restored.add(existingByName.get(name));
            }
        }
        return List.copyOf(restored);
    }

    private static Set<String> secretFieldNames(List<SchemaField> schema) {
        Set<String> names = new LinkedHashSet<>();
        if (schema == null) {
            return names;
        }
        for (SchemaField field : schema) {
            if (field.secret() || field.type() == FieldType.PASSWORD) {
                names.add(field.name());
            }
        }
        return names;
    }

    private static boolean isSecretAttribute(String attribute, Set<String> secretNames) {
        if (attribute == null || attribute.isBlank()) {
            return false;
        }
        return secretNames.contains(attribute) || "password".equalsIgnoreCase(attribute);
    }

    private static boolean isMaskedOrBlank(String value) {
        return value == null || value.isBlank() || SECRET_MASK.equals(value);
    }

    private static void requirePositiveInt(Object raw, String label) {
        Integer value = parseOptionalInt(raw);
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(label + " 必须大于 0");
        }
    }

    private static void requireIntRange(Object raw, int min, int max, String label) {
        Integer value = parseOptionalInt(raw);
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(label + " 必须在 " + min + "–" + max + " 之间");
        }
    }

    private static Integer parseOptionalInt(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
