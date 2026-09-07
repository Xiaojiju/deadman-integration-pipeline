package com.mtfm.gateway.runtime;

import com.mtfm.gateway.runtime.PipelineIoLoop.EgressWork;
import com.mtfm.gateway.runtime.PipelineIoLoop.IngressWork;
import com.mtfm.gateway.runtime.PipelineIoLoop.RawWork;
import com.mtfm.gateway.runtime.concurrent.DeviceSerialScheduler;
import com.mtfm.gateway.runtime.concurrent.DropOldestQueue;
import com.mtfm.gateway.runtime.metrics.CountingGatewayMetrics;
import com.mtfm.gateway.runtime.registry.DefaultRegistries;
import com.mtfm.gateway.runtime.reply.ReplyOrchestrator;
import com.mtfm.gateway.runtime.reply.ReplyWaiter;
import com.mtfm.gateway.runtime.seal.DefaultEnvelopeSealer;
import com.mtfm.gateway.runtime.stage.PipelineEngine;
import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.metrics.GatewayMetrics;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.RawInbound;
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;
import com.mtfm.gateway.spi.port.EnvelopeSealer;
import com.mtfm.gateway.spi.port.PipelineCommandPort;
import com.mtfm.gateway.spi.port.PipelineIngress;
import com.mtfm.gateway.spi.port.ReplyOccupancy;
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
 * 网关流水线调度门面。线程池与队列在此，入出站循环见 {@link PipelineIoLoop}，应答见
 * {@link ReplyOrchestrator}。
 *
 * <p>使用示例：
 * <pre>{@code
 * GatewayPipeline pipeline = GatewayPipeline.builder()
 *         .functionCatalog(catalog)
 *         .build();
 * pipeline.registerDriver(modbusDriver);
 * pipeline.registerExecutor(modbusExecutor);
 * pipeline.start();
 * pipeline.submit(command);
 * }</pre>
 */
