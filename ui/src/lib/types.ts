export type SchemaField = {
  name: string
  type: string
  required: boolean
  description: string
  label: string
  defaultValue?: unknown
  secret?: boolean
  choices?: string[]
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
  readValueOptions?: ValueOption[]
  protocolMapping?: Record<string, unknown> | string
  sortIndex?: number
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
  parameters: Array<{
    name: string
    type: string
    required: boolean
    description: string
    label: string
    value?: unknown
    secret?: boolean
    choices?: string[]
    defaultValue?: unknown
  }>
  properties?: PropertyItem[]
  writeValueOptions?: ValueOption[]
  values: Record<string, unknown>
  protocolMapping?: Record<string, unknown>
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
