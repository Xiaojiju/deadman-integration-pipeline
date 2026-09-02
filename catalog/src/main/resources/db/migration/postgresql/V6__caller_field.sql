ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS caller_field VARCHAR(64) NULL;
COMMENT ON COLUMN gw_write_option.caller_field IS 'MAPPED 时调用方传入的字段名，默认 value';

ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS caller_field VARCHAR(64) NULL;
