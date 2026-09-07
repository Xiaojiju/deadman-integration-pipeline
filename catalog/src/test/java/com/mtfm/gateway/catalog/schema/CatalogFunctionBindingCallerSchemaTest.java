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
        List<SchemaField> schema = CatalogFunctionBinding.schemaFromWriteFields(List.of(
                new WriteFieldOption(
                        "params.0", "动作", "string", "string", true,
                        List.of(), "none", null, "mapped", null, "action"),
                new WriteFieldOption(
                        "params.2", "密码", "string", "string", false,
                        List.of(), "none", null, "caller", null, "password"),
                new WriteFieldOption(
                        "seq", "序列", "string", "string", true,
                        List.of(), "none", "random_alnum_32", "platform", null)),
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
                new WriteFieldOption(
                        "area",
                        "寄存器区",
                        "select",
                        "select",
                        true,
                        List.of(new ValueOption("HOLDING", "HOLDING", "HOLDING", "string", "string", true)),
                        "none",
                        null,
                        "constant",
                        "HOLDING"),
                new WriteFieldOption(
                        "value",
                        "写入值",
                        "string",
                        "string",
                        true,
                        List.of(
                                new ValueOption("1", "open", "开", "string", "string", false),
                                new ValueOption("0", "close", "关", "string", "string", false)),
                        "none",
                        null,
                        "mapped",
                        null,
                        "lock"));
        List<WriteFieldOption> bound = CatalogFunctionBinding.bindContractFields(
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
        List<SchemaField> schema = CatalogFunctionBinding.schemaFromWriteFields(List.of(
                new WriteFieldOption(
                        "value", "写入值", "string", "string", true,
                        List.of(), "none", null, "mapped", null),
                new WriteFieldOption(
                        "note", "备注", "string", "string", false,
                        List.of(), "none", null, "caller", null, "remark")),
                true);
        List<SchemaField> withChoices = CatalogFunctionBinding.applyWriteValueOptionChoices(
                schema,
                List.of(
                        new WriteFieldOption(
                                "value", "写入值", "string", "string", true,
                                List.of(), "none", null, "mapped", null),
                        new WriteFieldOption(
                                "note", "备注", "string", "string", false,
                                List.of(), "none", null, "caller", null, "remark")),
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
