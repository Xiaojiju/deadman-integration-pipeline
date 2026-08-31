package com.mtfm.gateway.capability.loopback.device;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 南向回环能力常量，无真实网络，用于集成测试与演示。
 *
 * <p>Channel（connection）仅文档用途；Address（alias）为逻辑别名，不参与寻址。
 */
public final class LoopbackCapability {

    public static final String TYPE = "PROTO";
    public static final String FN_SWITCH = "fn.switch";
    public static final String FN_STATUS = "fn.status";

    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(SchemaField.optional("mode", "string", "回环模式，仅文档")),
            List.of(SchemaField.optional("alias", "string", "逻辑别名，不属于寻址片")),
            List.of(
                    FunctionTemplate.of(FN_SWITCH, "WRITE", List.of(
                            SchemaField.choice("action", "开关动作", true, "on", List.of("on", "off"))
                    )),
                    FunctionTemplate.of(FN_STATUS, "READ", List.of())
            )
    );

    private LoopbackCapability() {
    }
}
