package com.mtfm.gateway.catalog.action;

/** 动作组 / 场景触发常量。 */
public final class ActionKinds {

    public static final String CLUSTER = "CLUSTER";
    public static final String SCENE = "SCENE";
    public static final String LISTEN = "LISTEN";
    public static final String TIMER = "TIMER";
    public static final String ONCE = "ONCE";
    public static final String CRON = "CRON";
    public static final String SOURCE_CLUSTER = "cluster";
    public static final String SOURCE_SCENE = "scene";
    public static final String SOURCE_SCHEDULER = "scheduler";
    public static final String DEFAULT_ZONE = "Asia/Shanghai";
    public static final long GROUP_TIMEOUT_MS = 15_000L;

    private ActionKinds() {
    }

    public static boolean skipListen(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String key = source.trim().toLowerCase();
        return SOURCE_CLUSTER.equals(key) || SOURCE_SCENE.equals(key) || SOURCE_SCHEDULER.equals(key);
    }
}
