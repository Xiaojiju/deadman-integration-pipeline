ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS payload_mode VARCHAR(16) NOT NULL DEFAULT 'STRUCT';
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS struct_schema TEXT NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS value_mappings TEXT NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS publish_topic_slot VARCHAR(64) NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS subscribe_topic_slot VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS gw_device_field_override (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(128) NOT NULL,
    field_path VARCHAR(256) NOT NULL,
    field_value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS gw_device_topic_override (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(128) NOT NULL,
    topic_slot VARCHAR(64) NOT NULL,
    topic_value VARCHAR(512) NOT NULL
);

COMMENT ON COLUMN gw_product_function.payload_mode IS 'VALUE / STRUCT';
COMMENT ON COLUMN gw_product_function.struct_schema IS 'FieldNode 树 JSON';
COMMENT ON COLUMN gw_product_function.publish_topic_slot IS 'MQTT 发布 topic slot';
