package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaValidator;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Optional;

/**
 * 功能字段绑定入口：按能力目录模式分派到 FIXED / CONTRACT / OPEN。
 */
final class CatalogFunctionBinding {

    private CatalogFunctionBinding() {
    }

    record FunctionOptionPlan(
            ValueAccessType writeAccess,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> writeValueOptions,
            List<ValueOption> readValueOptions) {
    }

    static FunctionOptionPlan bindRequest(
            CapabilityDescriptor descriptor,
            Optional<FunctionTemplate> template,
            ProductFunctionWriteRequest request,
            String accessType,
            boolean openLockedEmpty) {
        ValueAccessType writeAccess;
        List<ValueOption> writeValueOptions;
        List<WriteFieldOption> writeFields;
        List<WriteFieldOption> readFields;
        if (descriptor.fixedFunctions()) {
            writeAccess = ValueAccessType.STRUCT;
            writeFields = CatalogFixedFunctionBinding.resolveFixedWriteFields(request.writeFields(), template);
            writeValueOptions = List.of();
            readFields = List.of();
        } else if (openLockedEmpty) {
            writeAccess = ValueAccessType.STRUCT;
            writeFields = List.of();
            writeValueOptions = List.of();
            readFields = List.of();
        } else if (descriptor.contractedParameters()) {
            writeAccess = CatalogOpenFunctionBinding.resolveWriteAccess(request);
            String access = accessType != null && !accessType.isBlank()
                    ? accessType
                    : template.map(t -> t.accessType()).orElse("WRITE");
            FunctionTemplate contract = CatalogContractFunctionBinding.requireContractTemplate(descriptor, access);
            PayloadMode mode = writeAccess == ValueAccessType.VALUE ? PayloadMode.VALUE : PayloadMode.STRUCT;
            boolean isRead = "READ".equalsIgnoreCase(access);
            writeFields = isRead
                    ? List.of()
                    : CatalogContractFunctionBinding.bindContractFields(contract, request.writeFields(), mode);
            readFields = isRead
                    ? CatalogContractFunctionBinding.bindContractFields(contract, request.readFields(), mode)
                    : List.of();
            writeValueOptions = isRead
                    ? List.of()
                    : CatalogContractFunctionBinding.constrainContractValueOptions(
                            request.writeValueOptions() == null ? List.of() : request.writeValueOptions(),
                            contract,
                            mode);
            SchemaValidator.requireConstraints(
                    contract.parameters(), CatalogFunctionSchemas.constantsOf(writeFields), "功能参数");
            SchemaValidator.requireConstraints(
                    contract.parameters(), CatalogFunctionSchemas.constantsOf(readFields), "功能参数");
        } else {
            writeAccess = CatalogOpenFunctionBinding.resolveWriteAccess(request);
            boolean isRead = "READ".equalsIgnoreCase(accessType);
            writeFields = isRead ? List.of() : CatalogOpenFunctionBinding.nullSafeFields(request.writeFields());
            readFields = CatalogOpenFunctionBinding.nullSafeFields(request.readFields());
            writeValueOptions = request.writeValueOptions() == null ? List.of() : request.writeValueOptions();
        }
        List<ValueOption> readValueOptions = request.readValueOptions() == null
                ? List.of()
                : request.readValueOptions();
        if (openLockedEmpty) {
            readValueOptions = List.of();
        } else if (descriptor.fixedFunctions() && template.isPresent()) {
            readValueOptions = (request.readValueOptions() == null || request.readValueOptions().isEmpty())
                    ? List.of()
                    : CatalogFixedFunctionBinding.constrainFixedValueOptions(readValueOptions, template.get());
        }
        return new FunctionOptionPlan(writeAccess, writeFields, readFields, writeValueOptions, readValueOptions);
    }
}
