package com.mtfm.gateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关 Spring Boot 宿主入口。
 *
 * <p>扫描 {@code com.mtfm.gateway} 包，启动后由 {@link GatewayAssembly} 装配流水线与各能力模块。
 * 本类只负责启动容器，不包含任何协议逻辑。
 */
@SpringBootApplication(scanBasePackages = "com.mtfm.gateway")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
