package com.mtfm.gateway.catalog.payload;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.spi.payload.CommandAssembler;
import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.FunctionRoute;
import com.mtfm.gateway.spi.payload.LegacyFieldAdapter;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.payload.ValueMapping;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;
import java.util.Map;

/** 从产品功能实体 + 旧表解析 Payload 定义。 */
public final class PayloadDefinitionResolver {

    private PayloadDefinitionResolver() {
    }

    public record Definition(
            PayloadMode payloadMode,
            FieldNode structRoot,
            List<ValueMapping> valueMappings,
            FunctionRoute route
    ) {
    }

    public static Definition resolve(
            ProductFunctionEntity function, CatalogPropertyRepository properties) {
        FieldNode root = PayloadCodec.readFieldNode(function.getStructSchema());
        List<ValueMapping> mappings = PayloadCodec.readMappings(function.getValueMappings());
        PayloadMode mode = PayloadMode.from(function.getPayloadMode());

        if (root == null) {
            boolean isRead = "READ".equalsIgnoreCase(function.getAccessType());
            List<WriteFieldOption> fields = isRead
                    ? properties.listReadFields(function.getId())
                    : properties.listWriteFields(function.getId());
            root = LegacyFieldAdapter.fromWriteFields(fields);
            if (mode == PayloadMode.STRUCT) {
                List<ValueOption> valueOptions = properties.listWriteValueOptions(function.getId());
                mode = LegacyFieldAdapter.payloadModeFromWriteAccess(function.getWriteAccessType(), valueOptions);
                if (mode == PayloadMode.VALUE && mappings.isEmpty()) {
                    mappings = LegacyFieldAdapter.fromWriteValueOptions(valueOptions, guessTargetField(fields));
                }
            }
        }

        FunctionRoute route = new FunctionRoute(function.getPublishTopicSlot(), function.getSubscribeTopicSlot());
        return new Definition(mode, root, mappings, route);
    }

    public static void syncFromLegacyFields(
            ProductFunctionEntity entity,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> writeValueOptions) {
        boolean isRead = "READ".equalsIgnoreCase(entity.getAccessType());
        List<WriteFieldOption> fields = isRead ? readFields : writeFields;
        entity.setStructSchema(PayloadCodec.writeFieldNode(LegacyFieldAdapter.fromWriteFields(fields)));
        PayloadMode mode = LegacyFieldAdapter.payloadModeFromWriteAccess(entity.getWriteAccessType(), writeValueOptions);
        entity.setPayloadMode(mode.wire());
        if (mode == PayloadMode.VALUE) {
            entity.setValueMappings(PayloadCodec.writeMappings(
                    LegacyFieldAdapter.fromWriteValueOptions(writeValueOptions, guessTargetField(fields))));
        } else {
            entity.setValueMappings(null);
        }
    }

    /** 请求体携带 structSchema / valueMappings / payloadMode 时直接落库。 */
    public static void applyPayloadFromRequest(ProductFunctionEntity entity, ProductFunctionWriteRequest request) {
        if (request == null) {
            return;
        }
        if (request.payloadMode() != null && !request.payloadMode().isBlank()) {
            PayloadMode mode = PayloadMode.from(request.payloadMode());
            entity.setPayloadMode(mode.wire());
            entity.setWriteAccessType(mode == PayloadMode.VALUE ? "VALUE" : "STRUCT");
        }
        if (request.structSchema() != null) {
            entity.setStructSchema(PayloadCodec.writeFieldNode(request.structSchema()));
        }
        if (request.valueMappings() != null) {
            entity.setValueMappings(request.valueMappings().isEmpty()
                    ? null
                    : PayloadCodec.writeMappings(request.valueMappings()));
        }
    }

    public static boolean hasDirectPayload(ProductFunctionWriteRequest request) {
        if (request == null) {
            return false;
        }
        return request.structSchema() != null
                || request.valueMappings() != null
                || (request.payloadMode() != null && !request.payloadMode().isBlank());
    }

    public static Map<String, Object> assemble(
            Definition definition,
            Map<String, Object> caller,
            Map<String, Object> deviceFieldOverrides) {
        Map<String, Object> normalizedCaller = normalizeCaller(definition.payloadMode(), caller);
        return CommandAssembler.assemble(new CommandAssembler.Request(
                definition.payloadMode(),
                definition.structRoot(),
                definition.valueMappings(),
                normalizedCaller,
                deviceFieldOverrides));
    }

    private static Map<String, Object> normalizeCaller(PayloadMode mode, Map<String, Object> caller) {
        if (caller == null || caller.isEmpty()) {
            return Map.of();
        }
        if (mode == PayloadMode.VALUE && !caller.containsKey(CommandAssembler.CALLER_VALUE_KEY) && caller.size() == 1) {
            Object only = caller.values().iterator().next();
            return Map.of(CommandAssembler.CALLER_VALUE_KEY, only);
        }
        return caller;
    }

    private static String guessTargetField(List<WriteFieldOption> fields) {
        if (fields == null || fields.size() != 1) {
            return "command";
        }
        return fields.get(0).field();
    }
}
