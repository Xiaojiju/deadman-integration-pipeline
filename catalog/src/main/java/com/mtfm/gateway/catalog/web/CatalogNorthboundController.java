package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.dto.NorthboundView;
import com.mtfm.gateway.catalog.dto.NorthboundWriteRequest;
import com.mtfm.gateway.catalog.store.CatalogNorthbound;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 北向双通道（MQTT/HTTP）配置 REST 接口，前缀 {@code /catalog/northbound}。
 *
 * <p>示例：{@code GET /catalog/northbound} 读取配置；
 * {@code PUT /catalog/northbound} 更新 MQTT/HTTP 参数。
 */
@RestController
@RequestMapping("/catalog/northbound")
public class CatalogNorthboundController {

    private final CatalogNorthbound northbound;

    public CatalogNorthboundController(CatalogNorthbound northbound) {
        this.northbound = northbound;
    }

    @GetMapping
    public NorthboundView getNorthbound() {
        return northbound.view();
    }

    @PutMapping
    public NorthboundView updateNorthbound(@Valid @RequestBody NorthboundWriteRequest request) {
        return northbound.update(request);
    }
}
