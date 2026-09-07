package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 创建/更新动作组（集群或场景）。
 *
 * <pre>{@code
 * POST /catalog/scenes
 * {"code":"time-door","name":"定时开门","members":[{"deviceCode":"door-1","functionId":"remoteControlDoor"}]}
 * }</pre>
 */
public record ActionGroupWriteRequest(
        @NotBlank(groups = CreateOp.class, message = "code 不能为空")
        String code,
        @NotBlank(groups = CreateOp.class, message = "name 不能为空")
        String name,
        String description,
        Boolean enabled,
        @Valid List<ActionMemberWriteRequest> members,
        @Valid SceneTriggerWriteRequest trigger
) {
}
