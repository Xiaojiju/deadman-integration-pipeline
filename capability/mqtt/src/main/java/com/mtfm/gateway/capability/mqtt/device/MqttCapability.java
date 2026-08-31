package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 南向 MQTT 能力常量。与北向 cloud 模块分包，互不 import。
 *
 * <p>Channel（connection）= Broker 连接参数；Address = 子设备 topic 段。
 */
public final class MqttCapability {

    public static final String TYPE = "MQTT";
    public static final String FN_PUBLISH = "fn.publish";

    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(
                    SchemaField.required("host", "string", "MQTT Broker 主机"),
                    SchemaField.optional("port", "int", "端口", 1883),
                    SchemaField.optional("username", "string", "用户名，凭证建议来自环境变量"),
                    SchemaField.optionalSecret("password", "密码，凭证建议来自环境变量"),
                    SchemaField.optional("clientId", "string", "共享会话客户端 ID")
            ),
            List.of(SchemaField.required("topic", "string", "子设备 topic 段")),
            List.of(FunctionTemplate.of(FN_PUBLISH, "WRITE", List.of(
                    SchemaField.required("text", "string", "发布载荷")
            )))
    );

    private MqttCapability() {
    }
}
