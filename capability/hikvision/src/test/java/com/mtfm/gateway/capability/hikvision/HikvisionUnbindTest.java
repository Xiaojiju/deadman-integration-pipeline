package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HikvisionUnbindTest {

    @Test
    void unbindOneDoesNotAffectOtherChannels() {
        ConcurrentHashMap<String, StubHikvisionClient> created = new ConcurrentHashMap<>();
        HikvisionExecutor executor = new HikvisionExecutor(config ->
                created.computeIfAbsent(config.channelId(), StubHikvisionClient::new));
        executor.bind(binding("door-a", "ch-a"));
        executor.bind(binding("door-b", "ch-b"));
        executor.execute(FunctionCommand.of("door-a", HikvisionCapability.FN_REMOTE_CONTROL_DOOR,
                Map.of("command", "open")));
        executor.execute(FunctionCommand.of("door-b", HikvisionCapability.FN_REMOTE_CONTROL_DOOR,
                Map.of("command", "open")));

        assertTrue(executor.unbind("door-a"));
        assertFalse(executor.channelOpen("ch-a"));
        assertTrue(created.get("ch-a").closed());
        assertTrue(executor.channelOpen("ch-b"));
        assertFalse(created.get("ch-b").closed());
    }

    @Test
    void dispatchesByFunction() {
        StubHikvisionClient client = new StubHikvisionClient("ch-1");
        HikvisionExecutor executor = new HikvisionExecutor(config -> client);
        executor.bind(binding("door-1", "ch-1"));

        executor.execute(FunctionCommand.of("door-1", HikvisionCapability.FN_REMOTE_CONTROL_DOOR,
                Map.of("command", "open", "target", "1")));
        executor.execute(FunctionCommand.of("door-1", HikvisionCapability.FN_SET_UP_USER,
                Map.of("employeeNo", "E001", "name", "张三")));
        executor.execute(FunctionCommand.of("door-1", HikvisionCapability.FN_DELETE_USER,
                Map.of("employeeNoList", "E001,E002")));

        assertEquals(1, client.remoteControls());
        assertEquals(1, client.userOpCount("setUpUser"));
        assertEquals(1, client.userOpCount("deleteUser"));
    }

    private static DeviceEndpointBinding binding(String deviceId, String channelId) {
        return new DeviceEndpointBinding(
                deviceId,
                channelId,
                HikvisionCapability.TYPE,
                Attributes.from(Map.of("host", "10.0.0.1", "port", 80)),
                Attributes.from(Map.of("deviceSerialNo", "DS-001"))
        );
    }
}
