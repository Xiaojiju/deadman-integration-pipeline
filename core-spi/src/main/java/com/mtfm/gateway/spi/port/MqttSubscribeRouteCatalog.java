package com.mtfm.gateway.spi.port;

import java.util.List;
import java.util.Map;

/**
 * 解析设备端点 address 下各 READ 功能的 MQTT 订阅 topic。
 */
public interface MqttSubscribeRouteCatalog {

    List<MqttSubscribeRoute> routesForDevice(String deviceCode, Map<String, Object> addressValues);
}
