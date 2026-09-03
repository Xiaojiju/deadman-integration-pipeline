package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.OutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP Webhook 出站。独立线程池，短超时、有界重试；失败不回压南向。
 */
public final class HttpWebhookPublisher implements NorthboundSink, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HttpWebhookPublisher.class);

    private final String webhookUrl;
    private final int maxAttempts;
    private final Duration timeout;
    private final ExecutorService pool;
    private final HttpClient client;

    public HttpWebhookPublisher(String webhookUrl) {
        this(webhookUrl, 2, Duration.ofMillis(3000));
    }

    public HttpWebhookPublisher(String webhookUrl, int maxAttempts, Duration timeout) {
        this.webhookUrl = blankToNull(webhookUrl);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofMillis(3000)
                : timeout;
        this.pool = Executors.newFixedThreadPool(2, namedFactory());
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .executor(pool)
                .build();
    }

    public boolean enabled() {
        return webhookUrl != null;
    }

    @Override
    public void publish(OutboundMessage message) {
        if (!enabled() || message == null) {
            return;
        }
        String body;
        try {
            body = NorthboundJson.stringify(message);
        } catch (RuntimeException ex) {
            LOG.warn("Webhook 序列化失败: {}", ex.getMessage());
            return;
        }
        pool.execute(() -> postWithRetry(body, message.deviceId()));
    }

    private void postWithRetry(String body, String deviceId) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                        .timeout(timeout)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return;
                }
                LOG.warn("Webhook 非成功状态 deviceId={} status={} attempt={}/{}",
                        deviceId, status, attempt, maxAttempts);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOG.warn("Webhook 中断 deviceId={}", deviceId);
                return;
            } catch (Exception ex) {
                LOG.warn("Webhook 失败 deviceId={} attempt={}/{}: {}",
                        deviceId, attempt, maxAttempts, ex.getMessage());
            }
        }
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }

    private static ThreadFactory namedFactory() {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "gateway-northbound-webhook-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
