package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.spi.concurrent.DeadlineDaemon;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 场景日历调度。与功能级 interval 轮询分离。
 */
@Service
public class SceneCronDispatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SceneCronDispatcher.class);

    private final CatalogActionRepository actions;
    private final ActionGroupExecutor executor;
    private final DeadlineDaemon daemon = new DeadlineDaemon();
    private final AtomicLong generation = new AtomicLong();

    public SceneCronDispatcher(CatalogActionRepository actions, ActionGroupExecutor executor) {
        this.actions = actions;
        this.executor = executor;
    }

    @PostConstruct
    public void start() {
        daemon.start("gateway-scene-cron", this::onDue);
        reload();
    }

    public synchronized void reload() {
        long gen = generation.incrementAndGet();
        daemon.clear();
        Instant now = Instant.now();
        List<SceneTriggerEntity> timers;
        try {
            timers = actions.listEnabledTimers();
        } catch (RuntimeException ex) {
            LOG.warn("场景定时未加载（请确认已执行 V14 SQL）: {}", ex.getMessage());
            return;
        }
        Map<String, ActionGroupEntity> groups = actions.findGroupsByIds(
                timers.stream().map(trigger -> trigger.getGroupId()).collect(Collectors.toList()));
        for (SceneTriggerEntity trigger : timers) {
            schedule(trigger, groups.get(trigger.getGroupId()), gen, now);
        }
    }

    public synchronized void refreshGroup(String groupId) {
        removeGroup(groupId);
        if (groupId == null || groupId.isBlank()) {
            return;
        }
        ActionGroupEntity group = actions.findGroup(groupId).orElse(null);
        SceneTriggerEntity trigger = actions.findTrigger(groupId).orElse(null);
        if (trigger != null && Boolean.TRUE.equals(trigger.getEnabled())
                && ActionKinds.TIMER.equalsIgnoreCase(trigger.getMode())) {
            schedule(trigger, group, generation.get(), Instant.now());
        }
    }

    public synchronized void removeGroup(String groupId) {
        if (groupId == null) {
            return;
        }
        daemon.removeIf(task -> groupId.equals(task.key()));
    }

    @PreDestroy
    @Override
    public void close() {
        daemon.close();
    }

    private void onDue(DeadlineDaemon.Task task) {
        if (task.generation() != generation.get()) {
            return;
        }
        SceneTriggerEntity trigger = actions.findTrigger(task.key()).orElse(null);
        if (trigger == null
                || Boolean.FALSE.equals(trigger.getEnabled())
                || !ActionKinds.TIMER.equalsIgnoreCase(trigger.getMode())) {
            return;
        }
        try {
            executor.execute(task.key(), ActionKinds.SOURCE_SCENE)
                    .whenComplete((view, error) -> {
                        if (error != null) {
                            LOG.warn("场景定时执行失败 group={}: {}", task.key(), error.getMessage());
                        }
                    });
        } catch (RuntimeException ex) {
            LOG.warn("场景定时提交失败 group={}: {}", task.key(), ex.getMessage());
        }
        if (ActionKinds.ONCE.equalsIgnoreCase(trigger.getTimerKind())) {
            trigger.setEnabled(false);
            actions.saveTrigger(trigger);
            return;
        }
        Instant next = nextFire(trigger, Instant.now().plusMillis(500));
        if (next != null) {
            daemon.offer(task.key(), task.generation(), next);
        }
    }

    private void schedule(SceneTriggerEntity trigger, ActionGroupEntity group, long gen, Instant now) {
        if (group == null || Boolean.FALSE.equals(group.getEnabled())
                || !ActionKinds.SCENE.equalsIgnoreCase(group.getKind())) {
            return;
        }
        Instant next = nextFire(trigger, now);
        if (next == null) {
            return;
        }
        daemon.offer(trigger.getGroupId(), gen, next);
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
}
