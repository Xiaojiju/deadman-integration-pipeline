package com.mtfm.gateway.runtime;

import com.mtfm.gateway.runtime.metrics.CountingGatewayMetrics;
import com.mtfm.gateway.runtime.registry.DefaultRegistries;
import com.mtfm.gateway.runtime.seal.DefaultEnvelopeSealer;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.port.EnvelopeSealer;

/**
 * {@link GatewayPipeline} 构建器，用于注入目录、运行参数与自定义贴胶器。
 *
 * <pre>{@code
 * GatewayPipeline pipeline = GatewayPipeline.builder()
 *         .functionCatalog(catalogStore)
 *         .settings(GatewaySettings.builder().executeWorkers(8).build())
 *         .build();
 * }</pre>
 */
public final class GatewayPipelineBuilder {

    private FunctionCatalog functionCatalog;
    private GatewaySettings settings = GatewaySettings.defaults();
    private EnvelopeSealer sealer = new DefaultEnvelopeSealer();
    private CountingGatewayMetrics counters = new CountingGatewayMetrics();
    private DefaultRegistries registries;

    public GatewayPipelineBuilder functionCatalog(FunctionCatalog functionCatalog) {
        this.functionCatalog = functionCatalog;
        return this;
    }

    public GatewayPipelineBuilder settings(GatewaySettings settings) {
        this.settings = settings;
        return this;
    }

    public GatewayPipelineBuilder sealer(EnvelopeSealer sealer) {
        this.sealer = sealer;
        return this;
    }

    public GatewayPipelineBuilder registries(DefaultRegistries registries) {
        this.registries = registries;
        return this;
    }

    public GatewayPipeline build() {
        return new GatewayPipeline(functionCatalog, settings, sealer, counters, registries);
    }
}
