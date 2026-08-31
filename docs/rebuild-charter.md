# 控制网关重建章程

> 状态：待新仓库落地。本文是从现网关拆出的整改契约，不是对旧仓库的补丁清单。  
> 日期：2026-08-28  
> 决策：新建项目实施；**不接入、不依赖**已废弃的 `deadman-control-pipeline`。该仓库仅作历史设计出处，概念可吸收，代码与 Maven 坐标一律不引用。

---

## 0. 怎么用这份文档

1. 在新仓库创建后，把本文原样放入 `docs/rebuild-charter.md`，作为 SPI / 模块边界的冻结稿。
2. 文中标记分为两类，见 §1。实现时只允许按 **[GW-***]** 对照旧行为、按 **[REQ-***]** 验收；**[HIST-***]** 不得变成 `com.mtfm:core-pipeline` 依赖。
3. Spring Security 不在本文范围，由后续自行集成。
4. 旧仓库 `inshin-control-gateway` 在新项目跑通一条协议前保持可运行，作为对照，不在旧仓做绞杀式大改。

---

## 1. 出处标记约定

| 前缀 | 含义 | 使用规则 |
| --- | --- | --- |
| **GW** | 现网关 `inshin-control-gateway` 源码或配置 | 问题证据、应保留的领域概念、迁移对照 |
| **HIST** | 废弃设计 `deadman-control-pipeline` | 只引用设计意图与文件路径；禁止 Maven 接入、禁止复制模块目录当内核 |
| **REQ** | 本次重建的硬性需求（你在对话中确认的五块） | 新项目验收条款 |
| **DEBT** | 现网关必须消化的劣势（已剔除 Spring Security） | 新项目禁止再现 |

路径基准：

- 现网关：`/Users/moyang/make_the_fucking_money/inshin-control-gateway`
- 废弃设计：`/Users/moyang/make_the_fucking_money/deadman-control-pipeline`（只读参考）

---

## 2. 总原则

新项目是边缘控制网关：远程指令入站、能力执行、结果出站。宏观流冻结为：

```text
指令入站 → 入站包装（插件链）→ 调度核心贴胶 → 能力执行 → 出站包装（插件链）→ 出站贴胶 → 结果出站
```

[REQ-FLOW] 上述阶段顺序不可被插件或能力跳过或重排。扩展没有 `pipeline.next()`。

硬约束：

| ID | 约束 |
| --- | --- |
| [REQ-SEAL] | 信封贴胶后只读。插件只改未贴胶草稿；贴胶由调度核心的封装代理完成。 |
| [REQ-PLUGIN] | 插件 `support==true` 才入链；按 `order` 升序串行；失败即停，不贴胶、不进 Execute。 |
| [REQ-CAP] | 协议经统一能力注册器接入；同一协议不同 endpoint 用「通道 + 地址」，不用再注册一套协议实现。 |
| [REQ-QUEUE] | 大量设备下用有界队列保稳定：命令反压、遥测可丢最旧、同设备串行、跨设备并行。 |
| [REQ-PRODUCT] | 产品持有同质功能；设备引用产品并覆盖实例值。从站/子设备 key 属于地址片，不属于 Option。 |
| [REQ-OPTION] | `Option` 支持标量、多级对象、多级数组。 |
| [REQ-DB] | 配置域必须能在 **MySQL** 与 **PostgreSQL** 上运行；通过配置切换，不改业务代码。运行时核心（信封/流水线/能力）不依赖 JDBC。本地可用 H2，但 H2 不是生产存储。 |
| [REQ-NO-HIST-DEP] | 不依赖 `deadman-control-pipeline` 任一 artifact。 |
| [REQ-NO-SECURITY] | 本文不规定认证授权实现。 |

应保留的旧资产（不要在新项目里丢掉）：

| 资产 | 出处 |
| --- | --- |
| 驱动 / 设备 / 功能 分层意识 | [GW-DRIVER] `property/.../model/Driver.java`；[GW-DEVICE] `property/.../model/Device.java` |
| 读写权限与数据类型 | [GW-PERM] `core/.../enums/AccessPermission.java`；[GW-DTYPE] `core/.../enums/AccessDataType.java` |
| 驱动字段外置模板 | [GW-YAML] `property/src/main/resources/application-property.yml` 中 `inshin.drivers` |
| 装配策略接口方向 | [GW-REG] `plugins/automatic-assembly/.../ComponentHandleRegister.java`；[GW-EXEC] `integration/.../cmd/CommandExecutor.java` |
| 技术栈 | Java 21、Spring Boot 3.x（新仓库自定版本，不要复制旧 POM 的依赖冲突） |

