# UI 接入指南

仓库内 `ui/` 是**独立部署的参考控制台**（Vite + React），不是必须嵌入的组件库。接入其它界面（Vue、Angular、小程序、原生、自研后台）时，只遵守本文的 **HTTP 契约与交互规范** 即可，不必复用 React 或 shadcn。

行为以当前 `ui/` 为准。不确定时对照：

| 主题 | 参考实现 |
| --- | --- |
| 全部 HTTP 调用 | `ui/src/lib/api.ts` |
| 类型与字段名 | `ui/src/lib/types.ts` |
| Schema / EAV 编解码 | `ui/src/lib/schema-form.ts` |
| 字段控件映射 | `ui/src/components/schema-field-control.tsx` |
| 功能目录模式文案 | `ui/src/lib/catalog-mode.ts` |
| 场景定时友好表单 | `ui/src/lib/scene-schedule.ts` |
| 页面信息架构 | `ui/src/App.tsx` |
| 产品类型 | `ui/src/components/product-types-panel.tsx` |
| 通道扫描 | `ui/src/components/channels-panel.tsx` |
| 设备筛选 / 批量 Load / 在线 | `ui/src/components/devices-panel.tsx` |

---

## 1. 定位与边界

网关配置面只有一套契约：前缀 `/catalog` 的 JSON REST。运行时指令也走 catalog（`POST /catalog/devices/{deviceCode}/commands`），不要另造 WebSocket 配置协议。

Vite 开发代理还转发了 `/commands`，那是北向入站通道，**不是**本指南的配置 UI 契约。自建 UI 不要依赖 `/commands` 做产品/设备管理。

当前 catalog **没有认证授权**。独立部署时由接入方在网关前加网关鉴权、或同源托管静态资源。CORS 仅对 `http://localhost:*` / `http://127.0.0.1:*` 开放 `/catalog/**`。

推荐接入顺序：

1. 实现通用 HTTP 客户端（编码、错误、空响应）。
2. 实现 Schema 渲染器 + `PropertyItem` 编解码（几乎所有写接口都依赖它）。
3. 按「能力 → 产品类型 → 通道 → 产品 → 设备 → 指令 → 集群/场景 → 北向」补页面。
4. 用参考控制台对照同一条数据的读写结果。

---

## 2. 传输约定

### 2.1 基址与同源

| 场景 | 做法 |
| --- | --- |
| 与参考 UI 一样本地开发 | 前端开发服务器把 `/catalog` 反代到网关 `http://127.0.0.1:8080`（参考 `ui/vite.config.ts`） |
| 前端独立域名 | 自配 CORS 或走反向代理；默认 CORS 只放行本机 localhost |
| 随网关一起发布 | 把静态资源打进网关 `static/`，浏览器同源请求 `/catalog/...` |

网关默认端口 **8080**。参考控制台开发端口 **5173**。

### 2.2 请求

- 方法：`GET` / `POST` / `PUT` / `DELETE`
- 请求头：写操作带 `Content-Type: application/json`
- 路径与查询参数中的业务键一律 **URL 编码**（`deviceCode`、`functionId`、`capabilityType` 都可能含 `.` `/` 等）
- JSON 字段名 **camelCase**，与 Java record 一致
- 时间：`Instant` / `timerAt` 用 ISO-8601 文本（例如 `2026-09-08T08:00:00+08:00`）
- 布尔、枚举按 JSON 原生类型发送；枚举 wire 为**大写**字符串（`WRITE`、`LISTEN`、`CRON`），Schema `type`/`format` 为**小写**

### 2.3 响应

- 成功：`200` + JSON 体
- 无体成功：`204` 或空 body。客户端应视为成功并返回空值，不要当 JSON 解析失败
- 业务参数错误：`400` `{"error":"..."}`
- 状态冲突（例如设备未 load 就下发）：`409` `{"error":"..."}`
- 展示 **`error` 原文** 给用户（参考控制台用 toast），不要自己映射一套错误码

```ts
// 任意语言等价逻辑
async function request(path, init) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.error || `HTTP ${res.status}`)
  }
  if (res.status === 204) return undefined
  const text = await res.text()
  return text ? JSON.parse(text) : undefined
}
```

### 2.4 分页

列表接口（产品 / 通道 / 设备）统一：

