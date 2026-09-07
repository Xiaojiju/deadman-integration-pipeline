package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.ActionMemberEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 按动作组成员调用现有 {@link CatalogApplyService#invoke}，跨设备并行、尽力而为。
 */
@Service
public class ActionGroupExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ActionGroupExecutor.class);

    private final CatalogActionRepository actions;
    private final ObjectProvider<CatalogApplyService> apply;

    public ActionGroupExecutor(CatalogActionRepository actions, ObjectProvider<CatalogApplyService> apply) {
        this.actions = actions;
        this.apply = apply;
    }

    public CompletableFuture<ActionGroupExecutionView> execute(String idOrCode, String source) {
        ActionGroupEntity group = actions.findGroup(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("动作组不存在: " + idOrCode));
        if (Boolean.FALSE.equals(group.getEnabled())) {
            throw new IllegalArgumentException("动作组已禁用: " + group.getCode());
        }
        List<ActionMemberEntity> members = actions.listMembers(group.getId());
        if (members.isEmpty()) {
            return CompletableFuture.completedFuture(new ActionGroupExecutionView(
                    group.getId(), group.getCode(), group.getKind(), source, List.of()));
        }
        CatalogApplyService service = apply.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("命令端口尚未装配，无法执行动作组");
        }
        String origin = source == null || source.isBlank() ? ActionKinds.SOURCE_CLUSTER : source.trim();
        List<CompletableFuture<ExecutionResult>> futures = new ArrayList<>(members.size());
        for (ActionMemberEntity member : members) {
            futures.add(invokeMember(service, member, origin));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .orTimeout(ActionKinds.GROUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .handle((ignored, error) -> {
                    if (error != null && !(error instanceof TimeoutException)
                            && error.getCause() instanceof TimeoutException) {
                        LOG.warn("动作组执行超时 group={}: {}", group.getCode(), error.getMessage());
                    } else if (error instanceof TimeoutException) {
                        LOG.warn("动作组执行超时 group={}", group.getCode());
                    }
                    List<ExecutionResult> items = new ArrayList<>(futures.size());
                    for (int i = 0; i < futures.size(); i++) {
                        ActionMemberEntity member = members.get(i);
                        items.add(resultOrTimeout(futures.get(i), member, error));
                    }
                    return new ActionGroupExecutionView(
                            group.getId(), group.getCode(), group.getKind(), origin, List.copyOf(items));
                });
    }

    private static CompletableFuture<ExecutionResult> invokeMember(
            CatalogApplyService service, ActionMemberEntity member, String source) {
        Map<String, Object> arguments = JsonMaps.readMap(member.getArgumentsJson());
        try {
            return service.invoke(
                    member.getDeviceCode(),
                    new DeviceCommandRequest(member.getFunctionId(), arguments, null, source));
        } catch (RuntimeException ex) {
            FunctionCommand command = FunctionCommand.of(member.getDeviceCode(), member.getFunctionId(), arguments);
            return CompletableFuture.completedFuture(
                    ExecutionResult.rejected(command, Failure.executorError("action-group", ex.getMessage(), false)));
        }
    }

    private static ExecutionResult resultOrTimeout(
            CompletableFuture<ExecutionResult> future,
            ActionMemberEntity member,
            Throwable groupError) {
        FunctionCommand command = FunctionCommand.of(
                member.getDeviceCode(),
                member.getFunctionId(),
                JsonMaps.readMap(member.getArgumentsJson()));
        if (future.isDone() && !future.isCompletedExceptionally()) {
            ExecutionResult result = future.getNow(null);
            return result != null
                    ? result
                    : ExecutionResult.rejected(command, Failure.executorError("action-group", "空结果", false));
        }
        if (future.isCompletedExceptionally()) {
            Throwable cause = future.exceptionNow();
            String message = cause == null ? "成员执行失败" : String.valueOf(cause.getMessage());
            return ExecutionResult.rejected(command, Failure.executorError("action-group", message, false));
        }
        String timeoutMessage = groupError == null ? "成员执行未完成" : "动作组等待超时";
        return ExecutionResult.timeout(command, Failure.timeout("action-group", timeoutMessage));
    }
}
