package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultValueMapperTest {

    @Test
    void mapsDeviceEnumToBusinessValue() {
        FunctionDef def = readDef(
                List.of(new ValueOption("2", "cool", "制冷", "string", "string", false)),
                null,
                null);
        ExecutionResult mapped = ResultValueMapper.apply(
                def, ExecutionResult.success("r1", "dev-1", "mode.read", Map.of("values", List.of(2))));
        assertEquals("cool", ((List<?>) mapped.data().values().get("values")).getFirst());
        assertEquals("cool", mapped.data().values().get("value"));
    }

    @Test
    void scalesRegisterToBusinessTemperature() {
        FunctionDef def = readDef(List.of(), "divide", "10");
        ExecutionResult mapped = ResultValueMapper.apply(
                def, ExecutionResult.success("r1", "dev-1", "temp.read", Map.of("values", List.of(238))));
        assertEquals(23.8, ((Number) ((List<?>) mapped.data().values().get("values")).getFirst()).doubleValue(), 0.0001);
    }

    @Test
    void mappingWinsOverScale() {
        FunctionDef def = readDef(
                List.of(new ValueOption("2", "cool", "制冷", "string", "string", false)),
                "divide",
                "10");
        ExecutionResult mapped = ResultValueMapper.apply(
                def, ExecutionResult.success("r1", "dev-1", "mode.read", Map.of("value", 2)));
        assertEquals("cool", mapped.data().values().get("value"));
    }

    private static FunctionDef readDef(List<ValueOption> readOptions, String scaleOp, String scaleOperand) {
        return FunctionDef.builder("fn.read")
                .accessType("READ")
                .accessPermission(1)
                .writeAccessType(ValueAccessType.STRUCT)
                .readValueOptions(readOptions)
                .scale(FunctionDef.ScaleSpec.of(scaleOp, scaleOperand))
                .build();
    }
}
