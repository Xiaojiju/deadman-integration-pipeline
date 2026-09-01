import { Loader2Icon } from "lucide-react"

import { SchemaFieldControl } from "@/components/schema-field-control"
import { Button } from "@/components/ui/button"
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
import { catalogApi } from "@/lib/api"
import { isIntFieldType, recordToProperties } from "@/lib/schema-form"
import type { CapabilityDescriptor, ChannelEntity, ProductEntity, SchemaField } from "@/lib/types"
import { useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  products: ProductEntity[]
  channels: ChannelEntity[]
  capabilities: CapabilityDescriptor[]
  onRegistered: () => void
}

export function RegisterDeviceDialog({
  open,
  onOpenChange,
  products,
  channels,
  capabilities,
  onRegistered,
}: Props) {
  const [deviceCode, setDeviceCode] = useState("")
  const [name, setName] = useState("")
  const [productId, setProductId] = useState("")
  const [channelId, setChannelId] = useState("")
  const [address, setAddress] = useState<Record<string, string>>({})
  const [load, setLoad] = useState(true)
  const [pending, setPending] = useState(false)

  const selectedChannel = useMemo(
    () => channels.find((item) => item.id === channelId || item.code === channelId),
    [channels, channelId]
  )

  const addressSchema: SchemaField[] = useMemo(() => {
    if (!selectedChannel) {
      return []
    }
    return (
      capabilities.find((item) => item.capabilityType === selectedChannel.capabilityType)
        ?.addressSchema ?? []
    )
  }, [capabilities, selectedChannel])

  useEffect(() => {
    if (!open) {
      return
    }
    setDeviceCode("")
    setName("")
    setProductId(products[0]?.id ?? "")
    setChannelId(channels[0]?.id ?? channels[0]?.code ?? "")
    setAddress({})
    setLoad(true)
  }, [open, products, channels])

  useEffect(() => {
    const next: Record<string, string> = {}
    for (const field of addressSchema) {
      next[field.name] =
        field.defaultValue !== undefined && field.defaultValue !== null
          ? String(field.defaultValue)
          : ""
    }
    setAddress(next)
  }, [addressSchema])

  async function submit() {
    if (!deviceCode.trim() || !productId || !channelId) {
      toast.error("请填写设备编码、产品与通道")
      return
    }
    setPending(true)
    try {
      const parsedAddress: Record<string, unknown> = {}
      for (const field of addressSchema) {
        const raw = address[field.name] ?? ""
        if (!raw && !field.required) {
          continue
        }
        parsedAddress[field.name] = isIntFieldType(field.type) ? Number(raw) : raw
      }
      await catalogApi.registerDevice({
        deviceCode: deviceCode.trim(),
        productId,
        name: name.trim() || undefined,
        endpoints: [{ channelId, properties: recordToProperties(parsedAddress, addressSchema) }],
        load,
      })
      toast.success(`设备 ${deviceCode} 已登记`)
      onOpenChange(false)
      onRegistered()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "登记失败")
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>登记设备</DialogTitle>
          <DialogDescription>
            创建设备并绑定通道端点，可选立即加载到运行时。
          </DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="deviceCode">设备编码</FieldLabel>
            <Input
              id="deviceCode"
              value={deviceCode}
              onChange={(event) => setDeviceCode(event.target.value)}
              placeholder="door-1"
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="deviceName">显示名称</FieldLabel>
            <Input
              id="deviceName"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="一号门"
            />
          </Field>
          <Field>
            <FieldLabel>产品</FieldLabel>
            <Select value={productId} onValueChange={setProductId}>
              <SelectTrigger>
                <SelectValue placeholder="选择产品" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {products.map((product) => (
                    <SelectItem key={product.id} value={product.id}>
                      {product.name || product.code}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>通道</FieldLabel>
            <Select value={channelId} onValueChange={setChannelId}>
              <SelectTrigger>
                <SelectValue placeholder="选择通道" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {channels.map((channel) => (
                    <SelectItem key={channel.id} value={channel.id}>
                      {channel.code} · {channel.capabilityType}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          {addressSchema.map((field) => (
            <Field key={field.name}>
              <FieldLabel htmlFor={`addr-${field.name}`}>
                {field.label || field.name}
                {field.required ? " *" : ""}
              </FieldLabel>
              <SchemaFieldControl
                field={field}
                value={address[field.name] ?? ""}
                idPrefix="addr"
                onChange={(next) =>
                  setAddress((prev) => ({ ...prev, [field.name]: next }))
                }
              />
            </Field>
          ))}
          <Field orientation="horizontal" className="items-center justify-between">
            <FieldLabel htmlFor="autoLoad">登记后立即 load</FieldLabel>
            <Switch id="autoLoad" checked={load} onCheckedChange={setLoad} />
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            取消
          </Button>
          <Button onClick={submit} disabled={pending}>
            {pending ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
            登记
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
