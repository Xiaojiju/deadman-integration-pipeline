import { Loader2Icon, PlusIcon, Trash2Icon } from "lucide-react"
import { useCallback, useEffect, useMemo, useState, type Dispatch, type SetStateAction } from "react"
import { toast } from "sonner"

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
import { catalogApi } from "@/lib/api"
import type { DeviceEntity, ProductEntity, ProductFunctionEntity } from "@/lib/types"

type KeyValueRow = { key: string; value: string }

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  device: DeviceEntity | null
  product?: ProductEntity
}

function rowsFromRecord(record: Record<string, unknown> | Record<string, string>): KeyValueRow[] {
  return Object.entries(record).map(([key, value]) => ({
    key,
    value: typeof value === "string" ? value : JSON.stringify(value),
  }))
}

function recordFromRows(rows: KeyValueRow[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const row of rows) {
    const key = row.key.trim()
    const value = row.value.trim()
    if (!key || !value) {
      continue
    }
    result[key] = value
  }
  return result
}

function objectRecordFromRows(rows: KeyValueRow[]): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const row of rows) {
    const key = row.key.trim()
    const raw = row.value.trim()
    if (!key || !raw) {
      continue
    }
    if (raw.startsWith("{") || raw.startsWith("[") || raw === "true" || raw === "false") {
      try {
        result[key] = JSON.parse(raw)
        continue
      } catch {
        // fall through
      }
    }
    if (/^-?\d+$/.test(raw)) {
      result[key] = Number(raw)
      continue
    }
    result[key] = raw
  }
  return result
}

export function DeviceOverrideDialog({ open, onOpenChange, device, product }: Props) {
  const [functions, setFunctions] = useState<ProductFunctionEntity[]>([])
  const [functionId, setFunctionId] = useState("")
  const [fieldRows, setFieldRows] = useState<KeyValueRow[]>([])
  const [topicRows, setTopicRows] = useState<KeyValueRow[]>([])
  const [loading, setLoading] = useState(false)
  const [pending, setPending] = useState(false)

  const selectedFn = useMemo(
    () => functions.find((item) => item.functionId === functionId),
    [functions, functionId]
  )

  const loadOverrides = useCallback(async (code: string, fnId: string) => {
    const [fields, topics] = await Promise.all([
      catalogApi.deviceFieldOverrides(code, fnId),
      catalogApi.deviceTopicOverrides(code, fnId),
    ])
    setFieldRows(rowsFromRecord(fields))
    setTopicRows(rowsFromRecord(topics))
  }, [])

  useEffect(() => {
    if (!open || !device?.productId) {
      return
    }
    setLoading(true)
    catalogApi
      .listProductFunctions(device.productId)
      .then((items) => {
        setFunctions(items)
        const first = items[0]?.functionId ?? ""
        setFunctionId(first)
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : "加载产品功能失败"))
      .finally(() => setLoading(false))
  }, [open, device?.productId])

  useEffect(() => {
    if (!open || !device?.deviceCode || !functionId) {
      setFieldRows([])
      setTopicRows([])
      return
    }
    setLoading(true)
    loadOverrides(device.deviceCode, functionId)
      .catch((error) => toast.error(error instanceof Error ? error.message : "加载覆盖失败"))
      .finally(() => setLoading(false))
  }, [open, device?.deviceCode, functionId, loadOverrides])

  function updateRow(
    setter: Dispatch<SetStateAction<KeyValueRow[]>>,
    index: number,
    patch: Partial<KeyValueRow>
  ) {
    setter((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  async function save() {
    if (!device?.deviceCode || !functionId) {
      return
    }
    setPending(true)
    try {
      await catalogApi.replaceDeviceFieldOverrides(
        device.deviceCode,
        functionId,
        objectRecordFromRows(fieldRows)
      )
      await catalogApi.replaceDeviceTopicOverrides(
        device.deviceCode,
        functionId,
        recordFromRows(topicRows)
      )
      toast.success(`${functionId} 覆盖已保存`)
      onOpenChange(false)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存失败")
    } finally {
      setPending(false)
    }
  }

  const isMqtt = selectedFn?.capabilityType === "MQTT"

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>设备覆盖 · {device?.deviceCode}</DialogTitle>
          <DialogDescription>
            按功能配置字段 path 覆盖（如 deviceId）与 MQTT topic slot 覆盖；下发时自动合并。
            {product ? ` 产品：${product.name || product.code}` : null}
          </DialogDescription>
        </DialogHeader>
        {loading && functions.length === 0 ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" />
            加载…
          </div>
        ) : functions.length === 0 ? (
          <p className="text-sm text-muted-foreground">该产品尚未配置功能。</p>
        ) : (
          <FieldGroup>
            <Field>
              <FieldLabel>功能</FieldLabel>
              <Select value={functionId} onValueChange={setFunctionId}>
                <SelectTrigger>
                  <SelectValue placeholder="选择功能" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {functions.map((item) => (
                      <SelectItem key={item.functionId} value={item.functionId}>
                        {item.description && item.description !== item.functionId
                          ? `${item.functionId} · ${item.description}`
                          : `${item.functionId} · ${item.accessType}`}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <FieldLabel>字段覆盖 field path → value</FieldLabel>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => setFieldRows((prev) => [...prev, { key: "", value: "" }])}
                >
                  <PlusIcon data-icon="inline-start" />
                  添加
                </Button>
              </div>
              {fieldRows.length === 0 ? (
                <p className="text-sm text-muted-foreground">无字段覆盖；可添加如 deviceId → MFG-A-001。</p>
              ) : (
                fieldRows.map((row, index) => (
                  <div key={`field-${index}`} className="flex gap-2">
                    <Input
                      placeholder="path，如 deviceId"
                      value={row.key}
                      onChange={(e) => updateRow(setFieldRows, index, { key: e.target.value })}
                      className="font-mono text-sm"
                    />
                    <Input
                      placeholder="覆盖值"
                      value={row.value}
                      onChange={(e) => updateRow(setFieldRows, index, { value: e.target.value })}
                      className="font-mono text-sm"
                    />
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      onClick={() => setFieldRows((prev) => prev.filter((_, i) => i !== index))}
                    >
                      <Trash2Icon className="size-4" />
                    </Button>
                  </div>
                ))
              )}
            </div>
            {isMqtt ? (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <FieldLabel>Topic Slot 覆盖 slot → topic</FieldLabel>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => setTopicRows((prev) => [...prev, { key: "", value: "" }])}
                  >
                    <PlusIcon data-icon="inline-start" />
                    添加
                  </Button>
                </div>
                {topicRows.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    无 topic 覆盖；可填 default_pub 或 topics 中的 slot 名。
                  </p>
                ) : (
                  topicRows.map((row, index) => (
                    <div key={`topic-${index}`} className="flex gap-2">
                      <Input
                        placeholder="slot，如 default_pub"
                        value={row.key}
                        onChange={(e) => updateRow(setTopicRows, index, { key: e.target.value })}
                        className="font-mono text-sm"
                      />
                      <Input
                        placeholder="实际 topic"
                        value={row.value}
                        onChange={(e) => updateRow(setTopicRows, index, { value: e.target.value })}
                        className="font-mono text-sm"
                      />
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        onClick={() => setTopicRows((prev) => prev.filter((_, i) => i !== index))}
                      >
                        <Trash2Icon className="size-4" />
                      </Button>
                    </div>
                  ))
                )}
              </div>
            ) : null}
          </FieldGroup>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            取消
          </Button>
          <Button onClick={() => void save()} disabled={pending || !functionId || loading}>
            {pending ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