---

## 3. 现网关债务登记（新项目禁止再现）

Spring Security 已按你的要求排除。H2 对他人开放与鉴权框架无关，仍登记。

### 3.1 正确性与凭证

| ID | 问题 | 出处 | 新项目要求 |
| --- | --- | --- | --- |
| [DEBT-D1] | 运行时状态全是 static Holder | [GW-HOLD-C] `integration/.../context/ComponentHolder.java` L30–39；[GW-HOLD-M] `integration/.../protocol/modbus/ModbusPointHolder.java` L13–37；[GW-HOLD-Q] `integration/.../protocol/mqtt/MqttSubscriptionPointHolder.java`；[GW-HOLD-H] `integration/.../hikvision/service/HikvisionAccessControlServiceHolder.java` L36–48；[GW-HOLD-P] `plugins/control-message/.../handler/StaticMessagePool.java` L21–26, `getInstance()` L86–88 | 状态进可注入的注册表 / 绑定表，禁止进程级 static Map 当运行时 |
| [DEBT-D2] | 海康注销清空全部客户端 | [GW-HIK-CLR] `integration/.../DeviceGatewayHikvisionAccessControlService.java` L132–135：`unregisterClient` → `HikvisionAccessControlServiceHolder.clear()` | 按 `deviceId` / `channelId` 解绑；通道引用计数为 0 才关连接 |
| [DEBT-D3] | 活点位被 LRU 丢掉 | [GW-LRU] `integration/.../cache/IntegrationValueCache.java` L32, L75–80：`LinkedHashMap` 容量上限驱逐 | 不以 LRU 当设备状态库。遥测走有界队列，满则丢最旧 |
| [DEBT-D4] | 密钥与真环境进仓库 | [GW-SECRET] `app/src/main/resources/application.yml` L14–16（云端 MQTT 明文）；[GW-TEST-PWD] `integration/src/test/.../HikvisionAccessControlServiceTest.java` L46–49 等 | 凭证只来自环境 / 外部配置；联调测试默认不进 CI |
| [DEBT-D5] | H2 控制台与 TCP 对他人开放 | [GW-H2] `property/src/main/resources/application-property.yml` L8–18：`web-allow-others`、`-tcpAllowOthers` | 默认关闭外网/旁路访问 |
| [DEBT-D23] | 配置只绑 H2 文件库，无 MySQL / PostgreSQL | [GW-H2-DS] `property/src/main/resources/application-property.yml` L1–6 `jdbc:p6spy:h2:file:...`；[GW-SCHEMA] `property/src/main/resources/db/schema-h2.sql` 仅 H2 DDL（无外键、`VARCHAR` 无长度） | 见 §4.6：同一套迁移脚本覆盖 MySQL 与 PostgreSQL |

### 3.2 结构耦合

| ID | 问题 | 出处 | 新项目要求 |
| --- | --- | --- | --- |
| [DEBT-D6] | 插件直连协议内部 Holder；app 写死插件 | [GW-YAYA] `plugins/yaya-door-lock/.../DeviceCommandInterceptor.java` import `MqttSubscriptionPointHolder`；[GW-APP-POM] `app/pom.xml` 直接依赖 automatic-assembly / yaya / struct | 插件只依赖信封 SPI；能力只依赖能力 SPI；宿主只 `register` |
| [DEBT-D7] | 一个类同时管连接生命周期和命令执行 | [GW-MB-SVC] `integration/.../bean/DynamicModbusService.java` L51：同时 `CommandExecutor` + `IntegrationItemRegistry`；[GW-MQ-SVC] `integration/.../mqtt/DynamicMqttService.java` L50 同样 | 拆成 Decode / Execute / Channel 生命周期三个口 |
| [DEBT-D8] | 运行时往 Spring 注册/删除 BeanDefinition | [GW-BEAN] `integration/.../DynamicModbusEndpointRegistry.java` L110–123, L201–213 `registerBeanDefinition` / `removeBeanDefinition` | 连接由能力总线自管，不占用容器 Bean 名 |
| [DEBT-D9] | 装配只加不撤；CRUD 后全量刷新 | [GW-ASM] `plugins/automatic-assembly/.../DriverAutomaticAssemblyHandler.java` L91–107 `refreshAssembly` 只 `register`；[GW-OP] `plugins/automatic-assembly/.../listener/OperateEventListener.java` L54–57 每次 `refreshAssembly()` | 按设备 unload 再 load；禁止并行 stream 改运行时 |
| [DEBT-D10] | 上行处理器无契约，靠 @Primary 偷换 | [GW-MSG] `integration/.../message/IntegrationMessageHandler.java` L10 空标记接口 | 北向 `Publisher.publish`；下行 `Ingress.accept` |
| [DEBT-D11] | MQTT 读权限校验被注释 | [GW-MQTT-PERM] `plugins/automatic-assembly/.../MqttComponentHandleRegister.java` L195–201 | 功能 accessType 由产品定义，Execute 前由目录校验 |

