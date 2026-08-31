package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;

/**
 * 插件登记端口。{@link com.mtfm.gateway.spi.plugin.InboundPlugin#support} /
 * {@link com.mtfm.gateway.spi.plugin.OutboundPlugin#support} 为 {@code true} 才入链；按 {@code order} 升序。
 *
 * <p>使用示例：
 * <pre>{@code
 * pluginRegistry.register(new AuthInboundPlugin());
 * pluginRegistry.register(new TraceOutboundPlugin());
 * }</pre>
 */
public interface PluginRegistry {

    /** 注册入站插件。 */
    boolean register(InboundPlugin plugin);

    /** 注册出站插件。 */
    boolean register(OutboundPlugin plugin);
}
