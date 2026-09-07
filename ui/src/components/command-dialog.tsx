import { Loader2Icon } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

import {
  ActionArgumentFields,
  collectActionArguments,
  isValueModeOf,
} from "@/components/action-argument-fields"
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
import { toFieldStringMap } from "@/lib/schema-form"
import type { FunctionFormView, ValueOption } from "@/lib/types"

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  deviceCode: string
}

export function CommandDialogPanel({ open, onOpenChange, deviceCode }: Props) {
  const [functions, setFunctions] = useState<FunctionFormView[]>([])
  const [functionId, setFunctionId] = useState("")
  const [values, setValues] = useState<Record<string, string>>({})
  const [selectedOptionValue, setSelectedOptionValue] = useState<string>("")
  const [loading, setLoading] = useState(false)
  const [pending, setPending] = useState(false)

  const commandFunctions = useMemo(
    () =>
      functions.filter((item) => {
        const access = item.accessType?.toUpperCase()
        return access === "WRITE" || access === "READ"
      }),
    [functions]
  )

  const selected = useMemo(
    () => commandFunctions.find((item) => item.functionId === functionId) ?? null,
    [commandFunctions, functionId]
  )
  const isRead = selected?.accessType?.toUpperCase() === "READ"
  const writeOptions: ValueOption[] = selected?.writeValueOptions ?? []
  const isValueMode = isValueModeOf(selected)
  const callerFields = selected?.parameters ?? []

  useEffect(() => {
    if (!open || !deviceCode) {
      return
    }
    setLoading(true)
    catalogApi
      .deviceFunctions(deviceCode)
      .then((items) => {
        const commandable = items.filter((item) => {
          const access = item.accessType?.toUpperCase()
          return access === "WRITE" || access === "READ"
        })
        setFunctions(items)
        const first = commandable[0]
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
    const options = selected.writeValueOptions ?? []
    const preferred =
      options.find((item) => item.isDefault)?.mappingValue
      ?? options.find((item) => item.isDefault)?.optionValue
      ?? options[0]?.mappingValue
      ?? options[0]?.optionValue
      ?? ""
    setSelectedOptionValue(preferred)
    const next = toFieldStringMap(selected.values ?? {})
    const valueMode = isValueModeOf(selected)
    const parameters = selected.parameters ?? []
    for (const field of parameters) {
      if (!next[field.name]) {
        const fromChoices = field.choices?.[0]
        const fromOptions =
          valueMode && (parameters.length === 1 || field.name === "value")
            ? (options[0]?.mappingValue || options[0]?.optionValue)
            : undefined
        const choice = fromChoices ?? fromOptions
        if (choice != null) {
          next[field.name] = String(choice)
        }
      }
    }
    setValues(next)
  }, [selected])

  async function submit() {
    if (!functionId || !selected) {
      toast.error("请选择功能")
      return
    }
    setPending(true)
    try {
      const collected = collectActionArguments({
        selected,
        values,
        selectedOptionValue,
      })
      if (!collected.ok) {
        toast.error(collected.error)
        setPending(false)
        return
      }
      const result = await catalogApi.invokeCommand(deviceCode, functionId, collected.args)
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
    (isRead
      ? callerFields.every((field) => !field.required || Boolean(values[field.name]))
      : isValueMode && callerFields.length > 0
        ? callerFields.every((field) => !field.required || Boolean(values[field.name]))
        : writeOptions.length === 0 || !!selectedOptionValue)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>手动下发 · {deviceCode}</DialogTitle>
          <DialogDescription>
            可下发 WRITE 或主动 READ。调用方字段按需填写；seq、at、deviceId 等平台/设备字段由配置自动填充。
          </DialogDescription>
        </DialogHeader>
        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" />
            加载功能列表…
          </div>
        ) : commandFunctions.length === 0 ? (
          <p className="text-sm text-muted-foreground">该设备产品尚未配置可下发的 READ / WRITE 功能。</p>
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
                    {commandFunctions.map((item) => (
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
            <ActionArgumentFields
              selected={selected}
              values={values}
              onValuesChange={setValues}
              selectedOptionValue={selectedOptionValue}
              onSelectedOptionValue={setSelectedOptionValue}
              pending={pending}
              idPrefix="cmd"
            />
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
