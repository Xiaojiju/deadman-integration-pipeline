ALTER TABLE gw_product_function
    ADD COLUMN payload_encoding VARCHAR(16) NOT NULL DEFAULT 'JSON' COMMENT '载荷编码 JSON/HEX/BINARY';

ALTER TABLE gw_write_option
    ADD COLUMN byte_length INT NULL COMMENT 'HEX/BINARY 字段占用字节数',
    ADD COLUMN byte_order VARCHAR(16) NULL COMMENT '字节序 big/little，默认 big';

ALTER TABLE gw_read_field
    ADD COLUMN byte_length INT NULL COMMENT 'HEX/BINARY 字段占用字节数',
    ADD COLUMN byte_order VARCHAR(16) NULL COMMENT '字节序 big/little，默认 big';
