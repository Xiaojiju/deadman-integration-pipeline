package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.ActionMemberEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionGroupExecutorTest {

    @Mock
    private CatalogActionRepository actions;
    @Mock
    private ObjectProvider<CatalogApplyService> applyProvider;
    @Mock
    private CatalogApplyService apply;

    @Test
    void partialFailureStillReturnsEachMemberResult() throws Exception {
        ActionGroupEntity group = new ActionGroupEntity();
        group.setId("g1");
        group.setCode("cluster-1");
        group.setKind(ActionKinds.CLUSTER);
        group.setEnabled(true);

        ActionMemberEntity door = member("door", "open");
        ActionMemberEntity lamp = member("lamp", "on");
        when(actions.findGroup("g1")).thenReturn(Optional.of(group));
        when(actions.listMembers("g1")).thenReturn(List.of(door, lamp));
        when(applyProvider.getIfAvailable()).thenReturn(apply);
        when(apply.invoke(eq("door"), any())).thenReturn(CompletableFuture.completedFuture(
                ExecutionResult.success("r1", "door", "open", Map.of("ok", true))));
        when(apply.invoke(eq("lamp"), any())).thenReturn(CompletableFuture.completedFuture(
                ExecutionResult.failed(
                        FunctionCommand.of("lamp", "on", Map.of()),
                        Failure.executorError("modbus", "从站离线", false))));

        ActionGroupExecutor executor = new ActionGroupExecutor(actions, applyProvider);
        ActionGroupExecutionView view = executor.execute("g1", ActionKinds.SOURCE_CLUSTER)
                .get(2, TimeUnit.SECONDS);

        assertEquals(2, view.items().size());
        assertEquals(ExecutionStatus.SUCCESS, view.items().get(0).status());
        assertEquals(ExecutionStatus.FAILED, view.items().get(1).status());
        assertEquals("cluster", view.source());
    }

    private static ActionMemberEntity member(String deviceCode, String functionId) {
        ActionMemberEntity row = new ActionMemberEntity();
        row.setDeviceCode(deviceCode);
        row.setFunctionId(functionId);
        row.setArgumentsJson("{}");
        return row;
    }
}
