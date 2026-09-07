package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景日历调度。与功能级 interval 轮询分离。
 */
@Service
public class SceneCronDispatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SceneCronDispatcher.class);

    private final CatalogActionRepository actions;
    private final ActionGroupExecutor executor;
    private final DelayQueue<Tick> queue = new DelayQueue<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Thread worker;

    public SceneCronDispatcher(CatalogActionRepository actions, ActionGroupExecutor executor) {
        this.actions = actions;
        this.executor = executor;
    }

    @PostConstruct
    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        reload();
        worker = new Thread(this::drain, "gateway-scene-cron");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void reload() {
        long gen = generation.incrementAndGet();
        queue.clear();
        Instant now = Instant.now();
        List<SceneTriggerEntity> timers;
        try {
            timers = actions.listEnabledTimers();
        } catch (RuntimeException ex) {
            LOG.warn("场景定时未加载（请确认已执行 V14 SQL）: {}", ex.getMessage());
            return;
        }
        for (SceneTriggerEntity trigger : timers) {
            ActionGroupEntity group = actions.findGroup(trigger.getGroupId()).orElse(null);
            if (group == null || Boolean.FALSE.equals(group.getEnabled())
                    || !ActionKinds.SCENE.equalsIgnoreCase(group.getKind())) {
                continue;
            }
            Instant next = nextFire(trigger, now);
            if (next == null) {
                continue;
            }
            queue.offer(new Tick(trigger.getId(), trigger.getGroupId(), gen, next));
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        queue.clear();
        if (worker != null) {
            worker.interrupt();
        }
        started.set(false);
    }

    private void drain() {
        while (!closed.get()) {
            try {
                Tick tick = queue.poll(50, TimeUnit.MILLISECONDS);
                if (tick == null) {
                    continue;
                }
                if (tick.gen() != generation.get()) {
                    continue;
                }
                SceneTriggerEntity trigger = actions.findTrigger(tick.groupId()).orElse(null);
                if (trigger == null || !tick.triggerId().equals(trigger.getId())
                        || Boolean.FALSE.equals(trigger.getEnabled())
                        || !ActionKinds.TIMER.equalsIgnoreCase(trigger.getMode())) {
                    continue;
                }
                try {
                    executor.execute(tick.groupId(), ActionKinds.SOURCE_SCENE)
                            .whenComplete((view, error) -> {
                                if (error != null) {
                                    LOG.warn("场景定时执行失败 group={}: {}", tick.groupId(), error.getMessage());
                                }
                            });
                } catch (RuntimeException ex) {
                    LOG.warn("场景定时提交失败 group={}: {}", tick.groupId(), ex.getMessage());
                }
                if (ActionKinds.ONCE.equalsIgnoreCase(trigger.getTimerKind())) {
                    trigger.setEnabled(false);
                    actions.saveTrigger(trigger);
                    continue;
                }
                Instant next = nextFire(trigger, Instant.now().plusMillis(500));
                if (next != null) {
                    queue.offer(new Tick(trigger.getId(), trigger.getGroupId(), tick.gen(), next));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static Instant nextFire(SceneTriggerEntity trigger, Instant now) {
        if (trigger == null || Boolean.FALSE.equals(trigger.getEnabled())) {
            return null;
        }
        ZoneId zone = zoneOf(trigger.getTimezone());
        if (ActionKinds.ONCE.equalsIgnoreCase(trigger.getTimerKind())) {
            Instant at = parseInstant(trigger.getTimerAt(), zone);
            if (at == null || !at.isAfter(now)) {
                return null;
            }
            return at;
        }
        if (!ActionKinds.CRON.equalsIgnoreCase(trigger.getTimerKind())
                || trigger.getCronExpr() == null || trigger.getCronExpr().isBlank()) {
            return null;
        }
        try {
            CronExpression cron = CronExpression.parse(trigger.getCronExpr().trim());
            ZonedDateTime next = cron.next(now.atZone(zone));
            return next == null ? null : next.toInstant();
        } catch (IllegalArgumentException ex) {
            LOG.warn("非法 cron {} group={}: {}", trigger.getCronExpr(), trigger.getGroupId(), ex.getMessage());
            return null;
        }
    }

    public static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(ActionKinds.DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException ex) {
            return ZoneId.of(ActionKinds.DEFAULT_ZONE);
        }
    }

    public static Instant parseInstant(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            return Instant.parse(text);
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            return java.time.LocalDateTime.parse(text).atZone(zone).toInstant();
        } catch (RuntimeException ex) {
            LOG.warn("无法解析单次时间: {}", text);
            return null;
        }
    }

    private record Tick(String triggerId, String groupId, long gen, Instant deadline) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.between(Instant.now(), deadline).toNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
