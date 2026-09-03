package com.mtfm.gateway.capability.cloud;

/**
 * 北向命令提交口。宿主接到 {@link com.mtfm.gateway.catalog.apply.CatalogApplyService#invoke}。
 */
@FunctionalInterface
public interface NorthboundCommandPort {

    void submit(NorthboundCommand command);
}