public final class GatewayPipeline implements PipelineIngress, PipelineCommandPort, ReplyOccupancy,
        PipelineIoLoop.Host, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayPipeline.class);

    /**
     * 注册表。
     */
    private final DefaultRegistries registries;
    /**
     * 设置。
     */
    private final GatewaySettings settings;
    /**
     * 引擎。
     */
    private final PipelineEngine engine;
    /**
     * 函数目录。
     */
    private final FunctionCatalog functionCatalog;
    /**
     * 应答器。
     */
    private final ReplyOrchestrator replies;
    /**
     * 入出站循环。
     */
    private final PipelineIoLoop io;
    /**
     * 计数器。
     */
    private final CountingGatewayMetrics counters;
    /**
     * 指标。
     */
    private final GatewayMetrics metrics;
    /**
     * 启动标志。
     */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /**
     * 停止标志。
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    /**
     * 命令入站队列。
     */
    private final ArrayBlockingQueue<IngressWork> commandIngress;
    /**
     * 遥测入站队列。
     */
    private final DropOldestQueue<IngressWork> telemetryIngress;
    /**
     * 原始入站队列。
     */
    private final ArrayBlockingQueue<RawWork> rawIngress;
    /**
     * 出口高优先级队列。
     */
    private final ArrayBlockingQueue<EgressWork> egressHigh;
    /**
     * 出口普通优先级队列。
     */
    private final DropOldestQueue<EgressWork> egressNormal;
    /**
     * 正在执行的指令。
     */
    private final ConcurrentHashMap<String, CompletableFuture<ExecutionResult>> inflight = new ConcurrentHashMap<>();
    /**
     * 出口等待计数器。
     */
    private final AtomicInteger pendingEgress = new AtomicInteger();
    /**
     * 入站等待计数器。
     */
    private final AtomicInteger pendingIngress = new AtomicInteger();
    /**
     * 设备调度器。
     */
    private DeviceSerialScheduler scheduler;
    /**
     * 入站线程池。
     */
    private ExecutorService ingressPool;
    /**
     * 出口线程池。
     */
    private ExecutorService egressPool;

    public GatewayPipeline(FunctionCatalog functionCatalog) {
        this(functionCatalog, GatewaySettings.defaults(), new DefaultEnvelopeSealer(), new CountingGatewayMetrics());
    }

    public GatewayPipeline(FunctionCatalog functionCatalog, GatewaySettings settings,
            EnvelopeSealer sealer, CountingGatewayMetrics counters) {
        this(functionCatalog, settings, sealer, counters, new DefaultRegistries());
    }

    public GatewayPipeline(FunctionCatalog functionCatalog, GatewaySettings settings,
            EnvelopeSealer sealer, CountingGatewayMetrics counters, DefaultRegistries registries) {
        this.registries = registries == null ? new DefaultRegistries() : registries;
        this.settings = settings;
        this.functionCatalog = functionCatalog;
        this.counters = counters;
        this.metrics = counters;
        this.engine = new PipelineEngine(this.registries, this.registries, this.registries, functionCatalog, sealer);
        this.replies = new ReplyOrchestrator(functionCatalog, this::onReplyTimeout);
        this.commandIngress = new ArrayBlockingQueue<>(settings.ingressCommandCapacity());
        this.telemetryIngress = new DropOldestQueue<>(settings.ingressTelemetryCapacity());
        this.rawIngress = new ArrayBlockingQueue<>(settings.ingressRawCapacity());
        this.egressHigh = new ArrayBlockingQueue<>(settings.egressHighCapacity());
        this.egressNormal = new DropOldestQueue<>(settings.egressNormalCapacity());
        this.io = new PipelineIoLoop(this);
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
        this.replies.start();
        this.io.start();
        this.ingressPool = Executors.newFixedThreadPool(
                settings.ingressWorkers(), DeviceSerialScheduler.namedFactory("gateway-ingress"));
        this.egressPool = Executors.newFixedThreadPool(
                settings.egressWorkers(), DeviceSerialScheduler.namedFactory("gateway-egress"));
        for (int i = 0; i < settings.ingressWorkers(); i++) {
            ingressPool.execute(io::runIngress);
        }
        for (int i = 0; i < settings.egressWorkers(); i++) {
            egressPool.execute(io::runEgress);
        }
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        completeAll(Failure.pipelineStopped());
        replies.close();
        io.close();
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
        IngressWork work = new IngressWork(draft, new CompletableFuture<>(), null);
        if (draft.kind() == EnvelopeKind.TELEMETRY) {
            pendingIngress.incrementAndGet();
            List<IngressWork> dropped = telemetryIngress.offerDropOldest(work);
            dropped.forEach(item -> {
                pendingIngress.decrementAndGet();
                metrics.egressDrop(GatewayMetrics.DROP_TELEMETRY_INGRESS);
            });
            return true;
        }
        try {
            boolean offered = commandIngress.offer(work, settings.commandOfferTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
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
            boolean offered = rawIngress.offer(new RawWork(raw), settings.commandOfferTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (offered) {
                pendingIngress.incrementAndGet();
            }
            return offered;
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
        IngressWork work = new IngressWork(draft, future, command);
        remember(command.requestId(), future);
        try {
            boolean offered = commandIngress.offer(work, settings.commandOfferTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
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
                    && pendingEgress.get() == 0 && pendingIngress.get() == 0) {
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
    public boolean awaiting(String deviceId, String functionId) {
        return replies.awaiting(deviceId, functionId);
    }

    public void registerDriver(Driver driver) {
        registries.registerDriver(driver);
    }

    public void registerExecutor(FunctionExecutor executor) {
        registries.registerExecutor(executor);
    }

    public boolean register(String deviceId, String capabilityType) {
        return registries.register(deviceId, capabilityType);
    }

    public boolean unregister(String deviceId) {
        Optional<String> type = registries.findCapabilityType(deviceId);
        boolean removed = registries.unregister(deviceId);
        type.flatMap(registries::findExecutor).ifPresent(executor -> executor.unbind(deviceId));
        return removed;
    }

    public boolean isRegistered(String deviceId) {
        return registries.isRegistered(deviceId);
    }

    public Optional<FunctionExecutor> findExecutor(String capabilityType) {
        return registries.findExecutor(capabilityType);
    }

    public boolean register(InboundPlugin plugin) {
        return registries.register(plugin);
    }

    public boolean register(OutboundPlugin plugin) {
        return registries.register(plugin);
    }

    public boolean register(Publisher publisher) {
        return registries.register(publisher);
    }

    public void register(CapabilityDescriptor descriptor, Driver driver, FunctionExecutor executor) {
        registries.register(descriptor, driver, executor);
    }

    public Optional<CapabilityDescriptor> find(String capabilityType) {
        return registries.find(capabilityType);
    }

    public List<CapabilityDescriptor> list() {
        return registries.list();
    }

    @Override
    public DefaultRegistries registries() {
        return registries;
    }

    @Override
    public AtomicBoolean stopped() {
        return stopped;
    }

    @Override
    public GatewaySettings settings() {
        return settings;
    }

    @Override
    public PipelineEngine engine() {
        return engine;
    }

    @Override
    public FunctionCatalog functionCatalog() {
        return functionCatalog;
    }

    @Override
    public ReplyOrchestrator replies() {
        return replies;
    }

    @Override
    public GatewayMetrics metrics() {
        return metrics;
    }

    @Override
    public DeviceSerialScheduler scheduler() {
        return scheduler;
    }

    @Override
    public ArrayBlockingQueue<IngressWork> commandIngress() {
        return commandIngress;
    }

    @Override
    public DropOldestQueue<IngressWork> telemetryIngress() {
        return telemetryIngress;
    }

    @Override
    public ArrayBlockingQueue<RawWork> rawIngress() {
        return rawIngress;
    }

    @Override
    public ArrayBlockingQueue<EgressWork> egressHigh() {
        return egressHigh;
    }

    @Override
    public DropOldestQueue<EgressWork> egressNormal() {
        return egressNormal;
    }

    @Override
    public AtomicInteger pendingIngress() {
        return pendingIngress;
    }

    @Override
    public AtomicInteger pendingEgress() {
        return pendingEgress;
    }

    @Override
    public void complete(IngressWork work, ExecutionResult result) {
        metrics.correlateResponse(result.status());
        work.future().complete(result);
        if (work.draft().requestId() != null) {
            inflight.remove(work.draft().requestId(), work.future());
        }
    }

    private void onReplyTimeout(ReplyWaiter.Pending pending) {
        if (stopped.get()) {
            return;
        }
        FunctionCommand command = new FunctionCommand(
                pending.requestId(),
                pending.deviceId(),
                pending.functionId(),
                null,
                Attributes.empty(),
                Attributes.empty(),
                null);
        io.offerCorrelated(engine.correlate(ExecutionResult.timeout(
                command, Failure.timeout("core", "指令应答超时"))));
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
}
