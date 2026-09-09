package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.spi.probe.ChannelProbe;
import com.mtfm.gateway.spi.probe.ChannelProbeRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 收集 Spring 容器里的 {@link ChannelProbe}，按能力类型查找。
 */
@Component
public class DefaultChannelProbeRegistry implements ChannelProbeRegistry {

    private final List<ChannelProbe> probes;

    public DefaultChannelProbeRegistry(ObjectProvider<ChannelProbe> probes) {
        this.probes = probes == null ? List.of() : probes.orderedStream().toList();
    }

    @Override
    public Optional<ChannelProbe> find(String capabilityType) {
        if (capabilityType == null || capabilityType.isBlank()) {
            return Optional.empty();
        }
        return probes.stream()
                .filter(probe -> probe.supports(capabilityType))
                .findFirst();
    }
}
