-- catalog 全量初始化（单版本）：核心配置表 + EAV/Option 属性表
-- 产品：功能模板归属（一产品多功能）
CREATE TABLE gw_product (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

COMMENT ON TABLE gw_product IS '产品：挂载产品功能模板的配置根';
COMMENT ON COLUMN gw_product.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_product.code IS '产品业务编码，全局唯一';
COMMENT ON COLUMN gw_product.name IS '产品显示名称';
COMMENT ON COLUMN gw_product.description IS '产品说明';
COMMENT ON COLUMN gw_product.created_at IS '创建时间';
COMMENT ON COLUMN gw_product.updated_at IS '最后更新时间';

-- 通道：共享南向连接（多设备端点复用）
CREATE TABLE gw_channel (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    capability_type VARCHAR(64) NOT NULL,
    connection JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

COMMENT ON TABLE gw_channel IS '共享通道：能力连接参数（host/port 等），多设备复用';
COMMENT ON COLUMN gw_channel.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_channel.code IS '通道业务编码，全局唯一';
COMMENT ON COLUMN gw_channel.capability_type IS '南向能力类型，如 MODBUS / MQTT / HIKVISION';
COMMENT ON COLUMN gw_channel.connection IS '连接参数 JSON（兼容列；新写入优先 EAV gw_channel_property）';
COMMENT ON COLUMN gw_channel.enabled IS '是否启用';
COMMENT ON COLUMN gw_channel.created_at IS '创建时间';
COMMENT ON COLUMN gw_channel.updated_at IS '最后更新时间';

-- 设备实例：挂产品 + 可选功能参数覆盖
CREATE TABLE gw_device (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_code VARCHAR(64) NOT NULL UNIQUE,
    product_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NULL,
    option_overrides JSONB NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

COMMENT ON TABLE gw_device IS '设备实例：流水线 deviceId 对应 device_code';
COMMENT ON COLUMN gw_device.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_device.device_code IS '设备业务编码（流水线 deviceId），全局唯一';
COMMENT ON COLUMN gw_device.product_id IS '所属产品主键，对应 gw_product.id';
COMMENT ON COLUMN gw_device.name IS '设备显示名称';
COMMENT ON COLUMN gw_device.option_overrides IS '功能参数覆盖 JSON（兼容列；新写入优先 EAV gw_device_function_override）';
COMMENT ON COLUMN gw_device.enabled IS '是否启用';
COMMENT ON COLUMN gw_device.created_at IS '创建时间';
COMMENT ON COLUMN gw_device.updated_at IS '最后更新时间';

-- 设备端点：设备绑定通道 + 寻址片
CREATE TABLE gw_device_endpoint (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    channel_id VARCHAR(36) NOT NULL,
    address JSONB NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    UNIQUE (device_id, channel_id)
);

COMMENT ON TABLE gw_device_endpoint IS '设备端点：设备与共享通道的绑定及寻址参数';
COMMENT ON COLUMN gw_device_endpoint.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_device_endpoint.device_id IS '设备主键，对应 gw_device.id';
COMMENT ON COLUMN gw_device_endpoint.channel_id IS '通道主键，对应 gw_channel.id';
COMMENT ON COLUMN gw_device_endpoint.address IS '寻址参数 JSON（兼容列；新写入优先 EAV gw_endpoint_property）';
COMMENT ON COLUMN gw_device_endpoint.created_at IS '创建时间';

-- 产品功能：产品侧功能模板默认参数
CREATE TABLE gw_product_function (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(64) NOT NULL,
    access_type VARCHAR(32) NOT NULL,
    access_permission INTEGER NOT NULL DEFAULT 2,
    capability_type VARCHAR(64) NULL,
    option_schema JSONB NULL,
    protocol_mapping JSONB NULL,
    sort_index INTEGER NOT NULL DEFAULT 0,
    write_access_type VARCHAR(16) NOT NULL DEFAULT 'VALUE',
    description VARCHAR(128) NULL,
    UNIQUE (product_id, function_id)
);

COMMENT ON TABLE gw_product_function IS '产品功能：产品下挂载的逻辑功能定义与默认参数';
COMMENT ON COLUMN gw_product_function.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_product_function.product_id IS '所属产品主键，对应 gw_product.id';
COMMENT ON COLUMN gw_product_function.function_id IS '功能业务 ID，如 remoteControlDoor / fn.read';
COMMENT ON COLUMN gw_product_function.access_type IS '访问类型：READ / WRITE';
COMMENT ON COLUMN gw_product_function.access_permission IS '读写权限位：READ=1，WRITE=2，可按位组合';
COMMENT ON COLUMN gw_product_function.capability_type IS '南向能力类型，用于拉取 FunctionTemplate';
COMMENT ON COLUMN gw_product_function.option_schema IS '功能默认参数 JSON（兼容列；新写入优先 EAV gw_function_property）';
COMMENT ON COLUMN gw_product_function.protocol_mapping IS '协议层映射 JSON（如寄存器 offset，不含从站号）';
COMMENT ON COLUMN gw_product_function.sort_index IS '同产品内排序，数值越小越靠前';
COMMENT ON COLUMN gw_product_function.write_access_type IS '写访问模式：VALUE（标量选项）/ STRUCT（多字段）';
COMMENT ON COLUMN gw_product_function.description IS '功能说明（可覆盖能力模板 description）';
-- 通道连接属性（EAV）：挂 gw_channel
CREATE TABLE gw_channel_property (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    channel_id VARCHAR(36) NOT NULL,
    attribute VARCHAR(64) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    description VARCHAR(128) NULL
);

COMMENT ON TABLE gw_channel_property IS '通道连接属性 EAV：对齐旧 DriverProperty';
COMMENT ON COLUMN gw_channel_property.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_channel_property.channel_id IS '所属通道主键，对应 gw_channel.id';
COMMENT ON COLUMN gw_channel_property.attribute IS '属性名，如 host / port / username';
COMMENT ON COLUMN gw_channel_property.attribute_value IS '属性值（文本落库，按 data_type 解释）';
COMMENT ON COLUMN gw_channel_property.data_type IS '数据类型：string / int / boolean / select / password 等';
COMMENT ON COLUMN gw_channel_property.description IS '属性说明';

-- 端点寻址属性（EAV）：挂 gw_device_endpoint
CREATE TABLE gw_endpoint_property (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    endpoint_id VARCHAR(36) NOT NULL,
    attribute VARCHAR(64) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    description VARCHAR(128) NULL
);

COMMENT ON TABLE gw_endpoint_property IS '端点寻址属性 EAV：如 slaveId / topic / deviceSerialNo';
COMMENT ON COLUMN gw_endpoint_property.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_endpoint_property.endpoint_id IS '所属端点主键，对应 gw_device_endpoint.id';
COMMENT ON COLUMN gw_endpoint_property.attribute IS '属性名';
COMMENT ON COLUMN gw_endpoint_property.attribute_value IS '属性值（文本落库）';
COMMENT ON COLUMN gw_endpoint_property.data_type IS '数据类型';
COMMENT ON COLUMN gw_endpoint_property.description IS '属性说明';

-- 产品功能属性（EAV）：挂 gw_product_function
CREATE TABLE gw_function_property (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_function_id VARCHAR(36) NOT NULL,
    attribute VARCHAR(64) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    description VARCHAR(128) NULL
);

COMMENT ON TABLE gw_function_property IS '产品功能参数属性 EAV：对齐旧 FunctionProperty';
COMMENT ON COLUMN gw_function_property.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_function_property.product_function_id IS '所属产品功能主键，对应 gw_product_function.id';
COMMENT ON COLUMN gw_function_property.attribute IS '属性名，如 command / offset';
COMMENT ON COLUMN gw_function_property.attribute_value IS '属性值（文本落库）';
COMMENT ON COLUMN gw_function_property.data_type IS '数据类型';
COMMENT ON COLUMN gw_function_property.description IS '属性说明';

-- 设备级功能参数覆盖（EAV）：按 device + functionId
CREATE TABLE gw_device_function_override (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    function_id VARCHAR(64) NOT NULL,
    attribute VARCHAR(64) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    description VARCHAR(128) NULL
);

COMMENT ON TABLE gw_device_function_override IS '设备功能参数覆盖 EAV：相对产品功能默认值的差异，按 function_id 作用域';
COMMENT ON COLUMN gw_device_function_override.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_device_function_override.device_id IS '设备主键，对应 gw_device.id';
COMMENT ON COLUMN gw_device_function_override.function_id IS '功能业务 ID，与 gw_product_function.function_id 对齐';
COMMENT ON COLUMN gw_device_function_override.attribute IS '覆盖属性名';
COMMENT ON COLUMN gw_device_function_override.attribute_value IS '覆盖属性值（文本落库）';
COMMENT ON COLUMN gw_device_function_override.data_type IS '数据类型';
COMMENT ON COLUMN gw_device_function_override.description IS '属性说明';

-- STRUCT 写模式字段定义
CREATE TABLE gw_write_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_function_id VARCHAR(36) NOT NULL,
    field VARCHAR(64) NOT NULL,
    description VARCHAR(128) NULL,
    access_data_type VARCHAR(32) NOT NULL,
    transform_data_type VARCHAR(32) NOT NULL,
    ignore_request BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE gw_write_option IS 'STRUCT 写字段定义：多字段写模式下的字段元数据';
COMMENT ON COLUMN gw_write_option.id IS '主键 雪花ID；亦可作为 gw_write_value_option.parent_id';
COMMENT ON COLUMN gw_write_option.product_function_id IS '所属产品功能主键';
COMMENT ON COLUMN gw_write_option.field IS '写字段名';
COMMENT ON COLUMN gw_write_option.description IS '字段说明';
COMMENT ON COLUMN gw_write_option.access_data_type IS '设备侧原始数据类型';
COMMENT ON COLUMN gw_write_option.transform_data_type IS '业务侧转换后数据类型';
COMMENT ON COLUMN gw_write_option.ignore_request IS '是否忽略请求体中该字段';

-- 写值选项：VALUE 模式 parent=product_function；STRUCT 模式 parent=write_option
CREATE TABLE gw_write_value_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    parent_id VARCHAR(36) NOT NULL,
    description VARCHAR(128) NULL,
    option_value VARCHAR(128) NOT NULL,
    mapping_value VARCHAR(128) NULL,
    access_data_type VARCHAR(32) NOT NULL,
    transform_data_type VARCHAR(32) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE gw_write_value_option IS '写值选项：设备值 ↔ 业务映射；VALUE 时 parent 为产品功能，STRUCT 时 parent 为写字段';
COMMENT ON COLUMN gw_write_value_option.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_write_value_option.parent_id IS '父节点：product_function.id（VALUE）或 write_option.id（STRUCT）';
COMMENT ON COLUMN gw_write_value_option.description IS '选项说明，如 开门 / 关门';
COMMENT ON COLUMN gw_write_value_option.option_value IS '设备侧/协议侧取值，如下发 command=open';
COMMENT ON COLUMN gw_write_value_option.mapping_value IS '业务侧映射值，空则等同 option_value';
COMMENT ON COLUMN gw_write_value_option.access_data_type IS '原始数据类型';
COMMENT ON COLUMN gw_write_value_option.transform_data_type IS '转换后数据类型';
COMMENT ON COLUMN gw_write_value_option.is_default IS '是否默认选项';

-- 读值选项：挂产品功能
CREATE TABLE gw_read_value_option (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_function_id VARCHAR(36) NOT NULL,
    description VARCHAR(128) NULL,
    option_value VARCHAR(128) NOT NULL,
    mapping_value VARCHAR(128) NULL,
    access_data_type VARCHAR(32) NOT NULL,
    transform_data_type VARCHAR(32) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE gw_read_value_option IS '读值选项：设备上报值 ↔ 业务映射';
COMMENT ON COLUMN gw_read_value_option.id IS '主键 雪花ID';
COMMENT ON COLUMN gw_read_value_option.product_function_id IS '所属产品功能主键';
COMMENT ON COLUMN gw_read_value_option.description IS '选项说明';
COMMENT ON COLUMN gw_read_value_option.option_value IS '设备侧取值';
COMMENT ON COLUMN gw_read_value_option.mapping_value IS '业务侧映射值';
COMMENT ON COLUMN gw_read_value_option.access_data_type IS '原始数据类型';
COMMENT ON COLUMN gw_read_value_option.transform_data_type IS '转换后数据类型';
COMMENT ON COLUMN gw_read_value_option.is_default IS '是否默认选项';
