package com.mtfm.gateway.app.web;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.spi.model.ExecutionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    public CompletableFuture<ExecutionResult> submit(@Valid @RequestBody CommandRequest request) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        return applyService.invoke(request.deviceId(),
                new DeviceCommandRequest(request.functionId(), arguments, request.requestId(), null));
    }

    /**
     * HTTP 命令请求体。
     *
     * @param deviceId   设备编码
     * @param functionId 产品功能 ID
     * @param arguments  本次参数
     * @param requestId  调用方关联号
     */
    public record CommandRequest(
            @NotBlank(message = "deviceId 不能为空") String deviceId,
            @NotBlank(message = "functionId 不能为空") String functionId,
            Map<String, Object> arguments,
            String requestId) {
    }
}
