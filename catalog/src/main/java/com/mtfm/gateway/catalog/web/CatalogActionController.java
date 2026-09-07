package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.dto.ActionGroupView;
import com.mtfm.gateway.catalog.dto.ActionGroupWriteRequest;
import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import com.mtfm.gateway.catalog.dto.validation.UpdateOp;
import com.mtfm.gateway.catalog.schema.CatalogActionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 集群与场景动作组 REST 接口，前缀 {@code /catalog}。
 *
 * <p>示例：{@code GET /catalog/clusters} 列出集群；
 * {@code POST /catalog/scenes/{id}/execute} 手动触发场景。
 */
@Validated
@RestController
@RequestMapping("/catalog")
public class CatalogActionController {

    private final CatalogApplyService applyService;
    private final CatalogActionService actions;

    public CatalogActionController(CatalogApplyService applyService, CatalogActionService actions) {
        this.applyService = applyService;
        this.actions = actions;
    }

    @GetMapping("/clusters")
    public List<ActionGroupView> listClusters() {
        return actions.list("CLUSTER");
    }

    @PostMapping("/clusters")
    public ActionGroupView createCluster(@Validated(CreateOp.class) @RequestBody ActionGroupWriteRequest request) {
        return actions.create("CLUSTER", request);
    }

    @GetMapping("/clusters/{id}")
    public ActionGroupView getCluster(@PathVariable String id) {
        return requireKind(id, "CLUSTER", "不是集群: ");
    }

    @PutMapping("/clusters/{id}")
    public ActionGroupView updateCluster(@PathVariable String id,
            @Validated(UpdateOp.class) @RequestBody ActionGroupWriteRequest request) {
        return actions.update(requireKind(id, "CLUSTER", "不是集群: ").id(), request);
    }

    @DeleteMapping("/clusters/{id}")
    public Map<String, Object> deleteCluster(@PathVariable String id) {
        ActionGroupView existing = requireKind(id, "CLUSTER", "不是集群: ");
        boolean deleted = actions.delete(existing.id());
        return Map.of("id", existing.id(), "deleted", deleted);
    }

    @PostMapping("/clusters/{id}/execute")
    public CompletableFuture<ActionGroupExecutionView> executeCluster(@PathVariable String id) {
        return applyService.executeActionGroup(requireKind(id, "CLUSTER", "不是集群: ").id(), "cluster");
    }

    @GetMapping("/scenes")
    public List<ActionGroupView> listScenes() {
        return actions.list("SCENE");
    }

    @PostMapping("/scenes")
    public ActionGroupView createScene(@Validated(CreateOp.class) @RequestBody ActionGroupWriteRequest request) {
        return actions.create("SCENE", request);
    }

    @GetMapping("/scenes/{id}")
    public ActionGroupView getScene(@PathVariable String id) {
        return requireKind(id, "SCENE", "不是场景: ");
    }

    @PutMapping("/scenes/{id}")
    public ActionGroupView updateScene(@PathVariable String id,
            @Validated(UpdateOp.class) @RequestBody ActionGroupWriteRequest request) {
        return actions.update(requireKind(id, "SCENE", "不是场景: ").id(), request);
    }

    @DeleteMapping("/scenes/{id}")
    public Map<String, Object> deleteScene(@PathVariable String id) {
        ActionGroupView existing = requireKind(id, "SCENE", "不是场景: ");
        boolean deleted = actions.delete(existing.id());
        return Map.of("id", existing.id(), "deleted", deleted);
    }

    @PostMapping("/scenes/{id}/execute")
    public CompletableFuture<ActionGroupExecutionView> executeScene(@PathVariable String id) {
        return applyService.executeActionGroup(requireKind(id, "SCENE", "不是场景: ").id(), "scene");
    }

    private ActionGroupView requireKind(String id, String kind, String errorPrefix) {
        ActionGroupView view = actions.require(id);
        if (!kind.equalsIgnoreCase(view.kind())) {
            throw new IllegalArgumentException(errorPrefix + id);
        }
        return view;
    }
}
