package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.presence.DevicePresencePort;
import com.mtfm.gateway.spi.presence.PresencePoller;
import com.mtfm.gateway.spi.probe.ChannelProbeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * NATIVE 在线轮询：只扫已有 loaded 设备的通道，不创建设备。
 */
@Component
public class CatalogPresenceWatch {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogPresenceWatch.class);

    private final CatalogStore store;
    private final CatalogApplyService applyService;
    private final CapabilityRegistrar registrar;
    private final List<PresencePoller> pollers;
    private final DevicePresencePort presence;

    public CatalogPresenceWatch(
            CatalogStore store,
            CatalogApplyService applyService,
            @Autowired(required = false) CapabilityRegistrar registrar,
            ObjectProvider<PresencePoller> pollers,
            DevicePresencePort presence) {
        this.store = store;
        this.applyService = applyService;
        this.registrar = registrar;
        this.pollers = pollers == null ? List.of() : pollers.orderedStream().toList();
        this.presence = presence;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void pollLoadedChannels() {
        if (registrar == null || pollers.isEmpty()) {
            return;
        }
        List<ChannelEntity> channels;
        try {
            channels = store.listChannels();
        } catch (RuntimeException ex) {
            LOG.warn("通道在线轮询跳过: {}", ex.getMessage());
            return;
        }
        for (ChannelEntity channel : channels) {
            pollChannel(channel);
        }
    }

    private void pollChannel(ChannelEntity channel) {
        if (Boolean.FALSE.equals(channel.getEnabled())) {
            return;
        }
        Optional<CapabilityDescriptor> descriptor = registrar.find(channel.getCapabilityType());
        if (descriptor.isEmpty() || !descriptor.get().probeSupported()) {
            return;
        }
        PresencePoller poller = pollers.stream()
                .filter(item -> item.supports(channel.getCapabilityType()))
                .findFirst()
                .orElse(null);
        if (poller == null || !hasLoadedDevice(channel)) {
            return;
        }
        try {
            presence.apply(poller.poll(new ChannelProbeRequest(
                    channel.getCode(), store.openedConnection(channel))));
        } catch (RuntimeException ex) {
            LOG.warn("通道在线轮询失败 channel={}: {}", channel.getCode(), ex.getMessage());
        }
    }

    private boolean hasLoadedDevice(ChannelEntity channel) {
        for (DeviceEndpointEntity endpoint : store.listEndpointsByChannel(channel.getId())) {
            DeviceEntity device = store.findDeviceById(endpoint.getDeviceId()).orElse(null);
            if (device != null && applyService.isLoaded(device.getDeviceCode())) {
                return true;
            }
        }
        return false;
    }
}
