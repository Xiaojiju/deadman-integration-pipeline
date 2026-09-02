package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.ReadFieldEntity;
import com.mtfm.gateway.catalog.entity.ReadFieldValueOptionEntity;
import com.mtfm.gateway.catalog.entity.ReadValueOptionEntity;
import com.mtfm.gateway.catalog.entity.WriteOptionEntity;
import com.mtfm.gateway.catalog.entity.WriteValueOptionEntity;
import com.mtfm.gateway.catalog.mapper.ReadFieldMapper;
import com.mtfm.gateway.catalog.mapper.ReadFieldValueOptionMapper;
import com.mtfm.gateway.catalog.mapper.ReadValueOptionMapper;
import com.mtfm.gateway.catalog.mapper.WriteOptionMapper;
import com.mtfm.gateway.catalog.mapper.WriteValueOptionMapper;
import com.mtfm.gateway.catalog.store.support.FieldOptionSupport;
import com.mtfm.gateway.catalog.store.support.ValueOptionSupport;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 产品功能的读写 Option 树（write/read fields + value options）。
 *
 * <p>五张表的处理模式统一：字段行 + 子 value_option 行，或功能级 value_option 列表。
 */
@Repository
public class CatalogFunctionOptionRepository {

    private final WriteOptionMapper writeOptions;
    private final WriteValueOptionMapper writeValueOptions;
    private final ReadValueOptionMapper readValueOptions;
    private final ReadFieldMapper readFields;
    private final ReadFieldValueOptionMapper readFieldValueOptions;

    public CatalogFunctionOptionRepository(
            WriteOptionMapper writeOptions,
            WriteValueOptionMapper writeValueOptions,
            ReadValueOptionMapper readValueOptions,
            ReadFieldMapper readFields,
            ReadFieldValueOptionMapper readFieldValueOptions) {
        this.writeOptions = writeOptions;
        this.writeValueOptions = writeValueOptions;
        this.readValueOptions = readValueOptions;
        this.readFields = readFields;
        this.readFieldValueOptions = readFieldValueOptions;
    }

    public List<ValueOption> listWriteValueOptions(String productFunctionId) {
        return listWriteValueOptionsByParent(productFunctionId);
    }

    public List<WriteFieldOption> listWriteFields(String productFunctionId) {
        List<WriteOptionEntity> fields = writeOptions.selectList(new QueryWrapper<WriteOptionEntity>()
                .eq("product_function_id", productFunctionId)
                .orderByAsc("field"));
        List<WriteFieldOption> result = new ArrayList<>();
        for (WriteOptionEntity field : fields) {
            List<ValueOption> options = listWriteValueOptionsByParent(field.getId());
            result.add(FieldOptionSupport.from(field, options));
        }
        return List.copyOf(result);
    }

    public List<WriteFieldOption> listReadFields(String productFunctionId) {
        List<ReadFieldEntity> fields = readFields.selectList(new QueryWrapper<ReadFieldEntity>()
                .eq("product_function_id", productFunctionId)
                .orderByAsc("field"));
        List<WriteFieldOption> result = new ArrayList<>();
        for (ReadFieldEntity field : fields) {
            List<ValueOption> options = listReadFieldValueOptionsByParent(field.getId());
            result.add(FieldOptionSupport.from(field, options));
        }
        return List.copyOf(result);
    }

    public List<ValueOption> listReadValueOptions(String productFunctionId) {
        return readValueOptions.selectList(new QueryWrapper<ReadValueOptionEntity>()
                        .eq("product_function_id", productFunctionId)
                        .orderByAsc("option_value"))
                .stream()
                .map(ValueOptionSupport::toValueOption)
                .toList();
    }

