package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.ActionMemberEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.mapper.ActionGroupMapper;
import com.mtfm.gateway.catalog.mapper.ActionMemberMapper;
import com.mtfm.gateway.catalog.mapper.SceneTriggerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CatalogActionRepository {

    private final ActionGroupMapper groups;
    private final ActionMemberMapper members;
    private final SceneTriggerMapper triggers;

    public CatalogActionRepository(
            ActionGroupMapper groups,
            ActionMemberMapper members,
            SceneTriggerMapper triggers) {
        this.groups = groups;
        this.members = members;
        this.triggers = triggers;
    }

    public ActionGroupEntity insertGroup(ActionGroupEntity entity) {
        touch(entity, true);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        try {
            groups.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("动作组编码已存在: " + entity.getCode(), ex);
        }
        return entity;
    }

    public ActionGroupEntity updateGroup(ActionGroupEntity entity) {
        touch(entity, false);
        groups.updateById(entity);
        return entity;
    }

    public Optional<ActionGroupEntity> findGroup(String idOrCode) {
        if (idOrCode == null || idOrCode.isBlank()) {
            return Optional.empty();
        }
        ActionGroupEntity byId = groups.selectById(idOrCode);
        if (byId != null) {
            return Optional.of(byId);
        }
        return Optional.ofNullable(groups.selectOne(new QueryWrapper<ActionGroupEntity>().eq("code", idOrCode)));
    }

    public List<ActionGroupEntity> listGroups(String kind) {
        QueryWrapper<ActionGroupEntity> query = new QueryWrapper<ActionGroupEntity>().orderByAsc("code");
        if (kind != null && !kind.isBlank()) {
            query.eq("kind", kind.trim().toUpperCase());
        }
        return groups.selectList(query);
    }

    public Map<String, ActionGroupEntity> findGroupsByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<ActionGroupEntity> rows = groups.selectList(new QueryWrapper<ActionGroupEntity>().in("id", ids));
        Map<String, ActionGroupEntity> byId = new LinkedHashMap<>();
        for (ActionGroupEntity row : rows) {
            byId.put(row.getId(), row);
        }
        return byId;
    }

    public Map<String, List<ActionMemberEntity>> listMembersByGroupIds(Collection<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        List<ActionMemberEntity> rows = members.selectList(new QueryWrapper<ActionMemberEntity>()
                .in("group_id", groupIds)
                .orderByAsc("sort_index")
                .orderByAsc("id"));
        Map<String, List<ActionMemberEntity>> byGroup = new LinkedHashMap<>();
        for (ActionMemberEntity row : rows) {
            byGroup.computeIfAbsent(row.getGroupId(), key -> new ArrayList<>()).add(row);
        }
        return byGroup;
    }

    public Map<String, SceneTriggerEntity> findTriggersByGroupIds(Collection<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        List<SceneTriggerEntity> rows = triggers.selectList(
                new QueryWrapper<SceneTriggerEntity>().in("group_id", groupIds));
        Map<String, SceneTriggerEntity> byGroup = new LinkedHashMap<>();
        for (SceneTriggerEntity row : rows) {
            byGroup.put(row.getGroupId(), row);
        }
        return byGroup;
    }

    public boolean deleteGroup(String id) {
        members.delete(new QueryWrapper<ActionMemberEntity>().eq("group_id", id));
        triggers.delete(new QueryWrapper<SceneTriggerEntity>().eq("group_id", id));
        return groups.deleteById(id) > 0;
    }

    public List<ActionMemberEntity> listMembers(String groupId) {
        return members.selectList(new QueryWrapper<ActionMemberEntity>()
                .eq("group_id", groupId)
                .orderByAsc("sort_index")
                .orderByAsc("id"));
    }

    public void replaceMembers(String groupId, List<ActionMemberEntity> next) {
        members.delete(new QueryWrapper<ActionMemberEntity>().eq("group_id", groupId));
        if (next == null) {
            return;
        }
        for (ActionMemberEntity member : next) {
            member.setGroupId(groupId);
            if (member.getId() == null) {
                member.setId(SnowflakeIds.next());
            }
            if (member.getSortIndex() == null) {
                member.setSortIndex(0);
            }
            touchMember(member, true);
            members.insert(member);
        }
    }

    public Optional<SceneTriggerEntity> findTrigger(String groupId) {
        return Optional.ofNullable(triggers.selectOne(
                new QueryWrapper<SceneTriggerEntity>().eq("group_id", groupId)));
    }

    public SceneTriggerEntity saveTrigger(SceneTriggerEntity entity) {
        Optional<SceneTriggerEntity> existing = findTrigger(entity.getGroupId());
        if (existing.isPresent()) {
            entity.setId(existing.get().getId());
            entity.setCreatedAt(existing.get().getCreatedAt());
            touchTrigger(entity, false);
            triggers.updateById(entity);
            return entity;
        }
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        touchTrigger(entity, true);
        triggers.insert(entity);
        return entity;
    }

    public void deleteTrigger(String groupId) {
        triggers.delete(new QueryWrapper<SceneTriggerEntity>().eq("group_id", groupId));
    }

    public List<SceneTriggerEntity> listEnabledTriggers(String mode) {
        QueryWrapper<SceneTriggerEntity> query = new QueryWrapper<SceneTriggerEntity>()
                .eq("enabled", true);
        if (mode != null && !mode.isBlank()) {
            query.eq("mode", mode.trim().toUpperCase());
        }
        return triggers.selectList(query);
    }

    public List<SceneTriggerEntity> listEnabledListen() {
        return listEnabledTriggers(ActionKinds.LISTEN);
    }

    public List<SceneTriggerEntity> listEnabledTimers() {
        return listEnabledTriggers(ActionKinds.TIMER);
    }

    private static void touch(ActionGroupEntity entity, boolean create) {
        Instant now = Instant.now();
        if (create || entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private static void touchMember(ActionMemberEntity entity, boolean create) {
        Instant now = Instant.now();
        if (create || entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private static void touchTrigger(SceneTriggerEntity entity, boolean create) {
        Instant now = Instant.now();
        if (create || entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }
}
