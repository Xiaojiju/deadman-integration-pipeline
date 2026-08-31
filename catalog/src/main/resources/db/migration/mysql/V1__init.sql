-- catalog 全量初始化（单版本）：核心配置表 + EAV/Option 属性表
CREATE TABLE gw_product (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    code VARCHAR(64) NOT NULL COMMENT '产品业务编码，全局唯一',
    name VARCHAR(128) NOT NULL COMMENT '产品显示名称',
    description VARCHAR(512) NULL COMMENT '产品说明',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_product_code (code)
) COMMENT='产品：挂载产品功能模板的配置根';

CREATE TABLE gw_channel (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    code VARCHAR(64) NOT NULL COMMENT '通道业务编码，全局唯一',
    capability_type VARCHAR(64) NOT NULL COMMENT '南向能力类型，如 MODBUS / MQTT / HIKVISION',
    connection JSON NOT NULL COMMENT '连接参数 JSON（兼容列；新写入优先 EAV gw_channel_property）',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_channel_code (code)
) COMMENT='共享通道：能力连接参数（host/port 等），多设备复用';

CREATE TABLE gw_device (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    device_code VARCHAR(64) NOT NULL COMMENT '设备业务编码（流水线 deviceId），全局唯一',
    product_id VARCHAR(36) NOT NULL COMMENT '所属产品主键，对应 gw_product.id',
    name VARCHAR(128) NULL COMMENT '设备显示名称',
    option_overrides JSON NULL COMMENT '功能参数覆盖 JSON（兼容列；新写入优先 EAV gw_device_function_override）',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_gw_device_code (device_code)
) COMMENT='设备实例：流水线 deviceId 对应 device_code';

CREATE TABLE gw_device_endpoint (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    device_id VARCHAR(36) NOT NULL COMMENT '设备主键，对应 gw_device.id',
    channel_id VARCHAR(36) NOT NULL COMMENT '通道主键，对应 gw_channel.id',
    address JSON NOT NULL COMMENT '寻址参数 JSON（兼容列；新写入优先 EAV gw_endpoint_property）',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_gw_endpoint_device_channel (device_id, channel_id)
) COMMENT='设备端点：设备与共享通道的绑定及寻址参数';

CREATE TABLE gw_product_function (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    product_id VARCHAR(36) NOT NULL COMMENT '所属产品主键，对应 gw_product.id',
    function_id VARCHAR(64) NOT NULL COMMENT '功能业务 ID，如 remoteControlDoor / fn.read',
    access_type VARCHAR(32) NOT NULL COMMENT '访问类型：READ / WRITE',
    access_permission INT NOT NULL DEFAULT 2 COMMENT '读写权限位：READ=1，WRITE=2，可按位组合',
    capability_type VARCHAR(64) NULL COMMENT '南向能力类型，用于拉取 FunctionTemplate',
    option_schema JSON NULL COMMENT '功能默认参数 JSON（兼容列；新写入优先 EAV gw_function_property）',
    protocol_mapping JSON NULL COMMENT '协议层映射 JSON（如寄存器 offset，不含从站号）',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '同产品内排序，数值越小越靠前',
    write_access_type VARCHAR(16) NOT NULL DEFAULT 'VALUE' COMMENT '写访问模式：VALUE（标量选项）/ STRUCT（多字段）',
    description VARCHAR(128) NULL COMMENT '功能说明（可覆盖能力模板 description）',
    UNIQUE KEY uk_gw_product_function (product_id, function_id)
) COMMENT='产品功能：产品下挂载的逻辑功能定义与默认参数';
CREATE TABLE gw_channel_property (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    channel_id VARCHAR(36) NOT NULL COMMENT '所属通道主键，对应 gw_channel.id',
    attribute VARCHAR(64) NOT NULL COMMENT '属性名，如 host / port / username',
    attribute_value VARCHAR(512) NOT NULL COMMENT '属性值（文本落库，按 data_type 解释）',
    data_type VARCHAR(32) NOT NULL COMMENT '数据类型：string / int / boolean / select / password 等',
    description VARCHAR(128) NULL COMMENT '属性说明'
) COMMENT='通道连接属性 EAV：对齐旧 DriverProperty';

