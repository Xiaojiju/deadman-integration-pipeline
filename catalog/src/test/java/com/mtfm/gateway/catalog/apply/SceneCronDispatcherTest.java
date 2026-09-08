package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.entity.SceneTriggerEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneCronDispatcherTest {

    @Mock
    private CatalogActionRepository actions;
    @Mock
    private ActionGroupExecutor executor;

    private SceneCronDispatcher dispatcher;

    @AfterEach
    void stop() {
        if (dispatcher != null) {
            dispatcher.close();
        }
    }

    @Test
    void onceTimerFiresOnceThenDisables() throws InterruptedException {
        SceneTriggerEntity trigger = new SceneTriggerEntity();
        trigger.setId("t1");
        trigger.setGroupId("g1");
        trigger.setMode(ActionKinds.TIMER);
        trigger.setTimerKind(ActionKinds.ONCE);
        trigger.setTimerAt(Instant.now().plusMillis(400).toString());
        trigger.setTimezone(ActionKinds.DEFAULT_ZONE);
        trigger.setEnabled(true);

        ActionGroupEntity group = new ActionGroupEntity();
        group.setId("g1");
        group.setKind(ActionKinds.SCENE);
        group.setEnabled(true);

        when(actions.listEnabledTimers()).thenReturn(List.of(trigger));
        when(actions.findGroupsByIds(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("g1", group));
        when(actions.findTrigger("g1")).thenReturn(Optional.of(trigger));
        when(actions.findGroup("g1")).thenReturn(Optional.of(group));
        when(executor.execute("g1", ActionKinds.SOURCE_SCENE)).thenReturn(
                CompletableFuture.completedFuture(new ActionGroupExecutionView(
                        "g1", "once", ActionKinds.SCENE, ActionKinds.SOURCE_SCENE, List.of())));

        dispatcher = new SceneCronDispatcher(actions, executor);
        dispatcher.start();

        verify(executor, timeout(3000).times(1)).execute("g1", ActionKinds.SOURCE_SCENE);
        verify(actions, timeout(3000)).saveTrigger(argThat(row -> Boolean.FALSE.equals(row.getEnabled())));
        Thread.sleep(500);
        verify(executor, times(1)).execute("g1", ActionKinds.SOURCE_SCENE);
    }

    @Test
    void refreshGroupInvalidatesAlreadyQueuedFire() throws InterruptedException {
        SceneTriggerEntity trigger = new SceneTriggerEntity();
        trigger.setId("t1");
        trigger.setGroupId("g1");
        trigger.setMode(ActionKinds.TIMER);
        trigger.setTimerKind(ActionKinds.ONCE);
        trigger.setTimerAt(Instant.now().plusMillis(250).toString());
        trigger.setTimezone(ActionKinds.DEFAULT_ZONE);
        trigger.setEnabled(true);

        ActionGroupEntity group = new ActionGroupEntity();
        group.setId("g1");
        group.setKind(ActionKinds.SCENE);
        group.setEnabled(true);

        when(actions.listEnabledTimers()).thenReturn(List.of(trigger));
        when(actions.findGroupsByIds(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("g1", group));
        when(actions.findTrigger("g1")).thenReturn(Optional.of(trigger));
        when(actions.findGroup("g1")).thenReturn(Optional.of(group));

        dispatcher = new SceneCronDispatcher(actions, executor);
        dispatcher.start();

        trigger.setTimerAt(Instant.now().plusSeconds(60).toString());
        dispatcher.refreshGroup("g1");

        Thread.sleep(800);
        verify(executor, times(0)).execute("g1", ActionKinds.SOURCE_SCENE);
    }
}
