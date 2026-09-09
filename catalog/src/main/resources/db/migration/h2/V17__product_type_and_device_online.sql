-- 手跑。产品类型 + 设备在线状态。
CREATE TABLE gw_product_type (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

INSERT INTO gw_product_type (id, code, name, description, created_at, updated_at)
VALUES ('ACCESS_CONTROL', 'ACCESS_CONTROL', '门禁', '门禁控制器 / 人脸终端', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE gw_product ADD COLUMN product_type_id VARCHAR(36) NULL;
UPDATE gw_product SET product_type_id = 'ACCESS_CONTROL' WHERE product_type_id IS NULL;
ALTER TABLE gw_product ALTER COLUMN product_type_id VARCHAR(36) NOT NULL;

ALTER TABLE gw_device ADD COLUMN online BOOLEAN NULL;
ALTER TABLE gw_device ADD COLUMN online_updated_at TIMESTAMP(3) NULL;
