import { PlusIcon, Trash2Icon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"

export type KeyValueRow = {
  key: string
  value: string
}

type Props = {
  label: string
  description?: string
  value: KeyValueRow[]
  onChange: (next: KeyValueRow[]) => void
  keyLabel?: string
  valueLabel?: string
  keyPlaceholder?: string
  valuePlaceholder?: string
}

/** 键值对列表表单。 */
export function KeyValueListEditor({
  label,
  description,
  value,
  onChange,
  keyLabel = "字段名",
  valueLabel = "字段值",
  keyPlaceholder = "例如 holdingOffset",
  valuePlaceholder = "例如 100",
}: Props) {
  function updateRow(index: number, patch: Partial<KeyValueRow>) {
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
          onClick={() => onChange([...value, { key: "", value: "" }])}
        >
          <PlusIcon data-icon="inline-start" />
          添加
        </Button>
      </div>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          暂无条目
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {value.map((row, index) => (
            <div key={index} className="rounded-md border p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">映射 {index + 1}</span>
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
                  <FieldLabel htmlFor={`kv-key-${index}`}>{keyLabel}</FieldLabel>
                  <Input
                    id={`kv-key-${index}`}
                    placeholder={keyPlaceholder}
                    value={row.key}
                    onChange={(e) => updateRow(index, { key: e.target.value })}
                    className="font-mono text-xs"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel htmlFor={`kv-value-${index}`}>{valueLabel}</FieldLabel>
                  <Input
                    id={`kv-value-${index}`}
                    placeholder={valuePlaceholder}
                    value={row.value}
                    onChange={(e) => updateRow(index, { value: e.target.value })}
                    className="font-mono text-xs"
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

export function recordToKeyValueRows(record?: Record<string, unknown> | null): KeyValueRow[] {
  if (!record) {
    return []
  }
  return Object.entries(record).map(([key, value]) => ({
    key,
    value: value == null ? "" : String(value),
  }))
}

export function keyValueRowsToRecord(rows: KeyValueRow[]): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const row of rows) {
    const key = row.key.trim()
    if (!key) {
      continue
    }
    const raw = row.value.trim()
    if (raw !== "" && !Number.isNaN(Number(raw)) && /^-?\d+(\.\d+)?$/.test(raw)) {
      result[key] = Number(raw)
    } else if (raw === "true" || raw === "false") {
      result[key] = raw === "true"
    } else {
      result[key] = raw
    }
  }
  return result
}
