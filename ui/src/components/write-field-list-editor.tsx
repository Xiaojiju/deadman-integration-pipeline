import { PlusIcon, Trash2Icon } from "lucide-react"

import {
  ValueOptionListEditor,
  filterValidValueOptions,
} from "@/components/value-option-list-editor"
import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import type { WriteFieldOption } from "@/lib/types"

type Props = {
  label: string
  description?: string
  value: WriteFieldOption[]
  onChange: (next: WriteFieldOption[]) => void
}

const emptyField = (): WriteFieldOption => ({
  field: "",
  description: "",
  accessDataType: "string",
  transformDataType: "string",
  ignoreRequest: false,
  options: [],
})

/** STRUCT 写字段列表：每字段可挂 ValueOption 子表。 */
export function WriteFieldListEditor({ label, description, value, onChange }: Props) {
  function updateField(index: number, patch: Partial<WriteFieldOption>) {
    onChange(value.map((row, i) => (i === index ? { ...row, ...patch } : row)))
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
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={() => onChange([...value, emptyField()])}
        >
          <PlusIcon data-icon="inline-start" />
          添加字段
        </Button>
      </div>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          STRUCT 模式下按字段维护写选项
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {value.map((row, index) => (
            <div key={index} className="rounded-md border p-3">
              <div className="mb-3 flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">写字段 {index + 1}</span>
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  onClick={() => onChange(value.filter((_, i) => i !== index))}
                >
                  <Trash2Icon className="size-4" />
                </Button>
              </div>
              <div className="mb-3 grid gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`wf-field-${index}`}>字段名 field *</FieldLabel>
                  <Input
                    id={`wf-field-${index}`}
                    placeholder="例如 command"
                    value={row.field}
                    onChange={(e) => updateField(index, { field: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`wf-desc-${index}`}>字段说明 description</FieldLabel>
                  <Input
                    id={`wf-desc-${index}`}
                    placeholder="例如 控门指令"
                    value={row.description ?? ""}
                    onChange={(e) => updateField(index, { description: e.target.value })}
                  />
                </div>
                <div className="flex items-center gap-2 sm:col-span-2">
                  <Switch
                    id={`wf-ignore-${index}`}
                    checked={Boolean(row.ignoreRequest)}
                    onCheckedChange={(checked) =>
                      updateField(index, { ignoreRequest: checked })
                    }
                  />
                  <FieldLabel htmlFor={`wf-ignore-${index}`}>
                    忽略请求体中该字段 ignoreRequest
                  </FieldLabel>
                </div>
              </div>
              <ValueOptionListEditor
                label="该字段的可选值 options"
                description="此写字段下可选的 optionValue / mappingValue"
                value={row.options ?? []}
                onChange={(options) => updateField(index, { options })}
              />
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
    .map((row) => ({
      field: row.field.trim(),
      description: row.description ?? "",
      accessDataType: row.accessDataType || "string",
      transformDataType: row.transformDataType || row.accessDataType || "string",
      ignoreRequest: Boolean(row.ignoreRequest),
      options: filterValidValueOptions(row.options ?? []),
    }))
}
