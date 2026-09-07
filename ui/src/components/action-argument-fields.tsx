import { SchemaFieldControl } from "@/components/schema-field-control"
import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { isArrayFieldType, isBooleanFieldType, isIntFieldType } from "@/lib/schema-form"
import type { FunctionFormView, ValueOption } from "@/lib/types"

type CollectInput = {
  selected: FunctionFormView
  values: Record<string, string>
  selectedOptionValue: string
}

export type CollectArgsResult =
  | { ok: true; args: Record<string, unknown> }
  | { ok: false; error: string }

function enumChoicesForField(
  field: { name: string; choices?: string[] },
  writeOptions: ValueOption[],
  callerFields: Array<{ name: string }>,
  isValueMode: boolean
): string[] {
  if ((field.choices ?? []).length > 0) {
    return field.choices ?? []
  }
  if (!isValueMode || writeOptions.length === 0) {
    return []
  }
  if (callerFields.length === 1 || field.name === "value") {
    return writeOptions
      .map((item) => (item.mappingValue || item.optionValue || "").trim())
      .filter(Boolean)
  }
  return []
}

function choiceButtonLabel(choice: string, writeOptions: ValueOption[]): string {
  const option = writeOptions.find(
    (item) => item.mappingValue === choice || item.optionValue === choice
  )
  const label = option?.description?.trim()
  return label || choice
}

export function parseFieldValue(field: { type?: string; name: string }, raw: string): unknown {
  if (raw === "") {
    return raw
  }
  if (isIntFieldType(field.type)) {
    return Number(raw)
  }
  if (isBooleanFieldType(field.type)) {
    return raw === "true" || raw === "1"
  }
  if (
    isArrayFieldType(field.type) ||
    field.type === "json" ||
    raw.trim().startsWith("[") ||
    raw.trim().startsWith("{")
  ) {
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  }
  return raw
}

export function isValueModeOf(selected?: FunctionFormView | null): boolean {
  if (!selected) {
    return false
  }
  const writeOptions = selected.writeValueOptions ?? []
  return (
    selected.payloadMode === "VALUE" ||
    (writeOptions.length > 0 && selected.writeAccessType === "VALUE")
  )
}

export function collectActionArguments(input: CollectInput): CollectArgsResult {
  const { selected, values, selectedOptionValue } = input
  const callerFields = selected.parameters ?? []
  const writeOptions = selected.writeValueOptions ?? []
  const isRead = selected.accessType?.toUpperCase() === "READ"
  const isValueMode = isValueModeOf(selected)
  const args: Record<string, unknown> = {}

  if (isValueMode && callerFields.length > 0) {
    for (const field of callerFields) {
      const raw = values[field.name] ?? ""
      if (raw === "" && !field.required) {
        continue
      }
      if (raw === "") {
        return { ok: false, error: `请填写 ${field.label || field.name}` }
      }
      args[field.name] = parseFieldValue(field, raw)
    }
  } else if (callerFields.length > 0) {
    for (const field of callerFields) {
      const raw = values[field.name] ?? ""
      if (raw === "" && !field.required) {
        continue
      }
      args[field.name] = parseFieldValue(field, raw)
    }
  } else if (!isRead && writeOptions.length > 0) {
    if (!selectedOptionValue) {
      return { ok: false, error: "请选择写选项" }
    }
    const selectedOpt = writeOptions.find(
      (item) =>
        item.optionValue === selectedOptionValue || item.mappingValue === selectedOptionValue
    )
    const mapped = selectedOpt?.mappingValue || selectedOptionValue
    if (isValueMode) {
      args.value = mapped
    } else {
      const target = callerFields.length === 1 ? callerFields[0].name : "command"
      args[target] = selectedOptionValue
    }
  } else {
    for (const field of callerFields) {
      const raw = values[field.name] ?? ""
      if (raw === "" && !field.required) {
        continue
      }
      args[field.name] = parseFieldValue(field, raw)
    }
  }
  return { ok: true, args }
}

type Props = {
  selected: FunctionFormView | null
  values: Record<string, string>
  onValuesChange: (next: Record<string, string>) => void
  selectedOptionValue: string
  onSelectedOptionValue: (next: string) => void
  pending?: boolean
  idPrefix?: string
}

export function ActionArgumentFields({
  selected,
  values,
  onValuesChange,
  selectedOptionValue,
  onSelectedOptionValue,
  pending = false,
  idPrefix = "arg",
}: Props) {
  if (!selected) {
    return null
  }
  const callerFields = selected.parameters ?? []
  const writeOptions = selected.writeValueOptions ?? []
  const isValueMode = isValueModeOf(selected)

  if (isValueMode && callerFields.length > 0) {
    return (
      <>
        {callerFields.map((field) => {
          const enumChoices = enumChoicesForField(field, writeOptions, callerFields, isValueMode)
          return (
            <Field key={field.name}>
              <FieldLabel htmlFor={`${idPrefix}-${field.name}`}>
                {field.label || field.name}
                {field.required ? " *" : ""}
              </FieldLabel>
              {enumChoices.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {enumChoices.map((choice) => (
                    <Button
                      key={choice}
                      type="button"
                      variant={values[field.name] === String(choice) ? "default" : "outline"}
                      disabled={pending}
                      onClick={() =>
                        onValuesChange({ ...values, [field.name]: String(choice) })
                      }
                    >
                      {choiceButtonLabel(choice, writeOptions)}
                    </Button>
                  ))}
                </div>
              ) : (
                <SchemaFieldControl
                  field={field}
                  value={values[field.name] ?? ""}
                  idPrefix={idPrefix}
                  onChange={(next) => onValuesChange({ ...values, [field.name]: next })}
                />
              )}
            </Field>
          )
        })}
      </>
    )
  }
  if (callerFields.length > 0) {
    return (
      <>
        {callerFields.map((field) => (
          <Field key={field.name}>
            <FieldLabel htmlFor={`${idPrefix}-${field.name}`}>
              {field.label || field.name}
              {field.required ? " *" : ""}
            </FieldLabel>
            <SchemaFieldControl
              field={field}
              value={values[field.name] ?? ""}
              idPrefix={idPrefix}
              onChange={(next) => onValuesChange({ ...values, [field.name]: next })}
            />
          </Field>
        ))}
      </>
    )
  }
  if (writeOptions.length > 0) {
    return (
      <Field>
        <FieldLabel>{isValueMode ? "业务值" : "写选项"}</FieldLabel>
        <div className="flex flex-wrap gap-2">
          {writeOptions.map((option) => {
            const key = option.mappingValue || option.optionValue
            return (
              <Button
                key={option.optionValue}
                type="button"
                variant={
                  selectedOptionValue === key || selectedOptionValue === option.optionValue
                    ? "default"
                    : "outline"
                }
                disabled={pending}
                onClick={() => onSelectedOptionValue(key)}
              >
                {option.description || option.optionValue}
              </Button>
            )
          })}
        </div>
      </Field>
    )
  }
  return null
}