CREATE TABLE gw_endpoint_property (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    endpoint_id VARCHAR(36) NOT NULL COMMENT '所属端点主键，对应 gw_device_endpoint.id',
    attribute VARCHAR(64) NOT NULL COMMENT '属性名',
    attribute_value VARCHAR(512) NOT NULL COMMENT '属性值（文本落库）',
    data_type VARCHAR(32) NOT NULL COMMENT '数据类型',
    description VARCHAR(128) NULL COMMENT '属性说明'
) COMMENT='端点寻址属性 EAV：如 slaveId / topic / deviceSerialNo';

CREATE TABLE gw_function_property (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    product_function_id VARCHAR(36) NOT NULL COMMENT '所属产品功能主键，对应 gw_product_function.id',
    attribute VARCHAR(64) NOT NULL COMMENT '属性名，如 command / offset',
    attribute_value VARCHAR(512) NOT NULL COMMENT '属性值（文本落库）',
    data_type VARCHAR(32) NOT NULL COMMENT '数据类型',
    description VARCHAR(128) NULL COMMENT '属性说明'
) COMMENT='产品功能参数属性 EAV：对齐旧 FunctionProperty';

CREATE TABLE gw_device_function_override (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    device_id VARCHAR(36) NOT NULL COMMENT '设备主键，对应 gw_device.id',
    function_id VARCHAR(64) NOT NULL COMMENT '功能业务 ID，与 gw_product_function.function_id 对齐',
    attribute VARCHAR(64) NOT NULL COMMENT '覆盖属性名',
    attribute_value VARCHAR(512) NOT NULL COMMENT '覆盖属性值（文本落库）',
    data_type VARCHAR(32) NOT NULL COMMENT '数据类型',
    description VARCHAR(128) NULL COMMENT '属性说明'
) COMMENT='设备功能参数覆盖 EAV：相对产品功能默认值的差异，按 function_id 作用域';

CREATE TABLE gw_write_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID；亦可作为 gw_write_value_option.parent_id',
    product_function_id VARCHAR(36) NOT NULL COMMENT '所属产品功能主键',
    field VARCHAR(64) NOT NULL COMMENT '写字段名',
    description VARCHAR(128) NULL COMMENT '字段说明',
    access_data_type VARCHAR(32) NOT NULL COMMENT '设备侧原始数据类型',
    transform_data_type VARCHAR(32) NOT NULL COMMENT '业务侧转换后数据类型',
    ignore_request BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否忽略请求体中该字段'
) COMMENT='STRUCT 写字段定义：多字段写模式下的字段元数据';

CREATE TABLE gw_write_value_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    parent_id VARCHAR(36) NOT NULL COMMENT '父节点：product_function.id（VALUE）或 write_option.id（STRUCT）',
    description VARCHAR(128) NULL COMMENT '选项说明，如 开门 / 关门',
    option_value VARCHAR(128) NOT NULL COMMENT '设备侧/协议侧取值，如下发 command=open',
    mapping_value VARCHAR(128) NULL COMMENT '业务侧映射值，空则等同 option_value',
    access_data_type VARCHAR(32) NOT NULL COMMENT '原始数据类型',
    transform_data_type VARCHAR(32) NOT NULL COMMENT '转换后数据类型',
    is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否默认选项'
) COMMENT='写值选项：设备值 ↔ 业务映射；VALUE 时 parent 为产品功能，STRUCT 时 parent 为写字段';

CREATE TABLE gw_read_value_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键 雪花ID',
    product_function_id VARCHAR(36) NOT NULL COMMENT '所属产品功能主键',
    description VARCHAR(128) NULL COMMENT '选项说明',
    option_value VARCHAR(128) NOT NULL COMMENT '设备侧取值',
    mapping_value VARCHAR(128) NULL COMMENT '业务侧映射值',
    access_data_type VARCHAR(32) NOT NULL COMMENT '原始数据类型',
    transform_data_type VARCHAR(32) NOT NULL COMMENT '转换后数据类型',
    is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否默认选项'
) COMMENT='读值选项：设备上报值 ↔ 业务映射';
