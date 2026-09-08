package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionScheduleEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.mapper.DeviceEndpointMapper;
import com.mtfm.gateway.catalog.mapper.DeviceFunctionScheduleMapper;
import com.mtfm.gateway.catalog.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备、端点与设备级定时覆盖 CRUD。属性清理由 {@link CatalogStore} 编排。
 */
@Repository
public class CatalogDeviceRepository {

    private final DeviceMapper devices;
    private final DeviceEndpointMapper endpoints;
    private final DeviceFunctionScheduleMapper schedules;
    private final CatalogRevision revision;

    public CatalogDeviceRepository(
            DeviceMapper devices,
            DeviceEndpointMapper endpoints,
            DeviceFunctionScheduleMapper schedules,
            @Autowired(required = false) CatalogRevision revision) {
        this.devices = devices;
        this.endpoints = endpoints;
        this.schedules = schedules;
        this.revision = revision;
    }

    public DeviceEntity insert(DeviceEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        try {
            devices.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("设备编码已存在: " + entity.getDeviceCode(), ex);
        }
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public DeviceEntity update(DeviceEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        try {
            devices.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("设备编码已存在: " + entity.getDeviceCode(), ex);
        }
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public Optional<DeviceEntity> findByCode(String deviceCode) {
        return Optional.ofNullable(devices.selectOne(new QueryWrapper<DeviceEntity>()
                .eq("device_code", deviceCode)));
    }

    public Optional<DeviceEntity> findById(String deviceId) {
        return Optional.ofNullable(devices.selectById(deviceId));
    }

    public Optional<DeviceEntity> resolve(String deviceCodeOrId) {
        Optional<DeviceEntity> byCode = findByCode(deviceCodeOrId);
        if (byCode.isPresent()) {
            return byCode;
        }
        return findById(deviceCodeOrId);
    }

    public List<DeviceEntity> listEnabled() {
        return devices.selectList(new QueryWrapper<DeviceEntity>().eq("enabled", true));
    }

    public List<DeviceEntity> list() {
        return devices.selectList(new QueryWrapper<DeviceEntity>().orderByAsc("device_code"));
    }

    public PageResult<DeviceEntity> page(int page, int size) {
        Page<DeviceEntity> result = devices.selectPage(
                new Page<>(CatalogPages.page(page), CatalogPages.size(size)),
                new QueryWrapper<DeviceEntity>().orderByAsc("device_code"));
        return PageResult.of(result);
    }

    public long countByProduct(String productId) {
        return devices.selectCount(new QueryWrapper<DeviceEntity>().eq("product_id", productId));
    }

    public List<DeviceEntity> listByProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return List.of();
        }
        return devices.selectList(new QueryWrapper<DeviceEntity>().eq("product_id", productId));
    }

    public boolean deleteById(String devicePk) {
        boolean deleted = devices.deleteById(devicePk) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }

    public DeviceEndpointEntity insertEndpoint(DeviceEndpointEntity entity) {
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        endpoints.insert(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public DeviceEndpointEntity updateEndpoint(DeviceEndpointEntity entity) {
        endpoints.updateById(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public Optional<DeviceEndpointEntity> findEndpoint(String endpointId) {
        return Optional.ofNullable(endpoints.selectById(endpointId));
    }

    public List<DeviceEndpointEntity> listEndpoints(String devicePk) {
        return endpoints.selectList(new QueryWrapper<DeviceEndpointEntity>().eq("device_id", devicePk));
    }

    public long countEndpointsByChannel(String channelPk) {
        return endpoints.selectCount(new QueryWrapper<DeviceEndpointEntity>().eq("channel_id", channelPk));
    }

    public boolean deleteEndpointById(String endpointId) {
        boolean deleted = endpoints.deleteById(endpointId) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }

    public void deleteEndpointsByDevice(String devicePk) {
        endpoints.delete(new QueryWrapper<DeviceEndpointEntity>().eq("device_id", devicePk));
        CatalogTimestamps.bump(revision);
    }

    public Map<String, DeviceFunctionScheduleEntity> listScheduleOverrides(String devicePk) {
        if (devicePk == null || devicePk.isBlank()) {
            return Map.of();
        }
        List<DeviceFunctionScheduleEntity> rows = schedules.selectList(
                new QueryWrapper<DeviceFunctionScheduleEntity>().eq("device_id", devicePk));
        Map<String, DeviceFunctionScheduleEntity> byFunction = new LinkedHashMap<>();
        for (DeviceFunctionScheduleEntity row : rows) {
            byFunction.put(row.getFunctionId(), row);
        }
        return byFunction;
    }

    public Optional<DeviceFunctionScheduleEntity> findScheduleOverride(String devicePk, String functionId) {
        if (devicePk == null || functionId == null || functionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(schedules.selectOne(new QueryWrapper<DeviceFunctionScheduleEntity>()
                .eq("device_id", devicePk)
                .eq("function_id", functionId)));
    }

    public DeviceFunctionScheduleEntity saveScheduleOverride(DeviceFunctionScheduleEntity entity) {
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            schedules.insert(entity);
            CatalogTimestamps.bump(revision);
            return entity;
        }
        entity.setUpdatedAt(now);
        schedules.updateById(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public boolean deleteScheduleOverride(String devicePk, String functionId) {
        boolean deleted = schedules.delete(new QueryWrapper<DeviceFunctionScheduleEntity>()
                .eq("device_id", devicePk)
                .eq("function_id", functionId)) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }

    public void deleteSchedulesByDevice(String devicePk) {
        schedules.delete(new QueryWrapper<DeviceFunctionScheduleEntity>().eq("device_id", devicePk));
        CatalogTimestamps.bump(revision);
    }
}
