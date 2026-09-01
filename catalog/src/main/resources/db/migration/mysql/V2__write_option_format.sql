-- STRUCT 写字段增加 UI 采值约束 format（FieldFormat wire）
ALTER TABLE gw_write_option
    ADD COLUMN format VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT 'UI 采值约束（FieldFormat）';
