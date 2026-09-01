import type { ValueOption, WriteFieldOption } from "@/lib/types"

export type FieldSourceWire = "caller" | "platform" | "device" | "constant" | "mapped"

export type FieldNodeModel = {
  name: string
  type: string
  format?: string
  source?: FieldSourceWire | string
  valueGenerator?: string | null
  constant?: unknown
  children?: FieldNodeModel[]
  element?: FieldNodeModel | null
  choices?: string[]
  description?: string
}

export type FieldPatchModel = {
  path: string
  value: unknown
}

export type ValueMappingModel = {
  mappingValue: string
  description?: string
  target?: "PATCH_FIELDS" | "FILL_ROOT" | string
  rootValue?: unknown
  patches?: FieldPatchModel[]
}

export function emptyObjectRoot(): FieldNodeModel {
  return { name: "root", type: "object", format: "none", source: "caller", children: [] }
}

export function writeFieldsToFieldNode(fields: WriteFieldOption[]): FieldNodeModel {
  const children = (fields ?? [])
    .filter((row) => row.field?.trim())
    .map((row) => {
      const choices = (row.options ?? []).map((o) => o.optionValue).filter(Boolean)
      const type = choices.length > 0 ? "select" : row.accessDataType || "string"
      const source: FieldSourceWire = row.valueGenerator ? "platform" : "caller"
      return {
        name: row.field.trim(),
        type,
        format: row.format || "none",
        source,
        valueGenerator: row.valueGenerator ?? null,
        constant: null,
        children: [],
        choices,
        description: row.description ?? "",
      }
    })
  return { ...emptyObjectRoot(), children }
}

export function fieldNodeToWriteFields(root: FieldNodeModel | null | undefined): WriteFieldOption[] {
  const leaves = collectLeaves(root, "")
  return leaves.map(({ path, node }) => {
    const choices = node.choices ?? []
    const options =
      choices.length > 0
        ? choices.map((value) => ({
            optionValue: value,
            mappingValue: value,
            description: value,
          }))
        : []
    const platform = node.source === "platform" || Boolean(node.valueGenerator)
    return {
      field: path,
      description: node.description ?? "",
      accessDataType: node.type === "select" ? "string" : node.type || "string",
      transformDataType: node.type === "select" ? "string" : node.type || "string",
      ignoreRequest: platform,
      options,
      format: node.format || "none",
      valueGenerator: node.valueGenerator || undefined,
    }
  })
}

function collectLeaves(
  node: FieldNodeModel | null | undefined,
  prefix: string
): Array<{ path: string; node: FieldNodeModel }> {
  if (!node) {
    return []
  }
  const isContainer =
    node.type === "object" ||
    node.type === "json" ||
    (node.children && node.children.length > 0)
  if (isContainer && node.children && node.children.length > 0) {
    return node.children.flatMap((child) => {
      const nextPrefix = prefix ? `${prefix}.${child.name}` : child.name
      if (
        child.type === "object" ||
        child.type === "json" ||
        (child.children && child.children.length > 0)
      ) {
        return collectLeaves(child, prefix ? `${prefix}.${child.name}` : child.name)
      }
      return [{ path: nextPrefix, node: child }]
    })
  }
  if (node.name === "root") {
    return []
  }
  return [{ path: prefix || node.name, node }]
}

export function valueOptionsToMappings(
  options: ValueOption[],
  fields: WriteFieldOption[]
): ValueMappingModel[] {
  const callerField =
    fields.find((f) => !f.valueGenerator && f.field)?.field ??
    fields[0]?.field ??
    "command"
  return (options ?? [])
    .filter((o) => o.optionValue?.trim())
    .map((o) => ({
      mappingValue: o.mappingValue?.trim() || o.optionValue.trim(),
      description: o.description ?? "",
      target: "PATCH_FIELDS" as const,
      patches: [{ path: callerField, value: o.optionValue.trim() }],
    }))
}

export function mappingsToValueOptions(mappings: ValueMappingModel[]): ValueOption[] {
  return (mappings ?? [])
    .filter((m) => m.mappingValue?.trim())
    .map((m) => {
      const patch = m.patches?.[0]
      return {
        optionValue: patch ? String(patch.value ?? m.mappingValue) : m.mappingValue.trim(),
        mappingValue: m.mappingValue.trim(),
        description: m.description ?? m.mappingValue,
      }
    })
}

export function normalizeFieldNode(raw: FieldNodeModel | null | undefined): FieldNodeModel {
  if (!raw || !raw.name) {
    return emptyObjectRoot()
  }
  return {
    name: raw.name,
    type: raw.type || "object",
    format: raw.format || "none",
    source: (raw.source as FieldSourceWire) || "caller",
    valueGenerator: raw.valueGenerator ?? null,
    constant: raw.constant ?? null,
    children: (raw.children ?? []).map((child) => normalizeChildNode(child)),
    element: raw.element ? normalizeChildNode(raw.element) : null,
    choices: raw.choices ?? [],
    description: raw.description ?? "",
  }
}

function normalizeChildNode(node: FieldNodeModel): FieldNodeModel {
  return {
    name: node.name || "field",
    type: node.type || "string",
    format: node.format || "none",
    source: (node.source as FieldSourceWire) || "caller",
    valueGenerator: node.valueGenerator ?? null,
    constant: node.constant ?? null,
    children: (node.children ?? []).map((c) => normalizeChildNode(c)),
    element: node.element ? normalizeChildNode(node.element) : null,
    choices: node.choices ?? [],
    description: node.description ?? "",
  }
}

export function emptyFieldNode(name = "field"): FieldNodeModel {
  return {
    name,
    type: "string",
    format: "none",
    source: "caller",
    valueGenerator: null,
    constant: null,
    children: [],
    choices: [],
    description: "",
  }
}

export function emptyValueMapping(): ValueMappingModel {
  return {
    mappingValue: "",
    description: "",
    target: "PATCH_FIELDS",
    patches: [{ path: "command", value: "" }],
  }
}

export function filterValidMappings(rows: ValueMappingModel[]): ValueMappingModel[] {
  return (rows ?? [])
    .filter((row) => row.mappingValue?.trim())
    .map((row) => ({
      mappingValue: row.mappingValue.trim(),
      description: row.description?.trim() || "",
      target: row.target || "PATCH_FIELDS",
      rootValue: row.rootValue,
      patches: (row.patches ?? [])
        .filter((p) => p.path?.trim())
        .map((p) => ({
          path: p.path.trim(),
          value: parsePatchValue(p.value),
        })),
    }))
}

function parsePatchValue(raw: unknown): unknown {
  if (typeof raw !== "string") {
    return raw
  }
  const text = raw.trim()
  if (!text) {
    return ""
  }
  if (text.startsWith("{") || text.startsWith("[") || text === "true" || text === "false") {
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
  if (/^-?\d+(\.\d+)?$/.test(text)) {
    return Number(text)
  }
  return text
}

export function resolvePayloadMode(fn: {
  payloadMode?: string
  writeAccessType?: string
}): "VALUE" | "STRUCT" {
  const mode = fn.payloadMode?.toUpperCase()
  if (mode === "VALUE" || mode === "STRUCT") {
    return mode
  }
  return fn.writeAccessType === "VALUE" ? "VALUE" : "STRUCT"
}
