-- STRUCT 写字段增加 UI 采值约束 format（FieldFormat wire）
ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS format VARCHAR(32) NOT NULL DEFAULT 'none';

COMMENT ON COLUMN gw_write_option.format IS 'UI 采值约束（FieldFormat），如 none / datetime_iso8601 / image_base64 / text_list';