| 查询参数 | 规则 |
| --- | --- |
| `page` | 从 **1** 开始，默认 1 |
| `size` | 默认 20，最小 1，最大 **100** |

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "size": 20,
  "totalPages": 0
}
```

交互约定（与参考列表一致）：

- 仅一页时只显示「共 N 条」，不画翻页
- 多页显示「第 page/totalPages 页 · 共 total 条」
- 删除当前页最后一条且 `page > 1` 时，回到 `page - 1` 再拉列表

产品类型 / 集群 / 场景 / 能力列表**不分页**，一次返回数组。

产品、设备列表支持可选筛选查询参数（均可省略；空串视为未筛选）：

| 列表 | 参数 | 规则 |
| --- | --- | --- |
| 产品 | `name` | 模糊匹配显示名 |
| 产品 | `code` | 模糊匹配产品编码 |
| 产品 | `productTypeId` | 产品类型 **id 或 code** |
| 设备 | `name` | 模糊匹配显示名 |
| 设备 | `deviceCode` | 模糊匹配设备编码 |
| 设备 | `online` | `true` / `online`、`false` / `offline`、`unknown` / `null` |
| 设备 | `productTypeId` | 按产品所属类型过滤（id 或 code） |

参考控制台把筛选项放在表头第二行；改筛选时把 `page` 重置为 1。

---

## 3. 领域模型

```text
Capability（南向能力，进程内登记）
  ├─ connectionSchema   → 通道连接参数
  ├─ addressSchema      → 设备端点寻址（序列号 / slaveId / topic…）
  ├─ functionTemplates  → 预置功能
  └─ probeSupported     → 该能力是否提供通道扫描

ProductType（产品类型，配置库；与 capabilityType 分离）
  └─ 种子 code：ACCESS_CONTROL（门禁）

Product（产品，配置库）
  ├─ 必选 productTypeId
  └─ ProductFunction    → 同质设备共享的功能定义

Channel（共享通道）
  ├─ connection properties（EAV）
  └─ POST .../probe     → 扫描子设备（能力声明 probeSupported 时）

Device（设备实例，路由键 = deviceCode）
  ├─ 引用 productId
  ├─ Endpoint[]：绑到一条 Channel + address properties
  ├─ functionOverrides / field-overrides / topic-overrides
  ├─ loaded：是否已进入运行时（与在线无关）
  └─ online / onlineUpdatedAt：探针或 NATIVE 轮询得到的在线；空=未知
```

硬约束（UI 必须遵守，否则运行时会拒）：

- 一台已 load 的设备在运行时只能绑**一种** `capabilityType`
- 寻址片（序列号、从站号等）属于**端点 properties**，不是设备表字段
- `deviceCode` 即流水线 `deviceId`，全局唯一
- 产品 `code`、通道 `code` 创建后参考 UI **不提供修改**；设备 `deviceCode` **可以改**

### 3.1 身份键

| 对象 | 路径参数 | 说明 |
| --- | --- | --- |
| 产品类型 | `{typeId}` | id 或 code；种子 `ACCESS_CONTROL` 的 id 与 code 相同 |
| 产品 | `{productId}` | 雪花主键；展示用 `code` / `name` |
| 通道 | `{channelId}` | **id 或 code** 都能解析 |
| 设备 | `{deviceCode}` | 优先业务编码；部分接口也能 resolve 主键 |
| 端点 | `{endpointId}` | 主键；`deviceId` 是设备主键，**不是** `deviceCode` |
| 功能 | `{functionId}` | 业务键，如 `remoteControlDoor`、`light.switch` |
| 集群/场景 | `{id}` | id 或 code 都能解析；更新/删除前先按 kind 校验 |

页面里设备、动作组成员、监听目标一律用 **`deviceCode`**，不要存设备主键。

---

## 4. Schema 驱动表单（跨框架核心）

几乎所有「通道连接 / 设备地址 / 指令参数」都不是写死表单，而是服务端下发 `SchemaField[]`，客户端按字段渲染，提交时编成 `PropertyItem[]` 或 `Record<string, unknown>`。

自建 UI 的第一件事：做一个与框架无关的 **Schema 控件映射**。参考实现是 `SchemaFieldControl`，你可以换成 Element Plus、TDesign、Ant Design、原生 input，**映射表必须一致**。

### 4.1 SchemaField

```json
{
  "name": "deviceSerialNo",
  "type": "string",
  "required": true,
  "description": "设备序列号",
  "label": "设备序列号",
  "defaultValue": null,
  "secret": false,
  "choices": [],
  "format": "none"
}
```

`type` 与 `format` **解耦**：`type` 解释落库值，`format` 只决定怎么采集。

**type（小写 wire）**

| type | 兼容别名 | 控件 |
| --- | --- | --- |
| `string` | `text` | 单行输入 |
| `int` | `integer` / `number` / `long` | `number` 输入；提交转 Number |
| `boolean` | `bool` | 开关；提交 `"true"`/`"false"` 或 JSON bool |
| `select` | `enum` / `choice` | 有 `choices` 时下拉；恰好 2 项可用按钮组 |
| `password` | `secret` | 密码框；`secret=true` 同样处理 |
| `json` | `object` | 多行文本，提交时 `JSON.parse` |
| `array` | `list` | 多行文本，提交 JSON 数组 |

**format**

| format | 兼容 | 控件 |
| --- | --- | --- |
| `none` | 默认 | 跟 type |
| `datetime_iso8601` | `datetime` | 日期时间选择，值必须是 ISO-8601 |
| `image_base64` | `image` | 本地选图 → **纯 Base64**（去掉 `data:image/...;base64,` 前缀） |
| `text_list` | 字段名含 `list` 也可走多行 | 多行文本 |

未知 `type` 回落字符串输入。选项文案可本地化（参考：`open`→开门、`close`→关门），**提交值仍用原始 choice**。

### 4.2 PropertyItem（EAV）

通道连接、端点地址、部分覆盖都以列表落库：

```json
{
  "attribute": "host",
  "attributeValue": "10.0.0.8",
  "dataType": "string",
  "description": "Modbus 主机"
}
```

`attributeValue` **一律是字符串**。表单内部用 `Record` 编辑，提交前转换。

```ts
function propertiesToRecord(items) {
  const out = {}
  for (const item of items ?? []) out[item.attribute] = item.attributeValue
  return out
}

