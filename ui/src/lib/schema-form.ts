import type { PropertyItem, SchemaField } from "@/lib/types"

/** 兼容 JSON 文本或已解析对象。 */
export function asRecord(value?: string | Record<string, unknown> | null): Record<string, unknown> {
  if (value == null) {
    return {}
  }
  if (typeof value === "object" && !Array.isArray(value)) {
    return value
  }
  if (typeof value === "string") {
    return parseJsonRecord(value)
  }
  return {}
}

/** PropertyItem[] → 表单用 Record。 */
export function propertiesToRecord(items?: PropertyItem[] | null): Record<string, unknown> {
  if (!items?.length) {
    return {}
  }
  const result: Record<string, unknown> = {}
  for (const item of items) {
    result[item.attribute] = item.attributeValue
  }
  return result
}

/** Record + schema → PropertyItem[]。 */
export function recordToProperties(
  values: Record<string, unknown>,
  schema?: SchemaField[]
): PropertyItem[] {
  const typeByName = new Map((schema ?? []).map((field) => [field.name, field]))
  return Object.entries(values).map(([attribute, value]) => {
    const field = typeByName.get(attribute)
    return {
      attribute,
      attributeValue: value == null ? "" : String(value),
      dataType: field?.type ?? inferDataType(value),
      description: field?.description ?? "",
    }
  })
}

function inferDataType(value: unknown): string {
  if (typeof value === "number") {
    return "int"
  }
  if (typeof value === "boolean") {
    return "boolean"
  }
  return "string"
}

/** 将 JSON 文本解析为对象；失败或空则返回 {}。 */
export function parseJsonRecord(raw?: string | null): Record<string, unknown> {
  if (!raw || !raw.trim()) {
    return {}
  }
  try {
    const parsed = JSON.parse(raw) as unknown
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
  } catch {
    // ignore
  }
  return {}
}

/** 表单字符串值 → 按 schema 类型转换。 */
export function buildValuesFromFields(
  schema: SchemaField[],
  values: Record<string, string>,
  options?: { skipEmptyOptional?: boolean }
): Record<string, unknown> {
  const skipEmptyOptional = options?.skipEmptyOptional ?? true
  const body: Record<string, unknown> = {}
  for (const field of schema) {
    const raw = values[field.name] ?? ""
    if (!raw && skipEmptyOptional && !field.required) {
      continue
    }
    if (field.type === "int" || field.type === "integer") {
      body[field.name] = raw === "" ? undefined : Number(raw)
      continue
    }
    if (field.type === "boolean") {
      body[field.name] = raw === "true" || raw === "1"
      continue
    }
    body[field.name] = raw
  }
  return body
}

/** 对象值 → 输入框字符串。 */
export function toFieldStringMap(values: Record<string, unknown>): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(values)) {
    if (value == null) {
      result[key] = ""
    } else if (typeof value === "object") {
      result[key] = JSON.stringify(value)
    } else {
      result[key] = String(value)
    }
  }
  return result
}

/** 用 schema 默认值初始化表单。 */
export function defaultsFromSchema(schema: SchemaField[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const field of schema) {
    if (field.defaultValue != null) {
      result[field.name] = String(field.defaultValue)
    }
  }
  return result
}
