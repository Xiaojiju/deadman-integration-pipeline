CREATE TABLE IF NOT EXISTS gw_action_group (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    code VARCHAR(64) NOT NULL COMMENT '业务编码，全局唯一',
    name VARCHAR(128) NOT NULL COMMENT '显示名',
    description VARCHAR(512) NULL COMMENT '说明',
    kind VARCHAR(16) NOT NULL COMMENT 'CLUSTER / SCENE',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_action_group_code (code)
) COMMENT='动作组：集群手动执行或场景触发执行的成员容器';

CREATE TABLE IF NOT EXISTS gw_action_member (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    group_id VARCHAR(36) NOT NULL COMMENT '所属动作组',
    device_code VARCHAR(64) NOT NULL COMMENT '设备编码',
    function_id VARCHAR(64) NOT NULL COMMENT '产品功能 ID',
    arguments_json TEXT NULL COMMENT '默认下发 arguments JSON',
    sort_index INTEGER NOT NULL DEFAULT 0 COMMENT '执行顺序',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间'
) COMMENT='动作组成员：设备功能 + 默认 arguments';

CREATE TABLE IF NOT EXISTS gw_scene_trigger (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    group_id VARCHAR(36) NOT NULL COMMENT '所属 SCENE 动作组',
    mode VARCHAR(16) NOT NULL COMMENT 'LISTEN / TIMER',
    listen_device_code VARCHAR(64) NULL COMMENT '监听设备编码',
    listen_function_id VARCHAR(64) NULL COMMENT '监听功能 ID',
    listen_match_json TEXT NULL COMMENT '可选 arguments 子集匹配',
    timer_kind VARCHAR(16) NULL COMMENT 'ONCE / CRON',
    timer_at VARCHAR(64) NULL COMMENT '单次执行 ISO-8601',
    cron_expr VARCHAR(64) NULL COMMENT '6 位 Spring cron',
    timezone VARCHAR(64) NULL COMMENT '默认 Asia/Shanghai',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_scene_trigger_group (group_id)
) COMMENT='场景触发器：LISTEN 指令成功后执行，TIMER 日历到点执行';
