package com.mtfm.gateway.catalog.dto;

import java.util.List;

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
