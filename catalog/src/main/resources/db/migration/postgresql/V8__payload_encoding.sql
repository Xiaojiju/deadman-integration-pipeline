ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS payload_encoding VARCHAR(16) NOT NULL DEFAULT 'JSON';
COMMENT ON COLUMN gw_product_function.payload_encoding IS '载荷编码 JSON/HEX/BINARY';

ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS byte_length INTEGER NULL;
ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS byte_order VARCHAR(16) NULL;
COMMENT ON COLUMN gw_write_option.byte_length IS 'HEX/BINARY 字段占用字节数';
COMMENT ON COLUMN gw_write_option.byte_order IS '字节序 big/little，默认 big';

ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS byte_length INTEGER NULL;
ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS byte_order VARCHAR(16) NULL;
COMMENT ON COLUMN gw_read_field.byte_length IS 'HEX/BINARY 字段占用字节数';
COMMENT ON COLUMN gw_read_field.byte_order IS '字节序 big/little，默认 big';
