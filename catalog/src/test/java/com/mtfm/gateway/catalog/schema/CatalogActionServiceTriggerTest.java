package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.action.ActionKinds;
import com.mtfm.gateway.catalog.apply.SceneCronDispatcher;
import com.mtfm.gateway.catalog.apply.SceneListenDispatcher;
import com.mtfm.gateway.catalog.dto.ActionGroupWriteRequest;
import com.mtfm.gateway.catalog.dto.SceneTriggerWriteRequest;
import com.mtfm.gateway.catalog.entity.ActionGroupEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogActionServiceTriggerTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogActionRepository actions;
    @Mock
    private SceneListenDispatcher listen;
    @Mock
    private SceneCronDispatcher cron;

    @Test
    void createSceneRejectsPastOnce() {
        stubInsert();
        CatalogActionService service = new CatalogActionService(store, actions, listen, cron);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(ActionKinds.SCENE, scene(Instant.now().minusSeconds(5).toString(), null)));
        assertTrue(ex.getMessage().contains("timerAt"));
    }

    @Test
    void createSceneRejectsInvalidTimezone() {
        stubInsert();
        CatalogActionService service = new CatalogActionService(store, actions, listen, cron);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(ActionKinds.SCENE, scene(
                        Instant.now().plusSeconds(60).toString(), "Not/AZone")));
        assertTrue(ex.getMessage().contains("时区"));
    }

    private void stubInsert() {
        when(actions.insertGroup(any())).thenAnswer(invocation -> {
            ActionGroupEntity entity = invocation.getArgument(0);
            entity.setId("g1");
            return entity;
        });
    }

    private static ActionGroupWriteRequest scene(String timerAt, String timezone) {
        return new ActionGroupWriteRequest(
                "morning",
                "晨间",
                null,
                true,
                List.of(),
                new SceneTriggerWriteRequest(
                        ActionKinds.TIMER,
                        null,
                        null,
                        null,
                        ActionKinds.ONCE,
                        timerAt,
                        null,
                        timezone,
                        true));
    }
}
