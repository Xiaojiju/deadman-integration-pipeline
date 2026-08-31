package com.mtfm.gateway.capability.modbus;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModbusSharedChannelTest {

    @Test
    void 一通道两从站互不影响且解绑按引用计数() {
        InMemoryModbusBus bus = new InMemoryModbusBus();
        ModbusExecutor executor = new ModbusExecutor(bus);
        executor.bind(endpoint("A", "gw-1", 1));
        executor.bind(endpoint("B", "gw-1", 2));
        assertEquals(2, bus.refCount("192.168.1.10:502"));

        executor.execute(FunctionCommand.of("A", ModbusCapability.FN_WRITE,
                Map.of("offset", 0, "value", 11)));
        executor.execute(FunctionCommand.of("B", ModbusCapability.FN_WRITE,
                Map.of("offset", 0, "value", 22)));

        ExecutionResult readA = executor.execute(FunctionCommand.of("A", ModbusCapability.FN_READ,
                Map.of("offset", 0, "quantity", 1)));
        ExecutionResult readB = executor.execute(FunctionCommand.of("B", ModbusCapability.FN_READ,
                Map.of("offset", 0, "quantity", 1)));
        assertEquals(ExecutionStatus.SUCCESS, readA.status());
        assertEquals(ExecutionStatus.SUCCESS, readB.status());
        assertEquals(11, ((Number) ((List<?>) readA.data().values().get("values")).get(0)).intValue());
        assertEquals(22, ((Number) ((List<?>) readB.data().values().get("values")).get(0)).intValue());
        assertEquals(1, readA.data().values().get("unitId"));
        assertEquals(2, readB.data().values().get("unitId"));

        assertEquals(true, executor.unbind("A"));
        assertEquals(1, bus.refCount("192.168.1.10:502"));
        assertEquals(true, executor.unbind("B"));
        assertEquals(0, bus.refCount("192.168.1.10:502"));
        assertFalse(executor.unbind("A"));
    }

    private static DeviceEndpointBinding endpoint(String deviceId, String channelId, int unitId) {
        return new DeviceEndpointBinding(
                deviceId,
                channelId,
                ModbusCapability.TYPE,
                Attributes.from(Map.of("host", "192.168.1.10", "port", 502)),
                Attributes.from(Map.of("slaveId", unitId))
        );
    }
}
