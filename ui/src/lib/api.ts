import type {
  CapabilityDescriptor,
  ChannelEntity,
  DeviceEntity,
  DeviceEndpointView,
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
  ActionGroupView,
  ActionGroupWriteRequest,
  ActionGroupExecutionView,
  ChannelProbeView,
  DeviceLoadBatchView,
  ProductTypeView,
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
  replyTimeoutMs?: number
  scheduleIntervalMs?: number
  scheduleEnabled?: boolean
  scaleOp?: string
  scaleOperand?: string
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

function withQuery(path: string, params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === "") {
      continue
    }
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `${path}?${query}` : path
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
  listProducts: (
    page = 1,
    size = 20,
    filters?: { name?: string; code?: string; productTypeId?: string }
  ) =>
    request<PageResult<ProductEntity>>(
      withQuery("/catalog/products", {
        page,
        size,
        name: filters?.name,
        code: filters?.code,
        productTypeId: filters?.productTypeId,
      })
    ),
  listProductTypes: () => request<ProductTypeView[]>("/catalog/product-types"),
  createProductType: (body: { code: string; name: string; description?: string }) =>
    request<ProductTypeView>("/catalog/product-types", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateProductType: (typeId: string, body: { name?: string; description?: string }) =>
    request<ProductTypeView>(`/catalog/product-types/${encodeURIComponent(typeId)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteProductType: (typeId: string) =>
    request<{ typeId: string; deleted: boolean }>(
      `/catalog/product-types/${encodeURIComponent(typeId)}`,
      { method: "DELETE" }
    ),
  listChannels: (page = 1, size = 20) =>
    request<PageResult<ChannelEntity>>(`/catalog/channels?page=${page}&size=${size}`),
  listDevices: (
    page = 1,
    size = 20,
    filters?: {
      name?: string
      deviceCode?: string
      online?: string
      productTypeId?: string
    }
  ) =>
    request<PageResult<DeviceEntity>>(
      withQuery("/catalog/devices", {
        page,
        size,
        name: filters?.name,
        deviceCode: filters?.deviceCode,
        online: filters?.online,
        productTypeId: filters?.productTypeId,
      })
    ),
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
    body: { properties?: PropertyItem[]; enabled?: boolean }
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
      deviceCode?: string
      functionOverrides?: Record<string, PropertyItem[]>
      enabled?: boolean
      endpoints?: Array<{
        id: string
        properties?: PropertyItem[]
      }>
    }
  ) =>
    request<DeviceEntity>(`/catalog/devices/${encodeURIComponent(deviceCode)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  listDeviceEndpoints: (deviceCode: string) =>
    request<DeviceEndpointView[]>(
      `/catalog/devices/${encodeURIComponent(deviceCode)}/endpoints`
    ),
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
  loadDeviceBatch: (deviceCodes?: string[]) =>
    request<DeviceLoadBatchView>("/catalog/devices/load-batch", {
      method: "POST",
      body: JSON.stringify({ deviceCodes: deviceCodes ?? [] }),
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
    enabled?: boolean
  }) =>
    request<ChannelEntity>("/catalog/channels", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  probeChannel: (channelId: string, productId: string) =>
    request<ChannelProbeView>(`/catalog/channels/${encodeURIComponent(channelId)}/probe`, {
      method: "POST",
      body: JSON.stringify({ productId }),
    }),
  createProduct: (body: {
    code: string
    name: string
    description?: string
    productTypeId: string
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
  listClusters: () => request<ActionGroupView[]>("/catalog/clusters"),
  createCluster: (body: ActionGroupWriteRequest) =>
    request<ActionGroupView>("/catalog/clusters", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateCluster: (id: string, body: ActionGroupWriteRequest) =>
    request<ActionGroupView>(`/catalog/clusters/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteCluster: (id: string) =>
    request<{ id: string; deleted: boolean }>(`/catalog/clusters/${encodeURIComponent(id)}`, {
      method: "DELETE",
    }),
  executeCluster: (id: string) =>
    request<ActionGroupExecutionView>(`/catalog/clusters/${encodeURIComponent(id)}/execute`, {
      method: "POST",
    }),
  listScenes: () => request<ActionGroupView[]>("/catalog/scenes"),
  createScene: (body: ActionGroupWriteRequest) =>
    request<ActionGroupView>("/catalog/scenes", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateScene: (id: string, body: ActionGroupWriteRequest) =>
    request<ActionGroupView>(`/catalog/scenes/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteScene: (id: string) =>
    request<{ id: string; deleted: boolean }>(`/catalog/scenes/${encodeURIComponent(id)}`, {
      method: "DELETE",
    }),
  executeScene: (id: string) =>
    request<ActionGroupExecutionView>(`/catalog/scenes/${encodeURIComponent(id)}/execute`, {
      method: "POST",
    }),
}
