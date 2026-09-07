ALTER TABLE gw_product_function
    ADD COLUMN scale_op VARCHAR(16) NULL COMMENT '入站换算运算符 add/subtract/multiply/divide',
    ADD COLUMN scale_operand VARCHAR(32) NULL COMMENT '入站换算操作数';

ALTER TABLE gw_write_option
    ADD COLUMN scale_op VARCHAR(16) NULL,
    ADD COLUMN scale_operand VARCHAR(32) NULL;

ALTER TABLE gw_read_field
    ADD COLUMN scale_op VARCHAR(16) NULL,
    ADD COLUMN scale_operand VARCHAR(32) NULL;
