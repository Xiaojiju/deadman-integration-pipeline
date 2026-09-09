package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.capability.hikvision.isapi.HikvisionHttpClient;
import com.mtfm.gateway.spi.presence.DeviceOnlineHint;
import com.mtfm.gateway.spi.presence.PresencePoller;
import com.mtfm.gateway.spi.probe.ChannelProbe;
import com.mtfm.gateway.spi.probe.ChannelProbeRequest;
import com.mtfm.gateway.spi.probe.ChannelProbeResult;
import com.mtfm.gateway.spi.probe.DiscoveredDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 海康通道探针与 NATIVE 在线轮询，共用 deviceList。
 */
public final class HikvisionChannelProbe implements ChannelProbe, PresencePoller {

    static final String DEVICE_LIST_PATH = "/ISAPI/ContentMgmt/DeviceMgmt/deviceList?format=json";
    static final int PAGE_SIZE = 100;

    @Override
    public boolean supports(String capabilityType) {
        return HikvisionCapability.TYPE.equalsIgnoreCase(capabilityType);
    }

    @Override
    public ChannelProbeResult scan(ChannelProbeRequest request) {
        return new ChannelProbeResult(listAll(request));
    }

    @Override
    public List<DeviceOnlineHint> poll(ChannelProbeRequest request) {
        return listAll(request).stream()
                .map(item -> new DeviceOnlineHint(item.deviceCode(), item.online()))
                .toList();
    }

    private List<DiscoveredDevice> listAll(ChannelProbeRequest request) {
        HikvisionChannelConfig config = HikvisionChannelConfig.from(request.channelId(), request.connection());
        List<DiscoveredDevice> all = new ArrayList<>();
        int position = 0;
        int total = Integer.MAX_VALUE;
        while (position < total) {
            HikvisionDeviceListParser.Page page = fetchPage(config, position);
            total = page.totalMatches();
            all.addAll(page.devices());
            if (page.devices().isEmpty()) {
                break;
            }
            position += PAGE_SIZE;
        }
        return List.copyOf(all);
    }

    private HikvisionDeviceListParser.Page fetchPage(HikvisionChannelConfig config, int position) {
        String url = config.baseUrl() + DEVICE_LIST_PATH;
        String body = HikvisionDeviceListParser.searchBody(position, PAGE_SIZE);
        try {
            String response = HikvisionHttpClient.post(url, body, config.username(), config.password());
            return HikvisionDeviceListParser.parse(response);
        } catch (IOException ex) {
            throw new HikvisionAccessException("扫描通道设备失败: " + ex.getMessage(), ex, true);
        }
    }
}
