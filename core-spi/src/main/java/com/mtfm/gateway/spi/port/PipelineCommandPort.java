package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionCommand;

import java.util.concurrent.CompletableFuture;

/**
 * 本地/HTTP 直接提交命令的端口。
 *
 * <p>绕过南向 Driver 解码，由核心直接进入 Normalize → Execute 流程。
 *
 * <p>使用示例：
 * <pre>{@code
 * CompletableFuture<ExecutionResult> future = commandPort.submit(
 *         FunctionCommand.of("dev-001", "fn.write", Map.of("value", 42)));
 * ExecutionResult result = future.get(5, TimeUnit.SECONDS);
 * }</pre>
 */
public interface PipelineCommandPort {

    /**
     * 异步提交功能命令。
     *
     * @param command 功能命令
     * @return 执行结果的 Future
     */
    CompletableFuture<ExecutionResult> submit(FunctionCommand command);
}
