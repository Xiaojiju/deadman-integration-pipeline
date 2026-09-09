import {
  Loader2Icon,
  PlayIcon,
  PlusIcon,
  PowerIcon,
  PowerOffIcon,
  RefreshCwIcon,
  Trash2Icon,
} from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"

import { CommandDialogPanel } from "@/components/command-dialog"
import { DeviceOverrideDialog } from "@/components/device-override-dialog"
import { ListPagination } from "@/components/list-pagination"
import { RegisterDeviceDialog } from "@/components/register-device-dialog"
import { SchemaFieldControl } from "@/components/schema-field-control"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import { catalogApi } from "@/lib/api"
import { isIntFieldType, propertiesToRecord, recordToProperties } from "@/lib/schema-form"
import type {
  CapabilityDescriptor,
  ChannelEntity,
  DeviceEntity,
  DeviceEndpointView,
  ProductEntity,
  ProductTypeView,
  PropertyItem,
  SchemaField,
} from "@/lib/types"

const PAGE_SIZE = 20

type EditEndpointDraft = {
  id: string
  channelId: string
  channelLabel: string
  schema: SchemaField[]
  values: Record<string, string>
}

type Props = {
  products: ProductEntity[]
  channels: ChannelEntity[]
  capabilities: CapabilityDescriptor[]
  productMap: Map<string, ProductEntity>
}

