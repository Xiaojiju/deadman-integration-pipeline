CREATE TABLE IF NOT EXISTS gw_device_function_schedule (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    device_id VARCHAR(36) NOT NULL COMMENT '设备主键，对应 gw_device.id',
    function_id VARCHAR(64) NOT NULL COMMENT '产品功能业务 ID',
    enabled TINYINT(1) NULL COMMENT '覆盖是否启用；空表示继承产品',
    interval_ms BIGINT NULL COMMENT '覆盖间隔毫秒；空表示继承产品',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_device_function_schedule (device_id, function_id)
) COMMENT='设备功能定时下发覆盖：相对产品 schedule_enabled / schedule_interval_ms 的差异';
