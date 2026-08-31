import { PlusIcon, Trash2Icon } from "lucide-react"

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
import type { PropertyItem } from "@/lib/types"

type Props = {
  label: string
  description?: string
  value: PropertyItem[]
  onChange: (next: PropertyItem[]) => void
}

const DATA_TYPES = ["string", "int", "boolean", "select", "password"]

const emptyRow = (): PropertyItem => ({
  attribute: "",
  attributeValue: "",
  dataType: "string",
  description: "",
})

/** 无 schema 时的 PropertyItem 行编辑器。 */
export function PropertyItemListEditor({ label, description, value, onChange }: Props) {
  function updateRow(index: number, patch: Partial<PropertyItem>) {
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
          onClick={() => onChange([...value, emptyRow()])}
        >
          <PlusIcon data-icon="inline-start" />
          添加属性
        </Button>
      </div>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          暂无属性，可按需添加（attribute / value / type）
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {value.map((row, index) => (
            <div key={index} className="rounded-md border p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">属性 {index + 1}</span>
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
                  <FieldLabel htmlFor={`prop-attr-${index}`}>属性名 attribute *</FieldLabel>
                  <Input
                    id={`prop-attr-${index}`}
                    placeholder="例如 offset"
                    value={row.attribute}
                    onChange={(e) => updateRow(index, { attribute: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`prop-val-${index}`}>属性值 attributeValue</FieldLabel>
                  <Input
                    id={`prop-val-${index}`}
                    placeholder="例如 100"
                    value={row.attributeValue}
                    onChange={(e) => updateRow(index, { attributeValue: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel>数据类型 dataType</FieldLabel>
                  <Select
                    value={row.dataType || "string"}
                    onValueChange={(next) => updateRow(index, { dataType: next })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {DATA_TYPES.map((type) => (
                          <SelectItem key={type} value={type}>
                            {type}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`prop-desc-${index}`}>说明 description</FieldLabel>
                  <Input
                    id={`prop-desc-${index}`}
                    placeholder="例如 寄存器偏移"
                    value={row.description ?? ""}
                    onChange={(e) => updateRow(index, { description: e.target.value })}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </Field>
  )
}

export function filterValidPropertyItems(rows: PropertyItem[]): PropertyItem[] {
  return rows
    .filter((row) => row.attribute.trim())
    .map((row) => ({
      attribute: row.attribute.trim(),
      attributeValue: row.attributeValue ?? "",
      dataType: row.dataType || "string",
      description: row.description ?? "",
    }))
}
