package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.spi.model.FunctionDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachingFunctionCatalogTest {

    @Mock
    private CatalogFunctionProjection projection;

    @Test
    void secondFindHitsCacheUntilRevisionBumps() {
        CatalogRevision revision = new CatalogRevision();
        CachingFunctionCatalog catalog = new CachingFunctionCatalog(projection, revision);
        FunctionDef def = org.mockito.Mockito.mock(FunctionDef.class);
        when(projection.find("door", "open")).thenReturn(Optional.of(def));

        assertSame(def, catalog.find("door", "open").orElseThrow());
        assertSame(def, catalog.find("door", "open").orElseThrow());
        verify(projection, times(1)).find("door", "open");

        revision.bump();
        assertSame(def, catalog.find("door", "open").orElseThrow());
        verify(projection, times(2)).find("door", "open");
    }
}