### 3.3 质量（随五块落地，不单开一期）

| ID | 问题 | 出处 |
| --- | --- | --- |
| [DEBT-D12] | `parallelStream` 改共享注册 | [GW-ASM] L105；[GW-PROP-MGR] `property/.../manager/IntegrationPropertyManager.java` L86 `drivers.parallelStream()` |
| [DEBT-D13] | Holder 内 `ArrayList` 非线程安全 | [GW-HOLD-M] `ModbusPointHolder.java` L139 `computeIfAbsent(..., ArrayList)` |
| [DEBT-D14] | 缓存写锁内向 Reactor sink 发事件 | [GW-CACHE-LOCK] `IntegrationValueCache.java` L112–146 |
| [DEBT-D15] | 消息池双重限流 | [GW-POOL] `StaticMessagePool.java` L184–187 `limitRate` + `delayElements` |
| [DEBT-D16] | 依赖版本打架 | [GW-UTIL-POM] `util/pom.xml`：Boot 3 + `javax.validation` 2.0 + hibernate-validator 6.2；Jackson 2.20 与 2.15 |
| [DEBT-D17] | 工具类膨胀 | [GW-JSON] `util/.../JSONUtils.java`（约 817 行） |
| [DEBT-D18] | SPI 基类空实现 | [GW-ASM] `DriverAutomaticAssemblyHandler.java` L121–123 `execute(...) {}` |
| [DEBT-D19] | 模块未接入 app | [GW-RTU] `plugins/modbus-rtu-to-mqtt` 在父 POM，不在 `app/pom.xml` |
| [DEBT-D20] | 动态 Bean 与 native-image 不兼容 | [GW-README] 根目录 `README.md` 仅有手工 `native-image`；无 reflect-config |
| [DEBT-D21] | Option 三套模型、无独立 Function 实体 | [GW-OPT] `core/.../Option.java`；[GW-WVO] `property/.../WriteValueOption.java`；功能只在 DTO `DeviceFunctionData` |
| [DEBT-D22] | 测试打真设备 | [GW-TEST] `integration/src/test/.../HikvisionAccessControlServiceTest.java` 无断言、含真实 host |

---

## 4. 五块架构（新项目实现规格）

历史五阶段叙述见 [HIST-STAGE] `deadman-control-pipeline/docs/architecture-pipeline.md` §5。新项目**自己实现**同等阶段，不引用该模块。

### 4.1 信封载体

[REQ-ENV] 入站信封与出站信封各有独立标识；组装完成（贴胶）后不可修改。

| 对象 | 何时 | 可变性 | 谁能碰 |
| --- | --- | --- | --- |
| RawInbound | 协议字节刚进入 | 只读原始载荷 | Driver.decode |
| EnvelopeDraft | 解码后、贴胶前 | 插件返回下一份草稿 | InboundPlugin 链，按 order 串行 |
| Envelope（已贴胶） | 封装代理 `seal` 之后 | 不可变 | 调度核心、Executor 只读 |
| OutboundDraft | Correlate 组回执后、贴胶前 | 可改 body / headers，禁止改通道提示 | OutboundPlugin 链 |
| OutboundMessage（已贴胶） | 出站 `seal` 之后 | 不可变 | Publisher 只读 |

建议字段（入站已贴胶信封）：

