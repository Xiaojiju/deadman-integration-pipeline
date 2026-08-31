package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 北向云通道能力常量。
 *
 * <p>下行走 {@link CloudDownlink} → {@link com.mtfm.gateway.spi.port.PipelineIngress}；
 * 上行走 {@link CloudPublisher}。无 Address schema（北向无子设备寻址）。
 */
public final class CloudCapability {

    public static final String TYPE = "CLOUD";

    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(
                    SchemaField.required("host", "string", "云端 Broker 主机"),
                    SchemaField.optional("port", "int", "端口", 1883),
                    SchemaField.optional("username", "string", "用户名，凭证建议来自环境变量"),
                    SchemaField.optionalSecret("password", "密码，凭证建议来自环境变量"),
                    SchemaField.optional("clientId", "string", "云端客户端 ID")
            ),
            List.of()
    );

    private CloudCapability() {
    }
}
