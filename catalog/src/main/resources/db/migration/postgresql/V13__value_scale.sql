ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS scale_op VARCHAR(16) NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS scale_operand VARCHAR(32) NULL;
COMMENT ON COLUMN gw_product_function.scale_op IS '入站换算运算符 add/subtract/multiply/divide';
COMMENT ON COLUMN gw_product_function.scale_operand IS '入站换算操作数';

ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS scale_op VARCHAR(16) NULL;
ALTER TABLE gw_write_option ADD COLUMN IF NOT EXISTS scale_operand VARCHAR(32) NULL;
ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS scale_op VARCHAR(16) NULL;
ALTER TABLE gw_read_field ADD COLUMN IF NOT EXISTS scale_operand VARCHAR(32) NULL;
