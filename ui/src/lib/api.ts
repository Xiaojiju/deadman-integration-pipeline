import type {
  CapabilityDescriptor,
  ChannelEntity,
  DeviceEntity,
  DeviceFunctionScheduleView,
  ExecutionResult,
  NorthboundView,
  NorthboundWriteRequest,
  FunctionFormView,
  FunctionTemplate,
  PageResult,
  ProductEntity,
  ProductFunctionEntity,
  PropertyItem,
  SupportedFunctionView,
  SupportedSchemaView,
  ValueOption,
  WriteFieldOption,
} from "@/lib/types"

export const MIN_SCHEDULE_MS = 1000

type ProductFunctionWriteBody = {
  accessType?: string
  properties?: PropertyItem[]
  writeAccessType?: string
  writeValueOptions?: ValueOption[]
  writeFields?: WriteFieldOption[]
  readFields?: WriteFieldOption[]
  readValueOptions?: ValueOption[]
  sortIndex?: number
  description?: string
  publishTopicSlot?: string
  subscribeTopicSlot?: string
  payloadMode?: string
  payloadEncoding?: string
  replyTopicSlot?: string
  correlationPath?: string
  correlationCommandPath?: string
  resultPath?: string
  replyTimeoutMs?: number
  scheduleIntervalMs?: number
  scheduleEnabled?: boolean
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
    ...init,
  })
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const body = (await response.json()) as { error?: string }
      if (body.error) {
        message = body.error
      }
    } catch {
      // ignore parse errors
    }
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  if (!text) {
    return undefined as T
  }
  return JSON.parse(text) as T
}