export function DevicesPanel({ products, channels, capabilities, productMap }: Props) {
  const [devices, setDevices] = useState<DeviceEntity[]>([])
  const [productTypes, setProductTypes] = useState<ProductTypeView[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [filterCode, setFilterCode] = useState("")
  const [filterName, setFilterName] = useState("")
  const [filterOnline, setFilterOnline] = useState("all")
  const [filterTypeId, setFilterTypeId] = useState("all")
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [registerOpen, setRegisterOpen] = useState(false)
  const [commandDevice, setCommandDevice] = useState<string | null>(null)
  const [overrideDevice, setOverrideDevice] = useState<DeviceEntity | null>(null)
  const [busyCode, setBusyCode] = useState<string | null>(null)
  const [editDevice, setEditDevice] = useState<DeviceEntity | null>(null)
  const [deleteDevice, setDeleteDevice] = useState<DeviceEntity | null>(null)
  const [editCode, setEditCode] = useState("")
  const [editName, setEditName] = useState("")
  const [editEnabled, setEditEnabled] = useState(true)
  const [editOverridesText, setEditOverridesText] = useState("")
  const [editEndpoints, setEditEndpoints] = useState<EditEndpointDraft[]>([])
  const [editEndpointsLoading, setEditEndpointsLoading] = useState(false)
  const [editPending, setEditPending] = useState(false)

  const load = useCallback(async (targetPage = page) => {
    setLoading(true)
    try {
      const [result, types] = await Promise.all([
        catalogApi.listDevices(targetPage, PAGE_SIZE, {
          name: filterName.trim() || undefined,
          deviceCode: filterCode.trim() || undefined,
          online: filterOnline === "all" ? undefined : filterOnline,
          productTypeId: filterTypeId === "all" ? undefined : filterTypeId,
        }),
        catalogApi.listProductTypes(),
      ])
      setDevices(result.items)
      setPage(result.page)
      setTotal(result.total)
      setTotalPages(result.totalPages)
      setProductTypes(types)
      setSelected((prev) => {
        const codes = new Set(result.items.map((item) => item.deviceCode))
        return new Set([...prev].filter((code) => codes.has(code)))
      })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载设备失败")
    } finally {
      setLoading(false)
    }
  }, [page, filterName, filterCode, filterOnline, filterTypeId])

  useEffect(() => {
    void load(page)
  }, [page, filterName, filterCode, filterOnline, filterTypeId]) // eslint-disable-line react-hooks/exhaustive-deps -- 翻页与筛选时加载

  function openEdit(device: DeviceEntity) {
    setEditDevice(device)
    setEditCode(device.deviceCode)
    setEditName(device.name ?? "")
    setEditEnabled(device.enabled !== false)
    const overrides = device.functionOverrides ?? {}
    setEditOverridesText(Object.keys(overrides).length ? JSON.stringify(overrides, null, 2) : "")
    setEditEndpoints([])
    void loadEditEndpoints(device)
  }

  async function loadEditEndpoints(device: DeviceEntity) {
    setEditEndpointsLoading(true)
    try {
      const rows = await catalogApi.listDeviceEndpoints(device.deviceCode)
      setEditEndpoints(rows.map((row) => toEditEndpoint(row, channels, capabilities)))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载设备端点失败")
    } finally {
      setEditEndpointsLoading(false)
    }
  }

  async function saveDevice() {
    if (!editDevice) {
      return
    }
    const nextCode = editCode.trim()
    if (!nextCode) {
      toast.error("请填写设备编码")
      return
    }
    let functionOverrides: Record<string, PropertyItem[]> | undefined
    if (editOverridesText.trim()) {
      try {
        functionOverrides = JSON.parse(editOverridesText) as Record<string, PropertyItem[]>
      } catch {
        toast.error("functionOverrides 不是合法 JSON")
        return
      }
    } else {
      functionOverrides = {}
    }
    setEditPending(true)
    try {
      await catalogApi.updateDevice(editDevice.deviceCode, {
        deviceCode: nextCode,
        name: editName.trim(),
        enabled: editEnabled,
        functionOverrides,
        endpoints: editEndpoints.map((endpoint) => ({
          id: endpoint.id,
          properties: recordToProperties(parseAddressValues(endpoint), endpoint.schema),
        })),
      })
      toast.success(`设备 ${nextCode} 已更新`)
      setEditDevice(null)
      await load(page)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "更新设备失败")
    } finally {
      setEditPending(false)
    }
  }

  async function runAction(deviceCode: string, action: "load" | "unload" | "delete") {
    setBusyCode(deviceCode)
    try {
      if (action === "load") {
        await catalogApi.loadDevice(deviceCode)
        toast.success(`${deviceCode} 已加载`)
      } else if (action === "unload") {
        await catalogApi.unloadDevice(deviceCode)
        toast.success(`${deviceCode} 已卸载`)
      } else {
        await catalogApi.deleteDevice(deviceCode)
        toast.success(`${deviceCode} 已删除`)
        setDeleteDevice(null)
        const nextPage = devices.length <= 1 && page > 1 ? page - 1 : page
        setPage(nextPage)
        await load(nextPage)
        return
      }
      await load(page)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "操作失败")
    } finally {
      setBusyCode(null)
    }
  }

  function toggleSelected(deviceCode: string, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (checked) {
        next.add(deviceCode)
      } else {
        next.delete(deviceCode)
      }
      return next
    })
  }

  function togglePage(checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev)
      for (const device of devices) {
        if (checked) {
          next.add(device.deviceCode)
        } else {
          next.delete(device.deviceCode)
        }
      }
      return next
    })
  }

  async function loadSelected() {
    const codes = [...selected]
    if (codes.length === 0) {
      toast.error("请先勾选要加载的设备")
      return
    }
    await runBatchLoad(codes)
  }

  async function loadAllEnabled() {
    if (!window.confirm("将加载全部已启用且尚未 Load 的设备，确认继续？")) {
      return
    }
    await runBatchLoad([])
  }

  async function runBatchLoad(deviceCodes: string[]) {
    setBusyCode("*")
    try {
      const result = await catalogApi.loadDeviceBatch(deviceCodes)
      const failed = result.items.filter((item) => item.status === "failed")
      if (result.failed > 0) {
        toast.error(
          `Load 完成：成功 ${result.loaded}，跳过 ${result.skipped}，失败 ${result.failed}` +
            (failed[0]?.error ? `。例如 ${failed[0].deviceCode}: ${failed[0].error}` : "")
        )
      } else {
        toast.success(`Load 完成：成功 ${result.loaded}，跳过 ${result.skipped}`)
      }
      setSelected(new Set())
      await load(page)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "批量 Load 失败")
    } finally {
      setBusyCode(null)
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <CardTitle>设备</CardTitle>
          <CardDescription>动态登记、编辑、加载/卸载、手动下发与删除。</CardDescription>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void loadAllEnabled()}
            disabled={loading || busyCode !== null}
          >
            <PowerIcon data-icon="inline-start" />
            全部 Load
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void loadSelected()}
            disabled={loading || busyCode !== null || selected.size === 0}
          >
            <PowerIcon data-icon="inline-start" />
            Load 所选 ({selected.size})
          </Button>
          <Button variant="outline" size="sm" onClick={() => void load(page)} disabled={loading}>
            <RefreshCwIcon data-icon="inline-start" />
            刷新
          </Button>
          <Button size="sm" onClick={() => setRegisterOpen(true)}>
            <PlusIcon data-icon="inline-start" />
            登记设备
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" />
            加载中…
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <input
                    type="checkbox"
                    aria-label="全选本页"
                    checked={devices.length > 0 && devices.every((item) => selected.has(item.deviceCode))}
                    onChange={(event) => togglePage(event.target.checked)}
                  />
                </TableHead>
                <TableHead>设备编码</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>产品</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>在线</TableHead>
                <TableHead>运行时</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
              <TableRow>
                <TableHead />
                <TableHead>
                  <Input
                    value={filterCode}
                    placeholder="筛选编码"
                    onChange={(event) => {
                      setPage(1)
                      setFilterCode(event.target.value)
                    }}
                  />
                </TableHead>
                <TableHead>
                  <Input
                    value={filterName}
                    placeholder="筛选名称"
                    onChange={(event) => {
                      setPage(1)
                      setFilterName(event.target.value)
                    }}
                  />
                </TableHead>
                <TableHead />
                <TableHead>
                  <Select
                    value={filterTypeId}
                    onValueChange={(value) => {
                      setPage(1)
                      setFilterTypeId(value)
                    }}
                  >
                    <SelectTrigger className="h-8">
                      <SelectValue placeholder="全部类型" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="all">全部类型</SelectItem>
                        {productTypes.map((item) => (
                          <SelectItem key={item.id} value={item.id}>
                            {item.name}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </TableHead>
                <TableHead />
                <TableHead>
                  <Select
                    value={filterOnline}
                    onValueChange={(value) => {
                      setPage(1)
                      setFilterOnline(value)
                    }}
                  >
                    <SelectTrigger className="h-8">
                      <SelectValue placeholder="在线" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="all">全部</SelectItem>
                        <SelectItem value="true">在线</SelectItem>
                        <SelectItem value="false">离线</SelectItem>
                        <SelectItem value="unknown">未知</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </TableHead>
                <TableHead />
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {devices.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="text-muted-foreground">
                    暂无设备。可先登记，或放宽筛选条件。
                  </TableCell>
                </TableRow>
              ) : (
                devices.map((device) => {
                const product = productMap.get(device.productId)
                const busy = busyCode === device.deviceCode || busyCode === "*"
                const loaded = Boolean(device.loaded)
                return (
                  <TableRow key={device.id}>
                    <TableCell>
                      <input
                        type="checkbox"
                        aria-label={`选择 ${device.deviceCode}`}
                        checked={selected.has(device.deviceCode)}
                        onChange={(event) => toggleSelected(device.deviceCode, event.target.checked)}
                      />
                    </TableCell>
                    <TableCell className="font-mono text-sm">{device.deviceCode}</TableCell>
                    <TableCell>{device.name || "—"}</TableCell>
                    <TableCell>{product?.name || product?.code || device.productId}</TableCell>
                    <TableCell>{product?.productTypeName || product?.productTypeCode || "—"}</TableCell>
                    <TableCell>
                      <Badge variant={device.enabled === false ? "secondary" : "outline"}>
                        {device.enabled === false ? "禁用" : "启用"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          device.online === true
                            ? "default"
                            : device.online === false
                              ? "destructive"
                              : "secondary"
                        }
                      >
                        {device.online === true ? "在线" : device.online === false ? "离线" : "未知"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={device.loaded ? "default" : "secondary"}>
                        {device.loaded ? "已加载" : "未加载"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy}
                          onClick={() => openEdit(device)}
                        >
                          编辑
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy}
                          onClick={() => setOverrideDevice(device)}
                        >
                          设备参数
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy || !loaded}
                          onClick={() => setCommandDevice(device.deviceCode)}
                        >
                          <PlayIcon data-icon="inline-start" />
                          指令
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy || loaded}
                          onClick={() => void runAction(device.deviceCode, "load")}
                        >
                          <PowerIcon data-icon="inline-start" />
                          Load
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy || !loaded}
                          onClick={() => void runAction(device.deviceCode, "unload")}
                        >
                          <PowerOffIcon data-icon="inline-start" />
                          Unload
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          disabled={busy}
                          onClick={() => setDeleteDevice(device)}
                        >
                          <Trash2Icon data-icon="inline-start" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
              )}
            </TableBody>
          </Table>
        )}
        <ListPagination
          page={page}
          totalPages={totalPages}
          total={total}
          onPageChange={setPage}
        />
      </CardContent>

      <Dialog
        open={editDevice != null}
        onOpenChange={(next) => {
          if (!next) {
            setEditDevice(null)
          }
        }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>编辑设备 · {editDevice?.deviceCode}</DialogTitle>
            <DialogDescription>
              可修改名称、设备编码、端点地址（如序列号）与启用状态。产品不可改。
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="editDeviceCode">设备编码</FieldLabel>
              <Input
                id="editDeviceCode"
                value={editCode}
                onChange={(e) => setEditCode(e.target.value)}
                placeholder="door-1"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="deviceName">名称</FieldLabel>
              <Input
                id="deviceName"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
              />
            </Field>
            {editEndpointsLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2Icon className="size-4 animate-spin" />
                加载端点地址…
              </div>
            ) : (
              editEndpoints.map((endpoint, endpointIndex) => (
                <div key={endpoint.id} className="flex flex-col gap-3">
                  {editEndpoints.length > 1 ? (
                    <p className="text-sm text-muted-foreground">{endpoint.channelLabel}</p>
                  ) : null}
                  {endpoint.schema.map((field) => (
                    <Field key={`${endpoint.id}-${field.name}`}>
                      <FieldLabel htmlFor={`edit-addr-${endpoint.id}-${field.name}`}>
                        {field.label || field.name}
                        {field.required ? " *" : ""}
                      </FieldLabel>
                      <SchemaFieldControl
                        field={field}
                        value={endpoint.values[field.name] ?? ""}
                        idPrefix={`edit-addr-${endpoint.id}`}
                        onChange={(next) =>
                          setEditEndpoints((prev) =>
                            prev.map((item, index) =>
                              index === endpointIndex
                                ? { ...item, values: { ...item.values, [field.name]: next } }
                                : item
                            )
                          )
                        }
                      />
                    </Field>
                  ))}
                </div>
              ))
            )}
            <Field orientation="horizontal">
              <FieldLabel htmlFor="deviceEnabled">启用</FieldLabel>
              <Switch
                id="deviceEnabled"
                checked={editEnabled}
                onCheckedChange={setEditEnabled}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="functionOverrides">functionOverrides JSON</FieldLabel>
              <Textarea
                id="functionOverrides"
                value={editOverridesText}
                onChange={(e) => setEditOverridesText(e.target.value)}
                placeholder='{"remoteControlDoor":[{"attribute":"target","attributeValue":"1","dataType":"string"}]}'
                className="min-h-28 font-mono text-xs"
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDevice(null)} disabled={editPending}>
              取消
            </Button>
            <Button onClick={() => void saveDevice()} disabled={editPending || editEndpointsLoading}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteDevice != null}
        onOpenChange={(next) => {
          if (!next && busyCode == null) {
            setDeleteDevice(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认删除设备</DialogTitle>
            <DialogDescription>
              将卸载运行时并删除设备 {deleteDevice?.deviceCode}
              {deleteDevice?.name ? `（${deleteDevice.name}）` : ""} 的配置，此操作不可恢复。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              disabled={busyCode != null}
              onClick={() => setDeleteDevice(null)}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              disabled={busyCode != null || deleteDevice == null}
              onClick={() => {
                if (deleteDevice) {
                  void runAction(deleteDevice.deviceCode, "delete")
                }
              }}
            >
              {busyCode != null ? "删除中…" : "确认删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <RegisterDeviceDialog
        open={registerOpen}
        onOpenChange={setRegisterOpen}
        products={products}
        channels={channels}
        capabilities={capabilities}
        onRegistered={() => {
          setPage(1)
          void load(1)
        }}
      />
      {commandDevice ? (
        <CommandDialogPanel
          open={Boolean(commandDevice)}
          onOpenChange={(open) => {
            if (!open) {
              setCommandDevice(null)
            }
          }}
          deviceCode={commandDevice}
        />
      ) : null}
      <DeviceOverrideDialog
        open={overrideDevice != null}
        onOpenChange={(open) => {
          if (!open) {
            setOverrideDevice(null)
          }
        }}
        device={overrideDevice}
        product={overrideDevice ? productMap.get(overrideDevice.productId) : undefined}
      />
    </Card>
  )
}

function toEditEndpoint(
  row: DeviceEndpointView,
  channels: ChannelEntity[],
  capabilities: CapabilityDescriptor[]
): EditEndpointDraft {
  const channel = channels.find((item) => item.id === row.channelId || item.code === row.channelId)
  const schema = addressSchemaOf(channel, capabilities, row.properties)
  const stored = propertiesToRecord(row.properties)
  const values: Record<string, string> = {}
  for (const field of schema) {
    const current = stored[field.name]
    values[field.name] =
      current !== undefined && current !== null
        ? String(current)
        : field.defaultValue !== undefined && field.defaultValue !== null
          ? String(field.defaultValue)
          : ""
  }
  return {
    id: row.id,
    channelId: row.channelId,
    channelLabel: channel
      ? `${channel.code} · ${channel.capabilityType}`
      : row.channelId,
    schema,
    values,
  }
}

function addressSchemaOf(
  channel: ChannelEntity | undefined,
  capabilities: CapabilityDescriptor[],
  properties?: PropertyItem[]
): SchemaField[] {
  const schema =
    capabilities.find((item) => item.capabilityType === channel?.capabilityType)?.addressSchema ?? []
  if (schema.length > 0) {
    return schema
  }
  return (properties ?? []).map((item) => ({
    name: item.attribute,
    type: item.dataType || "string",
    required: false,
    description: item.description ?? "",
    label: item.attribute,
  }))
}

function parseAddressValues(endpoint: EditEndpointDraft): Record<string, unknown> {
  const parsed: Record<string, unknown> = {}
  for (const field of endpoint.schema) {
    const raw = endpoint.values[field.name] ?? ""
    if (!raw && !field.required) {
      continue
    }
    parsed[field.name] = isIntFieldType(field.type) ? Number(raw) : raw
  }
  return parsed
}
