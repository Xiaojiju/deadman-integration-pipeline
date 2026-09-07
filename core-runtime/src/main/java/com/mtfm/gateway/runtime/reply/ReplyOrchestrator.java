package com.mtfm.gateway.runtime.reply;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.payload.PayloadDisassembler;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import com.mtfm.gateway.spi.port.ReplyOccupancy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 应答登记、匹配与超时。流水线只通过本类接触 {@link ReplyWaiter}/{@link ReplyBinder}。
 *
 * <p>使用示例：
 * <pre>{@code
 * ReplyOrchestrator replies = new ReplyOrchestrator(catalog, pending -> pipeline.onTimeout(pending));
 * replies.start();
 * if (replies.awaits(command) && replies.register(command)) {
 *     // 等待入站 TELEMETRY 经 bind 完成 future
 * }
 * }</pre>
 */
public final class ReplyOrchestrator implements ReplyOccupancy, AutoCloseable {

    private final ReplyWaiter waiter;
    private final ReplyBinder binder;

    public ReplyOrchestrator(FunctionCatalog catalog, Consumer<ReplyWaiter.Pending> onTimeout) {
        this.waiter = new ReplyWaiter(4096, 64, onTimeout);
        this.binder = new ReplyBinder(catalog, waiter);
    }

    public void start() {
        waiter.start();
    }

    public boolean awaits(FunctionCommand command) {
        return command.deliveryHints().get(TopicRouteResolver.MQTT_REPLY_TOPIC_HINT)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .isPresent();
    }

    public boolean register(FunctionCommand command) {
        String corr = correlationValue(command);
        if (corr == null || corr.isBlank()) {
            return false;
        }
        return waiter.tryRegister(new ReplyWaiter.Pending(
                command.requestId() == null ? corr : command.requestId(),
                command.deviceId(),
                command.functionId(),
                corr,
                Instant.now().plus(replyTimeout(command))));
    }

    public void cancel(FunctionCommand command) {
        waiter.cancel(command.deviceId(), correlationValue(command));
    }

    public Optional<ExecutionResult> bind(Envelope sealed) {
        return binder.bind(sealed);
    }

    public boolean replyFrame(Envelope sealed) {
        return binder.replyFrame(sealed);
    }

    public Optional<String> listenFunctionId(Envelope sealed) {
        return binder.listenFunctionId(sealed);
    }

    @Override
    public boolean awaiting(String deviceId, String functionId) {
        return waiter.awaiting(deviceId, functionId);
    }

    @Override
    public void close() {
        waiter.close();
    }

    static String correlationValue(FunctionCommand command) {
        String commandPath = hint(command, TopicRouteResolver.MQTT_CORRELATION_COMMAND_PATH_HINT);
        String fromCommand = extractCorrelation(command, commandPath);
        if (fromCommand != null) {
            return fromCommand;
        }
        String replyPath = hint(command, TopicRouteResolver.MQTT_CORRELATION_PATH_HINT);
        fromCommand = extractCorrelation(command, replyPath);
        if (fromCommand != null) {
            return fromCommand;
        }
        if (command.requestId() != null && !command.requestId().isBlank()) {
            return command.requestId();
        }
        return null;
    }

    private static String hint(FunctionCommand command, String key) {
        return command.deliveryHints().get(key).map(String::valueOf).orElse(null);
    }

    private static String extractCorrelation(FunctionCommand command, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        if ("$deviceCode".equalsIgnoreCase(trimmed)) {
            return command.deviceId();
        }
        if ("$requestId".equalsIgnoreCase(trimmed)) {
            return command.requestId() == null || command.requestId().isBlank() ? null : command.requestId();
        }
        Object raw = PayloadDisassembler.extractPath(command.arguments().values(), trimmed);
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        return text.isBlank() ? null : text;
    }

    private static Duration replyTimeout(FunctionCommand command) {
        return command.deliveryHints().get(TopicRouteResolver.MQTT_REPLY_TIMEOUT_MS_HINT)
                .map(value -> {
                    try {
                        long ms = Long.parseLong(String.valueOf(value));
                        return ms > 0 ? Duration.ofMillis(ms) : Duration.ofSeconds(8);
                    } catch (NumberFormatException ex) {
                        return Duration.ofSeconds(8);
                    }
                })
                .orElse(Duration.ofSeconds(8));
    }
}
