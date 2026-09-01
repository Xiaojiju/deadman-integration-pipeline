-- 写字段：平台值生成器
ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS value_generator VARCHAR(32) NULL;

-- READ 功能字段定义（结构与 write_option 对齐）
CREATE TABLE IF NOT EXISTS gw_read_field (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_function_id VARCHAR(36) NOT NULL,
    field VARCHAR(64) NOT NULL,
    description VARCHAR(128) NULL,
    access_data_type VARCHAR(32) NOT NULL,
    transform_data_type VARCHAR(32) NOT NULL,
    ignore_request BOOLEAN NOT NULL DEFAULT FALSE,
    format VARCHAR(32) NOT NULL DEFAULT 'none',
    value_generator VARCHAR(32) NULL
);

CREATE TABLE IF NOT EXISTS gw_read_field_value_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    parent_id VARCHAR(36) NOT NULL,
    description VARCHAR(128) NULL,
    option_value VARCHAR(128) NOT NULL,
    mapping_value VARCHAR(128) NULL,
    access_data_type VARCHAR(32) NOT NULL,
    transform_data_type VARCHAR(32) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);
