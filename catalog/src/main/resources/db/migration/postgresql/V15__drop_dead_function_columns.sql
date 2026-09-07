-- 手跑。result_path / protocol_mapping 已不再读写。
ALTER TABLE gw_product_function DROP COLUMN IF EXISTS result_path;
ALTER TABLE gw_product_function DROP COLUMN IF EXISTS protocol_mapping;
