# 运行时默认值与凭证策略

> 与 `docs/rebuild-charter.md` §4.2 / [REQ-QUEUE] / [DEBT-D4] / [DEBT-D5] 对齐。可通过 `GatewaySettings` 覆盖，禁止在业务代码里写死。

## 队列与分池

| 项 | 默认 | 满时行为 |
| --- | --- | --- |
| 入站 COMMAND / 原始入站 | 256 | 超时未入队则拒绝并回执 |
| 入站 TELEMETRY | 1024 | 丢最旧 |
| 每设备 Execute | 64 | `DEVICE_QUEUE_OVERFLOW`；同设备串行，跨设备并行 |
| 出站 HIGH（命令回执） | 256 | 有界反压，禁止静默丢弃 |
| 出站 NORMAL（遥测） | 1024 | 丢最旧 |
| Ingress workers | 2 | 与 Execute / Egress 隔离 |
| Execute workers | 4 | 跨设备并行 |
| Egress workers | 2 | 与 Execute 隔离 |
| 活跃设备槽上限 `maxDeviceSlots` | 4096 | 超出拒绝新 deviceId，防止伪造海量 ID 造成槽尖峰 |
| 命令 offer 超时 | 1s | 入站命令 / 回执出站 |
| 回执发布重试 | 最多 3 次 | 100ms × 2^n，封顶 2s |
| 遥测发布重试 | 0 | 不重试 |

无全局 `maxDeviceSlots` 时，伪造海量 `deviceId` 会在短时间建出全部槽。本实现默认 4096，可配置。

## 凭证

凭证只来自环境变量或外部配置（`GATEWAY_DATASOURCE_*`、`GATEWAY_MQTT_*`、`GATEWAY_HIK_*`）。仓库内配置文件不得写明文口令。联调测试默认不进 CI。

## H2 / 管理面

H2 仅 `local` / 单测可选。生产 profile 不得指向 H2。H2 控制台与 TCP 默认关闭，禁止 `web-allow-others` / `-tcpAllowOthers`。
