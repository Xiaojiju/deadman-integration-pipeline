import { PlusIcon, Trash2Icon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { emptyValueMapping, filterValidMappings, type ValueMappingModel } from "@/lib/payload-form"

type Props = {
  label: string
  description?: string
  value: ValueMappingModel[]
  onChange: (next: ValueMappingModel[]) => void
  readOnly?: boolean
}

/** VALUE 模式：业务简值 → 字段 patch。 */
export function ValueMappingEditor({ label, description, value, onChange, readOnly = false }: Props) {
  function updateRow(index: number, patch: Partial<ValueMappingModel>) {
    if (readOnly) return
    onChange(value.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  function updatePatch(rowIndex: number, patchIndex: number, path: string, val: string) {
    if (readOnly) return
    const rows = [...value]
    const patches = [...(rows[rowIndex].patches ?? [{ path: "command", value: "" }])]
    patches[patchIndex] = { path, value: val }
    rows[rowIndex] = { ...rows[rowIndex], patches }
    onChange(rows)
  }

  return (
    <Field>
      <div className="mb-2 flex items-center justify-between gap-2">
        <div>
          <FieldLabel>{label}</FieldLabel>
          {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {!readOnly ? (
          <Button type="button" size="sm" variant="outline" onClick={() => onChange([...value, emptyValueMapping()])}>
            <PlusIcon data-icon="inline-start" />
            添加映射
          </Button>
        ) : null}
      </div>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          调用方只传 value（如 open）；在此配置映射到协议字段。
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {value.map((row, index) => (
            <div key={index} className="rounded-md border p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">映射 {index + 1}</span>
                {!readOnly ? (
                  <Button type="button" size="icon" variant="ghost" onClick={() => onChange(value.filter((_, i) => i !== index))}>
                    <Trash2Icon className="size-4" />
                  </Button>
                ) : null}
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <FieldLabel className="text-xs">业务值 mappingValue *</FieldLabel>
                  <Input
                    placeholder="如 open"
                    value={row.mappingValue}
                    disabled={readOnly}
                    onChange={(e) => updateRow(index, { mappingValue: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel className="text-xs">说明</FieldLabel>
                  <Input
                    placeholder="如 开门"
                    value={row.description ?? ""}
                    disabled={readOnly}
                    onChange={(e) => updateRow(index, { description: e.target.value })}
                  />
                </div>
              </div>
              {(row.patches ?? [{ path: "command", value: "" }]).map((patch, pi) => (
                <div key={pi} className="mt-3 grid gap-2 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <FieldLabel className="text-xs">patch path</FieldLabel>
                    <Input
                      placeholder="command"
                      value={patch.path}
                      disabled={readOnly}
                      onChange={(e) => updatePatch(index, pi, e.target.value, String(patch.value ?? ""))}
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <FieldLabel className="text-xs">patch value</FieldLabel>
                    <Input
                      placeholder="协议字段值"
                      value={patch.value == null ? "" : String(patch.value)}
                      disabled={readOnly}
                      onChange={(e) => updatePatch(index, pi, patch.path, e.target.value)}
                    />
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </Field>
  )
}

export { filterValidMappings }
