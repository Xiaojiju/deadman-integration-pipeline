package com.mtfm.gateway.capability.modbus;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 南向 Modbus 能力常量与登记描述符。
 *
 * <p>Channel（connection）承载 TCP 连接参数 host/port；Address 承载从站号 slaveId。
 * 多设备共享同一 host:port 时复用 {@link ModbusChannel}，由 {@link ModbusExecutor} retain/release 管理生命周期。
 *
 * @see ModbusExecutor
 * @see ModbusChannel
 */
public final class ModbusCapability {

    public static final String TYPE = "MODBUS";
    public static final String FN_READ = "fn.read";
    public static final String FN_WRITE = "fn.write";
    public static final String ARG_AREA = "area";
    public static final String ARG_OFFSET = "offset";
    public static final String ARG_QUANTITY = "quantity";
    public static final String ARG_DATA_TYPE = "dataType";
    public static final String ARG_VALUE = "value";

    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(
                    SchemaField.required("host", FieldType.STRING, "Modbus TCP 主机"),
                    SchemaField.optional("port", FieldType.INT, "端口", 502)
            ),
            List.of(SchemaField.required("slaveId", FieldType.INT, "从站号，属于地址片，不属于 Option")),
            List.of(
                    FunctionTemplate.of(FN_READ, "READ", List.of(
                            SchemaField.choice(ARG_AREA, "寄存器区", true, "HOLDING",
                                    List.of("HOLDING", "INPUT", "COIL", "DISCRETE")),
                            SchemaField.required(ARG_OFFSET, FieldType.INT, "起始地址", 0),
                            SchemaField.optional(ARG_QUANTITY, FieldType.INT, "数量", 1),
                            SchemaField.choice(ARG_DATA_TYPE, "数据类型", false, "INT16",
                                    List.of("INT16", "BOOLEAN"))
                    )),
                    FunctionTemplate.of(FN_WRITE, "WRITE", List.of(
                            SchemaField.choice(ARG_AREA, "寄存器区", true, "HOLDING",
                                    List.of("HOLDING", "INPUT", "COIL", "DISCRETE")),
                            SchemaField.required(ARG_OFFSET, FieldType.INT, "起始地址", 0),
                            SchemaField.required(ARG_VALUE, FieldType.STRING, "写入值"),
                            SchemaField.choice(ARG_DATA_TYPE, "数据类型", false, "INT16",
                                    List.of("INT16", "BOOLEAN"))
                    ))
            )
    );

    private ModbusCapability() {
    }
}
