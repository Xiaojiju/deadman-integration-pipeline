package com.mtfm.gateway.app.web;

import com.mtfm.gateway.runtime.GatewayPipeline;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 本地命令 HTTP 入口，将 REST 请求转为 {@link FunctionCommand} 提交流水线。
 *
 * <p>路径 {@code POST /commands}，返回 {@link CompletableFuture} 异步结果。
 *
 * <pre>{@code
 * POST /commands
 * {"deviceId":"dev-001","functionId":"fn.read","arguments":{"area":"HOLDING","offset":0}}
 * }</pre>
 */
@RestController
@RequestMapping("/commands")
public class CommandController {

    private final GatewayPipeline pipeline;

    public CommandController(GatewayPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** 提交功能命令到流水线，异步返回 {@link ExecutionResult}。 */
    @PostMapping
    public CompletableFuture<ExecutionResult> submit(@RequestBody CommandRequest request) {
        return pipeline.submit(FunctionCommand.of(request.deviceId(), request.functionId(),
                request.arguments() == null ? Map.of() : request.arguments()));
    }

    /** HTTP 命令请求体。 */
    public record CommandRequest(String deviceId, String functionId, Map<String, Object> arguments) {
    }
}
