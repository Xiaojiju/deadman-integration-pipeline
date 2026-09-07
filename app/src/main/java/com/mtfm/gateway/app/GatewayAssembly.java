package com.mtfm.gateway.app;

import com.mtfm.gateway.capability.cloud.CloudCapability;
import com.mtfm.gateway.capability.cloud.CloudPublisher;
import com.mtfm.gateway.capability.cloud.NorthboundBinding;
import com.mtfm.gateway.capability.cloud.NorthboundCommandPort;
import com.mtfm.gateway.capability.hikvision.HikvisionCapability;
import com.mtfm.gateway.capability.hikvision.HikvisionDriver;
import com.mtfm.gateway.capability.hikvision.HikvisionExecutor;
import com.mtfm.gateway.capability.loopback.cloud.LoopbackCloudPublisher;
import com.mtfm.gateway.capability.loopback.device.LoopbackCapability;
import com.mtfm.gateway.capability.loopback.device.LoopbackDriver;
import com.mtfm.gateway.capability.loopback.device.LoopbackExecutor;
import com.mtfm.gateway.capability.modbus.InMemoryModbusBus;
import com.mtfm.gateway.capability.modbus.ModbusBus;
import com.mtfm.gateway.capability.modbus.ModbusCapability;
import com.mtfm.gateway.capability.modbus.ModbusDriver;
import com.mtfm.gateway.capability.modbus.ModbusExecutor;
import com.mtfm.gateway.capability.modbus.RoutingModbusBus;
import com.mtfm.gateway.capability.modbus.rtu.RtuModbusBus;
import com.mtfm.gateway.capability.modbus.tcp.TcpModbusBus;
import com.mtfm.gateway.capability.mqtt.device.InMemoryMqttTransport;
import com.mtfm.gateway.capability.mqtt.device.MqttCapability;
import com.mtfm.gateway.capability.mqtt.device.MqttDriver;
import com.mtfm.gateway.capability.mqtt.device.MqttExecutor;
import com.mtfm.gateway.capability.mqtt.device.MqttReadInboundPlugin;
import com.mtfm.gateway.capability.mqtt.device.MqttTransport;
import com.mtfm.gateway.capability.mqtt.device.PahoMqttTransport;
import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.apply.CatalogMqttSubscribeRoutes;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.store.CachingFunctionCatalog;
import com.mtfm.gateway.catalog.store.CatalogNorthbound;
import com.mtfm.gateway.plugin.struct.StructInboundPlugin;
import com.mtfm.gateway.plugin.yaya.YayaInboundPlugin;
import com.mtfm.gateway.runtime.GatewayPipeline;
import com.mtfm.gateway.runtime.registry.DefaultRegistries;
import com.mtfm.gateway.runtime.schedule.ScheduleDispatcher;
import com.mtfm.gateway.spi.capability.Publisher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 网关宿主装配配置：按契约顺序 register 各能力、插件与 Publisher。
 *
 * <p>
 * 装配顺序：
 * <ol>
 * <li>创建各南向 Executor Bean</li>
 * <li>构建 {@link GatewayPipeline} 并 register Driver + Executor + 入站插件</li>
 * <li>register 北向 {@link CloudPublisher} Hub；会话与扇出由 {@link NorthboundBinding} 按 catalog 热切换</li>
 * <li>{@link CatalogApplyService#attach}，最后 {@code pipeline.start()}</li>
 * </ol>
 *
 * <pre>{@code
 * // 由 Spring 自动注入，无需手动调用
 * GatewayPipeline pipeline; // @Bean gatewayPipeline(...)
 * }</pre>
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayAssembly {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayAssembly.class);

    @Bean
    public LoopbackExecutor loopbackExecutor() {
        return new LoopbackExecutor();
    }

    @Bean(destroyMethod = "close")
    public ModbusBus modbusBus(GatewayProperties gatewayProperties) {
        if (gatewayProperties.getModbus().memory()) {
            return new InMemoryModbusBus();
        }
        int requestTimeout = gatewayProperties.getModbus().getRequestTimeoutMs();
        return new RoutingModbusBus(
                new TcpModbusBus(gatewayProperties.getModbus().getConnectTimeoutMs(), requestTimeout),
                new RtuModbusBus(requestTimeout));
    }

    @Bean
    public ModbusExecutor modbusExecutor(ModbusBus modbusBus) {
        return new ModbusExecutor(modbusBus);
    }

    @Bean(destroyMethod = "close")
    public MqttTransport mqttTransport(GatewayProperties gatewayProperties) {
        if ("memory".equalsIgnoreCase(gatewayProperties.getMqtt().getTransport())) {
            return new InMemoryMqttTransport();
        }
        return new PahoMqttTransport();
    }

    @Bean
    public MqttExecutor mqttExecutor(MqttTransport mqttTransport) {
        return new MqttExecutor(mqttTransport);
    }

    @Bean(destroyMethod = "close")
    public HikvisionExecutor hikvisionExecutor() {
        return new HikvisionExecutor();
    }

    @Bean(destroyMethod = "close")
    @Primary
    public CloudPublisher cloudPublisher() {
        return new CloudPublisher();
    }

    /**
     * 按 catalog 落库配置热切换北向 MQTT / Webhook。须在流水线 attach 之后 apply，入站走 catalog invoke。
     */
    @Bean(destroyMethod = "close")
    @DependsOn("gatewayPipeline")
    public NorthboundBinding northboundBinding(
            CloudPublisher cloudPublisher,
            CatalogApplyService applyService,
            CatalogNorthbound catalogNorthbound) {
        NorthboundCommandPort port = command -> applyService.invoke(
                        command.deviceId(),
                        new DeviceCommandRequest(command.functionId(), command.arguments(), command.requestId(), null))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LOG.warn("北向 MQTT 命令提交失败 deviceId={} functionId={}: {}",
                                command.deviceId(), command.functionId(), error.getMessage());
                    }
                });
        NorthboundBinding binding = new NorthboundBinding(cloudPublisher, port);
        binding.apply(catalogNorthbound.openedSettings());
        return binding;
    }

    /**
     * 能力/驱动/插件注册表。与流水线分离，供 catalog 注入 {@code CapabilityRegistrar} / {@code DriverRegistry}。
     */
    @Bean
    public DefaultRegistries gatewayRegistries() {
        return new DefaultRegistries();
    }

    @Bean
    public GatewayPipeline gatewayPipeline(DefaultRegistries gatewayRegistries, CachingFunctionCatalog functionCatalog,
            CatalogApplyService applyService,
            CatalogMqttSubscribeRoutes mqttSubscribeRoutes,
            LoopbackExecutor loopbackExecutor, ModbusExecutor modbusExecutor,
            MqttExecutor mqttExecutor, HikvisionExecutor hikvisionExecutor,
            Publisher cloudPublisher) {
        GatewayPipeline pipeline = GatewayPipeline.builder()
                .functionCatalog(functionCatalog)
                .registries(gatewayRegistries)
                .build();
        pipeline.register(LoopbackCapability.DESCRIPTOR, new LoopbackDriver(), loopbackExecutor);
        pipeline.register(ModbusCapability.DESCRIPTOR, new ModbusDriver(), modbusExecutor);
        pipeline.register(MqttCapability.DESCRIPTOR, new MqttDriver(), mqttExecutor);
        pipeline.register(HikvisionCapability.DESCRIPTOR, new HikvisionDriver(), hikvisionExecutor);
        pipeline.register(CloudCapability.DESCRIPTOR, null, null);
        pipeline.register(new YayaInboundPlugin());
        pipeline.register(new StructInboundPlugin());
        pipeline.register(new MqttReadInboundPlugin(functionCatalog));
        pipeline.register(cloudPublisher);
        mqttExecutor.attach(pipeline, mqttSubscribeRoutes);
        applyService.attach(gatewayRegistries);
        pipeline.start();
        return pipeline;
    }

    /**
     * 单时间轮定时下发。到期只走 catalog invoke，来源 {@code scheduler}。
     */
    @Bean(initMethod = "start", destroyMethod = "close")
    @DependsOn("gatewayPipeline")
    public ScheduleDispatcher scheduleDispatcher(CatalogApplyService applyService, GatewayPipeline pipeline) {
        ScheduleDispatcher dispatcher = new ScheduleDispatcher();
        dispatcher.setOccupancy(pipeline);
        dispatcher.setMetrics(pipeline.stats());
        dispatcher.setHandler((deviceId, functionId) -> {
            if (!applyService.isLoaded(deviceId)) {
                return;
            }
            try {
                applyService.invoke(deviceId, new DeviceCommandRequest(functionId, Map.of(), null, "scheduler"));
            } catch (RuntimeException ex) {
                LOG.warn("定时下发提交失败 deviceId={} functionId={}: {}", deviceId, functionId, ex.getMessage());
            }
        });
        return dispatcher;
    }

    /**
     * 回环北向仅作演示备用，默认不注册，避免与 CloudPublisher 抢 CLOUD 通道。
     */
    @Bean
    public LoopbackCloudPublisher loopbackCloudPublisher() {
        return new LoopbackCloudPublisher();
    }
}
