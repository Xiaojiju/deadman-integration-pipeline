package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogFormServiceImportTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogPropertyRepository properties;
    @Mock
    private CapabilityRegistrar registrar;

    @InjectMocks
    private CatalogFormService forms;

    @Test
    void fixedImportWritesParametersIntoWriteFieldsWithChoices() {
        when(store.findProduct("p1")).thenReturn(Optional.of(new com.mtfm.gateway.catalog.entity.ProductEntity()));
        when(store.findFunction("p1", "remoteControlDoor")).thenReturn(Optional.empty());
        when(store.properties()).thenReturn(properties);
        when(store.saveFunction(any())).thenAnswer(invocation -> {
            ProductFunctionEntity entity = invocation.getArgument(0);
            entity.setId("pf-1");
            return entity;
        });

        FunctionTemplate template = new FunctionTemplate(
                "remoteControlDoor",
                "远程控门",
                "WRITE",
                2,
                List.of(SchemaField.choice("command", "控门指令", true, "open", List.of("open", "close"))));
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                "HIKVISION",
                List.of(),
                List.of(),
                List.of(template),
                FunctionCatalogMode.FIXED);
        when(registrar.find("HIKVISION")).thenReturn(Optional.of(descriptor));

        List<ProductFunctionEntity> imported = forms.importCapabilityFunctions("p1", "HIKVISION");
        assertEquals(1, imported.size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteFieldOption>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceWriteOptions(
                eq("pf-1"),
                eq(ValueAccessType.STRUCT),
                any(),
                fieldsCaptor.capture());
        List<WriteFieldOption> fields = fieldsCaptor.getValue();
        assertEquals(1, fields.size());
        assertEquals("command", fields.get(0).field());
        assertEquals("控门指令", fields.get(0).description());
        List<ValueOption> options = fields.get(0).options();
        assertEquals(2, options.size());
        assertTrue(options.stream().anyMatch(item -> "open".equals(item.optionValue()) && item.isDefault()));
        assertTrue(options.stream().anyMatch(item -> "close".equals(item.optionValue())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PropertyItem>> propsCaptor = ArgumentCaptor.forClass(List.class);
        verify(properties).replaceFunctionProperties(eq("pf-1"), propsCaptor.capture());
        assertTrue(propsCaptor.getValue().stream().anyMatch(item -> "command".equals(item.attribute())));
    }
}
