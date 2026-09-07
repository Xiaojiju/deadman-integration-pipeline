package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Optional;

/**
 * OPEN 能力：字段结构由产品自定义；无参预置模板结构锁定。
 */
final class CatalogOpenFunctionBinding {

    private CatalogOpenFunctionBinding() {
    }

    static boolean isOpenLockedEmptyTemplate(CapabilityDescriptor descriptor, Optional<FunctionTemplate> template) {
        return !descriptor.fixedFunctions()
                && template.isPresent()
                && template.get().parameters().isEmpty();
    }

    static void rejectOpenLockedStructureMutation(ProductFunctionWriteRequest request) {
        if (request.properties() != null && !request.properties().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 properties");
        }
        if (request.writeFields() != null && !request.writeFields().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 writeFields");
        }
        if (request.readFields() != null && !request.readFields().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 readFields");
        }
        if (request.writeValueOptions() != null && !request.writeValueOptions().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 writeValueOptions");
        }
    }

    static List<WriteFieldOption> nullSafeFields(List<WriteFieldOption> fields) {
        return fields == null ? List.of() : fields;
    }

    static ValueAccessType resolveWriteAccess(ProductFunctionWriteRequest request) {
        if (request.payloadMode() != null && !request.payloadMode().isBlank()) {
            return PayloadMode.from(request.payloadMode()) == PayloadMode.VALUE
                    ? ValueAccessType.VALUE
                    : ValueAccessType.STRUCT;
        }
        if (request.writeAccessType() != null && !request.writeAccessType().isBlank()) {
            return ValueAccessType.from(request.writeAccessType());
        }
        return ValueAccessType.STRUCT;
    }

    static List<PropertyItem> resolveFunctionProperties(
            ProductFunctionWriteRequest request, Optional<FunctionTemplate> template) {
        if (request.properties() != null) {
            return request.properties();
        }
        return template.map(item -> PropertySchemas.toPropertyItems(item.parameters())).orElse(List.of());
    }
}