| 字段 | 说明 |
| --- | --- |
| envelopeId | 核心生成，追踪 |
| requestId | 调用方关联，可空 |
| direction | INBOUND / OUTBOUND |
| kind | COMMAND / TELEMETRY / RESPONSE |
| deviceId | 业务设备编码（子设备，不是网关） |
| functionId | COMMAND 必填 |
| capabilityType | 南向能力类型 |
| payload | 开放业务属性，禁止放连接句柄 |
| headers | 传输头，禁止放连接句柄 |
| trace | 插件/阶段轨迹 |
| error | 失败即停后写入 |
| createdAt / deadlineAt | 超时由核心判定 |

历史对照（勿依赖）：[HIST-ENV] `core-spi/.../model/Envelope.java` 为 record + `withPayload` 副本改写。新项目与它的差异是 **[REQ-SEAL]**：插件链结束后由 Sealer 贴胶，贴胶类型不对外提供 mutator。

旧网关对照：[GW-MSG-HDR] 现用 Spring `Message` + `DynamicBeanName.DEVICE_HEADER` / `EVENT_HEADER` 散装路由，新项目用信封字段替代。

### 4.2 调度核心（流水线）

[REQ-CORE] 调度核心是唯一运行时。入站包装、找能力、执行、出站包装都由它推进。

旧入口（应淘汰，不要在新项目复制）：[GW-DISP] `integration/.../cmd/ConcurrentCommandSupportDispatcher.java` L59–106：从 header 取设备号，查 `ComponentHolder`，遍历 `CommandExecutor.support`。

阶段（新项目自己实现）：

```text
① Ingress    RawInbound → decode → Draft → 有界入站队列
② Normalize  InboundPlugin 链（失败即停）→ 贴胶 → COMMAND 物化作业 / TELEMETRY 物化事件
③ Execute    仅 COMMAND；按 deviceId 串行；能力 Executor 同步返回
④ Correlate  组出站草稿（只写 channelHint，不写 Publisher 类名）
⑤ Egress     OutboundPlugin → 贴胶 → 双队列 → 按 hint 选 Publisher
```

- COMMAND 走 Execute。
- TELEMETRY 跳过 Execute。
- RESPONSE 由 Correlate 从执行结果组装，不经入站插件。

有界队列（概念来源 [HIST-Q] `core-pipeline/.../concurrent/DeviceSerialScheduler.java`、`DropOldestQueue.java`、`PipelineSettings.java`；**自行实现，不 copy 包名**）：

| 队列 | 满时行为 |
| --- | --- |
| 入站 COMMAND / 原始入站 | 有界，超时未入队则拒绝并回执 |
| 入站 TELEMETRY | 有界，丢最旧 |
| 每设备 Execute | 有界 FIFO，满则 `DEVICE_QUEUE_OVERFLOW`；同设备串行，跨设备并行；空闲槽可回收 |
| 出站 HIGH（命令回执） | 有界反压，禁止静默丢弃 |
| 出站 NORMAL（遥测） | 有界，丢最旧 |

分池：Ingress / Execute / Egress 隔离。扩展禁止自建业务队列调用下一阶段。协议库 I/O 线程允许，完成必须交回 Ingress 或作为 `execute()` 返回值。

建议默认（可配置，勿写死）：每设备 Execute 容量 64；execute workers 4；入站命令 256；遥测 1024。须文档化：无全局 maxDeviceSlots 时，伪造海量 deviceId 会造成槽尖峰——新实现应考虑活跃槽上限。

### 4.3 插件拓展

[REQ-PLUGIN-API] 插件只能重新装配入/出站语义，不能推进阶段，不能持有 Driver/Publisher。

```text
for plugin in sortByOrder(plugins) where plugin.support(draft):
    result = plugin.apply(draft)
    if REJECT or ERROR: stop; 不贴胶; 命令路径组失败回执
    if DROP: 丢弃（命令默认不用）
    draft = result.next
core.sealer.seal(draft)  // 「贴胶」
```

旧代码迁移对照（新项目重写为插件，勿再依赖 property/integration 内部类）：

| 旧代码 | 新角色 |
| --- | --- |
| [GW-YAYA] `DeviceCommandInterceptor` | InboundPlugin，order 最低，按设备前缀 support |
| [GW-STRUCT] `plugins/automatic-struct-command/.../StructCommandInterceptor.java` | InboundPlugin，STRUCT 默认 option 填充 |
| [GW-CLOUD-MAP] `plugins/control-message/.../CloudIntegrationMessageHandler.java` L81–84 选项映射 | OutboundPlugin 或目录投影，禁止再进 static 消息池 |

