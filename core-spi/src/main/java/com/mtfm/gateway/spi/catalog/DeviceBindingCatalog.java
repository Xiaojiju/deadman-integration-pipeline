package com.mtfm.gateway.spi.catalog;

import com.mtfm.gateway.spi.model.DeviceBinding;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;

import java.util.List;
import java.util.Optional;

/**
 * 设备绑定与端点目录端口。
 *
 * <p>使用示例：
 * <pre>{@code
 * Optional<DeviceBinding> binding = catalog.findDevice("dev-001");
 * List<DeviceEndpointBinding> endpoints = catalog.findEndpoints("dev-001");
 * }</pre>
 */
public interface DeviceBindingCatalog {

    /** 查找设备与南向能力的绑定关系。 */
    Optional<DeviceBinding> findDevice(String deviceId);

    /** 查找设备下所有端点（通道 + 地址片）。 */
    List<DeviceEndpointBinding> findEndpoints(String deviceId);
}
