import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import type { ValueOption } from "@/lib/types"

type Props = {
  label: string
  description?: string
  /** 展示中的选项：取值只读，可改 description / 默认项。 */
  options: ValueOption[]
  /** 当前选中的默认 optionValue；空表示无默认。 */
  defaultOptionValue?: string
  onDefaultChange: (optionValue: string | undefined) => void
  onOptionsChange: (options: ValueOption[]) => void
}

/**
 * FIXED 能力专用：展示全部可选值，optionValue 不可改，允许改描述与默认项。
 */
export function FixedValueOptionPicker({
  label,
  description,
  options,
  defaultOptionValue,
  onDefaultChange,
  onOptionsChange,
}: Props) {
  if (options.length === 0) {
    return (
      <Field>
        <FieldLabel>{label}</FieldLabel>
        <p className="text-sm text-muted-foreground">请先选择能力预置功能模板以加载写选项。</p>
      </Field>
    )
  }

  function updateDescription(optionValue: string, nextDescription: string) {
    onOptionsChange(
      options.map((option) =>
        option.optionValue === optionValue
          ? { ...option, description: nextDescription }
          : option
      )
    )
  }

  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      {description ? (
        <p className="mb-2 text-xs text-muted-foreground">{description}</p>
      ) : null}
      <div className="flex flex-col gap-2">
        {options.map((option) => {
          const checked = defaultOptionValue === option.optionValue
          return (
            <div
              key={option.optionValue}
              className="flex flex-col gap-2 rounded-md border px-3 py-2 sm:flex-row sm:items-start sm:justify-between"
            >
              <div className="min-w-0 flex-1 space-y-2">
                <div className="truncate font-mono text-xs text-muted-foreground">
                  optionValue={option.optionValue}
                  {option.mappingValue && option.mappingValue !== option.optionValue
                    ? ` · mapping=${option.mappingValue}`
                    : ""}
                  <span className="ml-1 text-muted-foreground/80">（不可改）</span>
                </div>
                <div className="space-y-1">
                  <FieldLabel htmlFor={`fixed-desc-${option.optionValue}`}>
                    描述 description
                  </FieldLabel>
                  <Input
                    id={`fixed-desc-${option.optionValue}`}
                    value={option.description ?? ""}
                    placeholder={option.optionValue}
                    onChange={(e) => updateDescription(option.optionValue, e.target.value)}
                  />
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2 pt-1">
                <Switch
                  id={`fixed-default-${option.optionValue}`}
                  checked={checked}
                  onCheckedChange={(next) =>
                    onDefaultChange(next ? option.optionValue : undefined)
                  }
                />
                <FieldLabel htmlFor={`fixed-default-${option.optionValue}`}>
                  默认
                </FieldLabel>
              </div>
            </div>
          )
        })}
      </div>
    </Field>
  )
}

/** 模板 choices → ValueOption（取值固定）。 */
export function choicesToFixedOptions(
  fields: Array<{ name: string; type?: string; choices?: string[]; defaultValue?: unknown }>
): ValueOption[] {
  const options: ValueOption[] = []
  for (const field of fields) {
    for (const choice of field.choices ?? []) {
      options.push({
        optionValue: choice,
        mappingValue: choice,
        description: choice,
        accessDataType: field.type || "string",
        transformDataType: field.type || "string",
        isDefault: field.defaultValue != null && String(field.defaultValue) === choice,
      })
    }
  }
  return options
}

/**
 * 以模板规定的 optionValue 全集为准，合并已保存/编辑中的 description、isDefault 等。
 */
export function mergeFixedOptions(
  templateOptions: ValueOption[],
  savedOrEdited: ValueOption[]
): ValueOption[] {
  if (templateOptions.length === 0) {
    return savedOrEdited
  }
  const byValue = new Map(savedOrEdited.map((item) => [item.optionValue, item]))
  return templateOptions.map((base) => {
    const existing = byValue.get(base.optionValue)
    if (!existing) {
      return base
    }
    return {
      ...base,
      description: existing.description ?? base.description,
      mappingValue: existing.mappingValue || base.mappingValue,
      isDefault: existing.isDefault ?? base.isDefault,
      accessDataType: existing.accessDataType || base.accessDataType,
      transformDataType: existing.transformDataType || base.transformDataType,
    }
  })
}

export function withDefaultFlag(
  options: ValueOption[],
  defaultOptionValue?: string
): ValueOption[] {
  return options.map((option) => ({
    ...option,
    isDefault: defaultOptionValue
      ? option.optionValue === defaultOptionValue
      : Boolean(option.isDefault),
  }))
}
