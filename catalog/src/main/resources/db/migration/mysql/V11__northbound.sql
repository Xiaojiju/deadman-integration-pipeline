CREATE TABLE IF NOT EXISTS gw_northbound (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '固定 default',
    mqtt_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用北向 MQTT',
    mqtt_transport VARCHAR(16) NOT NULL DEFAULT 'paho' COMMENT 'paho / memory',
    mqtt_url VARCHAR(256) NULL COMMENT '独立 Broker URI',
    mqtt_command_topic VARCHAR(128) NULL COMMENT '入站命令主题',
    mqtt_response_topic VARCHAR(128) NULL COMMENT '出站回执主题模板',
    mqtt_telemetry_topic VARCHAR(128) NULL COMMENT '出站遥测主题模板',
    mqtt_client_id VARCHAR(128) NULL COMMENT 'MQTT 客户端 ID',
    mqtt_username VARCHAR(128) NULL COMMENT '用户名',
    mqtt_password VARCHAR(512) NULL COMMENT '密码，经 SecretCodec 落库',
    http_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 Webhook',
    http_webhook_url VARCHAR(512) NULL COMMENT 'Webhook URL',
    http_timeout_ms INT NOT NULL DEFAULT 3000 COMMENT 'Webhook 超时毫秒',
    http_max_attempts INT NOT NULL DEFAULT 2 COMMENT 'Webhook 最多尝试次数',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间'
) COMMENT='北向双通道运行时配置（单行 default）';
