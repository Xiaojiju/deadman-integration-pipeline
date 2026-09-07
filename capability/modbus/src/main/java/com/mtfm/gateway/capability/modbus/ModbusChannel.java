package com.mtfm.gateway.capability.modbus;

import com.mtfm.gateway.spi.model.Attributes;

/**
 * Modbus 通道键。TCP 按 host:port 复用；RTU 按串口名独占复用。
 *
 * <p>使用示例：
 * <pre>{@code
 * ModbusChannel tcp = ModbusChannel.tcp("c1", "127.0.0.1", 502);
 * ModbusChannel rtu = ModbusChannel.rtu("c2", "/dev/ttyUSB0", 9600, 8, "NONE", 1);
 * ModbusChannel fromProps = ModbusChannel.from("c1", Attributes.from(Map.of("host", "10.0.0.8", "port", 502)));
 * }</pre>
 *
 * @param channelId  通道 ID
 * @param transport  TCP 或 RTU
 * @param host       TCP 主机
 * @param port       TCP 端口
 * @param serialPort RTU 串口名
 * @param baudRate   RTU 波特率
 * @param dataBits   RTU 数据位
 * @param parity     RTU 校验 NONE/EVEN/ODD
 * @param stopBits   RTU 停止位
 * @param keepAlive  TCP 是否在命令间保持连接
 */
public record ModbusChannel(
        String channelId,
        ModbusTransport transport,
        String host,
        int port,
        String serialPort,
        int baudRate,
        int dataBits,
        String parity,
        int stopBits,
        boolean keepAlive
) {

    public ModbusChannel {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        transport = transport == null ? ModbusTransport.TCP : transport;
        if (transport == ModbusTransport.RTU) {
            if (serialPort == null || serialPort.isBlank()) {
                throw new IllegalArgumentException("RTU 通道 serialPort 不能为空");
            }
            serialPort = serialPort.trim();
            if (baudRate <= 0) {
                baudRate = 9600;
            }
            if (dataBits <= 0) {
                dataBits = 8;
            }
            if (parity == null || parity.isBlank()) {
                parity = "NONE";
            } else {
                parity = parity.trim().toUpperCase();
            }
            if (stopBits <= 0) {
                stopBits = 1;
            }
            if (port <= 0) {
                port = 502;
            }
            host = host == null ? "" : host;
        } else {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("TCP 通道 host 不能为空");
            }
            host = host.trim();
            if (port <= 0) {
                port = 502;
            }
            serialPort = serialPort == null ? "" : serialPort;
            if (baudRate <= 0) {
                baudRate = 9600;
            }
            if (dataBits <= 0) {
                dataBits = 8;
            }
            if (parity == null || parity.isBlank()) {
                parity = "NONE";
            }
            if (stopBits <= 0) {
                stopBits = 1;
            }
        }
    }

    public ModbusChannel(String channelId, String host, int port, boolean keepAlive) {
        this(channelId, ModbusTransport.TCP, host, port, "", 9600, 8, "NONE", 1, keepAlive);
    }

    public static ModbusChannel tcp(String channelId, String host, int port) {
        return new ModbusChannel(channelId, host, port, true);
    }

    public static ModbusChannel rtu(String channelId, String serialPort, int baudRate, int dataBits,
            String parity, int stopBits) {
        return new ModbusChannel(channelId, ModbusTransport.RTU, "", 502, serialPort, baudRate, dataBits, parity,
                stopBits, true);
    }

    public static ModbusChannel from(String channelId, Attributes connection) {
        Attributes values = connection == null ? Attributes.empty() : connection;
        ModbusTransport transport = ModbusTransport.parse(string(values, "transport"));
        boolean keepAlive = booleanValue(values, "keepAlive", true);
        if (transport == ModbusTransport.RTU) {
            return new ModbusChannel(
                    channelId,
                    ModbusTransport.RTU,
                    "",
                    502,
                    string(values, "serialPort"),
                    intValue(values, "baudRate", 9600),
                    intValue(values, "dataBits", 8),
                    string(values, "parity"),
                    intValue(values, "stopBits", 1),
                    keepAlive);
        }
        return new ModbusChannel(channelId, string(values, "host"), intValue(values, "port", 502), keepAlive);
    }

    public boolean rtu() {
        return transport == ModbusTransport.RTU;
    }

    public String sessionKey() {
        if (rtu()) {
            return "rtu:" + serialPort;
        }
        return host + ":" + port;
    }

    private static String string(Attributes attributes, String key) {
        return attributes.get(key).map(String::valueOf).orElse("");
    }

    private static int intValue(Attributes attributes, String key, int fallback) {
        return attributes.get(key).map(value -> {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private static boolean booleanValue(Attributes attributes, String key, boolean fallback) {
        return attributes.get(key).map(value -> {
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = String.valueOf(value).trim();
            if (text.equalsIgnoreCase("true") || text.equals("1")) {
                return true;
            }
            if (text.equalsIgnoreCase("false") || text.equals("0")) {
                return false;
            }
            return fallback;
        }).orElse(fallback);
    }
}
