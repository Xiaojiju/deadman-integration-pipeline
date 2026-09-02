ALTER TABLE gw_write_option
    ADD COLUMN caller_field VARCHAR(64) NULL COMMENT 'MAPPED 时调用方传入的字段名，默认 value';

ALTER TABLE gw_read_field
    ADD COLUMN caller_field VARCHAR(64) NULL;
