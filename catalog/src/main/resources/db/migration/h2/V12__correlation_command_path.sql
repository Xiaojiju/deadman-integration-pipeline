ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS correlation_command_path VARCHAR(128) NULL;
COMMENT ON COLUMN gw_product_function.correlation_command_path IS '下发载荷中与回包关联的字段 path；$deviceCode 表示设备编码';
