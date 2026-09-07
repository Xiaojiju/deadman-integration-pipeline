package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.FunctionDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneListenDispatcherTest {

    @Mock
    private CatalogActionRepository actions;
    @Mock
    private CatalogStore store;
    @Mock
    private ActionGroupExecutor executor;

    @Test
    void writeSuccessTriggersSceneAndSceneSourceDoesNotLoop() {
        SceneTriggerEntity trigger = new SceneTriggerEntity();
        trigger.setId("t1");
        trigger.setGroupId("g1");
        trigger.setMode(ActionKinds.LISTEN);
        trigger.setListenDeviceCode("door");
        trigger.setListenFunctionId("open");
        trigger.setEnabled(true);

        ActionGroupEntity group = new ActionGroupEntity();
        group.setId("g1");
        group.setKind(ActionKinds.SCENE);
        group.setEnabled(true);

        FunctionDef write = mock(FunctionDef.class);
        when(write.accessType()).thenReturn("WRITE");
        when(actions.listEnabledListen()).thenReturn(List.of(trigger));
        when(actions.findGroup("g1")).thenReturn(Optional.of(group));
        when(store.find("door", "open")).thenReturn(Optional.of(write));
        when(executor.execute("g1", ActionKinds.SOURCE_SCENE)).thenReturn(
                CompletableFuture.completedFuture(new ActionGroupExecutionView(
                        "g1", "lights", ActionKinds.SCENE, ActionKinds.SOURCE_SCENE, List.of())));

        SceneListenDispatcher dispatcher = new SceneListenDispatcher(actions, store, executor);
        dispatcher.rebuild();
        dispatcher.onCommandSuccess("door", "open", Attributes.from(java.util.Map.of("value", "1")), null);
        dispatcher.onCommandSuccess("door", "open", Attributes.empty(), ActionKinds.SOURCE_SCENE);

        verify(executor, times(1)).execute("g1", ActionKinds.SOURCE_SCENE);
        verify(executor, never()).execute(anyString(), org.mockito.ArgumentMatchers.eq("cluster"));
    }
}
