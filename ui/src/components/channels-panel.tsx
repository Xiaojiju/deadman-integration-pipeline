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
  propertiesToRecord,
  recordToProperties,
  toFieldStringMap,
} from "@/lib/schema-form"
import type { CapabilityDescriptor, ChannelEntity, SchemaField } from "@/lib/types"

type Props = {
  channels: ChannelEntity[]
  capabilities: CapabilityDescriptor[]
  onChanged: () => void
}

type Mode = "create" | "edit"

export function ChannelsPanel({ channels, capabilities, onChanged }: Props) {
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<Mode>("create")
  const [editingId, setEditingId] = useState<string | null>(null)
  const [code, setCode] = useState("")
  const [capabilityType, setCapabilityType] = useState("")
  const [connection, setConnection] = useState<Record<string, string>>({})
  const [enabled, setEnabled] = useState(true)
  const [pending, setPending] = useState(false)

  const schema: SchemaField[] = useMemo(() => {
    return capabilities.find((item) => item.capabilityType === capabilityType)?.connectionSchema ?? []
  }, [capabilities, capabilityType])

  function openCreate() {
    const first = capabilities[0]?.capabilityType ?? ""
    setMode("create")
    setEditingId(null)
    setCode("")
    setCapabilityType(first)
    setConnection({})
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
      const body = buildValuesFromFields(schema, connection)
      const properties = recordToProperties(body, schema)
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

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <CardTitle>通道</CardTitle>
          <CardDescription>共享连接（host/port/凭证），多设备复用。</CardDescription>
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
                <TableHead className="w-32">操作</TableHead>
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
                  <TableCell className="flex gap-1">
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
                  setConnection({})
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
            {schema.map((field) => (
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
    </Card>
  )
}