历史 API 形状（勿 import）：[HIST-IN] `core-spi/.../plugin/InboundPlugin.java`（`name/order/support/apply`）；[HIST-OUT] `OutboundPlugin.java`（禁止改 channelHint）。

### 4.4 能力集成

[REQ-CAP-REG] 提供统一能力注册器。登记一项能力必须带必填字段说明，便于调用方配置 Channel / Address。

能力最低契约：

| 提供项 | 说明 |
| --- | --- |
| capabilityType | 如 `MODBUS` / `MQTT` / `HIKVISION_ENTRANCE` |
| connectionSchema | Channel.connection 必填字段 |
| addressSchema | DeviceEndpoint.address 必填字段 |
| Driver.decode | RawInbound → EnvelopeDraft |
| FunctionExecutor.execute | 已贴胶命令 → ExecutionResult；禁止自行 publish |
| （北向）Publisher.publish | 已贴胶 OutboundMessage → PublishResult |

**通道 + 能力** 解决「同一协议、endpoint 不同」：

```text
Channel（共享会话）     Address（寻址片）
gw-1 host:502          设备 A unitId=1
                       设备 B unitId=2
```

业务 `deviceId` 只用 A/B。网关编码可以存在资产里，但不得作为流水线 `deviceId`。

通道生命周期：retain/release；引用计数 0 才断开。用来消化 [DEBT-D2]。

旧对照：[GW-MB-SVC] 每个 driver 配置一个 clientId 连接，从站塞在设备属性 `slaveId`（[GW-MB-REG] `ModbusComponentHandleRegister.java` L43, L107, L124–140），没有通道复用模型。

历史对照（勿依赖）：[HIST-CH] `docs/device-protocol-function-config.md` §3；[HIST-CAP] `core-spi/.../capability/Driver.java`、`FunctionExecutor.java`。

现有协议迁入新项目时的能力切分建议：

| 旧模块 | 新能力 |
| --- | --- |
| integration Modbus TCP | capability-modbus（南向） |
| integration 设备 MQTT | capability-mqtt（南向，与云通道分包） |
| integration 海康 ISAPI | capability-hikvision |
| control-message 云 MQTT | capability-cloud（北向 Publisher + 下行 Ingress） |
| [DEBT-D19] modbus-rtu-to-mqtt | 独立能力或删除，禁止再当死模块 |

### 4.5 设备集成（产品化 + Option 树）

保留「配置在 property 侧、运行在核心侧」的方向，但改分层。

```text
Product（功能 + 协议映射，映射不含从站号）
    └── Device（deviceCode = deviceId，引用 productId，覆盖实例值）
            └── DeviceEndpoint（channelId + address）
                    └── Channel（capabilityType + connection）
```

[REQ-ADDR] Modbus 多从站：Channel 一条 TCP，Address 为 `unitId`。不要把 unitId 放进 Option 或产品功能定义。

设备可覆盖的「特定值」指功能参数默认值（例如某台的默认转速），不是寻址片。

#### Option 扩展

现状：[GW-OPT] `core/.../Option.java` L38 `optionValue` 为 `String`；[GW-VAT] `core/.../enums/ValueAccessType.java` 仅 `VALUE` / `STRUCT`；[GW-WVO] `WriteValueOption.parentId` 只能做一层结构挂接。

新模型：

```text
Option
  description
  accessDataType / transformDataType
  isDefault
  mappingValue            // 读侧映射
  logicalNodeEnable       // 落到 OBJECT 节点，不再空置
  value: OptionValue

OptionValue
  SCALAR(raw)
  OBJECT(fields: Map<String, Option>)
  ARRAY(items: List<Option>)
```

`AccessDataType` 在 [GW-DTYPE] 现有 INT16…BOOLEAN 上增加 `OBJECT`、`ARRAY`。

持久化建议：`parent_id` + `sort_index` + `node_kind`（SCALAR/OBJECT/ARRAY）；顶层仍可被功能引用。旧扁字符串反序列化为 SCALAR，保证可迁移 [DEBT-D21]。

产品功能上的 option 是 schema + 默认值；设备覆盖只存差异节点，装配时与产品树合并。

### 4.6 配置存储：MySQL 与 PostgreSQL

