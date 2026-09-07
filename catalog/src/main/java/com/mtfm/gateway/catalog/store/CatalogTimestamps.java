package com.mtfm.gateway.catalog.store;

import java.time.Instant;
import java.util.function.Consumer;

final class CatalogTimestamps {

    private CatalogTimestamps() {
    }

    static void touch(Consumer<Instant> created, Consumer<Instant> updated, boolean create) {
        Instant now = Instant.now();
        if (create) {
            created.accept(now);
        }
        updated.accept(now);
    }

    static void bump(CatalogRevision revision) {
        if (revision != null) {
            revision.bump();
        }
    }
}
