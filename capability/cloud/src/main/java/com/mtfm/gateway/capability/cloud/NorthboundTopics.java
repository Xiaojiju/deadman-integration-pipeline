package com.mtfm.gateway.capability.cloud;

/**
 * 北向 MQTT 主题模板与通配匹配。与设备侧 Broker 无关。
 */
public final class NorthboundTopics {

    public static final String DEFAULT_COMMAND = "gw/+/command";
    public static final String DEFAULT_RESPONSE = "gw/{deviceId}/response";
    public static final String DEFAULT_TELEMETRY = "gw/{deviceId}/telemetry";

    private NorthboundTopics() {
    }

    public static String expand(String pattern, String deviceId) {
        if (pattern == null || pattern.isBlank()) {
            return pattern;
        }
        String id = deviceId == null ? "" : deviceId;
        return pattern.replace("{deviceId}", id);
    }

    /**
     * MQTT 单层 {@code +} 与多层 {@code #} 匹配。
     */
    public static boolean matches(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        if (filter.equals(topic)) {
            return true;
        }
        String[] filters = filter.split("/", -1);
        String[] topics = topic.split("/", -1);
        int i = 0;
        int j = 0;
        while (i < filters.length) {
            String token = filters[i];
            if ("#".equals(token)) {
                return i == filters.length - 1;
            }
            if (j >= topics.length) {
                return false;
            }
            if (!"+".equals(token) && !token.equals(topics[j])) {
                return false;
            }
            i++;
            j++;
        }
        return j == topics.length;
    }

    /**
     * 从 {@code gw/+/command} 这类过滤器和实际 topic 取出第一个 {@code +} 对应段，作为 deviceId。
     */
    public static String deviceIdFromTopic(String filter, String topic) {
        if (filter == null || topic == null) {
            return null;
        }
        String[] filters = filter.split("/", -1);
        String[] topics = topic.split("/", -1);
        int j = 0;
        for (String token : filters) {
            if ("#".equals(token)) {
                return null;
            }
            if (j >= topics.length) {
                return null;
            }
            if ("+".equals(token)) {
                String value = topics[j];
                return value == null || value.isBlank() ? null : value;
            }
            if (!token.equals(topics[j])) {
                return null;
            }
            j++;
        }
        return null;
    }
}