[REQ-DB] 产品 / 设备 / 通道 / Option 树等配置只存在 catalog，不进 runtime。存储必须同时支持 **MySQL 8+** 与 **PostgreSQL 14+**，用 profile 或 `spring.datasource` 切换。

约束：

| 项 | 要求 |
| --- | --- |
| 边界 | `core-spi` / `core-runtime` / `capability-*` / `plugin-*` 不出现 JDBC、MyBatis、Hibernate |
| 方言 | 一套逻辑 schema；用 Flyway 或 Liquibase，按库分目录（如 `db/migration/mysql`、`db/migration/postgresql`），禁止把 H2 专用 DDL 直接拿到生产 |
| 类型 | 嵌套 Option / 协议 mapping / address 用 JSON：MySQL `JSON`，PostgreSQL `JSONB`；布尔用各自布尔类型，不用 `INT deleted` 兼作开关除非两库统一约定 |
| 标识 | 主键策略两库一致（推荐应用侧雪花/UUID，或两库都用 identity，禁止只在一边用 H2 的无类型 `VARCHAR`） |
| 本地 | H2 仅开发/单测可选；生产 profile 不得指向 H2。H2 外开问题仍受 [DEBT-D5] 约束 |
| 测试 | catalog 集成测试须在 MySQL 与 PostgreSQL 各跑通（Testcontainers）；不得只测 H2 就算通过 [REQ-DB] |

历史对照（勿依赖）：[HIST-PERSIST] `deadman-control-pipeline/docs/modules.md` 中「内核零配置、持久化经 SPI」——新项目只吸收「核心不写库」，自己实现 catalog 的双库迁移。

旧对照：[GW-SCHEMA] 十张表无外键、无 MySQL/PG 脚本；[GW-H2-DS] 数据源写死文件 H2 + p6spy。

---

## 5. 新仓库建议模块

```text
new-gateway/
├── docs/rebuild-charter.md     # 本文
├── app                         # 宿主：register 能力/插件/设备绑定；不写协议
├── core-spi                    # 信封、草稿、插件、能力、目录端口（零协议 SDK）
├── core-runtime                # 五阶段、贴胶、有界队列、分池（只依赖 core-spi）
├── catalog                     # 产品/设备/通道 CRUD + Catalog 投影；MySQL / PostgreSQL（H2 仅本地可选）
├── capability-modbus
├── capability-mqtt
├── capability-hikvision
├── capability-cloud
├── plugin-yaya                 # 仅依赖 core-spi
└── plugin-struct
```

依赖方向：

```text
app → runtime + spi + catalog + 所选 capability/plugin
runtime → spi only
capability-* → spi + 协议 SDK
plugin-* → spi only
catalog → spi + JDBC 实现（MySQL / PostgreSQL 驱动按 profile 引入）

禁止：
  spi / runtime → 任何协议 SDK
  plugin-* → capability-* 或 runtime
  capability-* → plugin-* 或 runtime
  南向能力 → 北向能力
```

---

## 6. 与废弃设计的边界（防止误接入）

| 可吸收的意图 | 出处 | 新项目怎么做 |
| --- | --- | --- |
| 五阶段、失败即停、一设备一南向协议 | [HIST-ARCH] `docs/architecture-pipeline.md` §2–§5 | 写入本文 §4.2，自己实现 |
| 有界队列与设备串行 | [HIST-Q] `DeviceSerialScheduler` / `DropOldestQueue` | 自己实现；可改包名与类名 |
| 插件 support/order/apply | [HIST-IN] [HIST-OUT] | 自己定义接口；贴胶是额外步骤 |
| 产品 / 通道 / 地址 | [HIST-CH] `docs/device-protocol-function-config.md` | 落在 catalog，字段可按本文裁剪 |
| 配置换库 | [HIST-PERSIST] 内核不写库 | catalog 双库迁移，runtime 无 JDBC |
| Spring 可选装配 | [HIST-SPRING] `pipeline-spring-boot-starter` | 新项目若用 Boot，自己写 AutoConfiguration，不要依赖 `com.mtfm` |

明确不做：

- 不 `mvn install` 后引用 `com.mtfm:core-pipeline`
- 不把 `deadman-control-pipeline` 当 submodule
- 不复制 `com.mtfm.pipeline` 包名（避免以后误以为还在用旧内核）

---

## 7. 从旧网关迁行为时的对照表

