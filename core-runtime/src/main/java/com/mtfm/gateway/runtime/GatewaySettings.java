package com.mtfm.gateway.runtime;

import java.time.Duration;

/**
 * 流水线运行参数，控制队列容量、线程数、重试与超时策略。
 *
 * <p>默认值见 {@code docs/runtime-defaults.md}。可通过 {@link #builder()} 覆盖单项：
 * <pre>{@code
 * GatewaySettings settings = GatewaySettings.builder()
 *         .executeWorkers(8)
 *         .ingressCommandCapacity(512)
 *         .build();
 * }</pre>
 *
 * @param responseMaxAttempts     命令响应出站最大重试次数
 * @param telemetryMaxAttempts    遥测出站最大重试次数（通常为 1，丢旧保新）
 * @param ingressCommandCapacity  命令入站有界队列容量
 * @param ingressTelemetryCapacity 遥测入站 DropOldest 队列容量
 * @param ingressRawCapacity      原始字节入站队列容量
 * @param egressHighCapacity      高优先级出站队列容量
 * @param egressNormalCapacity    普通出站 DropOldest 队列容量
 * @param executeQueueCapacity    单设备执行队列容量
 * @param maxDeviceSlots          活跃设备槽上限，防伪造海量 deviceId
 * @param ingressWorkers          入站消费线程数
 * @param executeWorkers          执行线程池大小（跨设备并行）
 * @param egressWorkers           出站消费线程数
 * @param priorityFairnessBatch   高优先级连续处理批次，之后让出普通队列
 * @param commandOfferTimeout     入队 offer 超时
 * @param shutdownTimeout         stop 时等待调度器终止的超时
 * @param retryBaseDelay          出站重试指数退避基数
 * @param retryMaxDelay           出站重试最大间隔
 * @param idempotencyTtl          幂等窗口（预留）
 * @param idempotencyCapacity     幂等缓存容量（预留）
 */
public record GatewaySettings(
        int responseMaxAttempts,
        int telemetryMaxAttempts,
        int ingressCommandCapacity,
        int ingressTelemetryCapacity,
        int ingressRawCapacity,
        int egressHighCapacity,
        int egressNormalCapacity,
        int executeQueueCapacity,
        int maxDeviceSlots,
        int ingressWorkers,
        int executeWorkers,
        int egressWorkers,
        int priorityFairnessBatch,
        Duration commandOfferTimeout,
        Duration shutdownTimeout,
        Duration retryBaseDelay,
        Duration retryMaxDelay,
        Duration idempotencyTtl,
        int idempotencyCapacity
) {

    public GatewaySettings {
        requirePositive(responseMaxAttempts, "responseMaxAttempts");
        requirePositive(telemetryMaxAttempts, "telemetryMaxAttempts");
        requirePositive(ingressCommandCapacity, "ingressCommandCapacity");
        requirePositive(ingressTelemetryCapacity, "ingressTelemetryCapacity");
        requirePositive(ingressRawCapacity, "ingressRawCapacity");
        requirePositive(egressHighCapacity, "egressHighCapacity");
        requirePositive(egressNormalCapacity, "egressNormalCapacity");
        requirePositive(executeQueueCapacity, "executeQueueCapacity");
        requirePositive(maxDeviceSlots, "maxDeviceSlots");
        requirePositive(ingressWorkers, "ingressWorkers");
        requirePositive(executeWorkers, "executeWorkers");
        requirePositive(egressWorkers, "egressWorkers");
        requirePositive(priorityFairnessBatch, "priorityFairnessBatch");
        if (commandOfferTimeout == null || commandOfferTimeout.isNegative() || commandOfferTimeout.isZero()) {
            throw new IllegalArgumentException("commandOfferTimeout 必须为正");
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("shutdownTimeout 必须为正");
        }
        if (retryBaseDelay == null || retryBaseDelay.isNegative() || retryBaseDelay.isZero()) {
            throw new IllegalArgumentException("retryBaseDelay 必须为正");
        }
        if (retryMaxDelay == null || retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            throw new IllegalArgumentException("retryMaxDelay 必须 >= retryBaseDelay");
        }
        if (idempotencyTtl == null || idempotencyTtl.isNegative()) {
            throw new IllegalArgumentException("idempotencyTtl 不能为负");
        }
    }

    public static GatewaySettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration retryDelay(int attempt) {
        long millis = retryBaseDelay.toMillis() * (1L << Math.min(attempt, 16));
        return Duration.ofMillis(Math.min(millis, retryMaxDelay.toMillis()));
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " 至少为 1");
        }
    }

    public static final class Builder {
        private int responseMaxAttempts = 3;
        private int telemetryMaxAttempts = 1;
        private int ingressCommandCapacity = 256;
        private int ingressTelemetryCapacity = 1024;
        private int ingressRawCapacity = 256;
        private int egressHighCapacity = 256;
        private int egressNormalCapacity = 1024;
        private int executeQueueCapacity = 64;
        private int maxDeviceSlots = 4096;
        private int ingressWorkers = 2;
        private int executeWorkers = 4;
        private int egressWorkers = 2;
        private int priorityFairnessBatch = 8;
        private Duration commandOfferTimeout = Duration.ofSeconds(1);
        private Duration shutdownTimeout = Duration.ofSeconds(5);
        private Duration retryBaseDelay = Duration.ofMillis(100);
        private Duration retryMaxDelay = Duration.ofSeconds(2);
        private Duration idempotencyTtl = Duration.ofSeconds(60);
        private int idempotencyCapacity = 4096;

        public Builder executeWorkers(int executeWorkers) {
            this.executeWorkers = executeWorkers;
            return this;
        }

        public Builder executeQueueCapacity(int executeQueueCapacity) {
            this.executeQueueCapacity = executeQueueCapacity;
            return this;
        }

        public Builder maxDeviceSlots(int maxDeviceSlots) {
            this.maxDeviceSlots = maxDeviceSlots;
            return this;
        }

        public Builder ingressCommandCapacity(int ingressCommandCapacity) {
            this.ingressCommandCapacity = ingressCommandCapacity;
            return this;
        }

        public Builder commandOfferTimeout(Duration commandOfferTimeout) {
            this.commandOfferTimeout = commandOfferTimeout;
            return this;
        }

        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
            return this;
        }

        public GatewaySettings build() {
            return new GatewaySettings(
                    responseMaxAttempts, telemetryMaxAttempts,
                    ingressCommandCapacity, ingressTelemetryCapacity, ingressRawCapacity,
                    egressHighCapacity, egressNormalCapacity, executeQueueCapacity, maxDeviceSlots,
                    ingressWorkers, executeWorkers, egressWorkers, priorityFairnessBatch,
                    commandOfferTimeout, shutdownTimeout, retryBaseDelay, retryMaxDelay,
                    idempotencyTtl, idempotencyCapacity
            );
        }
    }
}
