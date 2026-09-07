package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogFunctionBindingCallerSchemaTest {

    @Test
    void callerFieldsExposeCallerFieldNameToInvokeForm() {
        List<SchemaField> schema = CatalogFunctionSchemas.schemaFromWriteFields(List.of(
                WriteFieldOption.builder("params.0")
                        .description("动作")
                        .ignoreRequest(true)
                        .source("mapped")
                        .callerField("action")
                        .build(),
                WriteFieldOption.builder("params.2")
                        .description("密码")
                        .source("caller")
                        .callerField("password")
                        .build(),
                WriteFieldOption.builder("seq")
                        .description("序列")
                        .ignoreRequest(true)
                        .valueGenerator("random_alnum_32")
                        .source("platform")
                        .build()),
                true);
        assertEquals(List.of("action", "password"), schema.stream().map(SchemaField::name).toList());
        SchemaField password = schema.stream().filter(field -> "password".equals(field.name())).findFirst().orElseThrow();
        assertTrue(password.required());
        assertEquals("密码", password.description());
    }

    @Test
    void valueModeDoesNotPersistMappingOptionsOnValueField() {
        FunctionTemplate template = FunctionTemplate.of("fn.write", "WRITE", List.of(
                SchemaField.choice("area", "寄存器区", true, "HOLDING", List.of("HOLDING", "COIL")),
                SchemaField.required("value", FieldType.STRING, "写入值")));
        List<WriteFieldOption> requested = List.of(
                WriteFieldOption.builder("area")
                        .description("寄存器区")
                        .accessDataType("select")
                        .transformDataType("select")
                        .ignoreRequest(true)
                        .options(List.of(new ValueOption("HOLDING", "HOLDING", "HOLDING", "string", "string", true)))
                        .source("constant")
                        .constant("HOLDING")
                        .build(),
                WriteFieldOption.builder("value")
                        .description("写入值")
                        .ignoreRequest(true)
                        .options(List.of(
                                new ValueOption("1", "open", "开", "string", "string", false),
                                new ValueOption("0", "close", "关", "string", "string", false)))
                        .source("mapped")
                        .callerField("lock")
                        .build());
        List<WriteFieldOption> bound = CatalogContractFunctionBinding.bindContractFields(
                template, requested, PayloadMode.VALUE);
        WriteFieldOption value = bound.stream()
                .filter(field -> "value".equals(field.field()))
                .findFirst()
                .orElseThrow();
        assertEquals("mapped", value.source());
        assertEquals("lock", value.callerField());
        assertTrue(value.options().isEmpty());
        WriteFieldOption area = bound.stream()
                .filter(field -> "area".equals(field.field()))
                .findFirst()
                .orElseThrow();
        assertTrue(area.options().stream().anyMatch(option -> "HOLDING".equals(option.optionValue())));
        assertTrue(area.options().stream().anyMatch(option -> "COIL".equals(option.optionValue())));
    }

    @Test
    void applyWriteValueOptionChoicesPutsButtonsOnMappedCallerOnly() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("value")
                        .description("写入值")
                        .ignoreRequest(true)
                        .source("mapped")
                        .build(),
                WriteFieldOption.builder("note")
                        .description("备注")
                        .source("caller")
                        .callerField("remark")
                        .build());
        List<SchemaField> schema = CatalogFunctionSchemas.schemaFromWriteFields(fields, true);
        List<SchemaField> withChoices = CatalogFunctionSchemas.applyWriteValueOptionChoices(
                schema,
                fields,
                List.of(
                        new ValueOption("1", "open", "开", "string", "string", false),
                        new ValueOption("0", "close", "关", "string", "string", false)));
        SchemaField value = withChoices.stream()
                .filter(field -> "value".equals(field.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("open", "close"), value.choices());
        SchemaField remark = withChoices.stream()
                .filter(field -> "remark".equals(field.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(remark.choices() == null || remark.choices().isEmpty());
    }
}
