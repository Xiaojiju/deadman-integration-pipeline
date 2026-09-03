package com.mtfm.gateway.capability.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingModbusBusTest {

    @Test
    void routesTcpAndRtuToSeparateBuses() {
        InMemoryModbusBus tcp = new InMemoryModbusBus();
        InMemoryModbusBus rtu = new InMemoryModbusBus();
        RoutingModbusBus routing = new RoutingModbusBus(tcp, rtu);
        ModbusChannel tcpChannel = ModbusChannel.tcp("c-tcp", "10.0.0.1", 502);
        ModbusChannel rtuChannel = ModbusChannel.rtu("c-rtu", "COM3", 9600, 8, "NONE", 1);

        routing.retain(tcpChannel);
        routing.retain(rtuChannel);
        routing.writeNumeric(tcpChannel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 11);
        routing.writeNumeric(rtuChannel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 22);

        assertEquals(11, tcp.readNumeric(tcpChannel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16).intValue());
        assertEquals(22, rtu.readNumeric(rtuChannel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16).intValue());
        assertEquals(0, tcp.refCount(rtuChannel.sessionKey()));
        assertEquals(1, routing.refCount(tcpChannel.sessionKey()));
        assertEquals(1, routing.refCount(rtuChannel.sessionKey()));

        routing.release(tcpChannel);
        routing.release(rtuChannel);
        routing.close();
    }
}
