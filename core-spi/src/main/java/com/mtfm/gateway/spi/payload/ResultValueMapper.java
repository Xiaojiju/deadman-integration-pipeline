package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主动读回执：把设备原值按 readValueOptions / 换算翻成业务值。
 */
public final class ResultValueMapper {

    private ResultValueMapper() {
    }

    public static ExecutionResult apply(FunctionDef def, ExecutionResult result) {
        if (def == null || result == null || result.data() == null || result.data().isEmpty()) {
            return result;
        }
        Map<String, Object> data = new LinkedHashMap<>(result.data().values());
        Object values = data.get("values");
        if (values instanceof List<?> list) {
            List<Object> mapped = new ArrayList<>(list.size());
            for (Object item : list) {
                mapped.add(inbound(item, def));
            }
            data.put("values", List.copyOf(mapped));
            if (mapped.size() == 1) {
                data.putIfAbsent("value", mapped.getFirst());
            }
            return new ExecutionResult(
                    result.requestId(),
                    result.deviceId(),
                    result.functionId(),
                    result.status(),
                    com.mtfm.gateway.spi.model.Attributes.from(data),
                    result.failure());
        }
        Object scalar = data.get("value");
        if (scalar != null) {
            data.put("value", inbound(scalar, def));
            return new ExecutionResult(
                    result.requestId(),
                    result.deviceId(),
                    result.functionId(),
                    result.status(),
                    com.mtfm.gateway.spi.model.Attributes.from(data),
                    result.failure());
        }
        return result;
    }

    static Object inbound(Object raw, FunctionDef def) {
        WriteFieldOption valueField = valueField(def);
        List<ValueOption> options = valueField != null && valueField.options() != null && !valueField.options().isEmpty()
                ? valueField.options()
                : def.readValueOptions();
        Object mapped = PayloadDisassembler.mapInboundValue(raw, options);
        if (mapped != raw && mapped != null && !String.valueOf(mapped).equals(String.valueOf(raw))) {
            return mapped;
        }
        String op = valueField != null ? valueField.scaleOp() : null;
        String operand = valueField != null ? valueField.scaleOperand() : null;
        if (!ScaleTransform.configured(op, operand)) {
            op = def.scaleOp();
            operand = def.scaleOperand();
        }
        if (ScaleTransform.configured(op, operand) && mapped == raw) {
            return ScaleTransform.inbound(raw, op, operand);
        }
        return mapped;
    }

    private static WriteFieldOption valueField(FunctionDef def) {
        for (WriteFieldOption field : def.readFields()) {
            if (field != null && "value".equals(field.field())) {
                return field;
            }
        }
        for (WriteFieldOption field : def.writeFields()) {
            if (field != null && "value".equals(field.field())) {
                return field;
            }
        }
        return null;
    }
}
