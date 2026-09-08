package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.apply.SceneCronDispatcher;
import com.mtfm.gateway.catalog.apply.SceneListenDispatcher;
import com.mtfm.gateway.catalog.dto.ActionGroupView;
import com.mtfm.gateway.catalog.dto.ActionGroupWriteRequest;
import com.mtfm.gateway.catalog.dto.ActionMemberView;
import com.mtfm.gateway.catalog.dto.ActionMemberWriteRequest;
import com.mtfm.gateway.catalog.dto.SceneTriggerView;
import com.mtfm.gateway.catalog.dto.SceneTriggerWriteRequest;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.ActionMemberEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CatalogActionService {

    private final CatalogStore store;
    private final CatalogActionRepository actions;
    private final SceneListenDispatcher listen;
    private final SceneCronDispatcher cron;

    public CatalogActionService(
            CatalogStore store,
            CatalogActionRepository actions,
            SceneListenDispatcher listen,
            SceneCronDispatcher cron) {
        this.store = store;
        this.actions = actions;
        this.listen = listen;
        this.cron = cron;
    }

    public List<ActionGroupView> list(String kind) {
        List<ActionGroupEntity> groups = actions.listGroups(kind);
        List<String> ids = groups.stream().map(group -> group.getId()).collect(Collectors.toList());
        Map<String, List<ActionMemberEntity>> members = actions.listMembersByGroupIds(ids);
        Map<String, SceneTriggerEntity> triggers = actions.findTriggersByGroupIds(ids);
        return groups.stream().map(group -> toView(group, members.getOrDefault(group.getId(), List.of()),
                Optional.ofNullable(triggers.get(group.getId())))).toList();
    }

    public ActionGroupView require(String idOrCode) {
        ActionGroupEntity group = actions.findGroup(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("动作组不存在: " + idOrCode));
        return toView(group);
    }

    @Transactional
    public ActionGroupView create(String kind, ActionGroupWriteRequest request) {
        String normalized = requireKind(kind);
        ActionGroupEntity entity = new ActionGroupEntity();
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        entity.setDescription(blankToNull(request.description()));
        entity.setKind(normalized);
        entity.setEnabled(request.enabled() == null || request.enabled());
        ActionGroupEntity saved = actions.insertGroup(entity);
        actions.replaceMembers(saved.getId(), bindMembers(request.members()));
        replaceTrigger(saved, request.trigger());
        refreshGroup(saved.getId());
        return toView(saved);
    }

    @Transactional
    public ActionGroupView update(String idOrCode, ActionGroupWriteRequest request) {
        ActionGroupEntity entity = actions.findGroup(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("动作组不存在: " + idOrCode));
        if (request != null) {
            if (request.name() != null && !request.name().isBlank()) {
                entity.setName(request.name().trim());
            }
            if (request.description() != null) {
                entity.setDescription(blankToNull(request.description()));
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
            actions.updateGroup(entity);
            if (request.members() != null) {
                actions.replaceMembers(entity.getId(), bindMembers(request.members()));
            }
            if (ActionKinds.CLUSTER.equalsIgnoreCase(entity.getKind())) {
                actions.deleteTrigger(entity.getId());
            } else if (request.trigger() != null) {
                replaceTrigger(entity, request.trigger());
            }
        }
        refreshGroup(entity.getId());
        return toView(entity);
    }

    @Transactional
    public boolean delete(String idOrCode) {
        ActionGroupEntity entity = actions.findGroup(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("动作组不存在: " + idOrCode));
        boolean deleted = actions.deleteGroup(entity.getId());
        dropGroup(entity.getId());
        return deleted;
    }

    private void replaceTrigger(ActionGroupEntity group, SceneTriggerWriteRequest request) {
        if (!ActionKinds.SCENE.equalsIgnoreCase(group.getKind())) {
            actions.deleteTrigger(group.getId());
            return;
        }
        if (request == null) {
            throw new IllegalArgumentException("场景必须配置 trigger");
        }
        SceneTriggerEntity trigger = bindTrigger(group.getId(), request);
        actions.saveTrigger(trigger);
    }

    private List<ActionMemberEntity> bindMembers(List<ActionMemberWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<ActionMemberEntity> rows = new ArrayList<>(requests.size());
        int index = 0;
        for (ActionMemberWriteRequest item : requests) {
            if (item == null || item.deviceCode() == null || item.deviceCode().isBlank()) {
                throw new IllegalArgumentException("成员 deviceCode 不能为空");
            }
            if (item.functionId() == null || item.functionId().isBlank()) {
                throw new IllegalArgumentException("成员 functionId 不能为空");
            }
            DeviceEntity device = store.findDeviceByCode(item.deviceCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + item.deviceCode()));
            store.findFunction(device.getProductId(), item.functionId().trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "功能未配置到产品: " + device.getProductId() + "/" + item.functionId()));
            ActionMemberEntity row = new ActionMemberEntity();
            row.setDeviceCode(item.deviceCode().trim());
            row.setFunctionId(item.functionId().trim());
            Map<String, Object> arguments = item.arguments() == null ? Map.of() : item.arguments();
            row.setArgumentsJson(JsonMaps.write(arguments));
            row.setSortIndex(item.sortIndex() == null ? index : item.sortIndex());
            rows.add(row);
            index++;
        }
        return rows;
    }

    private SceneTriggerEntity bindTrigger(String groupId, SceneTriggerWriteRequest request) {
        String mode = request.mode() == null ? "" : request.mode().trim().toUpperCase();
        if (!ActionKinds.LISTEN.equals(mode) && !ActionKinds.TIMER.equals(mode)) {
            throw new IllegalArgumentException("trigger.mode 须为 LISTEN 或 TIMER");
        }
        SceneTriggerEntity entity = new SceneTriggerEntity();
        entity.setGroupId(groupId);
        entity.setMode(mode);
        entity.setEnabled(request.enabled() == null || request.enabled());
        if (ActionKinds.LISTEN.equals(mode)) {
            if (request.listenDeviceCode() == null || request.listenDeviceCode().isBlank()
                    || request.listenFunctionId() == null || request.listenFunctionId().isBlank()) {
                throw new IllegalArgumentException("LISTEN 须指定 listenDeviceCode 与 listenFunctionId");
            }
            DeviceEntity device = store.findDeviceByCode(request.listenDeviceCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("监听设备不存在: " + request.listenDeviceCode()));
            store.findFunction(device.getProductId(), request.listenFunctionId().trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "监听功能未配置到产品: " + request.listenFunctionId()));
            entity.setListenDeviceCode(request.listenDeviceCode().trim());
            entity.setListenFunctionId(request.listenFunctionId().trim());
            Map<String, Object> match = request.listenMatch() == null ? Map.of() : request.listenMatch();
            entity.setListenMatchJson(match.isEmpty() ? null : JsonMaps.write(match));
            clearTimerFields(entity);
            return entity;
        }
        String timerKind = request.timerKind() == null ? "" : request.timerKind().trim().toUpperCase();
        if (!ActionKinds.ONCE.equals(timerKind) && !ActionKinds.CRON.equals(timerKind)) {
            throw new IllegalArgumentException("TIMER 须指定 timerKind=ONCE 或 CRON");
        }
        entity.setTimerKind(timerKind);
        entity.setTimezone(requireZone(request.timezone()));
        ZoneId zone = ZoneId.of(entity.getTimezone());
        clearListenFields(entity);
        if (ActionKinds.ONCE.equals(timerKind)) {
            Instant at = SceneCronDispatcher.parseInstant(request.timerAt(), zone);
            if (at == null) {
                throw new IllegalArgumentException("ONCE 须指定合法 timerAt");
            }
            if (!at.isAfter(Instant.now())) {
                throw new IllegalArgumentException("ONCE 的 timerAt 须晚于现在");
            }
            entity.setTimerAt(request.timerAt().trim());
            entity.setCronExpr(null);
        } else {
            if (request.cronExpr() == null || request.cronExpr().isBlank()) {
                throw new IllegalArgumentException("CRON 须指定 cronExpr");
            }
            try {
                CronExpression.parse(request.cronExpr().trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("非法 cronExpr: " + request.cronExpr(), ex);
            }
            entity.setCronExpr(request.cronExpr().trim());
            entity.setTimerAt(null);
        }
        return entity;
    }

    private ActionGroupView toView(ActionGroupEntity group) {
        return toView(group, actions.listMembers(group.getId()), actions.findTrigger(group.getId()));
    }

    private ActionGroupView toView(
            ActionGroupEntity group,
            List<ActionMemberEntity> memberRows,
            Optional<SceneTriggerEntity> triggerRow) {
        List<ActionMemberView> members = memberRows.stream()
                .map(row -> new ActionMemberView(
                        row.getId(),
                        row.getDeviceCode(),
                        row.getFunctionId(),
                        JsonMaps.readMap(row.getArgumentsJson()),
                        row.getSortIndex() == null ? 0 : row.getSortIndex()))
                .toList();
        SceneTriggerView trigger = triggerRow
                .map(row -> new SceneTriggerView(
                        row.getId(),
                        row.getMode(),
                        row.getListenDeviceCode(),
                        row.getListenFunctionId(),
                        JsonMaps.readMap(row.getListenMatchJson()),
                        row.getTimerKind(),
                        row.getTimerAt(),
                        row.getCronExpr(),
                        row.getTimezone(),
                        !Boolean.FALSE.equals(row.getEnabled())))
                .orElse(null);
        return new ActionGroupView(
                group.getId(),
                group.getCode(),
                group.getName(),
                group.getDescription(),
                group.getKind(),
                !Boolean.FALSE.equals(group.getEnabled()),
                members,
                trigger);
    }

    private void refreshGroup(String groupId) {
        listen.refreshGroup(groupId);
        cron.refreshGroup(groupId);
    }

    private void dropGroup(String groupId) {
        listen.removeGroup(groupId);
        cron.removeGroup(groupId);
    }

    private static String requireKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind 不能为空");
        }
        String normalized = kind.trim().toUpperCase();
        if (!ActionKinds.CLUSTER.equals(normalized) && !ActionKinds.SCENE.equals(normalized)) {
            throw new IllegalArgumentException("kind 须为 CLUSTER 或 SCENE");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireZone(String timezone) {
        String zone = timezone == null || timezone.isBlank() ? ActionKinds.DEFAULT_ZONE : timezone.trim();
        try {
            ZoneId.of(zone);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("非法时区: " + zone);
        }
        return zone;
    }

    private static void clearListenFields(SceneTriggerEntity entity) {
        entity.setListenDeviceCode(null);
        entity.setListenFunctionId(null);
        entity.setListenMatchJson(null);
    }

    private static void clearTimerFields(SceneTriggerEntity entity) {
        entity.setTimerKind(null);
        entity.setTimerAt(null);
        entity.setCronExpr(null);
        entity.setTimezone(null);
    }
}
