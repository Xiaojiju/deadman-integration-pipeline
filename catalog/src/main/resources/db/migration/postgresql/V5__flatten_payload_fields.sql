-- 去掉功能级 JSON 列；扁平字段补充 source / constant
ALTER TABLE gw_product_function DROP COLUMN IF EXISTS struct_schema;
ALTER TABLE gw_product_function DROP COLUMN IF EXISTS value_mappings;

ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS field_source VARCHAR(16) NULL;
ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS constant_value TEXT NULL;
ALTER TABLE gw_write_option ALTER COLUMN field TYPE VARCHAR(256);

ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS field_source VARCHAR(16) NULL;
ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS constant_value TEXT NULL;
ALTER TABLE gw_read_field ALTER COLUMN field TYPE VARCHAR(256);

COMMENT ON COLUMN gw_write_option.field_source IS 'caller / platform / device / constant / mapped';
COMMENT ON COLUMN gw_write_option.constant_value IS 'source=constant 时的固定值';
