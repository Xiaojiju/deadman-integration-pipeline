package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 南向 MQTT 能力。Channel = Broker；Address = 命名 topic 目录（slot）。
 */
public final class MqttCapability {

    public static final String TYPE = "MQTT";

    /** 入站 COMMAND 未带 functionId 时的回落标识。 */
    public static final String FN_PUBLISH = "fn.publish";

    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(
                    SchemaField.required("host", FieldType.STRING, "MQTT Broker 主机"),
                    SchemaField.optional("port", FieldType.INT, "端口", 1883),
                    SchemaField.optional("username", FieldType.STRING, "用户名，凭证建议来自环境变量"),
                    SchemaField.optionalSecret("password", "密码，凭证建议来自环境变量")),
            List.of(
                    SchemaField.required("default_pub", FieldType.STRING, "默认发布 topic"),
                    SchemaField.optional("default_sub", FieldType.STRING, "默认订阅 topic"),
                    SchemaField.optional("topics", FieldType.JSON, "扩展 topic 槽位 JSON，如 {\"door_cmd\":\"a/door/write\"}")),
            List.of(),
            FunctionCatalogMode.OPEN);

    private MqttCapability() {
    }
}
