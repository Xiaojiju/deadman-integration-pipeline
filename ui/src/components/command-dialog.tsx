import { Loader2Icon } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { catalogApi } from "@/lib/api"
import { isBooleanFieldType, isIntFieldType, isArrayFieldType, toFieldStringMap } from "@/lib/schema-form"
import type { FunctionFormView, ValueOption } from "@/lib/types"

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  deviceCode: string
}

function parseFieldValue(field: { type?: string; name: string }, raw: string): unknown {
  if (raw === "") {
    return raw
  }
  if (isIntFieldType(field.type)) {
    return Number(raw)
  }
  if (isBooleanFieldType(field.type)) {
    return raw === "true" || raw === "1"
  }
  if (
    isArrayFieldType(field.type) ||
    field.type === "json" ||
    raw.trim().startsWith("[") ||
    raw.trim().startsWith("{")
  ) {
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  }
  return raw
}

export function CommandDialogPanel({ open, onOpenChange, deviceCode }: Props) {
  const [functions, setFunctions] = useState<FunctionFormView[]>([])
  const [functionId, setFunctionId] = useState("")
  const [values, setValues] = useState<Record<string, string>>({})
  const [selectedOptionValue, setSelectedOptionValue] = useState<string>("")
  const [loading, setLoading] = useState(false)
  const [pending, setPending] = useState(false)

  const writeFunctions = useMemo(
    () => functions.filter((item) => item.accessType?.toUpperCase() === "WRITE"),
    [functions]
  )

  const selected = useMemo(
    () => writeFunctions.find((item) => item.functionId === functionId),
    [writeFunctions, functionId]
  )

  const writeOptions: ValueOption[] = selected?.writeValueOptions ?? []
  const isValueMode =
    selected?.payloadMode === "VALUE" ||
    (writeOptions.length > 0 && selected?.writeAccessType === "VALUE")

  const callerFields = useMemo(() => selected?.parameters ?? [], [selected])

  useEffect(() => {
    if (!open || !deviceCode) {
      return
    }
    setLoading(true)
    catalogApi
      .deviceFunctions(deviceCode)
      .then((items) => {
        const writable = items.filter((item) => item.accessType?.toUpperCase() === "WRITE")
        setFunctions(items)
        const first = writable[0]
        setFunctionId(first?.functionId ?? "")
        setValues(toFieldStringMap(first?.values ?? {}))
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : "加载功能失败"))
      .finally(() => setLoading(false))
  }, [open, deviceCode])

  useEffect(() => {
    if (!selected) {
      setValues({})
      setSelectedOptionValue("")
      return
    }
    setValues(toFieldStringMap(selected.values ?? {}))
    const options = selected.writeValueOptions ?? []
    const preferred =
      options.find((item) => item.isDefault)?.optionValue ?? options[0]?.optionValue ?? ""
    setSelectedOptionValue(preferred)
  }, [selected])

  async function submit() {
    if (!functionId) {
      toast.error("请选择功能")
      return
    }
    setPending(true)
    try {
      const args: Record<string, unknown> = {}
      if (writeOptions.length > 0) {
        if (!selectedOptionValue) {
          toast.error("请选择写选项")
          setPending(false)
          return
        }
        if (isValueMode) {
          args.value = selectedOptionValue
        } else {
          const target = callerFields.length === 1 ? callerFields[0].name : "command"
          args[target] = selectedOptionValue
        }
      } else {
        for (const field of callerFields) {
          const raw = values[field.name] ?? ""
          if (raw === "" && !field.required) {
            continue
          }
          args[field.name] = parseFieldValue(field, raw)
        }
      }
      const result = await catalogApi.invokeCommand(deviceCode, functionId, args)
      if (result.status === "SUCCESS") {
        toast.success(`${functionId} 执行成功`)
      } else {
        toast.error(result.failure?.message ?? `${functionId} 失败: ${result.status}`)
      }
      onOpenChange(false)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "下发失败")
    } finally {
      setPending(false)
    }
  }

  const canSubmit =
    !pending &&
    !loading &&
    !!functionId &&
    (writeOptions.length === 0 || !!selectedOptionValue)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>手动下发 · {deviceCode}</DialogTitle>
          <DialogDescription>
            仅需填写调用方字段；seq、at、deviceId 等平台/设备字段由配置自动填充。
          </DialogDescription>
        </DialogHeader>
        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" />
            加载功能列表…
          </div>
        ) : writeFunctions.length === 0 ? (
          <p className="text-sm text-muted-foreground">该设备产品尚未配置 WRITE 功能。</p>
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
                    {writeFunctions.map((item) => (
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
            {writeOptions.length > 0 ? (
              <Field>
                <FieldLabel>{isValueMode ? "业务值" : "写选项"}</FieldLabel>
                <div className="flex flex-wrap gap-2">
                  {writeOptions.map((option) => (
                    <Button
                      key={option.optionValue}
                      type="button"
                      variant={
                        selectedOptionValue === option.optionValue ? "default" : "outline"
                      }
                      disabled={pending}
                      onClick={() => setSelectedOptionValue(option.optionValue)}
                    >
                      {option.description || option.optionValue}
                    </Button>
                  ))}
                </div>
              </Field>
            ) : (
              callerFields.map((field) => (
                <Field key={field.name}>
                  <FieldLabel htmlFor={`cmd-${field.name}`}>
                    {field.label || field.name}
                    {field.required ? " *" : ""}
                  </FieldLabel>
                  <SchemaFieldControl
                    field={field}
                    value={values[field.name] ?? ""}
                    idPrefix="cmd"
                    onChange={(next) =>
                      setValues((prev) => ({ ...prev, [field.name]: next }))
                    }
                  />
                </Field>
              ))
            )}
          </FieldGroup>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            取消
          </Button>
          <Button onClick={() => void submit()} disabled={!canSubmit}>
            {pending ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
            下发
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
