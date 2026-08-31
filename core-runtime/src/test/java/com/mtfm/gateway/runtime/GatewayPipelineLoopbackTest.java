package com.mtfm.gateway.runtime;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FailureCodes;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.model.RawInbound;
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayPipelineLoopbackTest {

    private GatewayPipeline pipeline;

    @AfterEach
    void tearDown() {
        if (pipeline != null) {
            pipeline.close();
        }
    }

    @Test
    void 提交命令拿到贴胶回执() throws Exception {
        StubExecutor executor = new StubExecutor();
        StubPublisher publisher = new StubPublisher();
        InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog().allow("dev-1", "fn.switch");
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        pipeline.register(new CapabilityDescriptor("PROTO", List.of(), List.of()), new StubDriver(), executor);
        pipeline.register("dev-1", "PROTO");
        pipeline.register(publisher);
        pipeline.start();

        ExecutionResult result = pipeline.submit(FunctionCommand.of("dev-1", "fn.switch", Map.of("action", "on")))
                .get(3, TimeUnit.SECONDS);
        assertTrue(pipeline.awaitIdle(Duration.ofSeconds(2)));

        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals("on", executor.state);
        assertFalse(publisher.published.isEmpty());
        CommandResponse response = (CommandResponse) publisher.published.get(0).body();
        assertEquals(ExecutionStatus.SUCCESS, response.status());
        assertFalse(publisher.published.get(0).messageId().isBlank());
    }

    @Test
    void 插件拒绝不贴胶不进Execute() throws Exception {
        StubExecutor executor = new StubExecutor();
        StubPublisher publisher = new StubPublisher();
        InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog().allow("dev-1", "fn.switch");
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        pipeline.register(new CapabilityDescriptor("PROTO", List.of(), List.of()), new StubDriver(), executor);
        pipeline.register("dev-1", "PROTO");
        pipeline.register(publisher);
        pipeline.register(new RejectPlugin());
        pipeline.start();

        ExecutionResult result = pipeline.submit(FunctionCommand.of("dev-1", "fn.switch", Map.of("action", "on")))
                .get(3, TimeUnit.SECONDS);
        pipeline.awaitIdle(Duration.ofSeconds(2));

        assertEquals(ExecutionStatus.REJECTED, result.status());
        assertEquals(FailureCodes.PLUGIN_REJECT, result.failure().code());
        assertEquals(0, executor.calls.get());
        assertFalse(publisher.published.isEmpty());
    }

    @Test
    void 遥测跳过Execute() {
        StubExecutor executor = new StubExecutor();
        StubPublisher publisher = new StubPublisher();
        pipeline = GatewayPipeline.builder().build();
        pipeline.register(new CapabilityDescriptor("PROTO", List.of(), List.of()), new StubDriver(), executor);
        pipeline.register("dev-1", "PROTO");
        pipeline.register(publisher);
        pipeline.start();

        assertTrue(pipeline.accept(EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .deviceId("dev-1")
                .payload(Map.of("temp", "18"))
                .build()));
        pipeline.awaitIdle(Duration.ofSeconds(2));
        assertEquals(0, executor.calls.get());
        assertFalse(publisher.published.isEmpty());
    }

    private static final class StubDriver implements Driver {
        @Override
        public String capabilityType() {
            return "PROTO";
        }

        @Override
        public EnvelopeDraft decode(RawInbound raw) {
            return EnvelopeDraft.builder()
                    .kind(EnvelopeKind.TELEMETRY)
                    .deviceId(raw.deviceIdHint())
                    .capabilityType(capabilityType())
                    .payload(Attributes.empty())
                    .build();
        }
    }

    private static final class StubExecutor implements FunctionExecutor {
        private volatile String state = "off";
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String capabilityType() {
            return "PROTO";
        }

        @Override
        public ExecutionResult execute(FunctionCommand command) {
            calls.incrementAndGet();
            state = String.valueOf(command.arguments().get("action").orElse("off"));
            return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                    Map.of("state", state));
        }
    }

    private static final class StubPublisher implements Publisher {
        private final CopyOnWriteArrayList<OutboundMessage> published = new CopyOnWriteArrayList<>();

        @Override
        public String channel() {
            return Channels.CLOUD;
        }

        @Override
        public PublishResult publish(OutboundMessage message) {
            published.add(message);
            return PublishResult.success();
        }
    }

    private static final class RejectPlugin implements InboundPlugin {
        @Override
        public String name() {
            return "reject-all";
        }

        @Override
        public int order() {
            return 0;
        }

        @Override
        public boolean support(EnvelopeDraft draft) {
            return true;
        }

        @Override
        public InboundApplyResult apply(EnvelopeDraft draft) {
            return InboundApplyResult.reject(name(), "测试拒绝");
        }
    }
}
