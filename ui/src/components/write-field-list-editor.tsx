import { PlusIcon, Trash2Icon } from "lucide-react"

import {
  ValueOptionListEditor,
  filterValidValueOptions,
} from "@/components/value-option-list-editor"
import { Button } from "@/components/ui/button"
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
import type { WriteFieldOption } from "@/lib/types"

type Props = {
  label: string
  description?: string
  value: WriteFieldOption[]
  onChange: (next: WriteFieldOption[]) => void
  /** 只读：不可增删改字段结构 */
  readOnly?: boolean
}

const FIELD_TYPES = ["string", "int", "boolean", "select", "password", "json", "array"] as const
const FIELD_FORMATS = ["none", "datetime_iso8601", "image_base64", "text_list"] as const
const VALUE_GENERATORS = [
  { value: "__caller__", label: "调用方提供" },
  { value: "random_alnum_32", label: "32 位随机字符（seq）" },
  { value: "timestamp_millis", label: "当前毫秒时间戳（at）" },
  { value: "uuid", label: "UUID" },
] as const

const emptyField = (): WriteFieldOption => ({
  field: "",
  description: "",
  accessDataType: "string",
  transformDataType: "string",
  ignoreRequest: false,
  options: [],
  format: "none",
  valueGenerator: undefined,
})

/** STRUCT 字段列表：每字段可配 FieldType / FieldFormat / 平台生成器。 */
export function WriteFieldListEditor({
  label,
  description,
  value,
  onChange,
  readOnly = false,
}: Props) {
  function updateField(index: number, patch: Partial<WriteFieldOption>) {
    if (readOnly) {
      return
    }
    onChange(value.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  function setFieldType(index: number, type: string) {
    updateField(index, {
      accessDataType: type,
      transformDataType: type,
    })
  }

  function setValueGenerator(index: number, generator: string) {
    const code = generator === "__caller__" ? undefined : generator
    updateField(index, {
      valueGenerator: code,
      ignoreRequest: Boolean(code),
    })
  }

  return (
    <Field>
      <div className="mb-2 flex items-center justify-between gap-2">
        <div>
          <FieldLabel>{label}</FieldLabel>
          {description ? (
            <p className="text-xs text-muted-foreground">{description}</p>
          ) : null}
        </div>
        {!readOnly ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => onChange([...value, emptyField()])}
          >
            <PlusIcon data-icon="inline-start" />
            添加字段
          </Button>
        ) : null}
      </div>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          {readOnly ? "无字段" : "添加字段并配置 type / format / 平台生成器"}
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {value.map((row, index) => (
            <div key={index} className="rounded-md border p-3">
              <div className="mb-3 flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">字段 {index + 1}</span>
                {!readOnly ? (
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    onClick={() => onChange(value.filter((_, i) => i !== index))}
                  >
                    <Trash2Icon className="size-4" />
                  </Button>
                ) : null}
              </div>
              <div className="mb-3 grid gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`wf-field-${index}`}>字段名 field *</FieldLabel>
                  <Input
                    id={`wf-field-${index}`}
                    placeholder="例如 command / seq / at"
                    value={row.field}
                    disabled={readOnly}
                    onChange={(e) => updateField(index, { field: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`wf-desc-${index}`}>字段说明 description</FieldLabel>
                  <Input
                    id={`wf-desc-${index}`}
                    placeholder="例如 控门指令"
                    value={row.description ?? ""}
                    disabled={readOnly}
                    onChange={(e) => updateField(index, { description: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel>字段类型 FieldType</FieldLabel>
                  <Select
                    value={row.accessDataType || "string"}
                    disabled={readOnly}
                    onValueChange={(next) => setFieldType(index, next)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {FIELD_TYPES.map((type) => (
                          <SelectItem key={type} value={type}>
                            {type}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel>采值约束 format</FieldLabel>
                  <Select
                    value={row.format || "none"}
                    disabled={readOnly || Boolean(row.valueGenerator)}
                    onValueChange={(next) => updateField(index, { format: next })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {FIELD_FORMATS.map((format) => (
                          <SelectItem key={format} value={format}>
                            {format}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex flex-col gap-1.5 sm:col-span-2">
                  <FieldLabel>值来源 valueGenerator</FieldLabel>
                  <Select
                    value={row.valueGenerator ?? "__caller__"}
                    disabled={readOnly}
                    onValueChange={(next) => setValueGenerator(index, next)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="调用方提供" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {VALUE_GENERATORS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  {row.valueGenerator ? (
                    <p className="text-xs text-muted-foreground">
                      平台自动生成，调用方无需传递；下发表单中隐藏。
                    </p>
                  ) : null}
                </div>
              </div>
              {!row.valueGenerator && (!readOnly || (row.options ?? []).length > 0) ? (
                <ValueOptionListEditor
                  label="该字段的可选值 options"
                  description="select 类型或枚举指令时使用"
                  value={row.options ?? []}
                  onChange={(options) => updateField(index, { options })}
                />
              ) : null}
            </div>
          ))}
        </div>
      )}
    </Field>
  )
}

export function filterValidWriteFields(rows: WriteFieldOption[]): WriteFieldOption[] {
  return rows
    .filter((row) => row.field.trim())
    .map((row) => {
      const accessDataType = row.accessDataType || "string"
      const platform = Boolean(row.valueGenerator)
      return {
        field: row.field.trim(),
        description: row.description ?? "",
        accessDataType,
        transformDataType: row.transformDataType || accessDataType,
        ignoreRequest: platform || Boolean(row.ignoreRequest),
        options: platform ? [] : filterValidValueOptions(row.options ?? []),
        format: row.format || "none",
        valueGenerator: row.valueGenerator || undefined,
      }
    })
}
