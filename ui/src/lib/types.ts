export type FieldType =
  | "string"
  | "int"
  | "boolean"
  | "select"
  | "password"
  | "json"
  | "array"

export type FieldFormat =
  | "none"
  | "datetime_iso8601"
  | "image_base64"
  | "text_list"

export type SchemaField = {
  name: string
  type: FieldType | string
  required: boolean
  description: string
  label: string
  defaultValue?: unknown
  secret?: boolean
  choices?: string[]
  /** UI 采值约束；与 type 解耦 */
  format?: FieldFormat | string
  /** 平台值生成器 wire code（模板字段可选） */
  valueGenerator?: string
}

export type FormFieldView = {
  name: string
  type: FieldType | string
  required: boolean
  description: string
  label: string
  value?: unknown
  secret?: boolean
  choices?: string[]
  defaultValue?: unknown
  format?: FieldFormat | string
}

export type PropertyItem = {
  attribute: string
  attributeValue: string
  dataType: string
  description?: string
}

export type ValueOption = {
  optionValue: string
  mappingValue?: string
  description?: string
  accessDataType?: string
  transformDataType?: string
  isDefault?: boolean
}

export type WriteFieldOption = {
  field: string
  description?: string
  accessDataType?: string
  transformDataType?: string
  ignoreRequest?: boolean
  options?: ValueOption[]
  /** UI 采值约束（FieldFormat wire） */
  format?: string
  /** 平台值生成器（FieldValueGenerator wire）；空=调用方提供 */
  valueGenerator?: string
  /** caller / platform / device / constant / mapped */
  source?: string
  /** source=constant 时的固定值 */
  constant?: string
  /** source=mapped 时调用方传入的字段名，默认 value */
  callerField?: string
  /** HEX/BINARY 占用字节数 */
  byteLength?: number | null
  /** 字节序 big / little */
  byteOrder?: string | null
  /** 入站换算运算符 add/subtract/multiply/divide */
  scaleOp?: string
  /** 入站换算操作数 */
  scaleOperand?: string
}

export type FunctionTemplate = {
  functionId: string
  description: string
  accessType: string
  accessPermission: number
  parameters: SchemaField[]
}

export type FunctionCatalogMode = "FIXED" | "CONTRACT" | "OPEN"

export type CapabilityDescriptor = {
  capabilityType: string
  connectionSchema: SchemaField[]
  addressSchema: SchemaField[]
  functionTemplates: FunctionTemplate[]
  functionMode?: FunctionCatalogMode
}

export type SupportedSchemaView = {
  fields: SchemaField[]
  properties: PropertyItem[]
  choiceOptions?: Record<string, ValueOption[]>
}

export type SupportedFunctionView = {
  functionId: string
  description: string
  accessType: string
  accessPermission: number
  writeAccessType?: string
  fields: SchemaField[]
  properties: PropertyItem[]
  writeValueOptions?: ValueOption[]
  choiceOptions?: Record<string, ValueOption[]>
}

