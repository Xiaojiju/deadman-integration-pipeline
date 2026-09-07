package com.mtfm.gateway.runtime.reply;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.payload.PayloadDisassembler;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用功能 correlationPath 从入站载荷取值，命中 {@link ReplyWaiter} 则组装命令回执。
 */
public final class ReplyBinder {

    private final FunctionCatalog catalog;
    private final ReplyWaiter waiter;

    public ReplyBinder(FunctionCatalog catalog, ReplyWaiter waiter) {
        this.catalog = catalog;
        this.waiter = waiter;
    }

    public Optional<ExecutionResult> bind(Envelope envelope) {
        if (envelope == null || waiter == null) {
            return Optional.empty();
        }
        String deviceId = envelope.deviceId();
        Map<String, Object> data = envelope.payload().values();
        String path = correlationPath(envelope);
        Object raw = path == null ? null : PayloadDisassembler.extractPath(data, path);
        if (raw == null && path != null) {
            raw = data.get(path);
        }
        if (raw == null) {
            raw = firstPresent(data, "seq", "requestId", "msgid", "id");
        }
        if (raw == null) {
            return Optional.empty();
        }
        Optional<ReplyWaiter.Pending> matched = waiter.complete(deviceId, String.valueOf(raw));
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        ReplyWaiter.Pending pending = matched.get();
        Optional<FunctionDef> def = catalog == null
                ? Optional.empty()
                : catalog.find(envelope.deviceId(), envelope.functionId());
        List<WriteFieldOption> fields = def.map(FunctionDef::readFields).orElse(List.of());
        List<ValueOption> options = def.map(FunctionDef::readValueOptions).orElse(List.of());
        Map<String, Object> projected = PayloadDisassembler.project(
                data,
                fields,
                options,
                def.map(FunctionDef::scaleOp).orElse(null),
                def.map(FunctionDef::scaleOperand).orElse(null));
        if (isFailure(data, fields)) {
            return Optional.of(ExecutionResult.failed(
                    new com.mtfm.gateway.spi.model.FunctionCommand(
                            pending.requestId(),
                            pending.deviceId(),
                            pending.functionId(),
                            envelope.capabilityType(),
                            Attributes.from(projected),
                            Attributes.empty(),
                            null),
                    Failure.executorError("reply", "设备应答失败", false),
                    projected));
        }
        ExecutionResult result = ExecutionResult.success(
                pending.requestId(), pending.deviceId(), pending.functionId(), projected);
        if (fields.isEmpty() && def.isPresent()) {
            result = com.mtfm.gateway.spi.payload.ResultValueMapper.apply(def.get(), result);
        }
        return Optional.of(result);
    }

    private static Object firstPresent(Map<String, Object> data, String... keys) {
        if (data == null) {
            return null;
        }
        for (String key : keys) {
            Object value = data.get(key);
            if (value == null) {
                value = PayloadDisassembler.extractPath(data, key);
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public boolean replyFrame(Envelope envelope) {
        return envelope.headers().get("mqtt.reply").map("true"::equalsIgnoreCase).orElse(false);
    }

    public Optional<String> listenFunctionId(Envelope envelope) {
        return envelope.headers().get("mqtt.listenFunctionId").filter(value -> !value.isBlank());
    }

    private String correlationPath(Envelope envelope) {
        String header = envelope.headers().get("mqtt.correlationPath").orElse(null);
        if (catalog == null) {
            return header;
        }
        String fromDef = catalog.find(envelope.deviceId(), envelope.functionId())
                .map(FunctionDef::correlationPath)
                .orElse(null);
        return fromDef != null ? fromDef : header;
    }

    static boolean isFailure(Map<String, Object> data, List<WriteFieldOption> fields) {
        if (data == null || fields == null || fields.isEmpty()) {
            return false;
        }
        for (WriteFieldOption field : fields) {
            if (field == null || field.field() == null || field.field().isBlank()) {
                continue;
            }
            Object value = PayloadDisassembler.extractPath(data, field.field());
            if (failureValue(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean failureValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return !flag;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return switch (text) {
            case "0", "false", "fail", "failed", "error", "ng" -> true;
            default -> false;
        };
    }
}
