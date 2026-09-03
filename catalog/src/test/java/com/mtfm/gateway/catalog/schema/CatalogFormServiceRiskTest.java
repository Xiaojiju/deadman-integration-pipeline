package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ChannelView;
import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.dto.SupportedFunctionView;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.catalog.store.FunctionOptionBundle;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogFormServiceRiskTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogPropertyRepository properties;
    @Mock
    private CapabilityRegistrar registrar;

    @InjectMocks
    private CatalogFormService forms;

    @Test
    void createProductLooksUpCodeInsteadOfListingAll() {
        when(store.findProductByCode("door")).thenReturn(Optional.empty());
        when(store.saveProduct(any())).thenAnswer(invocation -> invocation.getArgument(0));

        forms.createProduct(new ProductWriteRequest("door", "门禁", null, null));

        verify(store).findProductByCode("door");
        verify(store, never()).listProducts();
    }

    @Test
    void updateFunctionRejectsAccessTypeChangeWithoutFields() {
        ProductFunctionEntity entity = function("pf-1", "light.switch", "WRITE");
        when(store.findFunction("p1", "light.switch")).thenReturn(Optional.of(entity));
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> forms.updateFunction("p1", "light.switch", accessOnly("READ")));
        assertTrue(ex.getMessage().contains("accessType"));
    }

    @Test
    void updateFunctionWriteToReadRebindsReadFields() {
        ProductFunctionEntity entity = function("pf-1", "light.switch", "WRITE");
        when(store.findFunction("p1", "light.switch")).thenReturn(Optional.of(entity));
        when(store.properties()).thenReturn(properties);
        when(store.updateFunction(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        forms.updateFunction("p1", "light.switch", accessWithReadFields("READ"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteFieldOption>> readCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceReadFields(eq("pf-1"), readCaptor.capture());
        assertEquals(List.of("area", "offset", "quantity", "dataType"),
                readCaptor.getValue().stream().map(f -> f.field()).toList());
        verify(properties).replaceWriteOptions(eq("pf-1"), eq(ValueAccessType.STRUCT), eq(List.of()), eq(List.of()));
        assertEquals("READ", entity.getAccessType());
    }

    @Test
    void contractValueOptionsMayBeCustomWhenValueHasNoChoices() {
        stubCreate();
        forms.createFunction("p1",
                writeValueOptions(List.of(new ValueOption("open", "lock", "开锁", "string", "string", false))));
        verify(properties).replaceWriteOptions(eq("pf-1"), eq(ValueAccessType.VALUE), any(), any());
    }

    @Test
    void contractValueOptionsMustMatchValueChoicesWhenPresent() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction("p1", "light.switch")).thenReturn(Optional.empty());
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusWithValueChoices()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> forms.createFunction("p1", writeValueOptions(List.of(ValueOption.of("maybe", "非法")))));
        assertTrue(ex.getMessage().contains("取值非法"));
    }

    @Test
    void extraConnectionFieldIsRejected() {
        when(store.findChannel("ch-1")).thenReturn(Optional.empty());
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> forms.createChannel(new ChannelWriteRequest(
                        "ch-1",
                        "MODBUS",
                        List.of(
                                PropertyItem.of("host", "127.0.0.1"),
                                PropertyItem.of("extra", "nope")),
                        null,
                        true)));
        assertTrue(ex.getMessage().contains("未声明字段"));
    }

    @Test
    void modbusPortOutOfRangeIsRejected() {
        when(store.findChannel("ch-1")).thenReturn(Optional.empty());
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> forms.createChannel(new ChannelWriteRequest(
                        "ch-1",
                        "MODBUS",
                        List.of(
                                PropertyItem.of("host", "127.0.0.1"),
                                PropertyItem.of("port", 70000, "int", "")),
                        null,
                        true)));
        assertTrue(ex.getMessage().contains("port"));
    }

    @Test
    void supportedFunctionDoesNotFlattenMultipleChoiceFields() {
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        SupportedFunctionView view = forms.supportedFunction("MODBUS", "fn.write");
        assertTrue(view.writeValueOptions().isEmpty());
        assertTrue(view.choiceOptions().containsKey("area"));
        assertTrue(view.choiceOptions().containsKey("dataType"));
    }

    @Test
    void importRefreshesExistingContractFields() {
        ProductFunctionEntity existing = function("pf-write", "fn.write", "WRITE");
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction("p1", "fn.read")).thenReturn(Optional.empty());
        when(store.findFunction("p1", "fn.write")).thenReturn(Optional.of(existing));
        when(store.properties()).thenReturn(properties);
        when(store.saveFunction(any())).thenAnswer(invocation -> {
            ProductFunctionEntity entity = invocation.getArgument(0);
            entity.setId("pf-" + entity.getFunctionId());
            return entity;
        });
        when(properties.listWriteFields("pf-write")).thenReturn(List.of(
                new WriteFieldOption("area", "寄存器区", "string", "string", true, List.of(), "none",
                        null, "constant", "HOLDING", null)));
        when(properties.listWriteValueOptions("pf-write")).thenReturn(List.of());
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));

        List<ProductFunctionEntity> imported = forms.importCapabilityFunctions("p1", "MODBUS");
        assertEquals(2, imported.size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteFieldOption>> writeCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceWriteOptions(
                eq("pf-write"),
                eq(ValueAccessType.STRUCT),
                any(),
                writeCaptor.capture());
        assertEquals(List.of("area", "offset", "value", "dataType"),
                writeCaptor.getValue().stream().map(f -> f.field()).toList());
        assertEquals("HOLDING", field(writeCaptor.getValue(), "area").constant());
    }

    @Test
    void listProductFunctionViewsLoadsOptionsOnce() {
        ProductFunctionEntity first = function("pf-1", "fn.read", "READ");
        ProductFunctionEntity second = function("pf-2", "fn.write", "WRITE");
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.listFunctions("p1")).thenReturn(List.of(first, second));
        when(store.loadFunctionProperties(List.of(first, second))).thenReturn(Map.of(
                "pf-1", List.of(),
                "pf-2", List.of()));
        when(store.properties()).thenReturn(properties);
        when(properties.loadFunctionOptions(List.of("pf-1", "pf-2"))).thenReturn(Map.of(
                "pf-1", FunctionOptionBundle.empty(),
                "pf-2", FunctionOptionBundle.empty()));

        assertEquals(2, forms.listProductFunctionViews("p1").size());
        verify(properties).loadFunctionOptions(List.of("pf-1", "pf-2"));
        verify(properties, never()).listWriteFields(any());
    }

    @Test
    void channelViewRedactsPassword() {
        ChannelEntity channel = new ChannelEntity();
        channel.setId("ch-1");
        channel.setCode("mqtt-1");
        channel.setCapabilityType("MQTT");
        channel.setEnabled(true);
        when(store.loadChannelProperties(channel)).thenReturn(List.of(
                PropertyItem.of("host", "broker"),
                new PropertyItem("password", "secret", "password", "密码")));
        when(registrar.find("MQTT")).thenReturn(Optional.of(mqttDescriptor()));

        ChannelView view = forms.toChannelView(channel);
        assertEquals("broker", view.properties().stream()
                .filter(item -> "host".equals(item.attribute()))
                .findFirst()
                .orElseThrow()
                .attributeValue());
        assertEquals(CatalogFormService.SECRET_MASK, view.properties().stream()
                .filter(item -> "password".equals(item.attribute()))
                .findFirst()
                .orElseThrow()
                .attributeValue());
    }

    private void stubCreate() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new ProductEntity()));
        when(store.findFunction("p1", "light.switch")).thenReturn(Optional.empty());
        when(store.properties()).thenReturn(properties);
        when(store.saveFunction(any())).thenAnswer(invocation -> {
            ProductFunctionEntity entity = invocation.getArgument(0);
            entity.setId("pf-1");
            return entity;
        });
        when(registrar.find("MODBUS")).thenReturn(Optional.of(modbusContract()));
    }

    private static ProductFunctionEntity function(String id, String functionId, String accessType) {
        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setId(id);
        entity.setProductId("p1");
        entity.setFunctionId(functionId);
        entity.setAccessType(accessType);
        entity.setCapabilityType("MODBUS");
        entity.setWriteAccessType(ValueAccessType.STRUCT.name());
        return entity;
    }

    private static ProductFunctionWriteRequest accessOnly(String accessType) {
        return new ProductFunctionWriteRequest(
                "light.switch", accessType, null, "MODBUS",
                null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static ProductFunctionWriteRequest accessWithReadFields(String accessType) {
        return new ProductFunctionWriteRequest(
                "light.switch", accessType, null, "MODBUS",
                null, null, null, null, List.of(), null,
                null, null, null, null, null, null);
    }

    private static ProductFunctionWriteRequest writeValueOptions(List<ValueOption> options) {
        return new ProductFunctionWriteRequest(
                "light.switch", "WRITE", null, "MODBUS",
                "VALUE", null, options, null, null, null,
                0, null, null, null, "VALUE", null);
    }

    private static WriteFieldOption field(List<WriteFieldOption> fields, String name) {
        return fields.stream().filter(item -> name.equals(item.field())).findFirst().orElseThrow();
    }

    private static CapabilityDescriptor mqttDescriptor() {
        return new CapabilityDescriptor(
                "MQTT",
                List.of(
                        SchemaField.required("host", FieldType.STRING, "主机"),
                        SchemaField.optionalSecret("password", "密码")),
                List.of());
    }

    private static CapabilityDescriptor modbusContract() {
        return new CapabilityDescriptor(
                "MODBUS",
                List.of(
                        SchemaField.choice("transport", "传输", false, "TCP", List.of("TCP", "RTU")),
                        SchemaField.optional("host", FieldType.STRING, "TCP 主机"),
                        SchemaField.optional("port", FieldType.INT, "TCP 端口", 502),
                        SchemaField.optional("serialPort", FieldType.STRING, "串口"),
                        SchemaField.optional("baudRate", FieldType.INT, "波特率", 9600),
                        SchemaField.optional("dataBits", FieldType.INT, "数据位", 8),
                        SchemaField.choice("parity", "校验", false, "NONE", List.of("NONE", "EVEN", "ODD")),
                        SchemaField.optional("stopBits", FieldType.INT, "停止位", 1)),
                List.of(SchemaField.required("slaveId", FieldType.INT, "从站号")),
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

    private static CapabilityDescriptor modbusWithValueChoices() {
        return new CapabilityDescriptor(
                "MODBUS",
                List.of(),
                List.of(),
                List.of(
                        FunctionTemplate.of("fn.read", "READ", List.of(
                                SchemaField.required("offset", FieldType.INT, "起始地址", 0))),
                        FunctionTemplate.of("fn.write", "WRITE", List.of(
                                SchemaField.choice("value", "写入值", true, "on", List.of("on", "off"))))),
                FunctionCatalogMode.CONTRACT);
    }
}
