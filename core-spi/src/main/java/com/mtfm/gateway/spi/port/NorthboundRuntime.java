package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.northbound.NorthboundLiveStatus;
import com.mtfm.gateway.spi.northbound.NorthboundSettings;

/**
 * 北向双通道热切换口。catalog 只落库；实现负责换 MQTT 会话 / Webhook，不得回压南向。
 */
public interface NorthboundRuntime {

    /**
     * 应用北向双通道配置。
     * 
     * @param settings 北向双通道配置
     * @return void
     */
    void apply(NorthboundSettings settings);

    /**
     * 获取北向双通道状态。
     * 
     * @return 北向双通道状态
     */
    NorthboundLiveStatus status();
}
