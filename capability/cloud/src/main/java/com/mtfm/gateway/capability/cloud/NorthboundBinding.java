package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.northbound.NorthboundLiveStatus;
import com.mtfm.gateway.spi.northbound.NorthboundSettings;
import com.mtfm.gateway.spi.port.NorthboundRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 按 catalog 配置热切换北向 MQTT 会话、入站与 Webhook。失败只记状态，不回压南向。
 */
public final class NorthboundBinding implements NorthboundRuntime, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NorthboundBinding.class);

    private final CloudPublisher publisher;
    private final NorthboundCommandPort commandPort;
    private volatile NorthboundMqttSession session;
    private volatile NorthboundMqttIngress ingress;
    private volatile NorthboundLiveStatus live = NorthboundLiveStatus.idle();

    public NorthboundBinding(CloudPublisher publisher, NorthboundCommandPort commandPort) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.commandPort = commandPort;
    }

    @Override
    public synchronized void apply(NorthboundSettings settings) {
        NorthboundSettings spec = settings == null ? NorthboundSettings.disabled() : settings;
        List<NorthboundSink> next = new ArrayList<>();
        NorthboundMqttSession newSession = null;
        NorthboundMqttIngress newIngress = null;
        boolean mqttLive = false;
        String mqttError = null;
        boolean httpLive = false;
        try {
            if (spec.mqttReady()) {
                newSession = createSession(spec);
                newIngress = new NorthboundMqttIngress(
                        newSession, commandPort, spec.commandTopicOrDefault());
                newIngress.start();
                next.add(new NorthboundMqttPublisher(
                        newSession, spec.responseTopicOrDefault(), spec.telemetryTopicOrDefault()));
                mqttLive = true;
            }
        } catch (RuntimeException ex) {
            mqttError = ex.getMessage();
            LOG.warn("北向 MQTT 应用失败: {}", mqttError);
            closeQuietly(newIngress);
            closeQuietly(newSession);
            newSession = null;
            newIngress = null;
        }
        if (spec.httpReady()) {
            next.add(new HttpWebhookPublisher(
                    spec.httpWebhookUrl(),
                    spec.maxAttemptsOrDefault(),
                    Duration.ofMillis(spec.timeoutMsOrDefault())));
            httpLive = true;
        }
        publisher.replaceSinks(next);
        closeQuietly(ingress);
        closeQuietly(session);
        this.session = newSession;
        this.ingress = newIngress;
        this.live = new NorthboundLiveStatus(mqttLive, mqttError, httpLive);
        LOG.info("北向已应用 mqttLive={} httpLive={} mqttError={}", mqttLive, httpLive, mqttError);
    }

    @Override
    public NorthboundLiveStatus status() {
        return live;
    }

    NorthboundMqttSession session() {
        return session;
    }

    @Override
    public synchronized void close() {
        publisher.replaceSinks(List.of());
        closeQuietly(ingress);
        closeQuietly(session);
        ingress = null;
        session = null;
        live = NorthboundLiveStatus.idle();
    }

    private static NorthboundMqttSession createSession(NorthboundSettings spec) {
        if (spec.memoryTransport()) {
            return new InMemoryNorthboundMqttSession();
        }
        return new PahoNorthboundMqttSession(
                spec.mqttUrl(), spec.clientIdOrDefault(), spec.mqttUsername(), spec.mqttPassword());
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            LOG.warn("关闭北向资源失败: {}", ex.getMessage());
        }
    }
}
