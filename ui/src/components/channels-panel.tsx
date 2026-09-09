import { PlusIcon } from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"

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
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty"
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
import { catalogApi } from "@/lib/api"
import {
  buildValuesFromFields,
  defaultsFromSchema,
  propertiesToRecord,
  recordToProperties,
  toFieldStringMap,
} from "@/lib/schema-form"
import type { CapabilityDescriptor, ChannelEntity, ChannelProbeView, ProductEntity, SchemaField } from "@/lib/types"

type Props = {
  channels: ChannelEntity[]
  capabilities: CapabilityDescriptor[]
  products: ProductEntity[]
  onChanged: () => void
}

type Mode = "create" | "edit"

const MODBUS_TCP_FIELDS = new Set(["transport", "host", "port", "keepAlive"])
const MODBUS_RTU_FIELDS = new Set(["transport", "serialPort", "baudRate", "dataBits", "parity", "stopBits"])

function visibleConnectionSchema(
  schema: SchemaField[],
  capabilityType: string,
  transport: string
): SchemaField[] {
  if (capabilityType !== "MODBUS") {
    return schema
  }
  const rtu = (transport || "TCP").toUpperCase() === "RTU"
  const allowed = rtu ? MODBUS_RTU_FIELDS : MODBUS_TCP_FIELDS
  return schema
    .filter((field) => allowed.has(field.name))
    .map((field) =>
      field.name === "host" || field.name === "serialPort" ? { ...field, required: true } : field
    )
}

