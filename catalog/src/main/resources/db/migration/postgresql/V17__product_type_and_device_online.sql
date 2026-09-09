-- 手跑。产品类型 + 设备在线状态。
CREATE TABLE gw_product_type (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

COMMENT ON TABLE gw_product_type IS '产品类型：业务分类，与南向能力类型分离';
COMMENT ON COLUMN gw_product_type.id IS '主键';
COMMENT ON COLUMN gw_product_type.code IS '类型编码，全局唯一';
COMMENT ON COLUMN gw_product_type.name IS '显示名称';
COMMENT ON COLUMN gw_product_type.description IS '说明';

INSERT INTO gw_product_type (id, code, name, description, created_at, updated_at)
VALUES ('ACCESS_CONTROL', 'ACCESS_CONTROL', '门禁', '门禁控制器 / 人脸终端', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE gw_product ADD COLUMN product_type_id VARCHAR(36) NULL;
UPDATE gw_product SET product_type_id = 'ACCESS_CONTROL' WHERE product_type_id IS NULL;
ALTER TABLE gw_product ALTER COLUMN product_type_id SET NOT NULL;

COMMENT ON COLUMN gw_product.product_type_id IS '产品类型主键，对应 gw_product_type.id';

ALTER TABLE gw_device ADD COLUMN online BOOLEAN NULL;
ALTER TABLE gw_device ADD COLUMN online_updated_at TIMESTAMP(3) NULL;

COMMENT ON COLUMN gw_device.online IS '最近一次探针/在线监听得到的在线状态，空=未知';
COMMENT ON COLUMN gw_device.online_updated_at IS '在线状态最近更新时间';
