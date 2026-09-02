package com.mtfm.gateway.app;

import com.mtfm.gateway.capability.cloud.CloudCapability;
import com.mtfm.gateway.capability.cloud.CloudPublisher;
import com.mtfm.gateway.capability.hikvision.HikvisionCapability;
import com.mtfm.gateway.capability.hikvision.HikvisionDriver;
import com.mtfm.gateway.capability.hikvision.HikvisionExecutor;
import com.mtfm.gateway.capability.loopback.cloud.LoopbackCloudPublisher;
import com.mtfm.gateway.capability.loopback.device.LoopbackCapability;
import com.mtfm.gateway.capability.loopback.device.LoopbackDriver;
import com.mtfm.gateway.capability.loopback.device.LoopbackExecutor;
import com.mtfm.gateway.capability.modbus.InMemoryModbusBus;
import com.mtfm.gateway.capability.modbus.ModbusCapability;
import com.mtfm.gateway.capability.modbus.ModbusDriver;
import com.mtfm.gateway.capability.modbus.ModbusExecutor;
import com.mtfm.gateway.capability.mqtt.device.InMemoryMqttTransport;
import com.mtfm.gateway.capability.mqtt.device.MqttCapability;
import com.mtfm.gateway.capability.mqtt.device.MqttDriver;
import com.mtfm.gateway.capability.mqtt.device.MqttExecutor;
import com.mtfm.gateway.capability.mqtt.device.MqttReadInboundPlugin;
import com.mtfm.gateway.capability.mqtt.device.MqttTransport;
import com.mtfm.gateway.capability.mqtt.device.PahoMqttTransport;
import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.apply.CatalogMqttSubscribeRoutes;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.plugin.struct.StructInboundPlugin;
import com.mtfm.gateway.plugin.yaya.YayaInboundPlugin;
import com.mtfm.gateway.runtime.GatewayPipeline;
import com.mtfm.gateway.spi.capability.Publisher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 网关宿主装配配置：按契约顺序 register 各能力、插件与 Publisher。
 *
 * <p>装配顺序：
 * <ol>
 *   <li>创建各南向 Executor Bean</li>
 *   <li>构建 {@link GatewayPipeline} 并 register Driver + Executor + 入站插件</li>
 *   <li>register 北向 {@link CloudPublisher}（唯一 CLOUD 通道）</li>
 *   <li>{@link CatalogApplyService#attach} 并 registerExecutor，最后 {@code pipeline.start()}</li>
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

    @Bean
    public LoopbackExecutor loopbackExecutor() {
        return new LoopbackExecutor();
    }

    @Bean
    public ModbusExecutor modbusExecutor() {
        return new ModbusExecutor(new InMemoryModbusBus());
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

    @Bean
    public HikvisionExecutor hikvisionExecutor() {
        return new HikvisionExecutor();
    }

    @Bean
    @Primary
    public Publisher cloudPublisher() {
        return new CloudPublisher();
    }

    @Bean
    public GatewayPipeline gatewayPipeline(CatalogStore catalogStore, CatalogApplyService applyService,
            CatalogMqttSubscribeRoutes mqttSubscribeRoutes,
            LoopbackExecutor loopbackExecutor, ModbusExecutor modbusExecutor,
            MqttExecutor mqttExecutor, HikvisionExecutor hikvisionExecutor,
            Publisher cloudPublisher) {
        GatewayPipeline pipeline = GatewayPipeline.builder()
                .functionCatalog(catalogStore)
                .build();
        pipeline.register(LoopbackCapability.DESCRIPTOR, new LoopbackDriver(), loopbackExecutor);
        pipeline.register(ModbusCapability.DESCRIPTOR, new ModbusDriver(), modbusExecutor);
        pipeline.register(MqttCapability.DESCRIPTOR, new MqttDriver(), mqttExecutor);
        pipeline.register(HikvisionCapability.DESCRIPTOR, new HikvisionDriver(), hikvisionExecutor);
        pipeline.register(CloudCapability.DESCRIPTOR, null, null);
        pipeline.register(new YayaInboundPlugin());
        pipeline.register(new StructInboundPlugin());
        pipeline.register(new MqttReadInboundPlugin(catalogStore));
        pipeline.register(cloudPublisher);
        mqttExecutor.attach(pipeline, mqttSubscribeRoutes);
        applyService.attach(pipeline);
        applyService.registerExecutor(loopbackExecutor);
        applyService.registerExecutor(modbusExecutor);
        applyService.registerExecutor(mqttExecutor);
        applyService.registerExecutor(hikvisionExecutor);
        pipeline.start();
        return pipeline;
    }

    /**
     * 回环北向仅作演示备用，默认不注册，避免与 CloudPublisher 抢 CLOUD 通道。
     */
    @Bean
    public LoopbackCloudPublisher loopbackCloudPublisher() {
        return new LoopbackCloudPublisher();
    }
}
