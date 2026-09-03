package com.mtfm.gateway.app.web;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.spi.model.ExecutionResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 本地命令 HTTP 入口。与 {@code POST /catalog/devices/{code}/commands} 同一装配路径：
 * 校验设备已 load、功能权限、端点与覆盖后再提交流水线。
 *
 * <pre>{@code
 * POST /commands
 * {"deviceId":"dev-001","functionId":"fn.read","arguments":{"area":"HOLDING","offset":0}}
 * }</pre>
 */
@RestController
@RequestMapping("/commands")
public class CommandController {

    private final CatalogApplyService applyService;

    public CommandController(CatalogApplyService applyService) {
        this.applyService = applyService;
    }

    /** 提交功能命令到流水线，异步返回 {@link ExecutionResult}。 */
    @PostMapping
    public CompletableFuture<ExecutionResult> submit(@RequestBody CommandRequest request) {
        if (request == null || request.deviceId() == null || request.deviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (request.functionId() == null || request.functionId().isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        return applyService.invoke(request.deviceId(),
                new DeviceCommandRequest(request.functionId(),
                        request.arguments() == null ? Map.of() : request.arguments(),
                        request.requestId()));
    }

    /** HTTP 命令请求体。 */
    public record CommandRequest(String deviceId, String functionId, Map<String, Object> arguments, String requestId) {
        public CommandRequest(String deviceId, String functionId, Map<String, Object> arguments) {
            this(deviceId, functionId, arguments, null);
        }
    }
}
