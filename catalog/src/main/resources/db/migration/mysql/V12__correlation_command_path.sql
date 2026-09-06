ALTER TABLE gw_product_function
    ADD COLUMN correlation_command_path VARCHAR(128) NULL COMMENT '下发载荷中与回包关联的字段 path；$deviceCode 表示设备编码';
