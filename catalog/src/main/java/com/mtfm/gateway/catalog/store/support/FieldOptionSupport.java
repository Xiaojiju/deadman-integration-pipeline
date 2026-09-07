package com.mtfm.gateway.catalog.store.support;

import com.mtfm.gateway.catalog.entity.ReadFieldEntity;
import com.mtfm.gateway.catalog.entity.WriteOptionEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;

/** 功能字段（write_option / read_field）与 {@link WriteFieldOption} 互转。 */
public final class FieldOptionSupport {

    private FieldOptionSupport() {
    }

    public static WriteFieldOption toFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            Boolean ignoreRequest,
            String format,
            String valueGenerator,
            String fieldSource,
            String constantValue,
            String callerField,
            Integer byteLength,
            String byteOrder,
            String scaleOp,
            String scaleOperand,
            List<ValueOption> options) {
        return new WriteFieldOption(
                field,
                description,
                accessDataType,
                transformDataType,
                Boolean.TRUE.equals(ignoreRequest),
                options,
                format,
                valueGenerator,
                fieldSource,
                constantValue,
                callerField,
                byteLength,
                byteOrder,
                scaleOp,
                scaleOperand);
    }

    public static WriteFieldOption from(WriteOptionEntity field, List<ValueOption> options) {
        return toFieldOption(
                field.getField(),
                field.getDescription(),
                field.getAccessDataType(),
                field.getTransformDataType(),
                field.getIgnoreRequest(),
                field.getFormat(),
                field.getValueGenerator(),
                field.getFieldSource(),
                field.getConstantValue(),
                field.getCallerField(),
                field.getByteLength(),
                field.getByteOrder(),
                field.getScaleOp(),
                field.getScaleOperand(),
                options);
    }

    public static WriteFieldOption from(ReadFieldEntity field, List<ValueOption> options) {
        return toFieldOption(
                field.getField(),
                field.getDescription(),
                field.getAccessDataType(),
                field.getTransformDataType(),
                field.getIgnoreRequest(),
                field.getFormat(),
                field.getValueGenerator(),
                field.getFieldSource(),
                field.getConstantValue(),
                field.getCallerField(),
                field.getByteLength(),
                field.getByteOrder(),
                field.getScaleOp(),
                field.getScaleOperand(),
                options);
    }

    public static WriteOptionEntity newWriteField(String productFunctionId, WriteFieldOption field) {
        WriteOptionEntity row = new WriteOptionEntity();
        row.setId(SnowflakeIds.next());
        row.setProductFunctionId(productFunctionId);
        applyWriteField(row, field);
        return row;
    }

    public static ReadFieldEntity newReadField(String productFunctionId, WriteFieldOption field) {
        ReadFieldEntity row = new ReadFieldEntity();
        row.setId(SnowflakeIds.next());
        row.setProductFunctionId(productFunctionId);
        applyReadField(row, field);
        return row;
    }

    private static void applyWriteField(WriteOptionEntity row, WriteFieldOption field) {
        row.setField(field.field());
        row.setDescription(field.description());
        row.setAccessDataType(field.accessDataType());
        row.setTransformDataType(field.transformDataType());
        row.setIgnoreRequest(field.ignoreRequest());
        row.setFormat(field.format());
        row.setValueGenerator(field.valueGenerator());
        row.setFieldSource(field.source());
        row.setConstantValue(field.constant());
        row.setCallerField(field.callerField());
        row.setByteLength(field.byteLength());
        row.setByteOrder(field.byteOrder());
        row.setScaleOp(field.scaleOp());
        row.setScaleOperand(field.scaleOperand());
    }

    private static void applyReadField(ReadFieldEntity row, WriteFieldOption field) {
        row.setField(field.field());
        row.setDescription(field.description());
        row.setAccessDataType(field.accessDataType());
        row.setTransformDataType(field.transformDataType());
        row.setIgnoreRequest(field.ignoreRequest());
        row.setFormat(field.format());
        row.setValueGenerator(field.valueGenerator());
        row.setFieldSource(field.source());
        row.setConstantValue(field.constant());
        row.setCallerField(field.callerField());
        row.setByteLength(field.byteLength());
        row.setByteOrder(field.byteOrder());
        row.setScaleOp(field.scaleOp());
        row.setScaleOperand(field.scaleOperand());
    }
}
