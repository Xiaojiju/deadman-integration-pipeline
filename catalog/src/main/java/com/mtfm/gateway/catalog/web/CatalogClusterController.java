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
 * 集群动作组 REST 接口，前缀 {@code /catalog/clusters}。
 *
 * <p>示例：{@code GET /catalog/clusters} 列出集群；
 * {@code POST /catalog/clusters/{id}/execute} 手动触发集群。
 */
@Validated
@RestController
@RequestMapping("/catalog/clusters")
public class CatalogClusterController {

    private final CatalogApplyService applyService;
    private final CatalogActionService actions;

    public CatalogClusterController(CatalogApplyService applyService, CatalogActionService actions) {
        this.applyService = applyService;
        this.actions = actions;
    }

    @GetMapping
    public List<ActionGroupView> listClusters() {
        return actions.list("CLUSTER");
    }

    @PostMapping
    public ActionGroupView createCluster(@Validated(CreateOp.class) @RequestBody ActionGroupWriteRequest request) {
        return actions.create("CLUSTER", request);
    }

    @GetMapping("/{id}")
    public ActionGroupView getCluster(@PathVariable String id) {
        return requireKind(id, "CLUSTER", "不是集群: ");
    }

    @PutMapping("/{id}")
    public ActionGroupView updateCluster(@PathVariable String id,
            @Validated(UpdateOp.class) @RequestBody ActionGroupWriteRequest request) {
        return actions.update(requireKind(id, "CLUSTER", "不是集群: ").id(), request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteCluster(@PathVariable String id) {
        ActionGroupView existing = requireKind(id, "CLUSTER", "不是集群: ");
        boolean deleted = actions.delete(existing.id());
        return Map.of("id", existing.id(), "deleted", deleted);
    }

    @PostMapping("/{id}/execute")
    public CompletableFuture<ActionGroupExecutionView> executeCluster(@PathVariable String id) {
        return applyService.executeActionGroup(requireKind(id, "CLUSTER", "不是集群: ").id(), "cluster");
    }

    private ActionGroupView requireKind(String id, String kind, String errorPrefix) {
        ActionGroupView view = actions.require(id);
        if (!kind.equalsIgnoreCase(view.kind())) {
            throw new IllegalArgumentException(errorPrefix + id);
        }
        return view;
    }
}
