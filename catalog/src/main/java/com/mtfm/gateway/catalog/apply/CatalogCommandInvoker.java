package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.port.PipelineCommandPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 装配并提交单设备指令。动作组执行器只依赖本类，避免与 {@link CatalogApplyService} 构造环。
 *
 * <p>对 {@link SceneListenDispatcher} 用 {@link ObjectProvider} 延迟回调，避免
 * Invoker → Listen → Executor → Invoker 环。
 *
 * <p>使用示例：{@code invoker.invoke("door-1", new DeviceCommandRequest("fn.open", Map.of(), null, null))}
 */
@Service
public class CatalogCommandInvoker {

    private final CatalogFormService forms;
    private final ObjectProvider<PipelineCommandPort> commandPort;
    private final DriverRegistry registry;
    private final ObjectProvider<SceneListenDispatcher> sceneListen;

    @Autowired
    public CatalogCommandInvoker(
            CatalogFormService forms,
            ObjectProvider<PipelineCommandPort> commandPort,
            @Autowired(required = false) DriverRegistry driverRegistry,
            ObjectProvider<SceneListenDispatcher> sceneListen) {
        this.forms = forms;
        this.commandPort = commandPort;
        this.registry = driverRegistry;
        this.sceneListen = sceneListen;
    }

    public CompletableFuture<ExecutionResult> invoke(String deviceCode, DeviceCommandRequest request) {
        PipelineCommandPort port = commandPort == null ? null : commandPort.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("命令端口尚未装配，无法手动下发");
        }
        FunctionCommand command = forms.buildCommand(deviceCode, request);
        if (registry == null || !registry.isRegistered(deviceCode)) {
            throw new IllegalStateException("设备未加载到运行时，请先 POST /catalog/devices/" + deviceCode + "/load");
        }
        return port.submit(command).whenComplete((result, error) -> {
            if (error != null || result == null || result.status() != ExecutionStatus.SUCCESS) {
                return;
            }
            SceneListenDispatcher dispatcher = sceneListen == null ? null : sceneListen.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.onCommandSuccess(
                        deviceCode, request.functionId(), command.arguments(), request.source());
            }
        });
    }
}
