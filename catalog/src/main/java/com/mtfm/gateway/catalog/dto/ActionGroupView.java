package com.mtfm.gateway.catalog.dto;

import java.util.List;

/**
 * 动作组对外视图，含成员列表与场景触发器（CLUSTER 时 trigger 可为空）。
 */
public record ActionGroupView(
        String id,
        String code,
        String name,
        String description,
        String kind,
        boolean enabled,
        List<ActionMemberView> members,
        SceneTriggerView trigger
) {
}