export function ChannelsPanel({ channels, capabilities, products, onChanged }: Props) {
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<Mode>("create")
  const [editingId, setEditingId] = useState<string | null>(null)
  const [code, setCode] = useState("")
  const [capabilityType, setCapabilityType] = useState("")
  const [connection, setConnection] = useState<Record<string, string>>({})
  const [enabled, setEnabled] = useState(true)
  const [pending, setPending] = useState(false)
  const [probeChannel, setProbeChannel] = useState<ChannelEntity | null>(null)
  const [probeProductId, setProbeProductId] = useState("")
  const [probeResult, setProbeResult] = useState<ChannelProbeView | null>(null)

  const schema: SchemaField[] = useMemo(() => {
    return capabilities.find((item) => item.capabilityType === capabilityType)?.connectionSchema ?? []
  }, [capabilities, capabilityType])
  const visibleSchema: SchemaField[] = useMemo(
    () => visibleConnectionSchema(schema, capabilityType, connection.transport || "TCP"),
    [schema, capabilityType, connection.transport]
  )

  function openCreate() {
    const first = capabilities[0]?.capabilityType ?? ""
    setMode("create")
    setEditingId(null)
    setCode("")
    setCapabilityType(first)
    setConnection(defaultsFromSchema(capabilities[0]?.connectionSchema ?? []))
    setEnabled(true)
    setOpen(true)
  }

  function openEdit(channel: ChannelEntity) {
    setMode("edit")
    setEditingId(channel.id)
    setCode(channel.code)
    setCapabilityType(channel.capabilityType)
    setConnection(toFieldStringMap(propertiesToRecord(channel.properties)))
    setEnabled(channel.enabled !== false)
    setOpen(true)
  }

  async function submit() {
    if (mode === "create" && (!code.trim() || !capabilityType)) {
      toast.error("请填写通道编码与能力类型")
      return
    }
    setPending(true)
    try {
      const body = buildValuesFromFields(visibleSchema, connection)
      const properties = recordToProperties(body, visibleSchema)
      if (mode === "create") {
        await catalogApi.createChannel({
          code: code.trim(),
          capabilityType,
          properties,
          enabled,
        })
        toast.success(`通道 ${code} 已创建`)
      } else if (editingId) {
        await catalogApi.updateChannel(editingId, {
          properties,
          enabled,
        })
        toast.success(`通道 ${code} 已更新`)
      }
      setOpen(false)
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : mode === "create" ? "创建通道失败" : "更新通道失败")
    } finally {
      setPending(false)
    }
  }

  async function removeChannel(channel: ChannelEntity) {
    if (!window.confirm(`确认删除通道 ${channel.code}？`)) {
      return
    }
    setPending(true)
    try {
      await catalogApi.deleteChannel(channel.id)
      toast.success(`通道 ${channel.code} 已删除`)
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除通道失败")
    } finally {
      setPending(false)
    }
  }

  function openProbe(channel: ChannelEntity) {
    const accessControl = products.find((item) => item.productTypeCode === "ACCESS_CONTROL")
    setProbeChannel(channel)
    setProbeProductId(accessControl?.id ?? products[0]?.id ?? "")
    setProbeResult(null)
  }

  async function submitProbe() {
    if (!probeChannel) {
      return
    }
    if (!probeProductId) {
      toast.error("请选择用于新建设备的产品")
      return
    }
    setPending(true)
    try {
      const result = await catalogApi.probeChannel(probeChannel.id, probeProductId)
      setProbeResult(result)
      toast.success(
        `扫描完成：发现 ${result.discovered}，新建 ${result.created}，序列号更新 ${result.serialUpdated}`
      )
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "扫描通道失败")
    } finally {
      setPending(false)
    }
  }

  function capabilityOf(channel: ChannelEntity) {
    return capabilities.find((item) => item.capabilityType === channel.capabilityType)
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <CardTitle>通道</CardTitle>
          <CardDescription>共享连接。Modbus 通道选 TCP 或 RTU，产品功能仍是同一套。</CardDescription>
        </div>
        <Button size="sm" onClick={openCreate}>
          <PlusIcon data-icon="inline-start" />
          新建通道
        </Button>
      </CardHeader>
      <CardContent>
        {channels.length === 0 ? (
          <Empty className="border border-dashed">
            <EmptyHeader>
              <EmptyTitle>暂无通道</EmptyTitle>
              <EmptyDescription>按能力 connection schema 创建共享通道。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>编码</TableHead>
                <TableHead>能力</TableHead>
                <TableHead>连接</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="w-48">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {channels.map((channel) => (
                <TableRow key={channel.id}>
                  <TableCell className="font-mono text-sm">{channel.code}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{channel.capabilityType}</Badge>
                  </TableCell>
                  <TableCell className="max-w-md truncate font-mono text-xs text-muted-foreground">
                    {JSON.stringify(propertiesToRecord(channel.properties))}
                  </TableCell>
                  <TableCell>{channel.enabled === false ? "禁用" : "启用"}</TableCell>
                  <TableCell className="flex flex-wrap gap-1">
                    {capabilityOf(channel)?.probeSupported ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={pending || channel.enabled === false}
                        onClick={() => openProbe(channel)}
                      >
                        扫描
                      </Button>
                    ) : null}
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={pending}
                      onClick={() => openEdit(channel)}
                    >
                      编辑
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={pending}
                      onClick={() => void removeChannel(channel)}
                    >
                      删除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{mode === "create" ? "新建通道" : "编辑通道"}</DialogTitle>
            <DialogDescription>
              {mode === "create"
                ? "填写能力连接参数，校验后落库。"
                : "可修改连接参数与启用状态；编码与能力类型不可改。"}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="channelCode">通道编码</FieldLabel>
              <Input
                id="channelCode"
                value={code}
                disabled={mode === "edit"}
                onChange={(event) => setCode(event.target.value)}
                placeholder="hik-1"
              />
            </Field>
            <Field>
              <FieldLabel>能力类型</FieldLabel>
              <Select
                value={capabilityType}
                disabled={mode === "edit"}
                onValueChange={(value) => {
                  setCapabilityType(value)
                  const next = capabilities.find((item) => item.capabilityType === value)?.connectionSchema ?? []
                  setConnection(defaultsFromSchema(next))
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择能力" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {capabilities.map((item) => (
                      <SelectItem key={item.capabilityType} value={item.capabilityType}>
                        {item.capabilityType}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            {visibleSchema.map((field) => (
              <Field key={field.name}>
                <FieldLabel htmlFor={`conn-${field.name}`}>
                  {field.label || field.name}
                  {field.required ? " *" : ""}
                </FieldLabel>
                <SchemaFieldControl
                  field={field}
                  value={connection[field.name] ?? ""}
                  idPrefix="conn"
                  onChange={(next) =>
                    setConnection((prev) => ({ ...prev, [field.name]: next }))
                  }
                />
              </Field>
            ))}
            <Field orientation="horizontal">
              <FieldLabel htmlFor="channelEnabled">启用</FieldLabel>
              <Switch id="channelEnabled" checked={enabled} onCheckedChange={setEnabled} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={pending}>
              取消
            </Button>
            <Button onClick={() => void submit()} disabled={pending}>
              {mode === "create" ? "创建" : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={probeChannel !== null} onOpenChange={(next) => !next && setProbeChannel(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>扫描通道设备</DialogTitle>
            <DialogDescription>
              从 {probeChannel?.code} 拉取门禁子设备。已存在的设备只更新序列号与在线状态，名称不变；新建的设备不会自动加载。
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel>新产品归属</FieldLabel>
              <Select value={probeProductId} onValueChange={setProbeProductId}>
                <SelectTrigger>
                  <SelectValue placeholder="选择门禁产品" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {products.map((item) => (
                      <SelectItem key={item.id} value={item.id}>
                        {item.name || item.code}
                        {item.productTypeName ? ` · ${item.productTypeName}` : ""}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            {probeResult ? (
              <p className="text-sm text-muted-foreground">
                发现 {probeResult.discovered} 台，新建 {probeResult.created}，序列号更新 {probeResult.serialUpdated}，未变 {probeResult.unchanged}。
              </p>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setProbeChannel(null)} disabled={pending}>
              关闭
            </Button>
            <Button onClick={() => void submitProbe()} disabled={pending || !probeProductId}>
              开始扫描
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
