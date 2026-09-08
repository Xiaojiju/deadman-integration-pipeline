package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.dto.SupportedFunctionView;
import com.mtfm.gateway.catalog.dto.SupportedSchemaView;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 南向能力元数据 REST 接口，前缀 {@code /catalog/capabilities}。
 *
 * <p>示例：{@code GET /catalog/capabilities/MODBUS/supported/connection} 获取连接 schema。
 */
@RestController
@RequestMapping("/catalog/capabilities")
public class CatalogCapabilityController {

    private final CatalogFormService forms;

    public CatalogCapabilityController(CatalogFormService forms) {
        this.forms = forms;
    }

    @GetMapping
    public List<CapabilityDescriptor> listCapabilities() {
        return forms.listCapabilities();
    }

    @GetMapping("/{capabilityType}")
    public CapabilityDescriptor capability(@PathVariable String capabilityType) {
        return forms.requireCapability(capabilityType);
    }

    @GetMapping("/{capabilityType}/functions")
    public List<FunctionTemplate> capabilityFunctions(@PathVariable String capabilityType) {
        return forms.capabilityFunctions(capabilityType);
    }

    @GetMapping("/{capabilityType}/supported/connection")
    public SupportedSchemaView supportedConnection(@PathVariable String capabilityType) {
        return forms.supportedConnection(capabilityType);
    }

    @GetMapping("/{capabilityType}/supported/address")
    public SupportedSchemaView supportedAddress(@PathVariable String capabilityType) {
        return forms.supportedAddress(capabilityType);
    }

    @GetMapping("/{capabilityType}/supported/functions")
    public List<SupportedFunctionView> supportedFunctions(@PathVariable String capabilityType) {
        return forms.supportedFunctions(capabilityType);
    }

    @GetMapping("/{capabilityType}/supported/functions/{functionId}")
    public SupportedFunctionView supportedFunction(
            @PathVariable String capabilityType, @PathVariable String functionId) {
        return forms.supportedFunction(capabilityType, functionId);
    }
}
