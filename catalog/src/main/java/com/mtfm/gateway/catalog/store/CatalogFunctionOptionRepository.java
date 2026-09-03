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
import com.mtfm.gateway.catalog.store.support.BatchMaps;
import com.mtfm.gateway.catalog.store.support.FieldOptionSupport;
import com.mtfm.gateway.catalog.store.support.ValueOptionSupport;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品功能的读写 Option 树（write/read fields + value options）。
 *
 * <p>
 * 五张表的处理模式统一：字段行 + 子 value_option 行，或功能级 value_option 列表。
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
        return listWriteValueOptionsByParents(List.of(productFunctionId))
                .getOrDefault(productFunctionId, List.of());
    }

    public List<WriteFieldOption> listWriteFields(String productFunctionId) {
        return listWriteFieldsByFunctionIds(List.of(productFunctionId))
                .getOrDefault(productFunctionId, List.of());
    }

    public List<WriteFieldOption> listReadFields(String productFunctionId) {
        return listReadFieldsByFunctionIds(List.of(productFunctionId))
                .getOrDefault(productFunctionId, List.of());
    }

    public List<ValueOption> listReadValueOptions(String productFunctionId) {
        return listReadValueOptionsByFunctionIds(List.of(productFunctionId))
                .getOrDefault(productFunctionId, List.of());
    }

    public FunctionOptionBundle loadBundle(String productFunctionId) {
        return loadBundles(List.of(productFunctionId))
                .getOrDefault(productFunctionId, FunctionOptionBundle.empty());
    }

    /**
     * 一次查出多个功能的写/读字段与值选项。字段子选项按 parent_id IN 批量取，不再逐字段查询。
     */
    public Map<String, FunctionOptionBundle> loadBundles(Collection<String> productFunctionIds) {
        if (BatchMaps.isEmpty(productFunctionIds)) {
            return Map.of();
        }
        Map<String, List<WriteFieldOption>> writes = listWriteFieldsByFunctionIds(productFunctionIds);
        Map<String, List<WriteFieldOption>> reads = listReadFieldsByFunctionIds(productFunctionIds);
        Map<String, List<ValueOption>> writeValues = listWriteValueOptionsByParents(productFunctionIds);
        Map<String, List<ValueOption>> readValues = listReadValueOptionsByFunctionIds(productFunctionIds);
        Map<String, FunctionOptionBundle> result = new LinkedHashMap<>();
        for (String id : productFunctionIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            result.put(id, new FunctionOptionBundle(
                    writes.getOrDefault(id, List.of()),
                    reads.getOrDefault(id, List.of()),
                    writeValues.getOrDefault(id, List.of()),
                    readValues.getOrDefault(id, List.of())));
        }
        return Map.copyOf(result);
    }

    public void replaceWriteOptions(
            String productFunctionId,
            ValueAccessType accessType,
            List<ValueOption> valueOptions,
            List<WriteFieldOption> writeFields) {
        deleteWriteOptions(productFunctionId);
        if (writeFields != null) {
            for (int i = 0; i < writeFields.size(); i++) {
                WriteFieldOption field = writeFields.get(i);
                WriteOptionEntity row = FieldOptionSupport.newWriteField(productFunctionId, field);
                row.setSortIndex(i);
                writeOptions.insert(row);
                insertWriteValueOptions(row.getId(), field.options());
            }
        }
        ValueAccessType mode = accessType == null ? ValueAccessType.VALUE : accessType;
        if (mode == ValueAccessType.VALUE) {
            insertWriteValueOptions(productFunctionId, valueOptions);
        }
    }

    public void replaceReadFields(String productFunctionId, List<WriteFieldOption> readFieldsList) {
        deleteReadFields(productFunctionId);
        if (readFieldsList == null || readFieldsList.isEmpty()) {
            return;
        }
        for (int i = 0; i < readFieldsList.size(); i++) {
            WriteFieldOption field = readFieldsList.get(i);
            ReadFieldEntity row = FieldOptionSupport.newReadField(productFunctionId, field);
            row.setSortIndex(i);
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

    private Map<String, List<WriteFieldOption>> listWriteFieldsByFunctionIds(Collection<String> functionIds) {
        Map<String, List<WriteFieldOption>> buckets = BatchMaps.buckets(functionIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<WriteOptionEntity> fields = writeOptions.selectList(new QueryWrapper<WriteOptionEntity>()
                .in("product_function_id", buckets.keySet())
                .orderByAsc("sort_index")
                .orderByAsc("id"));
        List<String> fieldIds = fields.stream().map(f -> f.getId()).toList();
        Map<String, List<ValueOption>> optionsByParent = listWriteValueOptionsByParents(fieldIds);
        for (WriteOptionEntity field : fields) {
            buckets.computeIfAbsent(field.getProductFunctionId(), key -> new ArrayList<>())
                    .add(FieldOptionSupport.from(field, optionsByParent.getOrDefault(field.getId(), List.of())));
        }
        return BatchMaps.freeze(buckets);
    }

    private Map<String, List<WriteFieldOption>> listReadFieldsByFunctionIds(Collection<String> functionIds) {
        Map<String, List<WriteFieldOption>> buckets = BatchMaps.buckets(functionIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<ReadFieldEntity> fields = readFields.selectList(new QueryWrapper<ReadFieldEntity>()
                .in("product_function_id", buckets.keySet())
                .orderByAsc("sort_index")
                .orderByAsc("id"));
        List<String> fieldIds = fields.stream().map(f -> f.getId()).toList();
        Map<String, List<ValueOption>> optionsByParent = listReadFieldValueOptionsByParents(fieldIds);
        for (ReadFieldEntity field : fields) {
            buckets.computeIfAbsent(field.getProductFunctionId(), key -> new ArrayList<>())
                    .add(FieldOptionSupport.from(field, optionsByParent.getOrDefault(field.getId(), List.of())));
        }
        return BatchMaps.freeze(buckets);
    }

    private Map<String, List<ValueOption>> listWriteValueOptionsByParents(Collection<String> parentIds) {
        Map<String, List<ValueOption>> buckets = BatchMaps.buckets(parentIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<WriteValueOptionEntity> rows = writeValueOptions.selectList(new QueryWrapper<WriteValueOptionEntity>()
                .in("parent_id", buckets.keySet())
                .orderByAsc("option_value"));
        for (WriteValueOptionEntity row : rows) {
            buckets.computeIfAbsent(row.getParentId(), key -> new ArrayList<>())
                    .add(ValueOptionSupport.toValueOption(row));
        }
        return BatchMaps.freeze(buckets);
    }

    private Map<String, List<ValueOption>> listReadFieldValueOptionsByParents(Collection<String> parentIds) {
        Map<String, List<ValueOption>> buckets = BatchMaps.buckets(parentIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<ReadFieldValueOptionEntity> rows = readFieldValueOptions.selectList(
                new QueryWrapper<ReadFieldValueOptionEntity>()
                        .in("parent_id", buckets.keySet())
                        .orderByAsc("option_value"));
        for (ReadFieldValueOptionEntity row : rows) {
            buckets.computeIfAbsent(row.getParentId(), key -> new ArrayList<>())
                    .add(ValueOptionSupport.toValueOption(row));
        }
        return BatchMaps.freeze(buckets);
    }

    private Map<String, List<ValueOption>> listReadValueOptionsByFunctionIds(Collection<String> functionIds) {
        Map<String, List<ValueOption>> buckets = BatchMaps.buckets(functionIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<ReadValueOptionEntity> rows = readValueOptions.selectList(new QueryWrapper<ReadValueOptionEntity>()
                .in("product_function_id", buckets.keySet())
                .orderByAsc("option_value"));
        for (ReadValueOptionEntity row : rows) {
            buckets.computeIfAbsent(row.getProductFunctionId(), key -> new ArrayList<>())
                    .add(ValueOptionSupport.toValueOption(row));
        }
        return BatchMaps.freeze(buckets);
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
