package com.mtfm.gateway.catalog.dto;

import java.util.List;

public record ActionGroupWriteRequest(
        String code,
        String name,
        String description,
        Boolean enabled,
        List<ActionMemberWriteRequest> members,
        SceneTriggerWriteRequest trigger
) {
}
