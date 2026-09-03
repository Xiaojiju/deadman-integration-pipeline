ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS reply_topic_slot VARCHAR(64) NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS correlation_path VARCHAR(128) NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS result_path VARCHAR(128) NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS reply_timeout_ms INTEGER NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS schedule_interval_ms BIGINT NULL;
ALTER TABLE gw_product_function ADD COLUMN IF NOT EXISTS schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE;
