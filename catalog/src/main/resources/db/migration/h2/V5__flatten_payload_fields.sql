ALTER TABLE gw_product_function DROP COLUMN IF EXISTS struct_schema;
ALTER TABLE gw_product_function DROP COLUMN IF EXISTS value_mappings;

ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS field_source VARCHAR(16) NULL;
ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS constant_value TEXT NULL;
ALTER TABLE gw_write_option ALTER COLUMN field VARCHAR(256);

ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS field_source VARCHAR(16) NULL;
ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS constant_value TEXT NULL;
ALTER TABLE gw_read_field ALTER COLUMN field VARCHAR(256);
