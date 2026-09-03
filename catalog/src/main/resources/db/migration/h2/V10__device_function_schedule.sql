CREATE TABLE IF NOT EXISTS gw_device_function_schedule (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NULL,
    interval_ms BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    UNIQUE (device_id, function_id)
);
COMMENT ON TABLE gw_device_function_schedule IS '设备功能定时下发覆盖：相对产品 schedule_enabled / schedule_interval_ms 的差异';
COMMENT ON COLUMN gw_device_function_schedule.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_device_function_schedule.device_id IS '设备主键，对应 gw_device.id';
COMMENT ON COLUMN gw_device_function_schedule.function_id IS '产品功能业务 ID';
COMMENT ON COLUMN gw_device_function_schedule.enabled IS '覆盖是否启用；空表示继承产品';
COMMENT ON COLUMN gw_device_function_schedule.interval_ms IS '覆盖间隔毫秒；空表示继承产品';
COMMENT ON COLUMN gw_device_function_schedule.created_at IS '创建时间';
COMMENT ON COLUMN gw_device_function_schedule.updated_at IS '最后更新时间';
