-- 手跑。连接 / 寻址 / 功能参数只认 EAV，JSON 兼容列不再读写。
ALTER TABLE gw_channel DROP COLUMN connection;
ALTER TABLE gw_device_endpoint DROP COLUMN address;
ALTER TABLE gw_product_function DROP COLUMN option_schema;
ALTER TABLE gw_device DROP COLUMN option_overrides;
