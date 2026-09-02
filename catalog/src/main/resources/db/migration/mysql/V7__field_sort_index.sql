ALTER TABLE gw_write_option
    ADD COLUMN sort_index INT NOT NULL DEFAULT 0 COMMENT '协议字段顺序，按配置传入顺序保存';

ALTER TABLE gw_read_field
    ADD COLUMN sort_index INT NOT NULL DEFAULT 0 COMMENT '协议字段顺序，按配置传入顺序保存';
