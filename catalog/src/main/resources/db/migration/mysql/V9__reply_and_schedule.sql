ALTER TABLE gw_product_function
    ADD COLUMN reply_topic_slot VARCHAR(64) NULL COMMENT 'MQTT 应答订阅 slot',
    ADD COLUMN correlation_path VARCHAR(128) NULL COMMENT '回包关联号字段 path',
    ADD COLUMN result_path VARCHAR(128) NULL COMMENT '回包成败字段 path',
    ADD COLUMN reply_timeout_ms INT NULL COMMENT '等待设备应答毫秒',
    ADD COLUMN schedule_interval_ms BIGINT NULL COMMENT '定时下发间隔毫秒',
    ADD COLUMN schedule_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时下发';
