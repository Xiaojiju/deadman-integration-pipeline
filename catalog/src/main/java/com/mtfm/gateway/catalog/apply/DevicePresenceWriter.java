package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.presence.DeviceOnlineHint;
import com.mtfm.gateway.spi.presence.DevicePresencePort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 把在线提示写回已存在的设备。不创建、不改名。
 */
@Component
public class DevicePresenceWriter implements DevicePresencePort {

    private final CatalogStore store;

    public DevicePresenceWriter(CatalogStore store) {
        this.store = store;
    }

    @Override
    public void apply(List<DeviceOnlineHint> hints) {
        if (hints == null || hints.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (DeviceOnlineHint hint : hints) {
            if (hint == null || hint.deviceCode() == null || hint.deviceCode().isBlank()) {
                continue;
            }
            store.findDeviceByCode(hint.deviceCode()).ifPresent(device -> writeOnline(device, hint.online(), now));
        }
    }

    private void writeOnline(DeviceEntity device, boolean online, Instant now) {
        device.setOnline(online);
        device.setOnlineUpdatedAt(now);
        store.updateDevice(device);
    }
}