| 旧概念 | 新概念 |
| --- | --- |
| Spring `Message` + header 设备号 | 已贴胶 Envelope |
| `ConcurrentCommandSupportDispatcher` | 调度核心 Execute 阶段 |
| `CommandExecutor` | `FunctionExecutor` |
| `IntegrationItemRegistry` + 动态 Bean | Channel 总线 retain/release |
| `ComponentHolder` / `*PointHolder` | DeviceBinding + Point 目录（非 static） |
| `IntegrationMessageHandler` @Primary | 北向 Publisher |
| `StaticMessagePool` | 核心 Egress 有界队列 |
| `Driver` 表一行 ≈ 一条连接 | Channel；多设备共享 |
| 设备属性 `slaveId` | DeviceEndpoint.address.unitId |
| 设备上的 function 行 | ProductFunction + 设备覆盖 |
| `inshin.drivers` YAML | 能力 `connectionSchema` / `addressSchema` 的文档与校验来源 |
| 仅 H2 文件库 | catalog：MySQL + PostgreSQL，H2 仅本地 |

---

## 8. 建议落地顺序（在新仓库）

每期结束后应能演示，而不是先拆完再联调。

| 期 | 目标 | 完成标准 |
| --- | --- | --- |
| P0 | 工程骨架 + 凭证策略 | 无明文口令进库；H2/管理面默认不外开 |
| P1 | spi + runtime 空转 | loopback 能力：submit 一条 COMMAND，拿到贴胶后的回执；插件链失败即停 |
| P2 | Option 树 | 对象/数组可序列化；旧扁 String 能读 |
| P3 | catalog 产品/设备/通道 + 双库 | 一 Channel 两 Address 能投影；**同一套迁移在 MySQL 与 PostgreSQL 上可启动**；Testcontainers 覆盖两库 |
| P4 | Modbus 能力 | 共享 TCP + 不同从站执行正确；无 BeanDefinition 动态注册 |
| P5 | MQTT 南向 + 云北向 | 上下行走 Ingress/Publisher；无 `StaticMessagePool.getInstance` |
| P6 | 海康 + yaya/struct 插件 | 解绑一台不影响其它；插件源码不引用协议内部类 |
| P7 | 单测与债务闭合 | 核心路径单测；对照 §3 逐条确认 DEBT 未再现（含 [DEBT-D23]） |

---

## 9. 验收反例（直接对应债务）

新项目 code review 若出现下列模式，视为未按本章程实施：

1. `static final Map` 充当设备/点位/会话表（[DEBT-D1]）
2. `unregister` 调用全局 `clear()`（[DEBT-D2]）
3. 用 LRU 缓存当「当前点位值库」（[DEBT-D3]）
4. 配置文件或测试里写死 broker 口令、设备密码（[DEBT-D4]）
5. 插件 import 某协议 Holder / Bus 实现类（[DEBT-D6]）
6. 同一类既 `register(Channel)` 又 `execute(Command)` 且对外只暴露这一个 Bean（[DEBT-D7]）
7. `BeanDefinitionRegistry.registerBeanDefinition` 用于设备连接（[DEBT-D8]）
8. 配置变更只 `register` 不 `unbind`（[DEBT-D9]）
9. 北向靠空标记接口 + `@Primary` 替换（[DEBT-D10]）
10. `pom` 出现 `com.mtfm` 的 pipeline 坐标（[REQ-NO-HIST-DEP]）
11. catalog 只提供 H2 schema、或 SQL 写死某一库方言导致另一库无法启动（[REQ-DB] / [DEBT-D23]）
12. runtime / capability / plugin 模块引入 JDBC 驱动（[REQ-DB] 边界）

---

## 10. 变更规则

兼容性变更（加字段、加能力类型）可在次版本。下列变更必须升主版本并改本文对应章节：

- 阶段顺序
- 贴胶时机与可变性
- 插件失败即停语义
- 一设备一南向协议
- Channel / Address 与 Option 的职责划分
- 配置存储官方支持的数据库集合（增删 MySQL / PostgreSQL 须改 [REQ-DB]）

对话记录与画布（非冻结源，仅辅助）：

- 现网关评审画布：`~/.cursor/projects/.../canvases/architecture-review.canvas.tsx`
- 方案画布（其中「Maven 接入 pipeline」条款作废，以本文 [REQ-NO-HIST-DEP] 为准）：`.../canvases/refactor-plan.canvas.tsx`
