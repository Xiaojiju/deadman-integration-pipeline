package com.mtfm.gateway.catalog.store.support;

import com.mtfm.gateway.catalog.entity.ReadFieldValueOptionEntity;
import com.mtfm.gateway.catalog.entity.ReadValueOptionEntity;
import com.mtfm.gateway.catalog.entity.WriteValueOptionEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.spi.property.ValueOption;

import java.util.function.Consumer;

/** ValueOption 与各类 value_option 表行的互转与写入。 */
public final class ValueOptionSupport {

    private ValueOptionSupport() {
    }

    public static ValueOption toValueOption(WriteValueOptionEntity row) {
        return fromRow(
                row.getOptionValue(),
                row.getMappingValue(),
                row.getDescription(),
                row.getAccessDataType(),
                row.getTransformDataType(),
                row.getIsDefault());
    }

    public static ValueOption toValueOption(ReadValueOptionEntity row) {
        return fromRow(
                row.getOptionValue(),
                row.getMappingValue(),
                row.getDescription(),
                row.getAccessDataType(),
                row.getTransformDataType(),
                row.getIsDefault());
    }

    public static ValueOption toValueOption(ReadFieldValueOptionEntity row) {
        return fromRow(
                row.getOptionValue(),
                row.getMappingValue(),
                row.getDescription(),
                row.getAccessDataType(),
                row.getTransformDataType(),
                row.getIsDefault());
    }

    public static void applyTo(ReadValueOptionEntity row, ValueOption option) {
        applyCommon(row::setDescription, row::setOptionValue, row::setMappingValue,
                row::setAccessDataType, row::setTransformDataType, row::setIsDefault, option);
    }

    public static WriteValueOptionEntity newWriteChild(String parentId, ValueOption option) {
        WriteValueOptionEntity row = new WriteValueOptionEntity();
        row.setId(SnowflakeIds.next());
        row.setParentId(parentId);
        applyTo(row, option);
        return row;
    }

    public static ReadFieldValueOptionEntity newReadFieldChild(String parentId, ValueOption option) {
        ReadFieldValueOptionEntity row = new ReadFieldValueOptionEntity();
        row.setId(SnowflakeIds.next());
        row.setParentId(parentId);
        applyTo(row, option);
        return row;
    }

    public static ReadValueOptionEntity newReadRoot(String productFunctionId, ValueOption option) {
        ReadValueOptionEntity row = new ReadValueOptionEntity();
        row.setId(SnowflakeIds.next());
        row.setProductFunctionId(productFunctionId);
        applyTo(row, option);
        return row;
    }

    private static void applyTo(WriteValueOptionEntity row, ValueOption option) {
        applyCommon(row::setDescription, row::setOptionValue, row::setMappingValue,
                row::setAccessDataType, row::setTransformDataType, row::setIsDefault, option);
    }

    private static void applyTo(ReadFieldValueOptionEntity row, ValueOption option) {
        applyCommon(row::setDescription, row::setOptionValue, row::setMappingValue,
                row::setAccessDataType, row::setTransformDataType, row::setIsDefault, option);
    }

    private static void applyCommon(
            Consumer<String> description,
            Consumer<String> optionValue,
            Consumer<String> mappingValue,
            Consumer<String> accessDataType,
            Consumer<String> transformDataType,
            Consumer<Boolean> isDefault,
            ValueOption option) {
        description.accept(option.description());
        optionValue.accept(option.optionValue());
        mappingValue.accept(option.mappingValue());
        accessDataType.accept(option.accessDataType());
        transformDataType.accept(option.transformDataType());
        isDefault.accept(option.isDefault());
    }

    private static ValueOption fromRow(
            String optionValue,
            String mappingValue,
            String description,
            String accessDataType,
            String transformDataType,
            Boolean isDefault) {
        return new ValueOption(
                optionValue,
                mappingValue,
                description,
                accessDataType,
                transformDataType,
                isDefault);
    }
}
