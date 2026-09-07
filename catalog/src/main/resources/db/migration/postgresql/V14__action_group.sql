CREATE TABLE IF NOT EXISTS gw_action_group (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    kind VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    UNIQUE (code)
);
COMMENT ON TABLE gw_action_group IS '动作组：集群手动执行或场景触发执行的成员容器';
COMMENT ON COLUMN gw_action_group.kind IS 'CLUSTER / SCENE';

CREATE TABLE IF NOT EXISTS gw_action_member (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    function_id VARCHAR(64) NOT NULL,
    arguments_json TEXT NULL,
    sort_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);
COMMENT ON TABLE gw_action_member IS '动作组成员：设备功能 + 默认 arguments';

CREATE TABLE IF NOT EXISTS gw_scene_trigger (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    listen_device_code VARCHAR(64) NULL,
    listen_function_id VARCHAR(64) NULL,
    listen_match_json TEXT NULL,
    timer_kind VARCHAR(16) NULL,
    timer_at VARCHAR(64) NULL,
    cron_expr VARCHAR(64) NULL,
    timezone VARCHAR(64) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    UNIQUE (group_id)
);
COMMENT ON TABLE gw_scene_trigger IS '场景触发器：LISTEN 指令成功后执行，TIMER 日历到点执行';
COMMENT ON COLUMN gw_scene_trigger.mode IS 'LISTEN / TIMER';
COMMENT ON COLUMN gw_scene_trigger.timer_kind IS 'ONCE / CRON';