export const catalogApi = {
  listCapabilities: () => request<CapabilityDescriptor[]>("/catalog/capabilities"),
  capabilityFunctions: (type: string) =>
    request<FunctionTemplate[]>(`/catalog/capabilities/${encodeURIComponent(type)}/functions`),
  supportedConnection: (type: string) =>
    request<SupportedSchemaView>(
      `/catalog/capabilities/${encodeURIComponent(type)}/supported/connection`
    ),
  supportedAddress: (type: string) =>
    request<SupportedSchemaView>(
      `/catalog/capabilities/${encodeURIComponent(type)}/supported/address`
    ),
  supportedFunctions: (type: string) =>
    request<SupportedFunctionView[]>(
      `/catalog/capabilities/${encodeURIComponent(type)}/supported/functions`
    ),
  supportedFunction: (type: string, functionId: string) =>
    request<SupportedFunctionView>(
      `/catalog/capabilities/${encodeURIComponent(type)}/supported/functions/${encodeURIComponent(functionId)}`
    ),
  listProducts: (page = 1, size = 20) =>
    request<PageResult<ProductEntity>>(`/catalog/products?page=${page}&size=${size}`),
  listChannels: (page = 1, size = 20) =>
    request<PageResult<ChannelEntity>>(`/catalog/channels?page=${page}&size=${size}`),
  listDevices: (page = 1, size = 20) =>
    request<PageResult<DeviceEntity>>(`/catalog/devices?page=${page}&size=${size}`),
  getProduct: (productId: string) =>
    request<ProductEntity>(`/catalog/products/${encodeURIComponent(productId)}`),
  listProductFunctions: (productId: string) =>
    request<ProductFunctionEntity[]>(
      `/catalog/products/${encodeURIComponent(productId)}/functions`
    ),
  productFunctionForms: (productId: string) =>
    request<FunctionFormView[]>(
      `/catalog/products/${encodeURIComponent(productId)}/function-forms`
    ),
  updateProduct: (productId: string, body: { name?: string; description?: string }) =>
    request<ProductEntity>(`/catalog/products/${encodeURIComponent(productId)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteProduct: (productId: string) =>
    request<{ productId: string; deleted: boolean }>(
      `/catalog/products/${encodeURIComponent(productId)}`,
      { method: "DELETE" }
    ),
  updateChannel: (
    channelId: string,
    body: { properties?: PropertyItem[]; connection?: Record<string, unknown>; enabled?: boolean }
  ) =>
    request<ChannelEntity>(`/catalog/channels/${encodeURIComponent(channelId)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteChannel: (channelId: string) =>
    request<{ channelId: string; deleted: boolean }>(
      `/catalog/channels/${encodeURIComponent(channelId)}`,
      { method: "DELETE" }
    ),
  updateDevice: (
    deviceCode: string,
    body: {
      name?: string
      functionOverrides?: Record<string, PropertyItem[]>
      optionOverrides?: Record<string, unknown>
      enabled?: boolean
    }
  ) =>
    request<DeviceEntity>(`/catalog/devices/${encodeURIComponent(deviceCode)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  importProductFunctions: (productId: string, capabilityType: string) =>
    request(
      `/catalog/products/${encodeURIComponent(productId)}/functions/import?capabilityType=${encodeURIComponent(capabilityType)}`,
      { method: "POST" }
    ),
  updateProductFunction: (
    productId: string,
    functionId: string,
    body: ProductFunctionWriteBody
  ) =>
    request(
      `/catalog/products/${encodeURIComponent(productId)}/functions/${encodeURIComponent(functionId)}`,
      {
        method: "PUT",
        body: JSON.stringify(body),
      }
    ),
  deleteProductFunction: (productId: string, functionId: string) =>
    request(
      `/catalog/products/${encodeURIComponent(productId)}/functions/${encodeURIComponent(functionId)}`,
      { method: "DELETE" }
    ),
  deviceFunctions: (deviceCode: string) =>
    request<FunctionFormView[]>(`/catalog/devices/${encodeURIComponent(deviceCode)}/functions`),
  deviceFieldOverrides: (deviceCode: string, functionId: string) =>
    request<Record<string, unknown>>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/functions/${encodeURIComponent(functionId)}/field-overrides`
    ),
  replaceDeviceFieldOverrides: (
    deviceCode: string,
    functionId: string,
    overrides: Record<string, unknown>
  ) =>
    request<Record<string, unknown>>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/functions/${encodeURIComponent(functionId)}/field-overrides`,
      { method: "PUT", body: JSON.stringify({ overrides }) }
    ),
  deviceTopicOverrides: (deviceCode: string, functionId: string) =>
    request<Record<string, string>>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/functions/${encodeURIComponent(functionId)}/topic-overrides`
    ),
  replaceDeviceTopicOverrides: (
    deviceCode: string,
    functionId: string,
    overrides: Record<string, string>
  ) =>
    request<Record<string, string>>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/functions/${encodeURIComponent(functionId)}/topic-overrides`,
      { method: "PUT", body: JSON.stringify({ overrides }) }
    ),
  deviceSchedule: (deviceCode: string, functionId: string) =>
    request<DeviceFunctionScheduleView>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/functions/${encodeURIComponent(functionId)}/schedule`
    ),
  replaceDeviceSchedule: (
    deviceCode: string,
    functionId: string,
    body: { enabled?: boolean | null; intervalMs?: number | null }
  ) =>
    request<DeviceFunctionScheduleView>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/functions/${encodeURIComponent(functionId)}/schedule`,
      { method: "PUT", body: JSON.stringify(body) }
    ),
  registerDevice: (body: {
    deviceCode: string
    productId: string
    name?: string
    enabled?: boolean
    endpoints: Array<{
      channelId: string
      properties?: PropertyItem[]
      address?: Record<string, unknown>
    }>
    load?: boolean
  }) =>
    request<DeviceEntity>("/catalog/devices/register", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  loadDevice: (deviceCode: string) =>
    request<void>(`/catalog/devices/${encodeURIComponent(deviceCode)}/load`, {
      method: "POST",
    }),
  unloadDevice: (deviceCode: string) =>
    request<void>(`/catalog/devices/${encodeURIComponent(deviceCode)}/unload`, {
      method: "POST",
    }),
  deleteDevice: (deviceCode: string) =>
    request<{ deviceCode: string; deleted: boolean }>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}`,
      { method: "DELETE" }
    ),
  invokeCommand: (deviceCode: string, functionId: string, argumentsMap: Record<string, unknown>) =>
    request<ExecutionResult>(`/catalog/devices/${encodeURIComponent(deviceCode)}/commands`, {
      method: "POST",
      body: JSON.stringify({ functionId, arguments: argumentsMap }),
    }),
  createChannel: (body: {
    code: string
    capabilityType: string
    properties?: PropertyItem[]
    connection?: Record<string, unknown>
    enabled?: boolean
  }) =>
    request<ChannelEntity>("/catalog/channels", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  createProduct: (body: {
    code: string
    name: string
    description?: string
    seedCapabilityType?: string
  }) =>
    request<ProductEntity>("/catalog/products", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getNorthbound: () => request<NorthboundView>("/catalog/northbound"),
  updateNorthbound: (body: NorthboundWriteRequest) =>
    request<NorthboundView>("/catalog/northbound", {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  createProductFunction: (
    productId: string,
    body: { functionId: string; capabilityType: string } & ProductFunctionWriteBody
  ) =>
    request(`/catalog/products/${encodeURIComponent(productId)}/functions`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
}
