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
}

export type FunctionTemplate = {
  functionId: string
  description: string
  accessType: string
  accessPermission: number
  parameters: SchemaField[]
}

export type FunctionCatalogMode = "FIXED" | "OPEN"

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

export type ProductEntity = {
  id: string
  code: string
  name: string
  description?: string
}

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
  structSchema?: import("@/lib/payload-form").FieldNodeModel
  valueMappings?: import("@/lib/payload-form").ValueMappingModel[]
}

export type ChannelEntity = {
  id: string
  code: string
  capabilityType: string
  properties?: PropertyItem[]
  /** @deprecated 兼容旧字段 */
  connection?: Record<string, unknown> | string
  enabled?: boolean
}

export type DeviceEntity = {
  id: string
  deviceCode: string
  productId: string
  name?: string
  functionOverrides?: Record<string, PropertyItem[]>
  /** @deprecated 兼容旧字段 */
  optionOverrides?: Record<string, unknown> | string
  enabled?: boolean
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
  protocolMapping?: Record<string, unknown>
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
