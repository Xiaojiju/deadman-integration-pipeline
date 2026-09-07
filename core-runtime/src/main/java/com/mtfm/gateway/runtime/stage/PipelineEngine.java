package com.mtfm.gateway.runtime.stage;

import com.mtfm.gateway.runtime.registry.DefaultRegistries;
import com.mtfm.gateway.runtime.seal.DefaultEnvelopeSealer;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.model.OutboundApplyResult;
import com.mtfm.gateway.spi.model.OutboundDraft;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;
import com.mtfm.gateway.spi.port.EnvelopeSealer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * 流水线五阶段纯逻辑引擎，不含线程与队列，由 {@link com.mtfm.gateway.runtime.GatewayPipeline} 驱动。
 *
 * <h2>阶段说明</h2>
 * <ol>
 *   <li><b>Normalize</b> — {@link #normalize(EnvelopeDraft)}：入站插件链（Continue/Reject/Drop）→
 *       {@link EnvelopeSealer} 贴胶 → {@link FunctionCatalog} 查功能与写权限</li>
 *   <li><b>Execute</b> — {@link #execute(FunctionCommand)}：查设备绑定 → 找 {@link FunctionExecutor} → 调用南向</li>
 *   <li><b>Correlate</b> — {@link #correlate(ExecutionResult)} / {@link #correlateTelemetry(Envelope)}：
 *       包装为 {@link OutboundDraft}，channelHint 默认 {@code CLOUD}</li>
 *   <li><b>Outbound Plugin</b> — {@link #applyOutboundPlugins(OutboundDraft)}：出站插件链，禁止改 channelHint</li>
 *   <li><b>Publish</b> — {@link #publish(OutboundMessage)}：按 channelHint 找 {@link Publisher} 发布</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * PipelineEngine engine = new PipelineEngine(registries, catalogStore, new DefaultEnvelopeSealer());
 *
 * // 入站归一化
 * NormalizeOutcome outcome = engine.normalize(draft);
 * if (outcome.drop()) { return; }
 * if (outcome.failure() != null) { /* 拒绝 *\/ }
 *
 * // 命令执行与出站
 * ExecutionResult result = engine.execute(FunctionCommand.from(outcome.envelope()));
 * OutboundDraft correlated = engine.correlate(result);
 * Optional<OutboundMessage> message = engine.applyOutboundPlugins(correlated);
 * message.ifPresent(msg -> engine.publish(msg));
 * }</pre>
 */
public final class PipelineEngine {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineEngine.class);

    private final DefaultRegistries registries;
    private final FunctionCatalog functionCatalog;
    private final EnvelopeSealer sealer;

    public PipelineEngine(DefaultRegistries registries, FunctionCatalog functionCatalog, EnvelopeSealer sealer) {
        this.registries = registries;
        this.functionCatalog = functionCatalog;
        this.sealer = sealer == null ? new DefaultEnvelopeSealer() : sealer;
    }

    /**
     * Normalize 阶段：入站插件链（失败即停）→ 贴胶 → 目录/权限校验。
     *
     * @param draft 未贴胶入站草稿
     * @return 归一化结果：ok / fail / drop
     */
    public NormalizeOutcome normalize(EnvelopeDraft draft) {
        EnvelopeDraft current = draft.appendTrace("ingress", draft.kind().name());
        for (InboundPlugin plugin : registries.inboundInOrder()) {
            if (!plugin.support(current)) {
                continue;
            }
            InboundApplyResult result;
            try {
                result = plugin.apply(current);
            } catch (RuntimeException ex) {
                Failure failure = Failure.pluginError(plugin.name(), String.valueOf(ex.getMessage()));
                return NormalizeOutcome.fail(current.withError(failure).appendTrace(plugin.name(), "error"), failure);
            }
            switch (result) {
                case InboundApplyResult.Continue cont ->
                    current = cont.draft().appendTrace(plugin.name(), "continue");
                case InboundApplyResult.Reject reject -> {
                    Failure failure = reject.reject().toFailure();
                    return NormalizeOutcome.fail(
                            current.withError(failure).appendTrace(plugin.name(), "reject"), failure);
                }
                case InboundApplyResult.Drop ignored -> {
                    return NormalizeOutcome.drop(current.appendTrace(plugin.name(), "drop"));
                }
            }
        }
        Envelope sealed = sealer.seal(current.appendTrace("seal", "inbound"));
        if (sealed.kind() == EnvelopeKind.COMMAND && functionCatalog != null) {
            Optional<FunctionDef> def = functionCatalog.find(sealed.deviceId(), sealed.functionId());
            if (def.isEmpty()) {
                Failure failure = Failure.functionNotFound(sealed.deviceId(), sealed.functionId());
                return NormalizeOutcome.failSealed(sealed, failure);
            }
            if ("WRITE".equalsIgnoreCase(def.get().accessType())
                    && !AccessPermission.contains(def.get().accessPermission(), AccessPermission.WRITE)) {
                Failure failure = Failure.accessDenied(sealed.deviceId(), sealed.functionId());
                return NormalizeOutcome.failSealed(sealed, failure);
            }
            if ("READ".equalsIgnoreCase(def.get().accessType())
                    && !AccessPermission.contains(def.get().accessPermission(), AccessPermission.READ)
                    && def.get().accessPermission() != AccessPermission.WRITE.code()) {
                Failure failure = Failure.accessDenied(sealed.deviceId(), sealed.functionId());
                return NormalizeOutcome.failSealed(sealed, failure);
            }
        }
        return NormalizeOutcome.ok(sealed);
    }

    /** Execute 阶段：查设备绑定 → 找 Executor → 调用南向 execute。 */
    public ExecutionResult execute(FunctionCommand command) {
        if (command.deadlineAt() != null && Instant.now().isAfter(command.deadlineAt())) {
            return ExecutionResult.timeout(command, Failure.timeout("core", "已超过 deadlineAt"));
        }
        Optional<String> binding = registries.findCapabilityType(command.deviceId());
        if (binding.isEmpty()) {
            return ExecutionResult.rejected(command, Failure.noDriver(command.deviceId()));
        }
        String capabilityType = binding.get();
        Optional<FunctionExecutor> executor = registries.findExecutor(capabilityType);
        if (executor.isEmpty() || !executor.get().support(command)) {
            return ExecutionResult.rejected(command, Failure.noExecutor(capabilityType));
        }
        LOG.info("即将执行指令 requestId={} deviceId={} functionId={} capability={} arguments={} hints={}",
                command.requestId(),
                command.deviceId(),
                command.functionId(),
                capabilityType,
                command.arguments().values(),
                command.deliveryHints().values());
        try {
            return executor.get().execute(command);
        } catch (RuntimeException ex) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType, String.valueOf(ex.getMessage()), true));
        }
    }

    /** Correlate 阶段：将命令执行结果包装为出站草稿，channelHint 默认 CLOUD。 */
    public OutboundDraft correlate(ExecutionResult result) {
        return OutboundDraft.builder()
                .channelHint(Channels.CLOUD)
                .body(CommandResponse.from(result))
                .build();
    }

    /** Correlate 阶段：将遥测信封包装为出站草稿。 */
    public OutboundDraft correlateTelemetry(Envelope envelope) {
        return OutboundDraft.builder()
                .channelHint(Channels.CLOUD)
                .body(com.mtfm.gateway.spi.model.TelemetryEvent.from(envelope))
                .build();
    }

    /** 出站插件链 + 贴胶，禁止插件修改 channelHint。 */
    public Optional<OutboundMessage> applyOutboundPlugins(OutboundDraft draft) {
        OutboundDraft current = draft;
        String originalHint = draft.channelHint();
        for (OutboundPlugin plugin : registries.outboundInOrder()) {
            if (!plugin.support(current)) {
                continue;
            }
            OutboundApplyResult result;
            try {
                result = plugin.apply(current);
            } catch (RuntimeException ex) {
                LOG.warn("出站插件异常 {}: {}", plugin.name(), ex.getMessage());
                return Optional.empty();
            }
            switch (result) {
                case OutboundApplyResult.Continue cont -> {
                    if (!originalHint.equals(cont.draft().channelHint())) {
                        LOG.warn("出站插件不得修改 channelHint: {}", plugin.name());
                        return Optional.empty();
                    }
                    current = cont.draft();
                }
                case OutboundApplyResult.Reject reject -> {
                    LOG.warn("出站插件拒绝 {}: {}", plugin.name(), reject.reject().message());
                    return Optional.empty();
                }
                case OutboundApplyResult.Drop ignored -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(sealer.seal(current));
    }

    /** Publish 阶段：按 channelHint 找 Publisher 并发布。 */
    public PublishResult publish(OutboundMessage message) {
        Optional<Publisher> publisher = registries.findPublisher(message.channelHint());
        if (publisher.isEmpty() || !publisher.get().support(message)) {
            return PublishResult.failed(Failure.of(
                    com.mtfm.gateway.spi.model.FailureCodes.CHANNEL_MISMATCH,
                    "无匹配 Publisher: " + message.channelHint(), "core", false));
        }
        return publisher.get().publish(message);
    }

    /** Normalize 阶段结果：成功信封、失败草稿、失败原因或 drop 标记。 */
    public record NormalizeOutcome(Envelope envelope, EnvelopeDraft failedDraft, Failure failure, boolean drop) {

        public static NormalizeOutcome ok(Envelope envelope) {
            return new NormalizeOutcome(envelope, null, null, false);
        }

        public static NormalizeOutcome fail(EnvelopeDraft draft, Failure failure) {
            return new NormalizeOutcome(null, draft, failure, false);
        }

        public static NormalizeOutcome failSealed(Envelope envelope, Failure failure) {
            return new NormalizeOutcome(envelope, null, failure, false);
        }

        public static NormalizeOutcome drop(EnvelopeDraft draft) {
            return new NormalizeOutcome(null, draft, null, true);
        }
    }
}
