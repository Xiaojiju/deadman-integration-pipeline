package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 北向云通道能力常量。
 *
 * <p>
 * 下行走 {@link NorthboundMqttIngress} → catalog invoke；
 * 上行走 {@link CloudPublisher} Hub 扇出 MQTT / Webhook。无 Address schema（北向无子设备寻址）。
 */
public final class CloudCapability {

    public static final String TYPE = "CLOUD";

    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(
                    SchemaField.required("host", FieldType.STRING, "云端 Broker 主机"),
                    SchemaField.optional("port", FieldType.INT, "端口", 1883).range(1, 65535),
                    SchemaField.optional("username", FieldType.STRING, "用户名，凭证建议来自环境变量"),
                    SchemaField.optionalSecret("password", "密码，凭证建议来自环境变量"),
                    SchemaField.optional("clientId", FieldType.STRING, "云端客户端 ID")),
            List.of());

    private CloudCapability() {
    }
}
