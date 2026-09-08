-- 手跑。连接 / 寻址 / 功能参数只认 EAV，JSON 兼容列不再读写。
ALTER TABLE gw_channel DROP COLUMN IF EXISTS connection;
ALTER TABLE gw_device_endpoint DROP COLUMN IF EXISTS address;
ALTER TABLE gw_product_function DROP COLUMN IF EXISTS option_schema;
ALTER TABLE gw_device DROP COLUMN IF EXISTS option_overrides;
