package com.mtfm.gateway.capability.loopback.device;

import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 南向回环执行器：进程内设备状态表，无 Channel retain/release。
 *
 * <p>无需 bind/unbind，deviceId 直接映射到内存 switchState，适合流水线端到端测试。
 */
public final class LoopbackExecutor implements FunctionExecutor {

    private final ConcurrentHashMap<String, String> switchState = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String capabilityType() {
        return LoopbackCapability.TYPE;
    }

    @Override
    public ExecutionResult execute(FunctionCommand command) {
        calls.incrementAndGet();
        String functionId = command.functionId();
        if (LoopbackCapability.FN_SWITCH.equals(functionId)) {
            return writeSwitch(command);
        }
        if (LoopbackCapability.FN_STATUS.equals(functionId)) {
            String state = switchState.getOrDefault(command.deviceId(), "off");
            return ExecutionResult.success(command.requestId(), command.deviceId(), functionId, Map.of("state", state));
        }
        return ExecutionResult.failed(command,
                Failure.executorError(capabilityType(), "不支持的功能: " + functionId, false));
    }

    public String stateOf(String deviceId) {
        return switchState.getOrDefault(deviceId, "off");
    }

    public int calls() {
        return calls.get();
    }

    private ExecutionResult writeSwitch(FunctionCommand command) {
        Object raw = command.arguments().get("action").orElse(null);
        if (raw == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "缺少 action", false));
        }
        String action = String.valueOf(raw);
        if (!"on".equals(action) && !"off".equals(action)) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "action 须为 on/off: " + action, false));
        }
        switchState.put(command.deviceId(), action);
        return ExecutionResult.success(
                command.requestId(),
                command.deviceId(),
                command.functionId(),
                Map.of("state", action));
    }
}
