-- 手跑。产品类型 + 设备在线状态。
CREATE TABLE gw_product_type (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '类型编码，全局唯一',
    name VARCHAR(128) NOT NULL COMMENT '显示名称',
    description VARCHAR(512) NULL COMMENT '说明',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_product_type_code (code)
) COMMENT='产品类型：业务分类，与南向能力类型分离';

INSERT INTO gw_product_type (id, code, name, description, created_at, updated_at)
VALUES ('ACCESS_CONTROL', 'ACCESS_CONTROL', '门禁', '门禁控制器 / 人脸终端', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

ALTER TABLE gw_product ADD COLUMN product_type_id VARCHAR(36) NULL COMMENT '产品类型主键，对应 gw_product_type.id';
UPDATE gw_product SET product_type_id = 'ACCESS_CONTROL' WHERE product_type_id IS NULL;
ALTER TABLE gw_product MODIFY product_type_id VARCHAR(36) NOT NULL COMMENT '产品类型主键，对应 gw_product_type.id';

ALTER TABLE gw_device ADD COLUMN online TINYINT(1) NULL COMMENT '最近一次探针/在线监听得到的在线状态，空=未知';
ALTER TABLE gw_device ADD COLUMN online_updated_at TIMESTAMP(3) NULL COMMENT '在线状态最近更新时间';
