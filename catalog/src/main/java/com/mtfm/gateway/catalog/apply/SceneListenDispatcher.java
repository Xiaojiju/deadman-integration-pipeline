package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令 SUCCESS 后匹配 LISTEN 场景。不重试；cluster/scene/scheduler 来源不触发。
 */
@Service
public class SceneListenDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(SceneListenDispatcher.class);

    private final CatalogActionRepository actions;
    private final FunctionCatalog catalog;
    private final ActionGroupExecutor executor;
    private final ConcurrentHashMap<String, List<SceneTriggerEntity>> index = new ConcurrentHashMap<>();

    public SceneListenDispatcher(
            CatalogActionRepository actions, FunctionCatalog catalog, ActionGroupExecutor executor) {
        this.actions = actions;
        this.catalog = catalog;
        this.executor = executor;
    }

    @PostConstruct
    public void start() {
        rebuild();
    }

    public synchronized void rebuild() {
        index.clear();
        List<SceneTriggerEntity> triggers;
        try {
            triggers = actions.listEnabledListen();
        } catch (RuntimeException ex) {
            LOG.warn("场景监听索引未加载（请确认已执行 V14 SQL）: {}", ex.getMessage());
            return;
        }
        Map<String, ActionGroupEntity> groups = actions.findGroupsByIds(
                triggers.stream().map(SceneTriggerEntity::getGroupId).toList());
        for (SceneTriggerEntity trigger : triggers) {
            indexTrigger(trigger, groups.get(trigger.getGroupId()));
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
                && ActionKinds.LISTEN.equalsIgnoreCase(trigger.getMode())) {
            indexTrigger(trigger, group);
        }
    }

    public synchronized void removeGroup(String groupId) {
        if (groupId == null) {
            return;
        }
        for (List<SceneTriggerEntity> list : index.values()) {
            list.removeIf(trigger -> groupId.equals(trigger.getGroupId()));
        }
        index.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void onCommandSuccess(String deviceCode, String functionId, Attributes arguments, String source) {
        if (ActionKinds.skipListen(source) || deviceCode == null || functionId == null) {
            return;
        }
        if (!catalog.isWrite(deviceCode, functionId)) {
            return;
        }
        List<SceneTriggerEntity> matched = index.get(listenKey(deviceCode, functionId));
        if (matched == null || matched.isEmpty()) {
            return;
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments.values();
        for (SceneTriggerEntity trigger : List.copyOf(matched)) {
            if (!matchArguments(trigger, args)) {
                continue;
            }
            try {
                executor.execute(trigger.getGroupId(), ActionKinds.SOURCE_SCENE)
                        .whenComplete((view, error) -> {
                            if (error != null) {
                                LOG.warn("场景监听执行失败 group={}: {}", trigger.getGroupId(), error.getMessage());
                            }
                        });
            } catch (RuntimeException ex) {
                LOG.warn("场景监听提交失败 group={}: {}", trigger.getGroupId(), ex.getMessage());
            }
        }
    }

    private void indexTrigger(SceneTriggerEntity trigger, ActionGroupEntity group) {
        if (trigger.getListenDeviceCode() == null || trigger.getListenFunctionId() == null) {
            return;
        }
        if (group == null || Boolean.FALSE.equals(group.getEnabled())
                || !ActionKinds.SCENE.equalsIgnoreCase(group.getKind())) {
            return;
        }
        index.computeIfAbsent(listenKey(trigger.getListenDeviceCode(), trigger.getListenFunctionId()),
                key -> new ArrayList<>()).add(trigger);
    }

    static boolean matchArguments(SceneTriggerEntity trigger, Map<String, Object> arguments) {
        Map<String, Object> expected = JsonMaps.readMap(trigger.getListenMatchJson());
        if (expected.isEmpty()) {
            return true;
        }
        Map<String, Object> actual = arguments == null ? Map.of() : arguments;
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object got = actual.get(entry.getKey());
            if (got == null || !String.valueOf(got).equals(String.valueOf(entry.getValue()))) {
                return false;
            }
        }
        return true;
    }

    static String listenKey(String deviceCode, String functionId) {
        return deviceCode.trim() + "\0" + functionId.trim();
    }
}
