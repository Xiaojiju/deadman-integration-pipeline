package com.mtfm.gateway.capability.hikvision;

import com.fasterxml.jackson.databind.JsonNode;
import com.mtfm.gateway.capability.hikvision.isapi.HikvisionJson;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.probe.DiscoveredDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 ISAPI deviceList 响应，只收 AccessControl。
 */
public final class HikvisionDeviceListParser {

    public static final String DEV_TYPE_ACCESS_CONTROL = "AccessControl";
    public static final String PROTOCOL_EHOME_V5 = "ehomeV5";

    private HikvisionDeviceListParser() {
    }

    public static Page parse(String json) {
        JsonNode root = HikvisionJson.tree(json);
        JsonNode result = root.path("SearchResult");
        int total = result.path("totalMatches").asInt(0);
        List<DiscoveredDevice> devices = new ArrayList<>();
        JsonNode matches = result.path("MatchList");
        if (matches.isArray()) {
            for (JsonNode item : matches) {
                JsonNode device = item.path("Device");
                if (device.isMissingNode() || device.isNull()) {
                    continue;
                }
                String devType = text(device, "devType");
                if (!DEV_TYPE_ACCESS_CONTROL.equalsIgnoreCase(devType)) {
                    continue;
                }
                String deviceCode = text(device.path("EhomeParams"), "EhomeID");
                String serial = text(device, "devIndex");
                if (deviceCode == null || deviceCode.isBlank() || serial == null || serial.isBlank()) {
                    continue;
                }
                devices.add(new DiscoveredDevice(
                        deviceCode.trim(),
                        text(device, "devName"),
                        Attributes.from(java.util.Map.of("deviceSerialNo", serial.trim())),
                        online(device)));
            }
        }
        return new Page(total, List.copyOf(devices));
    }

    public static String searchBody(int position, int maxResult) {
        return HikvisionJson.toJson(java.util.Map.of(
                "SearchDescription", java.util.Map.of(
                        "position", position,
                        "maxResult", maxResult,
                        "Filter", java.util.Map.of(
                                "key", "",
                                "devType", DEV_TYPE_ACCESS_CONTROL,
                                "protocolType", List.of(PROTOCOL_EHOME_V5),
                                "devStatus", List.of("online", "offline")))));
    }

    private static boolean online(JsonNode device) {
        JsonNode active = device.get("activeStatus");
        if (active != null && !active.isNull() && active.isBoolean()) {
            return active.booleanValue();
        }
        return "online".equalsIgnoreCase(text(device, "devStatus"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public record Page(int totalMatches, List<DiscoveredDevice> devices) {
    }
}
