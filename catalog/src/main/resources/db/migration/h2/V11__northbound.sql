CREATE TABLE IF NOT EXISTS gw_northbound (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    mqtt_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mqtt_transport VARCHAR(16) NOT NULL DEFAULT 'paho',
    mqtt_url VARCHAR(256) NULL,
    mqtt_command_topic VARCHAR(128) NULL,
    mqtt_response_topic VARCHAR(128) NULL,
    mqtt_telemetry_topic VARCHAR(128) NULL,
    mqtt_client_id VARCHAR(128) NULL,
    mqtt_username VARCHAR(128) NULL,
    mqtt_password VARCHAR(512) NULL,
    http_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    http_webhook_url VARCHAR(512) NULL,
    http_timeout_ms INTEGER NOT NULL DEFAULT 3000,
    http_max_attempts INTEGER NOT NULL DEFAULT 2,
    updated_at TIMESTAMP(3) NOT NULL
);
COMMENT ON TABLE gw_northbound IS '北向双通道运行时配置（单行 default）';
COMMENT ON COLUMN gw_northbound.mqtt_password IS 'MQTT 密码，经 SecretCodec 落库';
