package com.mtfm.gateway.runtime;

import com.mtfm.gateway.runtime.concurrent.DeviceSerialScheduler;
import com.mtfm.gateway.runtime.concurrent.DropOldestQueue;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.runtime.reply.ReplyOrchestrator;
import com.mtfm.gateway.runtime.seal.DefaultEnvelopeSealer;
import com.mtfm.gateway.runtime.stage.PipelineEngine;
import com.mtfm.gateway.runtime.stage.PipelineEngine.NormalizeOutcome;
import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.concurrent.DeadlineDaemon;
import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.metrics.GatewayMetrics;
import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.MessagePriority;
import com.mtfm.gateway.spi.model.OutboundDraft;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.model.RawInbound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 入站 / 出站工作循环。流水线门面只保留端口与注册。
 *
 * <p>使用示例：
 * <pre>{@code
 * PipelineIoLoop loop = new PipelineIoLoop(host);
 * loop.start();
 * loop.submitCommand(command, future);
 * loop.submitRaw(rawInbound);
 * }</pre>
 */
final class PipelineIoLoop {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineIoLoop.class);

    record IngressWork(EnvelopeDraft draft, CompletableFuture<ExecutionResult> future, FunctionCommand command) {
    }

    record RawWork(RawInbound raw) {
    }

    record EgressWork(OutboundMessage message) {
    }

    interface Host {
        AtomicBoolean stopped();

        GatewaySettings settings();

        DriverRegistry registries();

        PipelineEngine engine();

        FunctionCatalog functionCatalog();

        ReplyOrchestrator replies();

        GatewayMetrics metrics();

        DeviceSerialScheduler scheduler();

        ArrayBlockingQueue<IngressWork> commandIngress();

        DropOldestQueue<IngressWork> telemetryIngress();

        ArrayBlockingQueue<RawWork> rawIngress();

        ArrayBlockingQueue<EgressWork> egressHigh();

        DropOldestQueue<EgressWork> egressNormal();

        AtomicInteger pendingIngress();

        AtomicInteger pendingEgress();

        boolean accept(EnvelopeDraft draft);

        void complete(IngressWork work, ExecutionResult result);
    }

    private final Host host;
    private final DeadlineDaemon retryDelays = new DeadlineDaemon();
    private final ConcurrentHashMap<String, OutboundMessage> delayed = new ConcurrentHashMap<>();
    private final AtomicLong retrySeq = new AtomicLong();

    PipelineIoLoop(Host host) {
        this.host = host;
    }

    void start() {
        retryDelays.start("gateway-egress-retry", this::onRetryDue);
    }

    void close() {
        retryDelays.close();
        for (String key : List.copyOf(delayed.keySet())) {
            if (delayed.remove(key) != null) {
                host.pendingEgress().decrementAndGet();
            }
        }
    }

    void runIngress() {
        while (!host.stopped().get()) {
            try {
                drainRaw();
                IngressWork command = host.commandIngress().poll(20, TimeUnit.MILLISECONDS);
                if (command != null) {
                    handleDraft(command);
                }
                IngressWork telemetry = host.telemetryIngress().poll();
                if (telemetry != null) {
                    try {
                        handleDraft(telemetry);
                    } finally {
                        host.pendingIngress().decrementAndGet();
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                LOG.warn("Ingress 未捕获异常: {}", ex.getMessage());
            }
        }
    }

    void runEgress() {
        int highBatch = 0;
        while (!host.stopped().get()) {
            try {
                EgressWork high = host.egressHigh().poll(20, TimeUnit.MILLISECONDS);
                if (high != null) {
                    publishOnce(high.message());
                    highBatch++;
                    if (highBatch >= host.settings().priorityFairnessBatch()) {
                        EgressWork normal = host.egressNormal().poll();
                        if (normal != null) {
                            publishOnce(normal.message());
                        }
                        highBatch = 0;
                    }
                    continue;
                }
                EgressWork normal = host.egressNormal().poll();
                if (normal != null) {
                    publishOnce(normal.message());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void offerCorrelated(OutboundDraft draft) {
        Optional<OutboundMessage> sealed = host.engine().applyOutboundPlugins(draft);
        if (sealed.isEmpty()) {
            host.metrics().egressDrop(GatewayMetrics.DROP_OUTBOUND_PLUGIN);
            return;
        }
        EgressWork work = new EgressWork(sealed.get());
        host.pendingEgress().incrementAndGet();
        if (sealed.get().priority() == MessagePriority.HIGH) {
            try {
                boolean offered = host.egressHigh().offer(work, host.settings().commandOfferTimeout().toMillis(),
                        TimeUnit.MILLISECONDS);
                if (!offered) {
                    host.pendingEgress().decrementAndGet();
                    host.metrics().egressDrop(GatewayMetrics.DROP_EGRESS_OVERFLOW);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } else {
            List<EgressWork> dropped = host.egressNormal().offerDropOldest(work);
            dropped.forEach(item -> {
                host.pendingEgress().decrementAndGet();
                host.metrics().egressDrop(GatewayMetrics.DROP_TELEMETRY_EGRESS);
            });
        }
        host.metrics().egressQueueDepth(MessagePriority.HIGH, host.egressHigh().size());
        host.metrics().egressQueueDepth(MessagePriority.NORMAL, host.egressNormal().size());
    }

    private void drainRaw() {
        RawWork rawWork = host.rawIngress().poll();
        if (rawWork == null) {
            return;
        }
        try {
            RawInbound raw = rawWork.raw();
            Optional<Driver> driver = host.registries().findDriver(raw.capabilityType());
            if (driver.isEmpty()) {
                host.metrics().egressDrop(GatewayMetrics.DROP_DECODE);
                return;
            }
            try {
                EnvelopeDraft draft = driver.get().decode(raw);
                host.accept(draft);
            } catch (DecodeException ex) {
                host.metrics().egressDrop(GatewayMetrics.DROP_DECODE);
                LOG.warn("解码失败: {}", ex.getMessage());
            }
        } finally {
            host.pendingIngress().decrementAndGet();
        }
    }

    private void handleDraft(IngressWork work) {
        NormalizeOutcome outcome = host.engine().normalize(work.draft());
        if (outcome.drop()) {
            if (work.draft().kind() == EnvelopeKind.TELEMETRY) {
                host.metrics().egressDrop(GatewayMetrics.DROP_TELEMETRY_REJECT);
            }
            return;
        }
        if (outcome.failure() != null) {
            if (work.draft().kind() == EnvelopeKind.TELEMETRY) {
                host.metrics().egressDrop(GatewayMetrics.DROP_TELEMETRY_REJECT);
                return;
            }
            Envelope envelope = outcome.envelope();
            ExecutionResult rejected = envelope != null
                    ? ExecutionResult.rejected(envelope, outcome.failure())
                    : ExecutionResult.rejected(
                            FunctionCommand.from(syntheticEnvelope(work.draft(), outcome.failure())),
                            outcome.failure());
            offerCorrelated(host.engine().correlate(rejected));
            host.complete(work, rejected);
            return;
        }
        Envelope sealed = outcome.envelope();
        if (sealed.kind() == EnvelopeKind.TELEMETRY) {
            handleTelemetry(sealed);
            return;
        }
        FunctionCommand command = restoreCommand(work, sealed);
        if (command.deadlineAt() != null && java.time.Instant.now().isAfter(command.deadlineAt())) {
            ExecutionResult timeout = ExecutionResult.timeout(command, Failure.timeout("core", "已超过 deadlineAt"));
            offerCorrelated(host.engine().correlate(timeout));
            host.complete(work, timeout);
            return;
        }
        boolean awaitingReply = host.replies().awaits(command);
        if (awaitingReply && !host.replies().register(command)) {
            ExecutionResult rejected = ExecutionResult.rejected(command, Failure.replyWaiterFull(command.deviceId()));
            offerCorrelated(host.engine().correlate(rejected));
            host.complete(work, rejected);
            return;
        }
        boolean enqueued = host.scheduler().execute(command.deviceId(), () -> {
            ExecutionResult executed = host.engine().execute(command);
            FunctionCatalog catalog = host.functionCatalog();
            ExecutionResult result = catalog == null
                    ? executed
                    : catalog.find(command.deviceId(), command.functionId())
                            .map(def -> com.mtfm.gateway.spi.payload.ResultValueMapper.apply(def, executed))
                            .orElse(executed);
            if (awaitingReply && result.status() == com.mtfm.gateway.spi.model.ExecutionStatus.ACCEPTED) {
                host.complete(work, result);
                return;
            }
            if (awaitingReply) {
                host.replies().cancel(command);
            }
            offerCorrelated(host.engine().correlate(result));
            host.complete(work, result);
        });
        if (!enqueued) {
            if (awaitingReply) {
                host.replies().cancel(command);
            }
            Failure failure = host.scheduler().deviceSlots() >= host.settings().maxDeviceSlots()
                    ? Failure.deviceSlotExhausted(command.deviceId())
                    : Failure.deviceQueueOverflow(command.deviceId());
            ExecutionResult rejected = ExecutionResult.rejected(command, failure);
            offerCorrelated(host.engine().correlate(rejected));
            host.complete(work, rejected);
        }
    }

    private void handleTelemetry(Envelope sealed) {
        Optional<ExecutionResult> matched = host.replies().bind(sealed);
        if (matched.isPresent()) {
            offerCorrelated(host.engine().correlate(matched.get()));
            return;
        }
        if (host.replies().replyFrame(sealed)) {
            Optional<String> listenId = host.replies().listenFunctionId(sealed);
            if (listenId.isEmpty() && host.functionCatalog() != null) {
                listenId = host.functionCatalog().find(sealed.deviceId(), sealed.functionId())
                        .filter(def -> "READ".equalsIgnoreCase(def.accessType()))
                        .map(def -> sealed.functionId());
            }
            if (listenId.isPresent()) {
                String functionId = listenId.get();
                Envelope telemetry = functionId.equals(sealed.functionId())
                        ? sealed
                        : new Envelope(
                                sealed.envelopeId(),
                                sealed.requestId(),
                                sealed.direction(),
                                sealed.kind(),
                                sealed.deviceId(),
                                functionId,
                                sealed.capabilityType(),
                                sealed.payload(),
                                sealed.headers(),
                                sealed.trace(),
                                sealed.error(),
                                sealed.createdAt(),
                                sealed.deadlineAt());
                offerCorrelated(host.engine().correlateTelemetry(telemetry));
                return;
            }
            LOG.info("未匹配应答已丢弃 deviceId={} functionId={} topic={}",
                    sealed.deviceId(), sealed.functionId(),
                    sealed.headers().get("topic").orElse(""));
            host.metrics().egressDrop(GatewayMetrics.DROP_TELEMETRY_REJECT);
            return;
        }
        offerCorrelated(host.engine().correlateTelemetry(sealed));
    }

    private void publishOnce(OutboundMessage message) {
        if (host.stopped().get()) {
            host.pendingEgress().decrementAndGet();
            return;
        }
        int maxAttempts = message.kind() == EnvelopeKind.RESPONSE
                ? host.settings().responseMaxAttempts()
                : host.settings().telemetryMaxAttempts();
        PublishResult result = host.engine().publish(message);
        if (result instanceof PublishResult.Success) {
            host.metrics().egressPublish(message.channelHint(), message.kind(), "success");
            host.pendingEgress().decrementAndGet();
            return;
        }
        PublishResult.Failed failed = (PublishResult.Failed) result;
        host.metrics().egressPublish(message.channelHint(), message.kind(), "failed");
        int nextAttempt = message.attempt() + 1;
        if (!failed.failure().retryable() || nextAttempt >= maxAttempts) {
            host.metrics().egressDrop("publish_failed");
            host.pendingEgress().decrementAndGet();
            return;
        }
        host.metrics().egressRetry(message.channelHint());
        String key = Long.toString(retrySeq.incrementAndGet());
        delayed.put(key, message.withAttempt(nextAttempt));
        retryDelays.offer(key, 0L, Instant.now().plus(host.settings().retryDelay(message.attempt())));
    }

    private void onRetryDue(DeadlineDaemon.Task task) {
        OutboundMessage message = delayed.remove(task.key());
        if (message == null) {
            return;
        }
        publishOnce(message);
    }

    private static FunctionCommand restoreCommand(IngressWork work, Envelope sealed) {
        if (work.command() == null) {
            return FunctionCommand.from(sealed);
        }
        FunctionCommand original = work.command();
        return new FunctionCommand(
                sealed.requestId() != null ? sealed.requestId() : original.requestId(),
                sealed.deviceId(),
                sealed.functionId(),
                sealed.capabilityType() != null ? sealed.capabilityType() : original.capabilityType(),
                sealed.payload(),
                original.deliveryHints(),
                sealed.deadlineAt() != null ? sealed.deadlineAt() : original.deadlineAt());
    }

    private static Envelope syntheticEnvelope(EnvelopeDraft draft, Failure failure) {
        return new DefaultEnvelopeSealer().seal(draft.withError(failure));
    }
}
