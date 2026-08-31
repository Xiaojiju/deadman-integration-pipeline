package com.mtfm.gateway.spi.capability;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;

import java.util.Optional;

/**
 * 统一能力注册器。登记一项能力必须带 connection / address 字段说明。
 *
 * <p>宿主在启动时将 {@link CapabilityDescriptor} 与 {@link Driver}、
 * {@link FunctionExecutor} 一并注册，供可视化与校验使用。
 *
 * <p>使用示例：
 * <pre>{@code
 * registrar.register(
 *         new CapabilityDescriptor("modbus-tcp", connectionSchema, addressSchema, templates),
 *         modbusDriver,
 *         modbusExecutor);
 * Optional<CapabilityDescriptor> desc = registrar.find("modbus-tcp");
 * }</pre>
 */
public interface CapabilityRegistrar {

    /**
     * 登记能力描述与驱动/执行器。
     *
     * @param descriptor 含 connection/address schema 与功能模板
     * @param driver     南向解码驱动
     * @param executor   南向执行器
     */
    void register(CapabilityDescriptor descriptor, Driver driver, FunctionExecutor executor);

    /** 按能力类型查找描述。 */
    Optional<CapabilityDescriptor> find(String capabilityType);

    /**
     * 已登记能力清单，供可视化拉取必填字段。
     */
    java.util.List<CapabilityDescriptor> list();
}