    public void replaceWriteOptions(
            String productFunctionId,
            ValueAccessType accessType,
            List<ValueOption> valueOptions,
            List<WriteFieldOption> writeFields) {
        deleteWriteOptions(productFunctionId);
        ValueAccessType mode = accessType == null ? ValueAccessType.VALUE : accessType;
        if (mode == ValueAccessType.STRUCT) {
            if (writeFields == null) {
                return;
            }
            for (WriteFieldOption field : writeFields) {
                WriteOptionEntity row = FieldOptionSupport.newWriteField(productFunctionId, field);
                writeOptions.insert(row);
                insertWriteValueOptions(row.getId(), field.options());
            }
            return;
        }
        insertWriteValueOptions(productFunctionId, valueOptions);
    }

    public void replaceReadFields(String productFunctionId, List<WriteFieldOption> readFieldsList) {
        deleteReadFields(productFunctionId);
        if (readFieldsList == null || readFieldsList.isEmpty()) {
            return;
        }
        for (WriteFieldOption field : readFieldsList) {
            ReadFieldEntity row = FieldOptionSupport.newReadField(productFunctionId, field);
            readFields.insert(row);
            insertReadFieldValueOptions(row.getId(), field.options());
        }
    }

    public void replaceReadValueOptions(String productFunctionId, List<ValueOption> options) {
        readValueOptions.delete(new QueryWrapper<ReadValueOptionEntity>()
                .eq("product_function_id", productFunctionId));
        if (options == null || options.isEmpty()) {
            return;
        }
        for (ValueOption option : options) {
            readValueOptions.insert(ValueOptionSupport.newReadRoot(productFunctionId, option));
        }
    }

    public void deleteWriteOptions(String productFunctionId) {
        List<WriteOptionEntity> fields = writeOptions.selectList(new QueryWrapper<WriteOptionEntity>()
                .eq("product_function_id", productFunctionId));
        for (WriteOptionEntity field : fields) {
            writeValueOptions.delete(new QueryWrapper<WriteValueOptionEntity>().eq("parent_id", field.getId()));
        }
        writeOptions.delete(new QueryWrapper<WriteOptionEntity>().eq("product_function_id", productFunctionId));
        writeValueOptions.delete(new QueryWrapper<WriteValueOptionEntity>().eq("parent_id", productFunctionId));
    }

    public void deleteReadValueOptions(String productFunctionId) {
        readValueOptions.delete(new QueryWrapper<ReadValueOptionEntity>()
                .eq("product_function_id", productFunctionId));
    }

    public void deleteReadFields(String productFunctionId) {
        List<ReadFieldEntity> fields = readFields.selectList(new QueryWrapper<ReadFieldEntity>()
                .eq("product_function_id", productFunctionId));
        for (ReadFieldEntity field : fields) {
            readFieldValueOptions.delete(new QueryWrapper<ReadFieldValueOptionEntity>()
                    .eq("parent_id", field.getId()));
        }
        readFields.delete(new QueryWrapper<ReadFieldEntity>().eq("product_function_id", productFunctionId));
    }

    public void deleteAll(String productFunctionId) {
        deleteWriteOptions(productFunctionId);
        deleteReadFields(productFunctionId);
        deleteReadValueOptions(productFunctionId);
    }

    private List<ValueOption> listWriteValueOptionsByParent(String parentId) {
        return writeValueOptions.selectList(new QueryWrapper<WriteValueOptionEntity>()
                        .eq("parent_id", parentId)
                        .orderByAsc("option_value"))
                .stream()
                .map(ValueOptionSupport::toValueOption)
                .toList();
    }

    private List<ValueOption> listReadFieldValueOptionsByParent(String parentId) {
        return readFieldValueOptions.selectList(new QueryWrapper<ReadFieldValueOptionEntity>()
                        .eq("parent_id", parentId)
                        .orderByAsc("option_value"))
                .stream()
                .map(ValueOptionSupport::toValueOption)
                .toList();
    }

    private void insertWriteValueOptions(String parentId, List<ValueOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        for (ValueOption option : options) {
            writeValueOptions.insert(ValueOptionSupport.newWriteChild(parentId, option));
        }
    }

    private void insertReadFieldValueOptions(String parentId, List<ValueOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        for (ValueOption option : options) {
            readFieldValueOptions.insert(ValueOptionSupport.newReadFieldChild(parentId, option));
        }
    }
}
