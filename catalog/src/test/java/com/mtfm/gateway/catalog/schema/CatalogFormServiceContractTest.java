package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogFormServiceContractTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogPropertyRepository properties;
    @Mock
    private CapabilityRegistrar registrar;

    @InjectMocks
    private CatalogFormService forms;

    @Test
    void customFunctionIdSeedsContractConstantsAndMapsValue() {
        stubCreate();

        forms.createFunction("p1", request("light.switch", "WRITE", "VALUE", null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteFieldOption>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceWriteOptions(
                eq("pf-1"),
                eq(ValueAccessType.VALUE),
                any(),
                fieldsCaptor.capture());
        List<WriteFieldOption> fields = fieldsCaptor.getValue();
        assertEquals(List.of("area", "offset", "value", "dataType"),
                fields.stream().map(f -> f.field()).toList());
        WriteFieldOption area = field(fields, "area");
        assertEquals("constant", area.source());
        assertEquals("HOLDING", area.constant());
        WriteFieldOption offset = field(fields, "offset");
        assertEquals("constant", offset.source());
        assertEquals("0", offset.constant());
        WriteFieldOption value = field(fields, "value");
        assertEquals("mapped", value.source());
        assertEquals("value", value.callerField());
    }

    @Test
    void extraFieldsAreRejected() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction("p1", "light.switch")).thenReturn(Optional.empty());
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> forms.createFunction("p1", request("light.switch", "WRITE", "VALUE",
                        List.of(new WriteFieldOption("topic", "x", "string", "string", false, List.of(), "none")),
                        null)));
        assertTrue(ex.getMessage().contains("不允许自定义字段"));
    }

    @Test
    void mappedIsOnlyAllowedOnValue() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction("p1", "light.switch")).thenReturn(Optional.empty());
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> forms.createFunction("p1", request("light.switch", "WRITE", "VALUE",
                        List.of(new WriteFieldOption(
                                "offset", "起始地址", "int", "int", true, List.of(), "none",
                                null, "mapped", null, "value")),
                        null)));
        assertTrue(ex.getMessage().contains("不允许 mapped"));
    }

    @Test
    void importSeedsReadAndWriteContracts() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction(eq("p1"), any())).thenReturn(Optional.empty());
        when(store.properties()).thenReturn(properties);
        when(store.saveFunction(any())).thenAnswer(invocation -> {
            ProductFunctionEntity entity = invocation.getArgument(0);
            entity.setId("pf-" + entity.getFunctionId());
            return entity;
        });
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        List<ProductFunctionEntity> imported = forms.importCapabilityFunctions("p1", "MODBUS");
        assertEquals(2, imported.size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteFieldOption>> writeCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceWriteOptions(
                eq("pf-fn.write"),
                eq(ValueAccessType.STRUCT),
                any(),
                writeCaptor.capture());
        assertEquals("caller", field(writeCaptor.getValue(), "value").source());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteFieldOption>> readCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceReadFields(eq("pf-fn.read"), readCaptor.capture());
        assertEquals(List.of("area", "offset", "quantity", "dataType"),
                readCaptor.getValue().stream().map(f -> f.field()).toList());
    }

    private void stubCreate() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction(eq("p1"), any())).thenReturn(Optional.empty());
        when(store.properties()).thenReturn(properties);
        when(store.saveFunction(any())).thenAnswer(invocation -> {
            ProductFunctionEntity entity = invocation.getArgument(0);
            entity.setId("pf-1");
            return entity;
        });
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));
    }

    private static WriteFieldOption field(List<WriteFieldOption> fields, String name) {
        return fields.stream().filter(item -> name.equals(item.field())).findFirst().orElseThrow();
    }

    private static ProductFunctionWriteRequest request(
            String functionId,
            String accessType,
            String payloadMode,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields) {
        return new ProductFunctionWriteRequest(
                functionId,
                accessType,
                null,
                "MODBUS",
                payloadMode,
                null,
                null,
                writeFields,
                readFields,
                null,
                0,
                null,
                null,
                null,
                payloadMode,
                null);
    }

    private static CapabilityDescriptor modbusContract() {
        return new CapabilityDescriptor(
                "MODBUS",
                List.of(),
                List.of(),
                List.of(
                        FunctionTemplate.of("fn.read", "READ", List.of(
                                SchemaField.choice("area", "寄存器区", true, "HOLDING",
                                        List.of("HOLDING", "INPUT", "COIL", "DISCRETE")),
                                SchemaField.required("offset", FieldType.INT, "起始地址", 0),
                                SchemaField.optional("quantity", FieldType.INT, "数量", 1),
                                SchemaField.choice("dataType", "数据类型", false, "INT16",
                                        List.of("INT16", "BOOLEAN")))),
                        FunctionTemplate.of("fn.write", "WRITE", List.of(
                                SchemaField.choice("area", "寄存器区", true, "HOLDING",
                                        List.of("HOLDING", "INPUT", "COIL", "DISCRETE")),
                                SchemaField.required("offset", FieldType.INT, "起始地址", 0),
                                SchemaField.required("value", FieldType.STRING, "写入值"),
                                SchemaField.choice("dataType", "数据类型", false, "INT16",
                                        List.of("INT16", "BOOLEAN"))))),
                FunctionCatalogMode.CONTRACT);
    }
}
