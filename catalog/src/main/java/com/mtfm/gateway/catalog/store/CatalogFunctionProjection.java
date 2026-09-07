package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.option.Option;
import com.mtfm.gateway.spi.option.OptionTrees;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueAccessType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 产品功能 → {@link FunctionDef} 投影。供 {@link CachingFunctionCatalog} 缓存，不进 CRUD 门面。
 *
 * <p>使用示例：{@code projection.find("door-1", "fn.open")}
 */
@Service
public class CatalogFunctionProjection {

    private final CatalogDeviceRepository devices;
    private final CatalogProductRepository products;
    private final CatalogEavLoader eav;
    private final CatalogPropertyRepository properties;

    public CatalogFunctionProjection(
            CatalogDeviceRepository devices,
            CatalogProductRepository products,
            CatalogEavLoader eav,
            CatalogPropertyRepository properties) {
        this.devices = devices;
        this.products = products;
        this.eav = eav;
        this.properties = properties;
    }

    public Optional<FunctionDef> find(String deviceId, String functionId) {
        Optional<DeviceEntity> device = devices.findByCode(deviceId);
        if (device.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProductFunctionEntity> function = products.findFunction(device.get().getProductId(), functionId);
        if (function.isEmpty()) {
            return Optional.empty();
        }
        ProductFunctionEntity row = function.get();
        List<PropertyItem> base = eav.loadFunctionProperties(row);
        List<PropertyItem> overrides = eav.loadDeviceOverrides(device.get(), functionId);
        Map<String, Object> merged = new LinkedHashMap<>(PropertyCodec.toMap(base));
        merged.putAll(PropertyCodec.toMap(overrides));
        Option optionSchema = OptionTrees.fromUnknown(merged);
        ValueAccessType writeAccess = ValueAccessType.from(row.getWriteAccessType());
        FunctionOptionBundle bundle = properties.loadFunctionOptions(row.getId());
        return Optional.of(new FunctionDef(
                row.getFunctionId(),
                row.getAccessType(),
                row.getAccessPermission() == null ? 2 : row.getAccessPermission(),
                optionSchema,
                PropertyCodec.fromMap(merged),
                writeAccess,
                bundle.writeValueOptions(),
                bundle.writeFields(),
                bundle.readFields(),
                bundle.readValueOptions(),
                PayloadEncoding.from(row.getPayloadEncoding()),
                FunctionDef.ReplySpec.of(row.getReplyTopicSlot(), row.getCorrelationPath(), row.getReplyTimeoutMs()),
                FunctionDef.ScheduleSpec.of(row.getScheduleIntervalMs(), Boolean.TRUE.equals(row.getScheduleEnabled())),
                FunctionDef.ScaleSpec.of(row.getScaleOp(), row.getScaleOperand())));
    }
}
