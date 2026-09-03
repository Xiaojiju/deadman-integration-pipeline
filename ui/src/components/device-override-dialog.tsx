import { Loader2Icon, Trash2Icon } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
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
import { catalogApi, MIN_SCHEDULE_MS } from "@/lib/api"
import type { DeviceEntity, ProductEntity, ProductFunctionEntity, WriteFieldOption } from "@/lib/types"

type FieldRow = {
  key: string
  label: string
  hint?: string
  value: string
  fromSchema: boolean
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  device: DeviceEntity | null
  product?: ProductEntity
}

function stringifyValue(value: unknown): string {
  if (value == null) {
    return ""
  }
  if (typeof value === "string") {
    return value
  }
  return JSON.stringify(value)
}

function parseValue(raw: string): unknown {
  const text = raw.trim()
  if (!text) {
    return ""
  }
  if (text.startsWith("{") || text.startsWith("[") || text === "true" || text === "false") {
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
  if (/^-?\d+$/.test(text)) {
    return Number(text)
  }
  return text
}

function deviceSourceFields(fn: ProductFunctionEntity | undefined): WriteFieldOption[] {
  if (!fn) {
    return []
  }
  const fields = fn.accessType?.toUpperCase() === "READ" ? fn.readFields : fn.writeFields
  return (fields ?? []).filter((item) => (item.source ?? "").toLowerCase() === "device")
}

function topicSlotsOf(fn: ProductFunctionEntity | undefined): Array<{ key: string; label: string }> {
  if (!fn) {
    return []
  }
  const slots: Array<{ key: string; label: string }> = []
  const seen = new Set<string>()
  function add(key: string | undefined, label: string) {
    const slot = key?.trim()
    if (!slot || seen.has(slot)) {
      return
    }
    seen.add(slot)
    slots.push({ key: slot, label })
  }
  add(fn.publishTopicSlot, "发布 Topic")
  add(fn.subscribeTopicSlot, "订阅 Topic")
  add(fn.replyTopicSlot, "应答 Topic")
  if (fn.capabilityType === "MQTT") {
    if (!fn.publishTopicSlot?.trim() && fn.accessType?.toUpperCase() !== "READ") {
      add("default_pub", "默认发布 Topic")
    }
    if (!fn.subscribeTopicSlot?.trim() && fn.accessType?.toUpperCase() !== "WRITE") {
      add("default_sub", "默认订阅 Topic")
    }
  }
  return slots
}

function mergeFieldRows(
  schema: WriteFieldOption[],
  saved: Record<string, unknown>
): FieldRow[] {
  const remain = { ...saved }
  const rows: FieldRow[] = schema.map((field) => {
    const value = remain[field.field]
    delete remain[field.field]
    return {
      key: field.field,
      label: field.description?.trim() || field.field,
      hint: field.field,
      value: stringifyValue(value),
      fromSchema: true,
    }
  })
  for (const [key, value] of Object.entries(remain)) {
    rows.push({
      key,
      label: key,
      hint: "功能定义中已无此项",
      value: stringifyValue(value),
      fromSchema: false,
    })
  }
  return rows
}

function mergeTopicRows(
  schema: Array<{ key: string; label: string }>,
  saved: Record<string, string>
): FieldRow[] {
  const remain = { ...saved }
  const rows: FieldRow[] = schema.map((slot) => {
    const value = remain[slot.key]
    delete remain[slot.key]
    return {
      key: slot.key,
      label: slot.label,
      hint: slot.key,
      value: value ?? "",
      fromSchema: true,
    }
  })
  for (const [key, value] of Object.entries(remain)) {
    rows.push({
      key,
      label: key,
      hint: "功能定义中已无此项",
      value: value ?? "",
      fromSchema: false,
    })
  }
  return rows
}

export function DeviceOverrideDialog({ open, onOpenChange, device, product }: Props) {
  const [functions, setFunctions] = useState<ProductFunctionEntity[]>([])
  const [functionId, setFunctionId] = useState("")
  const [fieldRows, setFieldRows] = useState<FieldRow[]>([])
  const [topicRows, setTopicRows] = useState<FieldRow[]>([])
  const [scheduleEnabledChoice, setScheduleEnabledChoice] = useState<"inherit" | "on" | "off">("inherit")
  const [scheduleIntervalText, setScheduleIntervalText] = useState("")
  const [productScheduleHint, setProductScheduleHint] = useState("")
  const [loading, setLoading] = useState(false)
  const [pending, setPending] = useState(false)

  const selectedFn = useMemo(
    () => functions.find((item) => item.functionId === functionId),
    [functions, functionId]
  )

  const loadOverrides = useCallback(async (code: string, fn: ProductFunctionEntity) => {
    const [fields, topics, schedule] = await Promise.all([
      catalogApi.deviceFieldOverrides(code, fn.functionId),
      catalogApi.deviceTopicOverrides(code, fn.functionId),
      catalogApi.deviceSchedule(code, fn.functionId),
    ])
    setFieldRows(mergeFieldRows(deviceSourceFields(fn), fields))
    setTopicRows(mergeTopicRows(topicSlotsOf(fn), topics))
    setScheduleEnabledChoice(
      schedule.overrideEnabled == null ? "inherit" : schedule.overrideEnabled ? "on" : "off"
    )
    setScheduleIntervalText(
      schedule.overrideIntervalMs != null ? String(schedule.overrideIntervalMs) : ""
    )
    const productInterval = schedule.productIntervalMs != null ? `${schedule.productIntervalMs} ms` : "未配置"
    setProductScheduleHint(
      `产品默认：${schedule.productEnabled ? "启用" : "关闭"}，间隔 ${productInterval}`
    )
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
        setFunctionId(items[0]?.functionId ?? "")
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : "加载产品功能失败"))
      .finally(() => setLoading(false))
  }, [open, device?.productId])

  useEffect(() => {
    if (!open || !device?.deviceCode || !functionId) {
      setFieldRows([])
      setTopicRows([])
      setScheduleEnabledChoice("inherit")
      setScheduleIntervalText("")
      setProductScheduleHint("")
      return
    }
    const fn = functions.find((item) => item.functionId === functionId)
    if (!fn) {
      return
    }
    setLoading(true)
    loadOverrides(device.deviceCode, fn)
      .catch((error) => toast.error(error instanceof Error ? error.message : "加载设备参数失败"))
      .finally(() => setLoading(false))
  }, [open, device?.deviceCode, functionId, functions, loadOverrides])

  function updateValue(kind: "field" | "topic", index: number, value: string) {
    const setter = kind === "field" ? setFieldRows : setTopicRows
    setter((prev) => prev.map((row, i) => (i === index ? { ...row, value } : row)))
  }

  async function save() {
    if (!device?.deviceCode || !functionId) {
      return
    }
    setPending(true)
    try {
      const fieldOverrides: Record<string, unknown> = {}
      for (const row of fieldRows) {
        if (!row.value.trim()) {
          continue
        }
        fieldOverrides[row.key] = parseValue(row.value)
      }
      const topicOverrides: Record<string, string> = {}
      for (const row of topicRows) {
        if (!row.value.trim()) {
          continue
        }
        topicOverrides[row.key] = row.value.trim()
      }
      const intervalMs = scheduleIntervalText.trim() === "" ? null : Number(scheduleIntervalText)
      if (scheduleIntervalText.trim() && (!Number.isFinite(intervalMs) || (intervalMs ?? 0) < MIN_SCHEDULE_MS)) {
        toast.error(`定时间隔不能小于 ${MIN_SCHEDULE_MS} 毫秒`)
        return
      }
      const enabled =
        scheduleEnabledChoice === "inherit" ? null : scheduleEnabledChoice === "on"
      await catalogApi.replaceDeviceFieldOverrides(device.deviceCode, functionId, fieldOverrides)
      await catalogApi.replaceDeviceTopicOverrides(device.deviceCode, functionId, topicOverrides)
      await catalogApi.replaceDeviceSchedule(device.deviceCode, functionId, { enabled, intervalMs })
      toast.success(`${functionId} 设备参数已保存`)
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
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>设备参数 · {device?.deviceCode}</DialogTitle>
          <DialogDescription>
            下列字段来自产品功能里需要按设备填写的项，只需填本设备的值。
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
            <div className="space-y-3">
              <FieldLabel>设备字段</FieldLabel>
              {fieldRows.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  该功能没有 source=device 的字段，下发时不需要在这里填值。
                </p>
              ) : (
                fieldRows.map((row, index) => (
                  <Field key={`field-${row.key}-${index}`}>
                    <FieldLabel htmlFor={`dev-field-${index}`}>
                      {row.label}
                      {row.hint && row.hint !== row.label ? (
                        <span className="ml-2 font-mono text-xs font-normal text-muted-foreground">
                          {row.hint}
                        </span>
                      ) : null}
                    </FieldLabel>
                    <div className="flex gap-2">
                      <Input
                        id={`dev-field-${index}`}
                        placeholder="本设备的值"
                        value={row.value}
                        onChange={(e) => updateValue("field", index, e.target.value)}
                      />
                      {!row.fromSchema ? (
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          onClick={() => setFieldRows((prev) => prev.filter((_, i) => i !== index))}
                        >
                          <Trash2Icon className="size-4" />
                        </Button>
                      ) : null}
                    </div>
                  </Field>
                ))
              )}
            </div>
            {isMqtt ? (
              <div className="space-y-3">
                <FieldLabel>本设备 Topic</FieldLabel>
                {topicRows.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    该功能未配置 topic slot，一般使用设备 Address 里的 default_pub / default_sub。
                  </p>
                ) : (
                  topicRows.map((row, index) => (
                    <Field key={`topic-${row.key}-${index}`}>
                      <FieldLabel htmlFor={`dev-topic-${index}`}>
                        {row.label}
                        {row.hint && row.hint !== row.label ? (
                          <span className="ml-2 font-mono text-xs font-normal text-muted-foreground">
                            {row.hint}
                          </span>
                        ) : null}
                      </FieldLabel>
                      <div className="flex gap-2">
                        <Input
                          id={`dev-topic-${index}`}
                          placeholder="本设备实际 topic"
                          value={row.value}
                          onChange={(e) => updateValue("topic", index, e.target.value)}
                        />
                        {!row.fromSchema ? (
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            onClick={() =>
                              setTopicRows((prev) => prev.filter((_, i) => i !== index))
                            }
                          >
                            <Trash2Icon className="size-4" />
                          </Button>
                        ) : null}
                      </div>
                    </Field>
                  ))
                )}
              </div>
            ) : null}
            <Field>
              <FieldLabel>定时下发</FieldLabel>
              <Select
                value={scheduleEnabledChoice}
                onValueChange={(value) => setScheduleEnabledChoice(value as "inherit" | "on" | "off")}
              >
                <SelectTrigger>
                  <SelectValue placeholder="继承产品" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="inherit">继承产品</SelectItem>
                    <SelectItem value="on">本设备启用</SelectItem>
                    <SelectItem value="off">本设备关闭</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="dev-schedule-interval">定时间隔（毫秒）</FieldLabel>
              <Input
                id="dev-schedule-interval"
                type="number"
                min={MIN_SCHEDULE_MS}
                value={scheduleIntervalText}
                onChange={(e) => setScheduleIntervalText(e.target.value)}
                placeholder={`留空继承产品；最低 ${MIN_SCHEDULE_MS}`}
              />
              {productScheduleHint ? (
                <p className="text-sm text-muted-foreground">{productScheduleHint}</p>
              ) : null}
            </Field>
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