export type ProductView = {
  id: string
  code: string
  name: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

/** @deprecated 使用 ProductView；保留别名以免旧组件改名。 */
export type ProductEntity = ProductView

export type ProductFunctionEntity = {
  id: string
  productId: string
  functionId: string
  description?: string
  accessType: string
  accessPermission?: number
  capabilityType?: string
  writeAccessType?: string
  properties?: PropertyItem[]
  writeValueOptions?: ValueOption[]
  writeFields?: WriteFieldOption[]
  readFields?: WriteFieldOption[]
  readValueOptions?: ValueOption[]
  sortIndex?: number
  publishTopicSlot?: string
  subscribeTopicSlot?: string
  payloadMode?: string
  /** JSON / HEX / BINARY */
  payloadEncoding?: string
  replyTopicSlot?: string
  correlationPath?: string
  correlationCommandPath?: string
  replyTimeoutMs?: number
  scheduleIntervalMs?: number
  scheduleEnabled?: boolean
  /** 入站换算运算符 add/subtract/multiply/divide */
  scaleOp?: string
  /** 入站换算操作数 */
  scaleOperand?: string
}

export type DeviceFunctionScheduleView = {
  functionId: string
  enabled: boolean
  intervalMs?: number | null
  productEnabled: boolean
  productIntervalMs?: number | null
  overridden: boolean
  overrideEnabled?: boolean | null
  overrideIntervalMs?: number | null
}

export type ChannelEntity = {
  id: string
  code: string
  capabilityType: string
  properties?: PropertyItem[]
  enabled?: boolean
}

export type DeviceEntity = {
  id: string
  deviceCode: string
  productId: string
  name?: string
  functionOverrides?: Record<string, PropertyItem[]>
  enabled?: boolean
  /** 是否已 load 到网关运行时 */
  loaded?: boolean
}

export type DeviceEndpointView = {
  id: string
  deviceId: string
  channelId: string
  properties?: PropertyItem[]
  createdAt?: string
}

export type FunctionFormView = {
  functionId: string
  description?: string
  accessType: string
  accessPermission: number
  capabilityType: string
  writeAccessType?: string
  parameters: FormFieldView[]
  properties?: PropertyItem[]
  writeValueOptions?: ValueOption[]
  values: Record<string, unknown>
  /** VALUE / STRUCT；空则按 writeAccessType 推断 */
  payloadMode?: string
}

export type ExecutionResult = {
  requestId?: string
  deviceId: string
  functionId: string
  status: string
  data?: { values?: Record<string, unknown> }
  failure?: { message?: string; code?: string; retryable?: boolean }
}

export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export type NorthboundView = {
  mqttEnabled: boolean
  mqttTransport: string
  mqttUrl: string
  mqttCommandTopic: string
  mqttResponseTopic: string
  mqttTelemetryTopic: string
  mqttClientId: string
  mqttUsername: string
  mqttPassword: string
  mqttPasswordSet: boolean
  httpEnabled: boolean
  httpWebhookUrl: string
  httpTimeoutMs: number
  httpMaxAttempts: number
  mqttLive: boolean
  mqttError?: string | null
  httpLive: boolean
}

export type NorthboundWriteRequest = {
  mqttEnabled: boolean
  mqttTransport: string
  mqttUrl: string
  mqttCommandTopic: string
  mqttResponseTopic: string
  mqttTelemetryTopic: string
  mqttClientId: string
  mqttUsername: string
  mqttPassword: string
  httpEnabled: boolean
  httpWebhookUrl: string
  httpTimeoutMs: number
  httpMaxAttempts: number
}

export type ActionMemberView = {
  id?: string
  deviceCode: string
  functionId: string
  arguments?: Record<string, unknown>
  sortIndex?: number
}

export type SceneTriggerView = {
  id?: string
  mode: string
  listenDeviceCode?: string
  listenFunctionId?: string
  listenMatch?: Record<string, unknown>
  timerKind?: string
  timerAt?: string
  cronExpr?: string
  timezone?: string
  enabled?: boolean
}

export type ActionGroupView = {
  id: string
  code: string
  name: string
  description?: string
  kind: string
  enabled: boolean
  members: ActionMemberView[]
  trigger?: SceneTriggerView | null
}

export type ActionGroupWriteRequest = {
  code: string
  name: string
  description?: string
  enabled?: boolean
  members?: Array<{
    deviceCode: string
    functionId: string
    arguments?: Record<string, unknown>
    sortIndex?: number
  }>
  trigger?: {
    mode: string
    listenDeviceCode?: string
    listenFunctionId?: string
    listenMatch?: Record<string, unknown>
    timerKind?: string
    timerAt?: string
    cronExpr?: string
    timezone?: string
    enabled?: boolean
  }
}

export type ActionGroupExecutionView = {
  groupId: string
  code: string
  kind: string
  source: string
  items: ExecutionResult[]
}
