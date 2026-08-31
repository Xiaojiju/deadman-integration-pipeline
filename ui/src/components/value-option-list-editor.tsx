import { PlusIcon, Trash2Icon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import type { ValueOption } from "@/lib/types"

type Props = {
  label: string
  description?: string
  value: ValueOption[]
  onChange: (next: ValueOption[]) => void
}

const emptyRow = (): ValueOption => ({
  optionValue: "",
  mappingValue: "",
  description: "",
  accessDataType: "string",
  transformDataType: "string",
  isDefault: false,
})

/** 写/读 ValueOption 列表表单（增删行，非 JSON）。 */
export function ValueOptionListEditor({ label, description, value, onChange }: Props) {
  function updateRow(index: number, patch: Partial<ValueOption>) {
    onChange(value.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  function setDefault(index: number, checked: boolean) {
    onChange(
      value.map((row, i) => ({
        ...row,
        isDefault: checked ? i === index : false,
      }))
    )
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
          onClick={() => onChange([...value, emptyRow()])}
        >
          <PlusIcon data-icon="inline-start" />
          添加
        </Button>
      </div>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          暂无选项，点击「添加」维护枚举值（如 open / close）
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {value.map((row, index) => (
            <div key={index} className="rounded-md border p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">选项 {index + 1}</span>
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  onClick={() => onChange(value.filter((_, i) => i !== index))}
                >
                  <Trash2Icon className="size-4" />
                </Button>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`vo-option-${index}`}>设备侧取值 optionValue *</FieldLabel>
                  <Input
                    id={`vo-option-${index}`}
                    placeholder="例如 open"
                    value={row.optionValue}
                    onChange={(e) => updateRow(index, { optionValue: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`vo-mapping-${index}`}>业务映射值 mappingValue</FieldLabel>
                  <Input
                    id={`vo-mapping-${index}`}
                    placeholder="空则等同 optionValue"
                    value={row.mappingValue ?? ""}
                    onChange={(e) => updateRow(index, { mappingValue: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5 sm:col-span-2">
                  <FieldLabel htmlFor={`vo-desc-${index}`}>说明 description</FieldLabel>
                  <Input
                    id={`vo-desc-${index}`}
                    placeholder="例如 开门"
                    value={row.description ?? ""}
                    onChange={(e) => updateRow(index, { description: e.target.value })}
                  />
                </div>
                <div className="flex items-center gap-2 sm:col-span-2">
                  <Switch
                    id={`vo-default-${index}`}
                    checked={Boolean(row.isDefault)}
                    onCheckedChange={(checked) => setDefault(index, checked)}
                  />
                  <FieldLabel htmlFor={`vo-default-${index}`}>设为默认选项 isDefault</FieldLabel>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </Field>
  )
}

export function filterValidValueOptions(rows: ValueOption[]): ValueOption[] {
  return rows
    .filter((row) => row.optionValue.trim())
    .map((row) => ({
      optionValue: row.optionValue.trim(),
      mappingValue: (row.mappingValue || row.optionValue).trim(),
      description: row.description?.trim() || "",
      accessDataType: row.accessDataType || "string",
      transformDataType: row.transformDataType || row.accessDataType || "string",
      isDefault: Boolean(row.isDefault),
    }))
}
