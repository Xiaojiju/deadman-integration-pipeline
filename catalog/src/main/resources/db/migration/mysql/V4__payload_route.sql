ALTER TABLE gw_product_function
    ADD COLUMN payload_mode VARCHAR(16) NOT NULL DEFAULT 'STRUCT' COMMENT 'VALUE / STRUCT',
    ADD COLUMN struct_schema TEXT NULL COMMENT 'FieldNode 树 JSON',
    ADD COLUMN value_mappings TEXT NULL COMMENT 'ValueMapping 列表 JSON',
    ADD COLUMN publish_topic_slot VARCHAR(64) NULL COMMENT 'MQTT 发布 topic slot',
    ADD COLUMN subscribe_topic_slot VARCHAR(64) NULL COMMENT 'MQTT 订阅 topic slot';

CREATE TABLE gw_device_field_override (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(128) NOT NULL,
    field_path VARCHAR(256) NOT NULL,
    field_value TEXT NOT NULL
) COMMENT='设备功能字段 path 覆盖';

CREATE TABLE gw_device_topic_override (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(128) NOT NULL,
    topic_slot VARCHAR(64) NOT NULL,
    topic_value VARCHAR(512) NOT NULL
) COMMENT='设备功能 topic slot 覆盖';
