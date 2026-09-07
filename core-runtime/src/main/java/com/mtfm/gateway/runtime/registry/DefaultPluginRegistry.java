package com.mtfm.gateway.runtime.registry;

import com.mtfm.gateway.spi.exception.RegistryException;
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;
import com.mtfm.gateway.spi.port.PluginRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 入站 / 出站插件链，按 {@code order} 升序。
 */
public final class DefaultPluginRegistry implements PluginRegistry {

    private final CopyOnWriteArrayList<InboundPlugin> inboundPlugins = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OutboundPlugin> outboundPlugins = new CopyOnWriteArrayList<>();

    @Override
    public boolean register(InboundPlugin plugin) {
        if (plugin == null || plugin.name() == null || plugin.name().isBlank()) {
            throw new RegistryException("入站插件名不能为空");
        }
        boolean exists = inboundPlugins.stream().anyMatch(item -> item.name().equals(plugin.name()));
        if (exists) {
            return false;
        }
        inboundPlugins.add(plugin);
        inboundPlugins.sort(Comparator.comparingInt(InboundPlugin::order));
        return true;
    }

    @Override
    public boolean register(OutboundPlugin plugin) {
        if (plugin == null || plugin.name() == null || plugin.name().isBlank()) {
            throw new RegistryException("出站插件名不能为空");
        }
        boolean exists = outboundPlugins.stream().anyMatch(item -> item.name().equals(plugin.name()));
        if (exists) {
            return false;
        }
        outboundPlugins.add(plugin);
        outboundPlugins.sort(Comparator.comparingInt(OutboundPlugin::order));
        return true;
    }

    @Override
    public List<InboundPlugin> inboundInOrder() {
        return List.copyOf(inboundPlugins);
    }

    @Override
    public List<OutboundPlugin> outboundInOrder() {
        return List.copyOf(outboundPlugins);
    }
}
