package com.mtfm.gateway.catalog.payload;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.spi.payload.CommandAssembler;
import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.FramePacker;
import com.mtfm.gateway.spi.payload.FunctionRoute;
import com.mtfm.gateway.spi.payload.LegacyFieldAdapter;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.payload.ValueMapping;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Map;

/** 从产品功能实体 + 扁平字段表解析 Payload 定义。 */
public final class PayloadDefinitionResolver {

    private PayloadDefinitionResolver() {
    }

    public record Definition(
            PayloadMode payloadMode,
            FieldNode structRoot,
            List<ValueMapping> valueMappings,
            FunctionRoute route,
            PayloadEncoding payloadEncoding,
            List<WriteFieldOption> fields
    ) {
    }

    public static Definition resolve(
            ProductFunctionEntity function, CatalogPropertyRepository properties) {
        boolean isRead = "READ".equalsIgnoreCase(function.getAccessType());
        List<WriteFieldOption> fields = isRead
                ? properties.listReadFields(function.getId())
                : properties.listWriteFields(function.getId());
        FieldNode root = LegacyFieldAdapter.fromWriteFields(fields);
        List<ValueOption> valueOptions = properties.listWriteValueOptions(function.getId());
        PayloadMode mode = resolveMode(function, valueOptions);
        List<ValueMapping> mappings = mode == PayloadMode.VALUE
                ? LegacyFieldAdapter.fromFieldAndValueOptions(fields, valueOptions)
                : List.of();
        FunctionRoute route = new FunctionRoute(function.getPublishTopicSlot(), function.getSubscribeTopicSlot());
        PayloadEncoding encoding = PayloadEncoding.from(function.getPayloadEncoding());
        return new Definition(mode, root, mappings, route, encoding, fields);
    }

    public static void syncFromLegacyFields(
            ProductFunctionEntity entity,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> writeValueOptions) {
        PayloadMode mode = resolveMode(entity, writeValueOptions);
        entity.setPayloadMode(mode.wire());
        entity.setWriteAccessType(mode == PayloadMode.VALUE ? "VALUE" : "STRUCT");
    }

    /** 请求体携带 payloadMode 时写入功能实体。 */
    public static void applyPayloadFromRequest(ProductFunctionEntity entity, ProductFunctionWriteRequest request) {
        if (request == null) {
            return;
        }
        if (request.payloadMode() != null && !request.payloadMode().isBlank()) {
            PayloadMode mode = PayloadMode.from(request.payloadMode());
            entity.setPayloadMode(mode.wire());
            entity.setWriteAccessType(mode == PayloadMode.VALUE ? "VALUE" : "STRUCT");
        }
        if (request.payloadEncoding() != null && !request.payloadEncoding().isBlank()) {
            entity.setPayloadEncoding(PayloadEncoding.from(request.payloadEncoding()).wire());
        }
    }

    public static boolean hasDirectPayload(ProductFunctionWriteRequest request) {
        return request != null && request.payloadMode() != null && !request.payloadMode().isBlank();
    }

    public static Map<String, Object> assemble(
            Definition definition,
            Map<String, Object> caller,
            Map<String, Object> deviceFieldOverrides) {
        Map<String, Object> assembled = CommandAssembler.assemble(new CommandAssembler.Request(
                definition.payloadMode(),
                definition.structRoot(),
                definition.valueMappings(),
                caller,
                deviceFieldOverrides));
        return FramePacker.pack(definition.fields(), assembled, definition.payloadEncoding());
    }

    private static PayloadMode resolveMode(ProductFunctionEntity function, List<ValueOption> valueOptions) {
        if (function.getPayloadMode() != null && !function.getPayloadMode().isBlank()) {
            return PayloadMode.from(function.getPayloadMode());
        }
        return LegacyFieldAdapter.payloadModeFromWriteAccess(function.getWriteAccessType(), valueOptions);
    }
}
