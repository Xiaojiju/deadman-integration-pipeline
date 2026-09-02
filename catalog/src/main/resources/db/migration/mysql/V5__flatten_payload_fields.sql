ALTER TABLE gw_product_function
    DROP COLUMN struct_schema,
    DROP COLUMN value_mappings;

ALTER TABLE gw_write_option
    ADD COLUMN field_source VARCHAR(16) NULL COMMENT 'caller / platform / device / constant / mapped',
    ADD COLUMN constant_value TEXT NULL COMMENT 'source=constant 时的固定值',
    MODIFY COLUMN field VARCHAR(256) NOT NULL;

ALTER TABLE gw_read_field
    ADD COLUMN field_source VARCHAR(16) NULL COMMENT 'caller / platform / device / constant / mapped',
    ADD COLUMN constant_value TEXT NULL COMMENT 'source=constant 时的固定值',
    MODIFY COLUMN field VARCHAR(256) NOT NULL;
