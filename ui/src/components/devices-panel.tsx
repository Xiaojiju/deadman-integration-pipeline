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
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
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
import type {
  CapabilityDescriptor,
  ChannelEntity,
  DeviceEntity,
  ProductEntity,
  PropertyItem,
} from "@/lib/types"

const PAGE_SIZE = 20

type Props = {
  products: ProductEntity[]
  channels: ChannelEntity[]
  capabilities: CapabilityDescriptor[]
  productMap: Map<string, ProductEntity>
}

export function DevicesPanel({ products, channels, capabilities, productMap }: Props) {
  const [devices, setDevices] = useState<DeviceEntity[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [commandDevice, setCommandDevice] = useState<string | null>(null)
  const [overrideDevice, setOverrideDevice] = useState<DeviceEntity | null>(null)
  const [busyCode, setBusyCode] = useState<string | null>(null)
  const [editDevice, setEditDevice] = useState<DeviceEntity | null>(null)
  const [deleteDevice, setDeleteDevice] = useState<DeviceEntity | null>(null)
  const [editName, setEditName] = useState("")
  const [editEnabled, setEditEnabled] = useState(true)
  const [editOverridesText, setEditOverridesText] = useState("")
  const [editPending, setEditPending] = useState(false)

  const load = useCallback(async (targetPage = page) => {
    setLoading(true)
    try {
      const result = await catalogApi.listDevices(targetPage, PAGE_SIZE)
      setDevices(result.items)
      setPage(result.page)
      setTotal(result.total)
      setTotalPages(result.totalPages)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载设备失败")
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void load(page)
  }, [page]) // eslint-disable-line react-hooks/exhaustive-deps -- 翻页时加载

  function openEdit(device: DeviceEntity) {
    setEditDevice(device)
    setEditName(device.name ?? "")
    setEditEnabled(device.enabled !== false)
    const overrides = device.functionOverrides ?? {}
    setEditOverridesText(Object.keys(overrides).length ? JSON.stringify(overrides, null, 2) : "")
  }

  async function saveDevice() {
    if (!editDevice) {
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
        name: editName.trim(),
        enabled: editEnabled,
        functionOverrides,
      })
      toast.success(`设备 ${editDevice.deviceCode} 已更新`)
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

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <CardTitle>设备</CardTitle>
          <CardDescription>动态登记、编辑、加载/卸载、手动下发与删除。</CardDescription>
        </div>
        <div className="flex items-center gap-2">
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
        ) : devices.length === 0 ? (
          <Empty className="border border-dashed">
            <EmptyHeader>
              <EmptyTitle>暂无设备</EmptyTitle>
              <EmptyDescription>先创建产品与通道，再登记设备。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>设备编码</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>产品</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>运行时</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {devices.map((device) => {
                const product = productMap.get(device.productId)
                const busy = busyCode === device.deviceCode
                const loaded = Boolean(device.loaded)
                return (
                  <TableRow key={device.id}>
                    <TableCell className="font-mono text-sm">{device.deviceCode}</TableCell>
                    <TableCell>{device.name || "—"}</TableCell>
                    <TableCell>{product?.name || product?.code || device.productId}</TableCell>
                    <TableCell>
                      <Badge variant={device.enabled === false ? "secondary" : "outline"}>
                        {device.enabled === false ? "禁用" : "启用"}
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
              })}
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
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑设备 · {editDevice?.deviceCode}</DialogTitle>
            <DialogDescription>
              可修改名称、启用状态与 functionOverrides（按 functionId → PropertyItem[]）；deviceCode / 产品不可改。
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="deviceName">名称</FieldLabel>
              <Input
                id="deviceName"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
              />
            </Field>
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
            <Button onClick={() => void saveDevice()} disabled={editPending}>
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
