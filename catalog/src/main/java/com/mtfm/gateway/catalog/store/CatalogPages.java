package com.mtfm.gateway.catalog.store;

final class CatalogPages {

    private CatalogPages() {
    }

    static int page(int page) {
        return Math.max(page, 1);
    }

    static int size(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
