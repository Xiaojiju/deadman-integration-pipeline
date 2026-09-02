ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS sort_index INTEGER NOT NULL DEFAULT 0;
COMMENT ON COLUMN gw_write_option.sort_index IS '协议字段顺序，按配置传入顺序保存，数值越小越靠前';

ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS sort_index INTEGER NOT NULL DEFAULT 0;
COMMENT ON COLUMN gw_read_field.sort_index IS '协议字段顺序，按配置传入顺序保存，数值越小越靠前';
