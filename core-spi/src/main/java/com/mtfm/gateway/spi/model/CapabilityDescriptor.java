package com.mtfm.gateway.spi.model;

import java.util.List;
import java.util.Optional;

/**
 * 能力登记描述。登记时必须带上通道、地址与功能参数字段，供可视化表单使用。
 *
 * <p>使用示例：
 * <pre>{@code
 * CapabilityDescriptor modbus = new CapabilityDescriptor(
 *         "MODBUS",
 *         List.of(SchemaField.required("host", "string", "Modbus 主机")),
 *         List.of(SchemaField.required("slaveId", "int", "从站号")),
 *         List.of(FunctionTemplate.of("fn.read", "READ", readParams)),
 *         FunctionCatalogMode.CONTRACT);
 * }</pre>
 *
 * @param capabilityType     能力类型标识
 * @param connectionSchema   通道连接参数字段
 * @param addressSchema      设备地址片字段
 * @param functionTemplates  预置功能模板
 * @param functionMode       功能目录模式：FIXED 封闭 / CONTRACT 锁参数名 / OPEN 可扩展
 * @param probeSupported     是否支持通道探针扫描
 */
public record CapabilityDescriptor(
        String capabilityType,
        List<SchemaField> connectionSchema,
        List<SchemaField> addressSchema,
        List<FunctionTemplate> functionTemplates,
        FunctionCatalogMode functionMode,
        boolean probeSupported
) {

    public CapabilityDescriptor {
        if (capabilityType == null || capabilityType.isBlank()) {
            throw new IllegalArgumentException("capabilityType 不能为空");
        }
        connectionSchema = connectionSchema == null ? List.of() : List.copyOf(connectionSchema);
        addressSchema = addressSchema == null ? List.of() : List.copyOf(addressSchema);
        functionTemplates = functionTemplates == null ? List.of() : List.copyOf(functionTemplates);
        if (functionMode == null) {
            functionMode = FunctionCatalogMode.OPEN;
        }
    }

    /** 不含功能模板的简化构造（默认 OPEN，不支持探针）。 */
    public CapabilityDescriptor(String capabilityType, List<SchemaField> connectionSchema,
            List<SchemaField> addressSchema) {
        this(capabilityType, connectionSchema, addressSchema, List.of(), FunctionCatalogMode.OPEN, false);
    }

    /** 带功能模板、默认 OPEN（适合协议原语类能力）。 */
    public CapabilityDescriptor(String capabilityType, List<SchemaField> connectionSchema,
            List<SchemaField> addressSchema, List<FunctionTemplate> functionTemplates) {
        this(capabilityType, connectionSchema, addressSchema, functionTemplates, FunctionCatalogMode.OPEN, false);
    }

    /** 带功能模式、默认不支持探针。 */
    public CapabilityDescriptor(String capabilityType, List<SchemaField> connectionSchema,
            List<SchemaField> addressSchema, List<FunctionTemplate> functionTemplates,
            FunctionCatalogMode functionMode) {
        this(capabilityType, connectionSchema, addressSchema, functionTemplates, functionMode, false);
    }

    /** 是否封闭功能集。 */
    public boolean fixedFunctions() {
        return functionMode == FunctionCatalogMode.FIXED;
    }

    /** 是否锁死参数名、放开业务 functionId（如 Modbus）。 */
    public boolean contractedParameters() {
        return functionMode == FunctionCatalogMode.CONTRACT;
    }

    /** 按功能 ID 查找预置模板。 */
    public Optional<FunctionTemplate> functionTemplate(String functionId) {
        if (functionId == null) {
            return Optional.empty();
        }
        return functionTemplates.stream()
                .filter(item -> functionId.equals(item.functionId()))
                .findFirst();
    }

    /** 按访问类型取参数契约模板（CONTRACT 能力：READ / WRITE 各一套）。 */
    public Optional<FunctionTemplate> functionTemplateByAccessType(String accessType) {
        if (accessType == null || accessType.isBlank()) {
            return Optional.empty();
        }
        return functionTemplates.stream()
                .filter(item -> accessType.equalsIgnoreCase(item.accessType()))
                .findFirst();
    }
}
