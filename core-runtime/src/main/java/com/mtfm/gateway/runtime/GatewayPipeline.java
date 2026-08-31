package com.mtfm.gateway.runtime;

import com.mtfm.gateway.runtime.concurrent.DeviceSerialScheduler;
import com.mtfm.gateway.runtime.concurrent.DropOldestQueue;
import com.mtfm.gateway.runtime.metrics.CountingGatewayMetrics;
import com.mtfm.gateway.runtime.registry.DefaultRegistries;
import com.mtfm.gateway.runtime.seal.DefaultEnvelopeSealer;
import com.mtfm.gateway.runtime.stage.PipelineEngine;
import com.mtfm.gateway.runtime.stage.PipelineEngine.NormalizeOutcome;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.metrics.GatewayMetrics;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
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
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.port.EnvelopeSealer;
import com.mtfm.gateway.spi.port.PipelineCommandPort;
import com.mtfm.gateway.spi.port.PipelineIngress;
import com.mtfm.gateway.spi.port.PluginRegistry;
import com.mtfm.gateway.spi.port.PublisherRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关流水线调度核心门面，是运行时唯一入口。
 *
 * <p>负责线程池、有界队列与设备串行调度，将入站草稿经 {@link PipelineEngine} 五阶段处理后，
 * 异步投递到北向 {@link Publisher}。同时实现各类注册表接口，供宿主装配 Driver / Executor / Plugin。
 *
 * <h2>流水线阶段</h2>
 * <ol>
 *   <li><b>Ingress（入站）</b> — 从命令/遥测/原始字节队列取件；原始字节先经 {@link Driver#decode} 转为草稿</li>
 *   <li><b>Normalize（归一化）</b> — {@link PipelineEngine#normalize}：入站插件链 → 贴胶 → 目录/权限校验</li>
 *   <li><b>Execute（执行）</b> — {@link DeviceSerialScheduler} 按 deviceId 串行调用 {@link PipelineEngine#execute}</li>
 *   <li><b>Correlate（关联）</b> — 将 {@link ExecutionResult} 或遥测 {@link Envelope} 包装为 {@link OutboundDraft}</li>
 *   <li><b>Egress（出站）</b> — 出站插件链 → 贴胶 → 优先级队列 → {@link PipelineEngine#publish} 带重试</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * GatewayPipeline pipeline = GatewayPipeline.builder()
 *         .functionCatalog(catalogStore)
 *         .build();
 * pipeline.register(ModbusCapability.DESCRIPTOR, new ModbusDriver(), modbusExecutor);
 * pipeline.register(cloudPublisher);
 * pipeline.start();
 *
 * CompletableFuture<ExecutionResult> future = pipeline.submit(
 *         FunctionCommand.of("dev-001", "fn.read", Map.of("area", "HOLDING", "offset", 0)));
 * ExecutionResult result = future.get(5, TimeUnit.SECONDS);
 *
 * pipeline.stop();
 * }</pre>
 *
 * @see PipelineEngine
 * @see GatewaySettings
 * @see DefaultRegistries
 */
public final class GatewayPipeline implements PipelineIngress, PipelineCommandPort,
        DriverRegistry, PluginRegistry, PublisherRegistry, CapabilityRegistrar, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayPipeline.class);

    private final DefaultRegistries registries;
    private final GatewaySettings settings;
    private final PipelineEngine engine;
    private final CountingGatewayMetrics counters;
    private final GatewayMetrics metrics;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ArrayBlockingQueue<IngressWork> commandIngress;
    private final DropOldestQueue<IngressWork> telemetryIngress;
    private final ArrayBlockingQueue<RawWork> rawIngress;
    private final ArrayBlockingQueue<EgressWork> egressHigh;
    private final DropOldestQueue<EgressWork> egressNormal;
    private final ConcurrentHashMap<String, CompletableFuture<ExecutionResult>> inflight = new ConcurrentHashMap<>();
    private final AtomicInteger pendingEgress = new AtomicInteger();
    private DeviceSerialScheduler scheduler;
    private ExecutorService ingressPool;
    private ExecutorService egressPool;

    public GatewayPipeline(FunctionCatalog functionCatalog) {
        this(functionCatalog, GatewaySettings.defaults(), new DefaultEnvelopeSealer(), new CountingGatewayMetrics());
    }

    public GatewayPipeline(FunctionCatalog functionCatalog, GatewaySettings settings,
            EnvelopeSealer sealer, CountingGatewayMetrics counters) {
        this.registries = new DefaultRegistries();
        this.settings = settings;
        this.counters = counters;
        this.metrics = counters;
        this.engine = new PipelineEngine(registries, functionCatalog, sealer);
        this.commandIngress = new ArrayBlockingQueue<>(settings.ingressCommandCapacity());
        this.telemetryIngress = new DropOldestQueue<>(settings.ingressTelemetryCapacity());
        this.rawIngress = new ArrayBlockingQueue<>(settings.ingressRawCapacity());
        this.egressHigh = new ArrayBlockingQueue<>(settings.egressHighCapacity());
        this.egressNormal = new DropOldestQueue<>(settings.egressNormalCapacity());
    }

    public static GatewayPipelineBuilder builder() {
        return new GatewayPipelineBuilder();
    }

    public void start() {
        if (stopped.get()) {
            LOG.warn("流水线已停止，忽略 start()");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        this.scheduler = new DeviceSerialScheduler(
                settings.executeWorkers(), settings.executeQueueCapacity(), settings.maxDeviceSlots());
        this.ingressPool = Executors.newFixedThreadPool(
                settings.ingressWorkers(), DeviceSerialScheduler.namedFactory("gateway-ingress"));
        this.egressPool = Executors.newFixedThreadPool(
                settings.egressWorkers(), DeviceSerialScheduler.namedFactory("gateway-egress"));
        for (int i = 0; i < settings.ingressWorkers(); i++) {
            ingressPool.execute(this::runIngress);
        }
        for (int i = 0; i < settings.egressWorkers(); i++) {
            egressPool.execute(this::runEgress);
        }
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        completeAll(Failure.pipelineStopped());
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(settings.shutdownTimeout());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            scheduler.close();
        }
        shutdownPool(ingressPool);
        shutdownPool(egressPool);
        started.set(false);
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public boolean accept(EnvelopeDraft draft) {
        ensureStarted();
        if (stopped.get() || draft == null) {
            return false;
        }
        IngressWork work = new IngressWork(draft, new CompletableFuture<>());
        if (draft.kind() == EnvelopeKind.TELEMETRY) {
            List<IngressWork> dropped = telemetryIngress.offerDropOldest(work);
            dropped.forEach(item -> metrics.egressDrop(GatewayMetrics.DROP_TELEMETRY_INGRESS));
            return true;
        }
        try {
            boolean offered = commandIngress.offer(work, settings.commandOfferTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!offered) {
                complete(work, ExecutionResult.rejected(
                        FunctionCommand.from(syntheticEnvelope(draft, Failure.ingressOverflow())),
                        Failure.ingressOverflow()));
            }
            return offered;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean acceptRaw(RawInbound raw) {
        ensureStarted();
        if (stopped.get() || raw == null) {
            return false;
        }
        try {
            return rawIngress.offer(new RawWork(raw), settings.commandOfferTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public CompletableFuture<ExecutionResult> submit(FunctionCommand command) {
        ensureStarted();
        CompletableFuture<ExecutionResult> future = new CompletableFuture<>();
        if (stopped.get()) {
            future.complete(ExecutionResult.rejected(command, Failure.pipelineStopped()));
            return future;
        }
        EnvelopeDraft draft = EnvelopeDraft.builder()
                .requestId(command.requestId())
                .kind(EnvelopeKind.COMMAND)
                .deviceId(command.deviceId())
                .functionId(command.functionId())
                .capabilityType(command.capabilityType())
                .payload(command.arguments())
                .deadlineAt(command.deadlineAt())
                .build();
        IngressWork work = new IngressWork(draft, future);
        remember(command.requestId(), future);
        try {
            boolean offered = commandIngress.offer(work, settings.commandOfferTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!offered) {
                complete(work, ExecutionResult.rejected(command, Failure.ingressOverflow()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            complete(work, ExecutionResult.rejected(command, Failure.pipelineStopped()));
        }
        return future;
    }

    public boolean awaitIdle(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (inflight.isEmpty() && commandIngress.isEmpty() && rawIngress.isEmpty()
                    && egressHigh.isEmpty() && telemetryIngress.size() == 0 && egressNormal.size() == 0
                    && pendingEgress.get() == 0) {
                return true;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return inflight.isEmpty();
    }

    public CountingGatewayMetrics stats() {
        return counters;
    }

    @Override
    public void registerDriver(Driver driver) {
        registries.registerDriver(driver);
    }

    @Override
    public void registerExecutor(FunctionExecutor executor) {
        registries.registerExecutor(executor);
    }

    @Override
    public boolean register(String deviceId, String capabilityType) {
        return registries.register(deviceId, capabilityType);
    }

    @Override
    public boolean unregister(String deviceId) {
        Optional<String> type = registries.findCapabilityType(deviceId);
        boolean removed = registries.unregister(deviceId);
        type.flatMap(registries::findExecutor).ifPresent(executor -> executor.unbind(deviceId));
        return removed;
    }

    @Override
    public boolean register(InboundPlugin plugin) {
        return registries.register(plugin);
    }

    @Override
    public boolean register(OutboundPlugin plugin) {
        return registries.register(plugin);
    }

    @Override
    public boolean register(Publisher publisher) {
        return registries.register(publisher);
    }

    @Override
    public void register(CapabilityDescriptor descriptor, Driver driver, FunctionExecutor executor) {
        registries.register(descriptor, driver, executor);
    }

    @Override
    public Optional<CapabilityDescriptor> find(String capabilityType) {
        return registries.find(capabilityType);
    }

    @Override
    public java.util.List<CapabilityDescriptor> list() {
        return registries.list();
    }

    public DefaultRegistries registries() {
        return registries;
    }

    private void runIngress() {
        while (!stopped.get()) {
            try {
                drainRaw();
                IngressWork command = commandIngress.poll(20, TimeUnit.MILLISECONDS);
                if (command != null) {
                    handleDraft(command);
                }
                IngressWork telemetry = telemetryIngress.poll();
                if (telemetry != null) {
                    handleDraft(telemetry);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                LOG.warn("Ingress 未捕获异常: {}", ex.getMessage());
            }
        }
    }

    private void drainRaw() {
        RawWork rawWork = rawIngress.poll();
        if (rawWork == null) {
            return;
        }
        RawInbound raw = rawWork.raw();
        Optional<Driver> driver = registries.findDriver(raw.capabilityType());
        if (driver.isEmpty()) {
            metrics.egressDrop(GatewayMetrics.DROP_DECODE);
            return;
        }
        try {
            EnvelopeDraft draft = driver.get().decode(raw);
            accept(draft);
        } catch (DecodeException ex) {
            metrics.egressDrop(GatewayMetrics.DROP_DECODE);
            LOG.warn("解码失败: {}", ex.getMessage());
        }
    }

    private void handleDraft(IngressWork work) {
        NormalizeOutcome outcome = engine.normalize(work.draft());
        if (outcome.drop()) {
            if (work.draft().kind() == EnvelopeKind.TELEMETRY) {
                metrics.egressDrop(GatewayMetrics.DROP_TELEMETRY_REJECT);
            }
            return;
        }
        if (outcome.failure() != null) {
            if (work.draft().kind() == EnvelopeKind.TELEMETRY) {
                metrics.egressDrop(GatewayMetrics.DROP_TELEMETRY_REJECT);
                return;
            }
            Envelope envelope = outcome.envelope();
            ExecutionResult rejected = envelope != null
                    ? ExecutionResult.rejected(envelope, outcome.failure())
                    : ExecutionResult.rejected(
                            FunctionCommand.from(syntheticEnvelope(work.draft(), outcome.failure())),
                            outcome.failure());
            offerCorrelated(engine.correlate(rejected));
            complete(work, rejected);
            return;
        }
        Envelope sealed = outcome.envelope();
        if (sealed.kind() == EnvelopeKind.TELEMETRY) {
            offerCorrelated(engine.correlateTelemetry(sealed));
            return;
        }
        FunctionCommand command = FunctionCommand.from(sealed);
        if (command.deadlineAt() != null && Instant.now().isAfter(command.deadlineAt())) {
            ExecutionResult timeout = ExecutionResult.timeout(command, Failure.timeout("core", "已超过 deadlineAt"));
            offerCorrelated(engine.correlate(timeout));
            complete(work, timeout);
            return;
        }
        boolean enqueued = scheduler.execute(command.deviceId(), () -> {
            ExecutionResult result = engine.execute(command);
            offerCorrelated(engine.correlate(result));
            complete(work, result);
        });
        if (!enqueued) {
            Failure failure = scheduler.deviceSlots() >= settings.maxDeviceSlots()
                    ? Failure.deviceSlotExhausted(command.deviceId())
                    : Failure.deviceQueueOverflow(command.deviceId());
            ExecutionResult rejected = ExecutionResult.rejected(command, failure);
            offerCorrelated(engine.correlate(rejected));
            complete(work, rejected);
        }
    }

    private void offerCorrelated(OutboundDraft draft) {
        Optional<OutboundMessage> sealed = engine.applyOutboundPlugins(draft);
        if (sealed.isEmpty()) {
            metrics.egressDrop(GatewayMetrics.DROP_OUTBOUND_PLUGIN);
            return;
        }
        EgressWork work = new EgressWork(sealed.get());
        pendingEgress.incrementAndGet();
        if (sealed.get().priority() == MessagePriority.HIGH) {
            try {
                boolean offered = egressHigh.offer(work, settings.commandOfferTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!offered) {
                    pendingEgress.decrementAndGet();
                    metrics.egressDrop(GatewayMetrics.DROP_EGRESS_OVERFLOW);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } else {
            List<EgressWork> dropped = egressNormal.offerDropOldest(work);
            dropped.forEach(item -> {
                pendingEgress.decrementAndGet();
                metrics.egressDrop(GatewayMetrics.DROP_TELEMETRY_EGRESS);
            });
        }
        metrics.egressQueueDepth(MessagePriority.HIGH, egressHigh.size());
        metrics.egressQueueDepth(MessagePriority.NORMAL, egressNormal.size());
    }

    private void runEgress() {
        int highBatch = 0;
        while (!stopped.get()) {
            try {
                EgressWork high = egressHigh.poll(20, TimeUnit.MILLISECONDS);
                if (high != null) {
                    publishWithRetry(high.message());
                    pendingEgress.decrementAndGet();
                    highBatch++;
                    if (highBatch >= settings.priorityFairnessBatch()) {
                        EgressWork normal = egressNormal.poll();
                        if (normal != null) {
                            publishWithRetry(normal.message());
                            pendingEgress.decrementAndGet();
                        }
                        highBatch = 0;
                    }
                    continue;
                }
                EgressWork normal = egressNormal.poll();
                if (normal != null) {
                    publishWithRetry(normal.message());
                    pendingEgress.decrementAndGet();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void publishWithRetry(OutboundMessage message) {
        int maxAttempts = message.kind() == EnvelopeKind.RESPONSE
                ? settings.responseMaxAttempts()
                : settings.telemetryMaxAttempts();
        OutboundMessage current = message;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            PublishResult result = engine.publish(current);
            if (result instanceof PublishResult.Success) {
                metrics.egressPublish(current.channelHint(), current.kind(), "success");
                return;
            }
            PublishResult.Failed failed = (PublishResult.Failed) result;
            metrics.egressPublish(current.channelHint(), current.kind(), "failed");
            if (!failed.failure().retryable() || attempt + 1 >= maxAttempts) {
                metrics.egressDrop("publish_failed");
                return;
            }
            metrics.egressRetry(current.channelHint());
            try {
                Thread.sleep(settings.retryDelay(attempt).toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            current = current.withAttempt(attempt + 1);
        }
    }

    private void ensureStarted() {
        if (!started.get() && !stopped.get()) {
            start();
        }
    }

    private void remember(String requestId, CompletableFuture<ExecutionResult> future) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        inflight.put(requestId, future);
    }

    private void complete(IngressWork work, ExecutionResult result) {
        metrics.correlateResponse(result.status());
        work.future().complete(result);
        if (work.draft().requestId() != null) {
            inflight.remove(work.draft().requestId(), work.future());
        }
    }

    private void completeAll(Failure failure) {
        inflight.forEach((key, future) -> future.complete(
                new ExecutionResult(key, "unknown", "unknown",
                        com.mtfm.gateway.spi.model.ExecutionStatus.REJECTED,
                        com.mtfm.gateway.spi.model.Attributes.empty(), failure)));
        inflight.clear();
    }

    private Envelope syntheticEnvelope(EnvelopeDraft draft, Failure failure) {
        return new DefaultEnvelopeSealer().seal(draft.withError(failure));
    }

    private void shutdownPool(ExecutorService pool) {
        if (pool == null) {
            return;
        }
        pool.shutdownNow();
    }

    private record IngressWork(EnvelopeDraft draft, CompletableFuture<ExecutionResult> future) {
    }

    private record RawWork(RawInbound raw) {
    }

    private record EgressWork(OutboundMessage message) {
    }
}