function recordToProperties(values, schema) {
  const typeByName = new Map((schema ?? []).map((f) => [f.name, f]))
  return Object.entries(values).map(([attribute, value]) => {
    const field = typeByName.get(attribute)
    return {
      attribute,
      attributeValue: value == null ? "" : String(value),
      dataType: field?.type ? String(field.type) : inferDataType(value),
      description: field?.description ?? "",
    }
  })
}
```

按 schema 从字符串表单组装「指令 arguments」时：

- 空且非必填：跳过（不要提交空串）
- `int`：`Number(raw)`
- `boolean`：`raw === "true" || raw === "1"`
- `array` / `json`：`JSON.parse`，失败则按原字符串（服务端会再校验）

用 `defaultValue` 初始化新建表单。编辑时：已有属性值优先，缺的字段再填默认值。

### 4.3 密钥掩码

通道连接密码、北向 MQTT 密码读回时可能是 **`••••`**（四个圆点）。

更新规则：

- 用户没改：把掩码原样提交 → 服务端**保留旧值**
- 用户改了：提交明文
- 输入框用 password 类型；不要把掩码当成「密码就是这四个点」展示给业务逻辑去校验

### 4.4 从哪里取 schema

| 用途 | 接口 |
| --- | --- |
| 能力总览 / 模板 | `GET /catalog/capabilities` |
| 通道连接表单 | `capability.connectionSchema` 或 `GET /catalog/capabilities/{type}/supported/connection` |
| 设备地址（含序列号） | `capability.addressSchema` 或 `.../supported/address` |
| 预置功能列表 | `GET /catalog/capabilities/{type}/functions` 或 `.../supported/functions` |
| 产品/设备下发表单 | `GET /catalog/devices/{code}/functions` → `FunctionFormView.parameters` + `values` |

`supported/*` 比裸 schema 多带了默认 `properties` / `choiceOptions`，创建通道时优先用它预填。

---

## 5. 推荐信息架构

参考控制台的 Tab，自建 UI 建议保持同一心智，不必同一套视觉：

| 模块 | 职责 |
| --- | --- |
| 设备 | 筛选、勾选、登记、编辑（名称/编码/地址）、启用、单台/批量 Load、Unload、指令、删除；展示在线与已加载 |
| 产品 | 筛选、创建（必选类型，可选 seed 能力）、维护功能 |
| 产品类型 | CRUD；与南向 `capabilityType` 分开 |
| 通道 | 按能力建共享连接；`probeSupported` 时提供「扫描」 |
| 集群 | 手动/编排执行的成员列表，无触发器 |
| 场景 | 成员 + LISTEN / TIMER |
| 北向 | MQTT / HTTP 出站 |
| 能力 | 只读：已登记协议与模板（调试用） |

启动时并行拉取字典，供下拉使用：

```http
GET /catalog/capabilities
GET /catalog/product-types
GET /catalog/products?page=1&size=100
GET /catalog/channels?page=1&size=100
```

设备表用 `productId → Product` 映射显示产品名与 `productTypeName`。通道下拉显示 `code · capabilityType`。能力是否可扫描看 `probeSupported`，不要写死海康。

空态文案约定：先建产品类型与产品、通道，再登记或扫描设备。

---

## 6. 能力

```http
GET /catalog/capabilities
GET /catalog/capabilities/{capabilityType}
GET /catalog/capabilities/{capabilityType}/functions
GET /catalog/capabilities/{capabilityType}/supported/connection
GET /catalog/capabilities/{capabilityType}/supported/address
GET /catalog/capabilities/{capabilityType}/supported/functions
GET /catalog/capabilities/{capabilityType}/supported/functions/{functionId}
```

`CapabilityDescriptor.functionMode`：

| 模式 | 含义 | 产品功能编辑器 |
| --- | --- | --- |
| `FIXED` | 预置功能，字段名与取值由模板锁死 | 只能选模板；编辑仅说明与排序 |
| `CONTRACT` | 锁参数名，放开业务 `functionId` | 自定义 `functionId`（如 `light.switch`），只配来源/常量/映射 |
| `OPEN` | 可扩展字段 | 自定义 `functionId`、读写字段、Topic、载荷编码 |

缺省或未知按 `OPEN`。文案与参考控制台 `catalogModeLabel` 对齐即可。

常见 `capabilityType` 由运行时登记决定（如 `MODBUS`、`MQTT`、`HIKVISION_ENTRANCE`）。UI **不要写死**类型列表，一律拉 `/catalog/capabilities`。

`CapabilityDescriptor.probeSupported === true` 时，通道列表为该能力的通道提供「扫描」入口（见第 7.1 节）。缺省或未下发该字段视为 `false`。

---

## 7. 通道

```http
GET    /catalog/channels?page=&size=
GET    /catalog/channels/{channelId}
POST   /catalog/channels
PUT    /catalog/channels/{channelId}
DELETE /catalog/channels/{channelId}
```

**创建**

```json
{
  "code": "modbus-1",
  "capabilityType": "MODBUS",
  "properties": [
    { "attribute": "host", "attributeValue": "10.0.0.8", "dataType": "string" }
  ],
  "enabled": true
}
```

`code`、`capabilityType` 创建必填。连接参数只走 `properties`，不要再传 JSON `connection`。

**更新**：改 `properties`、`enabled`。密码字段遵守掩码规则。参考 UI 不改 `code` / `capabilityType`。

**删除**：若仍有设备端点引用，返回 400 `通道仍被设备端点引用`。先删设备或端点。

成功删除：`{"channelId":"...","deleted":true}`。

### 7.1 通道扫描（probe）

扫描是**通道运维动作**，不是设备指令（不要走 `POST /devices/{code}/commands`）。

```http
POST /catalog/channels/{channelId}/probe
```

```json
{ "productId": "产品主键或 code" }
```

前置条件（任一不满足返回 400，展示 `error` 原文）：

- 通道存在且启用
- 通道能力 `probeSupported === true`，且进程内已装配对应探针（当前海康门禁 `HIKVISION_ENTRANCE` 为 true）
- `productId` 必填；产品类型必须是 **`ACCESS_CONTROL`**
- 若产品已挂功能，至少一条功能的 `capabilityType` 与通道能力相同

身份与 upsert（海康）：**EhomeID → `deviceCode`**，**devIndex → 端点 `deviceSerialNo`**。

| 情况 | `items[].action` | 行为 |
| --- | --- | --- |
| 新设备 | `created` | 用扫描到的名称创建设备 + 端点；**不自动 Load** |
| 已存在且序列号变了 | `serialUpdated` | 只改端点序列号；**不改名称**；若已 Load 则 unload 再 load |
| 已存在且序列号未变 | `unchanged` | 只刷新在线 |

响应：

```json
{
  "channelId": "...",
  "channelCode": "hik-1",
  "productId": "...",
  "discovered": 3,
  "created": 1,
  "serialUpdated": 1,
  "unchanged": 1,
  "items": [
    { "deviceCode": "12345", "name": "一号门", "serial": "1", "online": true, "action": "created" }
  ]
}
```

参考 UI：仅当能力 `probeSupported` 时显示「扫描」；对话框选门禁产品，结果用 toast 汇总「发现 / 新建 / 序列号更新 / 未变」。扫描后应刷新设备列表。

NATIVE 在线轮询由网关后台完成（已 Load 设备所在、且 `probeSupported` 的通道），UI **只读** `DeviceView.online`，不要另造心跳协议。

---

## 8. 产品类型 / 产品与功能

产品类型与南向 `capabilityType` 是两套概念：类型描述业务分类（门禁等），能力描述协议。参考控制台单独一页做类型 CRUD。

```http
GET    /catalog/product-types
GET    /catalog/product-types/{typeId}
POST   /catalog/product-types
PUT    /catalog/product-types/{typeId}
DELETE /catalog/product-types/{typeId}
```

```json
{ "code": "ACCESS_CONTROL", "name": "门禁", "description": "" }
```

`code` 创建后不可改。库种子已有 `ACCESS_CONTROL`。仍被产品引用时不能删：`产品类型仍被产品引用，无法删除`。列表不分页。

```http
GET    /catalog/products?page=&size=&name=&code=&productTypeId=
GET    /catalog/products/{productId}
POST   /catalog/products
PUT    /catalog/products/{productId}
DELETE /catalog/products/{productId}

GET    /catalog/products/{productId}/functions
GET    /catalog/products/{productId}/function-forms
GET    /catalog/products/{productId}/functions/{functionId}
POST   /catalog/products/{productId}/functions
POST   /catalog/products/{productId}/functions/import?capabilityType=
PUT    /catalog/products/{productId}/functions/{functionId}
DELETE /catalog/products/{productId}/functions/{functionId}
```

**创建产品**

```json
{
  "code": "door",
  "name": "门锁",
  "description": "闸机",
  "productTypeId": "ACCESS_CONTROL",
  "seedCapabilityType": "HIKVISION_ENTRANCE"
}
```

`productTypeId` 创建**必填**（id 或 code）。`seedCapabilityType` 可选：创建后按该能力预置模板挂功能（FIXED 常用）。`code` 与 `productTypeId` 创建后不可改；更新只传 `name` / `description`。

读回 `ProductView` 带 `productTypeId` / `productTypeCode` / `productTypeName`，列表展示用名称。

仍被设备引用的产品不能删：`产品仍被设备引用，无法删除`。

**功能两条读路径**

| 接口 | 用途 |
| --- | --- |
| `/functions` | 配置结构（`writeFields` / Topic / 调度），给产品编辑器 |
| `/function-forms` 与 `/functions/{functionId}` | 合并后的调用表单（`parameters` + `values`），给指令与动作组成员 |

**创建/更新功能**（字段按模式选用，不要把 OPEN 的结构塞进 FIXED）：

```json
{
  "functionId": "light.switch",
  "capabilityType": "MODBUS",
  "accessType": "WRITE",
  "writeAccessType": "VALUE",
  "description": "开关",
  "sortIndex": 0,
  "writeFields": [
    { "field": "area", "source": "constant", "constant": "COIL" }
  ],
  "writeValueOptions": [
    { "optionValue": "on", "mappingValue": "1", "isDefault": true }
  ],
  "publishTopicSlot": "cmd",
  "subscribeTopicSlot": "ack",
  "payloadMode": "VALUE",
  "payloadEncoding": "JSON",
  "scheduleEnabled": false,
  "scheduleIntervalMs": 5000
}
```

`accessType`：`READ` / `WRITE`。`payloadMode`：`VALUE`（单值 + 选项）或 `STRUCT`（按 caller 字段填）。`payloadEncoding`：`JSON` / `HEX` / `BINARY`。

`WriteFieldOption.source`：`caller` | `platform` | `device` | `constant` | `mapped`。指令 UI **只渲染 caller 字段**；平台/设备字段由网关填充。

写功能会刷新该产品下已 load 设备的时间轮。`scheduleIntervalMs` 不得小于 **1000**。

导入：`POST .../functions/import?capabilityType=MODBUS`，把能力模板挂到产品。

---

## 9. 设备

```http
GET    /catalog/devices?page=&size=&name=&deviceCode=&online=&productTypeId=
GET    /catalog/devices/{deviceCode}
POST   /catalog/devices
POST   /catalog/devices/register
POST   /catalog/devices/load-batch
PUT    /catalog/devices/{deviceCode}
DELETE /catalog/devices/{deviceCode}

GET    /catalog/devices/{deviceCode}/endpoints
POST   /catalog/devices/{deviceCode}/endpoints
PUT    /catalog/devices/{deviceCode}/endpoints/{endpointId}
DELETE /catalog/devices/{deviceCode}/endpoints/{endpointId}

POST   /catalog/devices/{deviceCode}/load
POST   /catalog/devices/{deviceCode}/unload
POST   /catalog/devices/{deviceCode}/commands

GET|PUT /catalog/devices/{deviceCode}/functions
GET|PUT .../functions/{functionId}/field-overrides
GET|PUT .../functions/{functionId}/topic-overrides
GET|PUT .../functions/{functionId}/schedule
```

列表项同时含 **`loaded`**（是否已进入运行时）与 **`online`**（探针/轮询在线，`null`/缺省=未知）。二者独立：扫描新建的设备可以在线但未 Load；Load 成功也不保证在线。

徽章建议：启用/禁用、在线/离线/未知、已加载/未加载。`onlineUpdatedAt` 可作辅助提示。

### 9.0 列表筛选与勾选

查询参数见第 2.4 节。参考 UI 表头筛选；改筛选重置到第 1 页。

列表提供勾选（当前页），配合第 9.3 节批量 Load。不要把筛选条件误当成 `load-batch` 的隐式范围：批量接口只认 body 里的 `deviceCodes`（空=全部已启用设备，不是「当前筛选结果」）。

### 9.1 登记（推荐，一次完成）

参考「登记设备」对话框。不要拆成先 create 再手拼端点，除非你在做高级编辑。

```json
{
  "deviceCode": "door-1",
  "productId": "产品主键",
  "name": "一号门",
  "endpoints": [
    {
      "channelId": "通道id或code",
      "properties": [
        { "attribute": "deviceSerialNo", "attributeValue": "SN-001", "dataType": "string" }
      ]
    }
  ],
  "load": true
}
```

校验：设备编码、产品、通道必填。地址按所选通道的 `addressSchema` 采集。`load` 缺省视为 true（登记后立即进运行时）。

### 9.2 编辑

参考「编辑设备」：可改 **名称、设备编码、端点地址（序列号等）、启用**。产品不可改。

```json
{
  "deviceCode": "door-1-new",
  "name": "一号门",
  "enabled": true,
  "endpoints": [
    {
      "id": "端点主键",
      "properties": [
        { "attribute": "deviceSerialNo", "attributeValue": "SN-002", "dataType": "string" }
      ]
    }
  ]
}
```

约定：

- 打开编辑时先 `GET .../endpoints`，按通道能力渲染地址字段；加载完成前禁用保存
- 编码不能为空；冲突返回 `设备编码已存在`
- 改编码会级联动作组成员、场景 `listenDeviceCode`，并重建监听索引
- 已 load 时改编码或地址：服务端先落库，再按旧码 unload、按新码 load
- `functionOverrides` 参考 UI 仍提供原始 JSON，一般业务页可隐藏

### 9.3 Load / Unload / 批量 Load / 删除

| 操作 | 条件 |
| --- | --- |
| Load | 设备启用；至少一条已启用通道；运行时一设备一协议 |
| Unload | 已 loaded |
| 批量 Load | 见下 |
| 指令 | **必须已 loaded**，否则 409 |
| 删除 | 先确认；服务端会 unload 再删配置，不可恢复 |

按钮禁用逻辑与参考列表一致：未加载不能 Unload/指令；已加载不能再 Load。扫描新建的设备默认未 Load，需用户显式加载。

**批量 Load**

```http
POST /catalog/devices/load-batch
```

```json
{ "deviceCodes": [] }
```

| body | 行为 |
| --- | --- |
| 省略 / `deviceCodes` 空或 `[]` | 尝试加载**全部已启用**设备（已 Load 的记 `skipped`） |
| `["door-1","door-2"]` | 只处理这些编码（不存在则 400） |

单台失败不中断其余。响应：

```json
{
  "requested": 3,
  "loaded": 1,
  "skipped": 1,
  "failed": 1,
  "items": [
    { "deviceCode": "door-1", "status": "loaded", "error": null },
    { "deviceCode": "door-2", "status": "skipped", "error": "已加载" },
    { "deviceCode": "door-3", "status": "failed", "error": "..." }
  ]
}
```

`status`：`loaded` | `skipped` | `failed`。参考 UI：「全部 Load」传空数组；「Load 所选」传勾选的 `deviceCode`。完成后刷新设备列表，toast 汇总 loaded / skipped / failed。

### 9.4 手动下发

1. `GET /catalog/devices/{deviceCode}/functions`
2. 只列出 `accessType` 为 `READ` 或 `WRITE` 的项
3. 用 `parameters` + `values` 预填；`payloadMode=VALUE` 时用 `writeValueOptions` 选业务值
4. 只收集 caller 字段；必填空则拦截
5. `POST /catalog/devices/{code}/commands`

```json
{
  "functionId": "remoteControlDoor",
  "arguments": { "command": "open" }
}
```

```json
{
  "requestId": "...",
  "deviceId": "door-1",
  "functionId": "remoteControlDoor",
  "status": "SUCCESS",
  "data": { "values": {} },
  "failure": null
}
```

`status`：`SUCCESS` | `ACCEPTED` | `FAILED` | `TIMEOUT` | `REJECTED`。仅 `SUCCESS` 当成功 toast；其它展示 `failure.message`。

该接口可能较慢（等南向），UI 要有 pending 态。

### 9.5 设备级覆盖与调度

「设备参数」对话框走 field-overrides / topic-overrides / schedule，与产品默认合并：

- `PUT .../field-overrides` body：`{"overrides":{...}}`，锁死契约字段服务端会剥掉
- `PUT .../topic-overrides` body：`{"overrides":{"publish":"..."}}`
- `PUT .../schedule` body：`{"enabled":true,"intervalMs":5000}`；两者都空表示删除覆盖、回落产品默认
- `intervalMs` ≥ 1000；写完后已 load 设备会重算时间轮

---

## 10. 集群与场景

同一套资源形状，路径按 kind 分开，**不要混用**：

```http
GET|POST           /catalog/clusters
GET|PUT|DELETE     /catalog/clusters/{id}
POST               /catalog/clusters/{id}/execute

GET|POST           /catalog/scenes
GET|PUT|DELETE     /catalog/scenes/{id}
POST               /catalog/scenes/{id}/execute
```

```json
{
  "code": "time-door",
  "name": "定时开门",
  "description": "",
  "enabled": true,
  "members": [
    {
      "deviceCode": "door-1",
      "functionId": "remoteControlDoor",
      "arguments": { "command": "open" },
      "sortIndex": 0
    }
  ],
  "trigger": {
    "mode": "TIMER",
    "timerKind": "CRON",
    "cronExpr": "0 0 8 * * *",
    "timezone": "Asia/Shanghai",
    "enabled": true
  }
}
```

### 10.1 成员

每条成员：先选 `deviceCode`，再 `GET /catalog/devices/{code}/functions` 选功能，参数采集与手动下发相同（`collectActionArguments`）。`deviceCode` 必须已存在。

### 10.2 集群

不要传 `trigger`（或忽略）。执行来源为 `cluster`。用于手动一键跑一组设备指令。

### 10.3 场景触发器

`trigger.mode` 只能是 `LISTEN` 或 `TIMER`。

**LISTEN**

- 必填 `listenDeviceCode`、`listenFunctionId`（功能须挂在该设备产品上）
- `listenMatch`：回包字段 path → 期望值；空对象表示任意成功都触发
- 集群/场景/调度来源的成功**不会**再触发监听（防环）

**TIMER**

- `timerKind`：`ONCE` 或 `CRON`
- `ONCE`：`timerAt` 必须能解析且 **晚于现在**
- `CRON`：`cronExpr` 为 Spring **6 段**（秒 分 时 日 月 周），服务端会 `CronExpression.parse`
- `timezone`：IANA，缺省 `Asia/Shanghai`；非法时区 400
- 参考 UI 保存 TIMER 时 **`enabled` 固定 true**（ONCE 执行过后仍可再启用）

未用到的一侧字段应省略或由服务端清空：LISTEN 不留 cron；TIMER 不留 listen*。

### 10.4 场景定时的友好表单（UI 规范，后端只收 cron）

参考控制台不把 6 段 cron 直接甩给用户，而是编译：

| 交互 | 编译结果（秒 分 时 日 月 周） |
| --- | --- |
| 只执行一次 | 不走 cron，走 `timerKind=ONCE` + `timerAt` |
| 每天 HH:mm | `0 m H * * *` |
| 工作日 HH:mm | `0 m H ? * MON-FRI` |
| 每周选星期 HH:mm | `0 m H ? * MON,WED` |
| 每月 d 日 HH:mm | `0 m H d * ?` |
| 每 N 分钟 | `0 */N * * * *`（N=1–59，**对齐时钟**，不是从现在起算） |
| 每 N 小时 | `0 0 */N * * *`（N=1–23） |
| 自定义 | 用户填 6 段原文 |

星期 wire：`MON`…`SUN`。周期任务才展示时区。读回时按同一规则反解析，认不出则落「自定义」。

实现可直接移植 `ui/src/lib/scene-schedule.ts` 的纯函数（无 React 依赖）。

### 10.5 执行

`POST .../execute` 返回：

```json
{
  "groupId": "...",
  "code": "time-door",
  "kind": "SCENE",
  "source": "scene",
  "items": [ { "deviceId": "door-1", "functionId": "...", "status": "SUCCESS" } ]
}
```

组级超时约 15s。成员设备必须已 load，否则该条失败。展示每条 `items[].status` / `failure`。

---

## 11. 北向

```http
GET /catalog/northbound
PUT /catalog/northbound
```

读回除配置外还有运行态：`mqttPasswordSet`、`mqttLive`、`mqttError`、`httpLive`。密码字段是掩码；`mqttPasswordSet=true` 表示库里已有密码。

更新时整份写入（与参考表单一致），不要做部分 PATCH。密码传 `••••` 则保留原值。

`mqttTransport` 等取值以当前网关实现为准（参考 UI 用下拉绑现有 view）。

---

## 12. 状态与按钮（对齐参考 UI）

| 状态 | 展示 | 可做 |
| --- | --- | --- |
| `enabled !== false` | 徽章「启用」 | Load（若未加载） |
| `enabled === false` | 「禁用」 | 不可 Load |
| `online === true` | 「在线」 | 与 Load 无关；仍须 Load 才能下发 |
| `online === false` | 「离线」 | 同上 |
| `online` 空 | 「未知」 | 尚未扫描或轮询未覆盖 |
| `loaded === true` | 「已加载」 | 指令、Unload |
| `loaded !== true` | 「未加载」 | Load、编辑地址后需再 Load 才进运行时 |

破坏性操作（删设备 / 删产品 / 删产品类型 / 删通道 / 删功能 / 删组）必须二次确认。删除文案写清不可恢复。

异步按钮在请求期间 disable，并用 pending 文案（「保存」「删除中…」「登记」）。

---

## 13. 任意 UI 库的实现清单

按此清单验收即与参考控制台同规范：

1. HTTP 客户端：JSON、URL 编码、`error` toast、空 body、400/409。
2. Schema 渲染器：type + format 映射表、choices、secret、Base64 去前缀。
3. `PropertyItem` ↔ 表单 Record 双向转换；空可选字段不提交。
4. 掩码 `••••` 回写不覆盖。
5. 分页 `page` 从 1，size ≤ 100；产品/设备列表支持第 2.4 节筛选。
6. 字典：capabilities / **product-types** / products / channels。
7. 创建设备前先有产品类型；创建产品必传 `productTypeId`。
8. 设备登记用 `addressSchema`；编辑可改 code / name / 地址。`loaded` 与 `online` 分开展示。
9. `probeSupported` 的通道提供扫描；新设备不自动 Load；扫描产品必须是门禁类型。
10. 单台 Load 与 `POST /devices/load-batch`；空数组=全部已启用，非空=所选编码。
11. 指令与动作组成员共用同一套 caller 参数采集。
12. 仅 `loaded` 设备可下发；`status === SUCCESS` 才算成功。
13. 场景 TIMER：ONCE 校验未来时间；CRON 用 6 段 Spring 表达式；友好表单可选用第 10.4 节规则。
14. 产品功能编辑器按 `functionMode` 切换 FIXED / CONTRACT / OPEN，不要让用户在 FIXED 下改字段名。
15. 删除前检查服务端错误原文（产品类型被引用、产品被引用、通道被引用、编码冲突）。

不要求：React、Tailwind、特定组件库、WebSocket、GraphQL。

---

## 14. API 速查

下列 path 均相对网关根，前缀 `/catalog`。

### 能力

| 方法 | 路径 |
| --- | --- |
| GET | `/capabilities` |
| GET | `/capabilities/{capabilityType}` |
| GET | `/capabilities/{capabilityType}/functions` |
| GET | `/capabilities/{capabilityType}/supported/connection` |
| GET | `/capabilities/{capabilityType}/supported/address` |
| GET | `/capabilities/{capabilityType}/supported/functions` |
| GET | `/capabilities/{capabilityType}/supported/functions/{functionId}` |

### 通道 / 产品类型 / 产品

| 方法 | 路径 |
| --- | --- |
| GET POST | `/channels` |
| GET PUT DELETE | `/channels/{channelId}` |
| POST | `/channels/{channelId}/probe` |
| GET POST | `/product-types` |
| GET PUT DELETE | `/product-types/{typeId}` |
| GET POST | `/products` |
| GET PUT DELETE | `/products/{productId}` |
| GET POST | `/products/{productId}/functions` |
| GET PUT DELETE | `/products/{productId}/functions/{functionId}` |
| GET | `/products/{productId}/function-forms` |
| POST | `/products/{productId}/functions/import?capabilityType=` |

### 设备

| 方法 | 路径 |
| --- | --- |
| GET POST | `/devices` |
| POST | `/devices/register` |
| POST | `/devices/load-batch` |
| GET PUT DELETE | `/devices/{deviceCode}` |
| GET POST | `/devices/{deviceCode}/endpoints` |
| PUT DELETE | `/devices/{deviceCode}/endpoints/{endpointId}` |
| POST | `/devices/{deviceCode}/load` |
| POST | `/devices/{deviceCode}/unload` |
| POST | `/devices/{deviceCode}/commands` |
| GET | `/devices/{deviceCode}/functions` |
| GET | `/devices/{deviceCode}/functions/{functionId}` |
| GET PUT | `/devices/{deviceCode}/functions/{functionId}/field-overrides` |
| GET PUT | `/devices/{deviceCode}/functions/{functionId}/topic-overrides` |
| GET PUT | `/devices/{deviceCode}/functions/{functionId}/schedule` |

### 动作组 / 北向

| 方法 | 路径 |
| --- | --- |
| GET POST | `/clusters` `/scenes` |
| GET PUT DELETE | `/clusters/{id}` `/scenes/{id}` |
| POST | `/clusters/{id}/execute` `/scenes/{id}/execute` |
| GET PUT | `/northbound` |

---

## 15. 最小接入示例（与框架无关）

登记或扫描一台海康门禁并下发开门，伪代码：

```text
caps     = GET /catalog/capabilities
types    = GET /catalog/product-types
channel  = 用户选的通道
product  = 门禁类型产品（productTypeCode == ACCESS_CONTROL）

