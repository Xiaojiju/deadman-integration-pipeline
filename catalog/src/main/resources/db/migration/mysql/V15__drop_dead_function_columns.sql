-- 手跑。result_path / protocol_mapping 已不再读写。
ALTER TABLE gw_product_function DROP COLUMN result_path;
ALTER TABLE gw_product_function DROP COLUMN protocol_mapping;
