import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import type { SchemaField, WriteFieldOption } from "@/lib/types"

type FieldSourceWire = "caller" | "device" | "constant" | "mapped"

type Props = {
  label: string
  description?: string
  schema: SchemaField[]
  value: WriteFieldOption[]
  onChange: (next: WriteFieldOption[]) => void
}

const ADDRESS_SOURCES: { value: FieldSourceWire; label: string }[] = [
  { value: "constant", label: "常量 CONSTANT" },
  { value: "device", label: "设备 DEVICE" },
  { value: "caller", label: "调用方 CALLER" },
]

const VALUE_SOURCES: { value: FieldSourceWire; label: string }[] = [
  { value: "caller", label: "调用方 CALLER" },
  { value: "mapped", label: "映射 MAPPED" },
  { value: "constant", label: "常量 CONSTANT" },
  { value: "device", label: "设备 DEVICE" },
]

/** CONTRACT：字段名锁死，只配来源与取值。 */
export function ContractFieldEditor({ label, description, schema, value, onChange }: Props) {
  const rows = mergeContractFields(schemaToContractFields(schema), value)

  function updateField(fieldName: string, patch: Partial<WriteFieldOption>) {
    onChange(rows.map((row) => (row.field === fieldName ? { ...row, ...patch } : row)))
  }

  if (schema.length === 0) {
    return (
      <Field>
        <FieldLabel>{label}</FieldLabel>
        <p className="text-sm text-muted-foreground">当前访问类型没有参数契约。</p>
      </Field>
    )
  }

  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      {description ? <p className="mb-2 text-xs text-muted-foreground">{description}</p> : null}
      <div className="flex flex-col gap-3">
        {rows.map((row) => {
          const spec = schema.find((item) => item.name === row.field)
          const choices = spec?.choices ?? row.options?.map((item) => item.optionValue) ?? []
          const isValue = row.field === "value"
          const sources = isValue ? VALUE_SOURCES : ADDRESS_SOURCES
          const source = (row.source as FieldSourceWire) || (isValue ? "caller" : "constant")
          return (
            <div key={row.field} className="grid gap-3 rounded-md border p-3 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <p className="font-mono text-xs text-muted-foreground">
                  {row.field}
                  <span className="ml-1 text-muted-foreground/80">（契约字段，不可改名）</span>
                </p>
                <p className="text-xs text-muted-foreground">{row.description || spec?.description}</p>
              </div>
              <div className="flex flex-col gap-1.5">
                <FieldLabel>来源</FieldLabel>
                <Select
                  value={source}
                  onValueChange={(next) =>
                    updateField(row.field, {
                      source: next,
                      ignoreRequest: next !== "caller",
                      callerField: next === "mapped" ? row.callerField || "value" : undefined,
                      constant: next === "constant" ? row.constant ?? defaultConstant(spec) : undefined,
                    })
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {sources.map((item) => (
                        <SelectItem key={item.value} value={item.value}>
                          {item.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </div>
              {source === "constant" ? (
                <div className="flex flex-col gap-1.5">
                  <FieldLabel>常量值</FieldLabel>
                  {choices.length > 0 ? (
                    <Select
                      value={row.constant || String(spec?.defaultValue ?? choices[0] ?? "")}
                      onValueChange={(next) => updateField(row.field, { constant: next })}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {choices.map((choice) => (
                            <SelectItem key={choice} value={choice}>
                              {choice}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  ) : (
                    <Input
                      value={row.constant ?? ""}
                      placeholder={String(spec?.defaultValue ?? "")}
                      onChange={(e) => updateField(row.field, { constant: e.target.value })}
                    />
                  )}
                </div>
              ) : source === "mapped" ? (
                <p className="self-center text-xs text-muted-foreground">
                  调用方字段在下方 VALUE 映射填写，不必在契约行重复。
                </p>
              ) : (
                <p className="self-center text-xs text-muted-foreground">
                  {source === "device" ? "由本设备参数覆盖" : "由调用方传入"}
                </p>
              )}
            </div>
          )
        })}
      </div>
    </Field>
  )
}

export function schemaToContractFields(schema: SchemaField[]): WriteFieldOption[] {
  return (schema ?? []).map((field) => {
    const isValue = field.name === "value"
    const source: FieldSourceWire = isValue ? "caller" : "constant"
    const choices = field.choices ?? []
    return {
      field: field.name,
      description: field.description || field.label || "",
      accessDataType: field.type || "string",
      transformDataType: field.type || "string",
      ignoreRequest: source !== "caller",
      options: choices.map((choice) => ({
        optionValue: choice,
        mappingValue: choice,
        description: choice,
        isDefault: String(field.defaultValue ?? "") === choice,
      })),
      format: field.format || "none",
      source,
      constant: isValue ? undefined : defaultConstant(field),
      callerField: undefined,
    }
  })
}

export function mergeContractFields(
  templateFields: WriteFieldOption[],
  savedFields: WriteFieldOption[]
): WriteFieldOption[] {
  if (templateFields.length === 0) {
    return savedFields
  }
  const byField = new Map(savedFields.map((item) => [item.field, item]))
  return templateFields.map((base) => {
    const existing = byField.get(base.field)
    if (!existing) {
      return base
    }
    return {
      ...base,
      description: existing.description || base.description,
      source: existing.source || base.source,
      constant: existing.constant ?? base.constant,
      callerField: existing.callerField ?? base.callerField,
      ignoreRequest: existing.ignoreRequest ?? base.ignoreRequest,
      options: base.options,
    }
  })
}

function defaultConstant(field: SchemaField | undefined): string | undefined {
  if (field?.defaultValue == null || field.defaultValue === "") {
    return undefined
  }
  return String(field.defaultValue)
}
