package com.mtfm.gateway.capability.modbus;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 南向 Modbus 能力常量与登记描述符。
 *
 * <p>Channel 用 transport=TCP|RTU 分流：TCP 填 host/port，RTU 填串口参数。Address 仍是 slaveId。
 * 产品功能可自定义业务 functionId，参数名锁死为 area/offset/quantity/dataType/value。
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
                    SchemaField.choice("transport", "传输 TCP 或串口 RTU", false, "TCP",
                            List.of("TCP", "RTU")),
                    SchemaField.optional("host", FieldType.STRING, "TCP 主机"),
                    SchemaField.optional("port", FieldType.INT, "TCP 端口", 502).range(1, 65535),
                    SchemaField.optional("serialPort", FieldType.STRING, "RTU 串口，如 /dev/ttyUSB0 或 COM3"),
                    SchemaField.optional("baudRate", FieldType.INT, "波特率", 9600).atLeast(1),
                    SchemaField.optional("dataBits", FieldType.INT, "数据位", 8).range(5, 8),
                    SchemaField.choice("parity", "校验", false, "NONE",
                            List.of("NONE", "EVEN", "ODD")),
                    SchemaField.optional("stopBits", FieldType.INT, "停止位", 1).range(1, 2)
            ),
            List.of(SchemaField.required("slaveId", FieldType.INT, "从站号，属于地址片，不属于 Option")
                    .range(1, 247)),
            List.of(
                    FunctionTemplate.of(FN_READ, "READ", List.of(
                            SchemaField.choice(ARG_AREA, "寄存器区", true, "HOLDING",
                                    List.of("HOLDING", "INPUT", "COIL", "DISCRETE")),
                            SchemaField.required(ARG_OFFSET, FieldType.INT, "起始地址", 0).range(0, 65535),
                            SchemaField.optional(ARG_QUANTITY, FieldType.INT, "数量", 1).range(1, 125),
                            SchemaField.choice(ARG_DATA_TYPE, "数据类型", false, "INT16",
                                    List.of("INT16", "BOOLEAN"))
                    )),
                    FunctionTemplate.of(FN_WRITE, "WRITE", List.of(
                            SchemaField.choice(ARG_AREA, "寄存器区", true, "HOLDING",
                                    List.of("HOLDING", "INPUT", "COIL", "DISCRETE")),
                            SchemaField.required(ARG_OFFSET, FieldType.INT, "起始地址", 0).range(0, 65535),
                            SchemaField.required(ARG_VALUE, FieldType.STRING, "写入值"),
                            SchemaField.choice(ARG_DATA_TYPE, "数据类型", false, "INT16",
                                    List.of("INT16", "BOOLEAN"))
                    ))
            ),
            FunctionCatalogMode.CONTRACT
    );

    private ModbusCapability() {
    }
}
