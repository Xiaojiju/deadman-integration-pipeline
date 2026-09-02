import { PlusIcon, Trash2Icon } from "lucide-react"

import { ConfigExample, CodeSample } from "@/components/config-example"
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
  defaultPatchPath?: string
}

/** VALUE 模式：业务简值 → 字段 patch。 */
export function ValueMappingEditor({
  label,
  description,
  value,
  onChange,
  readOnly = false,
  defaultPatchPath = "",
}: Props) {
  function updateRow(index: number, patch: Partial<ValueMappingModel>) {
    if (readOnly) return
    onChange(value.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  function updatePatch(rowIndex: number, patchIndex: number, path: string, val: string) {
    if (readOnly) return
    const rows = [...value]
    const patches = [...(rows[rowIndex].patches ?? [{ path: "", value: "" }])]
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
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => onChange([...value, emptyValueMapping(defaultPatchPath)])}
          >
            <PlusIcon data-icon="inline-start" />
            添加映射
          </Button>
        ) : null}
      </div>
      <ConfigExample title="示例 · lock 开门">
        <p>
          调用方字段 <CodeSample>lock</CodeSample>，业务值 <CodeSample>open</CodeSample>，patch path{" "}
          <CodeSample>params.0</CodeSample>，patch value <CodeSample>open</CodeSample>。
        </p>
        <p>
          调用 <CodeSample>{`{ "lock": "open" }`}</CodeSample> 后，协议 JSON 的 params 第 0 项变成 open。
        </p>
        <p>再加一条 callerField=mode 即可让调用方同时传第二个参数。</p>
      </ConfigExample>
      {value.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          点击「添加映射」。调用方字段是请求 JSON 的 key，patch path 必须和上面协议字段叶子 path 一致。
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
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="flex flex-col gap-1.5">
                  <FieldLabel className="text-xs">调用方字段 *（请求 JSON 的 key）</FieldLabel>
                  <Input
                    placeholder="value / lock / mode"
                    value={row.callerField ?? "value"}
                    disabled={readOnly}
                    onChange={(e) => updateRow(index, { callerField: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <FieldLabel className="text-xs">业务值 mappingValue *</FieldLabel>
                  <Input
                    placeholder="如 1 / open"
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
              {(row.patches ?? [{ path: "", value: "" }]).map((patch, pi) => (
                <div key={pi} className="mt-3 grid gap-2 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <FieldLabel className="text-xs">patch path（协议叶子，如 params.0）</FieldLabel>
                    <Input
                      placeholder={defaultPatchPath || "params.0"}
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
