import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import type { PropertyItem, SchemaField } from "@/lib/types"

type Props = {
  label: string
  description?: string
  value: PropertyItem[]
  onChange: (next: PropertyItem[]) => void
}

/**
 * FIXED：功能默认参数 properties — attribute/value 只读，仅可改 description。
 */
export function FixedPropertyDescriptionEditor({
  label,
  description,
  value,
  onChange,
}: Props) {
  if (value.length === 0) {
    return (
      <Field>
        <FieldLabel>{label}</FieldLabel>
        <p className="text-sm text-muted-foreground">无默认参数（由模板种子）。</p>
      </Field>
    )
  }

  function updateDescription(attribute: string, nextDescription: string) {
    onChange(
      value.map((row) =>
        row.attribute === attribute ? { ...row, description: nextDescription } : row
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
        {value.map((row) => (
          <div key={row.attribute} className="space-y-2 rounded-md border px-3 py-2">
            <div className="font-mono text-xs text-muted-foreground">
              {row.attribute}
              <span className="mx-1">=</span>
              <span className="text-foreground">{row.attributeValue || "（空）"}</span>
              <span className="ml-1 text-muted-foreground/80">· {row.dataType}（不可改）</span>
            </div>
            <div className="space-y-1">
              <FieldLabel htmlFor={`fixed-prop-desc-${row.attribute}`}>
                说明 description
              </FieldLabel>
              <Input
                id={`fixed-prop-desc-${row.attribute}`}
                value={row.description ?? ""}
                placeholder={row.attribute}
                onChange={(e) => updateDescription(row.attribute, e.target.value)}
              />
            </div>
          </div>
        ))}
      </div>
    </Field>
  )
}

/** 模板 parameters → PropertyItem（默认值）。 */
export function schemaToFixedProperties(fields: SchemaField[]): PropertyItem[] {
  return (fields ?? []).map((field) => ({
    attribute: field.name,
    attributeValue: field.defaultValue != null ? String(field.defaultValue) : "",
    dataType: field.type || "string",
    description: field.description || field.label || "",
  }))
}

/** 以模板字段全集为准，合并已保存的 description / attributeValue。 */
export function mergeFixedProperties(
  templateProps: PropertyItem[],
  savedOrEdited: PropertyItem[]
): PropertyItem[] {
  if (templateProps.length === 0) {
    return savedOrEdited
  }
  const byAttr = new Map(savedOrEdited.map((item) => [item.attribute, item]))
  return templateProps.map((base) => {
    const existing = byAttr.get(base.attribute)
    if (!existing) {
      return base
    }
    return {
      ...base,
      attributeValue: existing.attributeValue || base.attributeValue,
      description: existing.description || base.description,
      dataType: existing.dataType || base.dataType,
    }
  })
}
