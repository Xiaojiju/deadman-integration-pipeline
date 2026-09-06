package com.mtfm.gateway.spi.northbound;

/**
 * 北向当前是否真正连上。与落库配置分离，避免「已保存但未接通」被当成成功。
 */
public record NorthboundLiveStatus(boolean mqttLive, String mqttError, boolean httpLive) {

    public static NorthboundLiveStatus idle() {
        return new NorthboundLiveStatus(false, null, false);
    }
}
