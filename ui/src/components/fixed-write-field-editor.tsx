import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import type { SchemaField, ValueOption, WriteFieldOption } from "@/lib/types"
import { choicesToFixedOptions, mergeFixedOptions } from "@/components/fixed-value-option-picker"

type Props = {
  label: string
  description?: string
  value: WriteFieldOption[]
  onChange: (next: WriteFieldOption[]) => void
}

/**
 * FIXED：罗列写字段 — field 只读，可改字段说明与各 option 说明。
 */
export function FixedWriteFieldEditor({ label, description, value, onChange }: Props) {
  if (value.length === 0) {
    return (
      <Field>
        <FieldLabel>{label}</FieldLabel>
        <p className="text-sm text-muted-foreground">请先选择能力预置功能模板以加载写字段。</p>
      </Field>
    )
  }

  function updateField(fieldName: string, patch: Partial<WriteFieldOption>) {
    onChange(value.map((row) => (row.field === fieldName ? { ...row, ...patch } : row)))
  }

  function updateOptionDescription(
    fieldName: string,
    optionValue: string,
    nextDescription: string
  ) {
    onChange(
      value.map((row) => {
        if (row.field !== fieldName) {
          return row
        }
        return {
          ...row,
          options: (row.options ?? []).map((option) =>
            option.optionValue === optionValue
              ? { ...option, description: nextDescription }
              : option
          ),
        }
      })
    )
  }

  function updateOptionDefault(fieldName: string, optionValue: string | undefined) {
    onChange(
      value.map((row) => {
        if (row.field !== fieldName) {
          return row
        }
        return {
          ...row,
          options: (row.options ?? []).map((option) => ({
            ...option,
            isDefault: optionValue ? option.optionValue === optionValue : false,
          })),
        }
      })
    )
  }

  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      {description ? (
        <p className="mb-2 text-xs text-muted-foreground">{description}</p>
      ) : null}
      <div className="flex flex-col gap-3">
        {value.map((row) => {
          const options = row.options ?? []
          const defaultOption = options.find((item) => item.isDefault)?.optionValue
          return (
            <div key={row.field} className="space-y-3 rounded-md border p-3">
              <div className="font-mono text-xs text-muted-foreground">
                field={row.field}
                <span className="ml-1 text-muted-foreground/80">（不可改）</span>
              </div>
              <div className="space-y-1">
                <FieldLabel htmlFor={`fixed-wf-desc-${row.field}`}>字段说明 description</FieldLabel>
                <Input
                  id={`fixed-wf-desc-${row.field}`}
                  value={row.description ?? ""}
                  placeholder={row.field}
                  onChange={(e) => updateField(row.field, { description: e.target.value })}
                />
              </div>
              {options.length > 0 ? (
                <div className="space-y-2">
                  <p className="text-xs font-medium text-muted-foreground">可选值 options</p>
                  {options.map((option) => (
                    <div
                      key={option.optionValue}
                      className="flex flex-col gap-2 rounded-md border bg-muted/20 px-3 py-2 sm:flex-row sm:items-start sm:justify-between"
                    >
                      <div className="min-w-0 flex-1 space-y-2">
                        <div className="truncate font-mono text-xs text-muted-foreground">
                          optionValue={option.optionValue}
                          <span className="ml-1 text-muted-foreground/80">（不可改）</span>
                        </div>
                        <div className="space-y-1">
                          <FieldLabel htmlFor={`fixed-wf-opt-${row.field}-${option.optionValue}`}>
                            选项说明
                          </FieldLabel>
                          <Input
                            id={`fixed-wf-opt-${row.field}-${option.optionValue}`}
                            value={option.description ?? ""}
                            placeholder={option.optionValue}
                            onChange={(e) =>
                              updateOptionDescription(
                                row.field,
                                option.optionValue,
                                e.target.value
                              )
                            }
                          />
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2 pt-1">
                        <Switch
                          id={`fixed-wf-def-${row.field}-${option.optionValue}`}
                          checked={defaultOption === option.optionValue}
                          onCheckedChange={(next) =>
                            updateOptionDefault(
                              row.field,
                              next ? option.optionValue : undefined
                            )
                          }
                        />
                        <FieldLabel htmlFor={`fixed-wf-def-${row.field}-${option.optionValue}`}>
                          默认
                        </FieldLabel>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">该字段无枚举可选值。</p>
              )}
            </div>
          )
        })}
      </div>
    </Field>
  )
}

/** 模板 parameters → WriteFieldOption（含 choices）。 */
export function schemaToFixedWriteFields(fields: SchemaField[]): WriteFieldOption[] {
  return (fields ?? []).map((field) => ({
    field: field.name,
    description: field.description || field.label || "",
    accessDataType: field.type || "string",
    transformDataType: field.type || "string",
    ignoreRequest: false,
    options: choicesToFixedOptions([field]),
    format: field.format || "none",
    valueGenerator: field.valueGenerator,
  }))
}

/**
 * 以模板写字段全集为准，合并已保存说明；兼容旧 VALUE 模式扁平 writeValueOptions。
 */
export function mergeFixedWriteFields(
  templateFields: WriteFieldOption[],
  savedFields: WriteFieldOption[],
  legacyValueOptions: ValueOption[] = []
): WriteFieldOption[] {
  if (templateFields.length === 0) {
    return savedFields
  }
  const byField = new Map(savedFields.map((item) => [item.field, item]))
  const choiceFields = templateFields.filter((item) => (item.options ?? []).length > 0)

  return templateFields.map((base) => {
    const existing = byField.get(base.field)
    let optionSource = existing?.options ?? []
    if (
      optionSource.length === 0 &&
      legacyValueOptions.length > 0 &&
      choiceFields.length === 1 &&
      choiceFields[0].field === base.field
    ) {
      optionSource = legacyValueOptions
    }
    return {
      ...base,
      description: existing?.description || base.description,
      accessDataType: existing?.accessDataType || base.accessDataType,
      transformDataType: existing?.transformDataType || base.transformDataType,
      format: existing?.format || base.format || "none",
      valueGenerator: existing?.valueGenerator ?? base.valueGenerator,
      ignoreRequest: existing?.ignoreRequest ?? base.ignoreRequest,
      options: mergeFixedOptions(base.options ?? [], optionSource),
    }
  })
}