方式 A — 手工登记：
  addressSchema = caps.find(c => c.capabilityType == channel.capabilityType).addressSchema
  properties    = recordToProperties(用户填写的地址表单, addressSchema)
  POST /catalog/devices/register
    { deviceCode, productId, name, endpoints: [{ channelId: channel.id, properties }], load: true }

方式 B — 通道扫描（capability.probeSupported == true）：
  POST /catalog/channels/{channel.id}/probe
    { productId: product.id }
  看 created / serialUpdated / unchanged
  新设备不会自动 Load，需要：
  POST /catalog/devices/load-batch
    { deviceCodes: [扫到的 deviceCode...] }
  或空数组加载全部已启用未加载设备

若要改序列号：
  endpoints = GET /catalog/devices/{deviceCode}/endpoints
  PUT /catalog/devices/{deviceCode}
    { name, deviceCode, endpoints: [{ id, properties: 新地址 }] }

下发（必须 loaded；online 仅展示）：
  forms = GET /catalog/devices/{deviceCode}/functions
  选 WRITE 功能，按 parameters 收集 arguments
  POST /catalog/devices/{deviceCode}/commands
    { functionId, arguments }
  看 status == SUCCESS
```

---

## 16. 与参考控制台的关系

`ui/` 继续作为**单独部署**的官方控制台：`cd ui && pnpm dev` 代理到 :8080。把它当作可运行的规范样品，而不是唯一实现。

契约变更时：先改 catalog DTO / 控制器，再改 `ui/src/lib/*`，最后同步本文。自建 UI 应以 **HTTP 字段与本节交互规则** 为准，而不是复制 React 组件树。
