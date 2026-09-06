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
  callerField?: string
  byteLength?: number | null
  byteOrder?: string | null
}

export type FieldPatchModel = {
  path: string
  value: unknown
}

export type ValueMappingModel = {
  mappingValue: string
  description?: string
  callerField?: string
  target?: "PATCH_FIELDS" | "FILL_ROOT" | string
  rootValue?: unknown
  patches?: FieldPatchModel[]
}

export function emptyObjectRoot(): FieldNodeModel {
  return { name: "root", type: "object", format: "none", source: "caller", children: [] }
}

export function writeFieldsToFieldNode(fields: WriteFieldOption[]): FieldNodeModel {
  const root = emptyObjectRoot()
  for (const row of fields ?? []) {
    if (!row.field?.trim()) {
      continue
    }
    insertPath(root, row.field.trim().split("."), row)
  }
  return root
}

function insertPath(parent: FieldNodeModel, parts: string[], row: WriteFieldOption) {
  const [head, ...rest] = parts
  const isIndex = /^\d+$/.test(head)
  if (isIndex && parent.type !== "array") {
    parent.type = "array"
  }
  parent.children = parent.children ?? []
  let child = parent.children.find((item) => item.name === head)
  if (!child) {
    child = emptyFieldNode(head)
    parent.children.push(child)
  }
  if (rest.length === 0) {
    applyFieldRow(child, row)
    return
  }
  if (rest[0] && /^\d+$/.test(rest[0])) {
    if (child.type === "string") {
      child.type = "array"
    }
  } else if (child.type === "string") {
    child.type = "object"
  }
  insertPath(child, rest, row)
}

function applyFieldRow(node: FieldNodeModel, row: WriteFieldOption) {
  const choices = (row.options ?? []).map((item) => item.optionValue).filter(Boolean)
  const source: FieldSourceWire =
    (row.source as FieldSourceWire) || (row.valueGenerator ? "platform" : "caller")
  const scalar = !["object", "json", "array"].includes(row.accessDataType || "")
  node.type =
    choices.length > 0 && source !== "mapped" && scalar ? "select" : row.accessDataType || "string"
  node.format = row.format || "none"
  node.source = source
  node.valueGenerator = row.valueGenerator ?? null
  node.constant = row.constant ?? null
  node.choices = choices
  node.description = row.description ?? ""
  node.callerField = row.callerField || (source === "mapped" ? "value" : undefined)
  node.byteLength = row.byteLength ?? undefined
  node.byteOrder = row.byteOrder || undefined
}

export function fieldNodeToWriteFields(root: FieldNodeModel | null | undefined): WriteFieldOption[] {
  const leaves = collectLeaves(root, "")
  return leaves.map(({ path, node }) => {
    const choices = node.choices ?? []
    const source = (node.source as FieldSourceWire) || (node.valueGenerator ? "platform" : "caller")
    const options =
      source === "mapped"
        ? []
        : choices.length > 0
          ? choices.map((value) => ({
              optionValue: value,
              mappingValue: value,
              description: value,
            }))
          : []
    const ignore = source !== "caller"
    return {
      field: path,
      description: node.description ?? "",
      accessDataType: node.type === "select" ? "string" : node.type || "string",
      transformDataType: node.type === "select" ? "string" : node.type || "string",
      ignoreRequest: ignore,
      options,
      format: node.format || "none",
      valueGenerator: node.valueGenerator || undefined,
      source,
      constant: node.constant == null || node.constant === "" ? undefined : String(node.constant),
      callerField:
        source === "mapped"
          ? node.callerField || "value"
          : source === "caller"
            ? node.callerField || undefined
            : undefined,
      byteLength: node.byteLength || undefined,
      byteOrder: node.byteOrder || undefined,
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
    node.type === "array" ||
    (node.children && node.children.length > 0)
  if (isContainer && node.children && node.children.length > 0) {
    return node.children.flatMap((child) => {
      const nextPrefix = prefix ? `${prefix}.${child.name}` : child.name
      if (
        child.type === "object" ||
        child.type === "json" ||
        child.type === "array" ||
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

export const CALLER_PASSTHROUGH = "$caller"

export function isPassthroughMapping(row: Pick<ValueMappingModel, "mappingValue">): boolean {
  return row.mappingValue?.trim() === CALLER_PASSTHROUGH
}

/** 编辑回显：writeValueOptions 优先；否则回落 MAPPED 叶子 options。透传行始终从 CALLER 叶子补齐。 */
export function editMappingsFromFunction(
  fields: WriteFieldOption[],
  writeValueOptions: ValueOption[]
): ValueMappingModel[] {
  const fromFields = fieldOptionsToMappings(fields)
  const passthrough = fromFields.filter(isPassthroughMapping)
  const fieldEnums = fromFields.filter((row) => !isPassthroughMapping(row))
  const fromOpts = valueOptionsToMappings(writeValueOptions ?? [], fields)
  return [...(fromOpts.length > 0 ? fromOpts : fieldEnums), ...passthrough]
}

export function fieldOptionsToMappings(fields: WriteFieldOption[]): ValueMappingModel[] {
  const byKey = new Map<string, ValueMappingModel>()
  const passthrough: ValueMappingModel[] = []
  for (const field of fields ?? []) {
    if (!field.field) {
      continue
    }
    if (field.source === "caller") {
      passthrough.push({
        mappingValue: CALLER_PASSTHROUGH,
        callerField: field.callerField?.trim() || field.field,
        description: field.description ?? "",
        target: "PATCH_FIELDS",
        patches: [{ path: field.field, value: "" }],
      })
      continue
    }
    if (field.source !== "mapped") {
      continue
    }
    const callerField = field.callerField?.trim() || "value"
    for (const option of field.options ?? []) {
      const mappingValue = option.mappingValue?.trim() || option.optionValue?.trim()
      if (!mappingValue) {
        continue
      }
      const groupKey = `${callerField}\0${mappingValue}`
      if (!byKey.has(groupKey)) {
        byKey.set(groupKey, {
          mappingValue,
          callerField,
          description: option.description ?? "",
          target: "PATCH_FIELDS",
          patches: [],
        })
      }
      byKey.get(groupKey)!.patches!.push({ path: field.field, value: option.optionValue })
    }
  }
  return [...byKey.values(), ...passthrough]
}

export function mergeMappingsIntoFields(
  fields: WriteFieldOption[],
  mappings: ValueMappingModel[]
): WriteFieldOption[] {
  if (!mappings?.length) {
    return fields
  }
  const next = fields.map((field) => ({ ...field, options: [...(field.options ?? [])] }))
  const reset = new Set<string>()
  for (const mapping of mappings) {
    for (const patch of mapping.patches ?? []) {
      const path = patch.path?.trim()
      if (!path) {
        continue
      }
      let field = next.find((item) => item.field === path)
      if (!field) {
        field = {
          field: path,
          description: "",
          accessDataType: "string",
          transformDataType: "string",
          ignoreRequest: true,
          options: [],
          format: "none",
          source: "mapped",
          callerField: mapping.callerField?.trim() || "value",
        }
        next.push(field)
      }
      if (isPassthroughMapping(mapping)) {
        field.source = "caller"
        field.ignoreRequest = false
        field.callerField = mapping.callerField?.trim() || undefined
        field.options = []
        if (mapping.description?.trim()) {
          field.description = mapping.description.trim()
        }
        continue
      }
      const source = (field.source || "").toLowerCase()
      if (source === "constant" || source === "device" || source === "platform") {
        continue
      }
      if (!reset.has(path)) {
        field.options = []
        reset.add(path)
      }
      field.source = "mapped"
      field.ignoreRequest = true
      field.callerField = mapping.callerField?.trim() || field.callerField || "value"
    }
  }
  return next
}

export function valueOptionsToMappings(
  options: ValueOption[],
  fields: WriteFieldOption[]
): ValueMappingModel[] {
  const mapped =
    fields.find((item) => item.source === "mapped" && item.field)
  const mappedField = mapped?.field ?? fields.find((item) => !item.valueGenerator && item.field)?.field ?? fields[0]?.field ?? "command"
  const callerField = mapped?.callerField?.trim() || "value"
  return (options ?? [])
    .filter((item) => item.optionValue?.trim())
    .map((item) => ({
      mappingValue: item.mappingValue?.trim() || item.optionValue.trim(),
      callerField,
      description: item.description ?? "",
      target: "PATCH_FIELDS" as const,
      patches: [{ path: mappedField, value: item.optionValue.trim() }],
    }))
}

export function mappingsToValueOptions(mappings: ValueMappingModel[]): ValueOption[] {
  return (mappings ?? [])
    .filter((m) => m.mappingValue?.trim() && !isPassthroughMapping(m))
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
    callerField: raw.callerField,
    byteLength: raw.byteLength,
    byteOrder: raw.byteOrder,
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
    callerField: node.callerField,
    byteLength: node.byteLength,
    byteOrder: node.byteOrder,
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
    callerField: undefined,
    byteLength: undefined,
    byteOrder: undefined,
  }
}

export function emptyValueMapping(defaultPath = ""): ValueMappingModel {
  return {
    mappingValue: "",
    callerField: "value",
    description: "",
    target: "PATCH_FIELDS",
    patches: [{ path: defaultPath, value: "" }],
  }
}

export function filterValidMappings(rows: ValueMappingModel[]): ValueMappingModel[] {
  return (rows ?? [])
    .filter((row) => {
      const hasPath = (row.patches ?? []).some((patch) => patch.path?.trim())
      if (!hasPath) {
        return false
      }
      if (isPassthroughMapping(row)) {
        return Boolean(row.callerField?.trim())
      }
      return Boolean(row.mappingValue?.trim())
    })
    .map((row) => {
      const callerField = row.callerField?.trim() || "value"
      const passthrough = isPassthroughMapping(row)
      return {
        mappingValue: passthrough ? CALLER_PASSTHROUGH : row.mappingValue.trim(),
        callerField,
        description: row.description?.trim() || "",
        target: row.target || "PATCH_FIELDS",
        rootValue: row.rootValue,
        patches: (row.patches ?? [])
          .filter((p) => p.path?.trim())
          .map((p) => ({
            path: p.path.trim(),
            value: passthrough ? "" : parsePatchValue(p.value),
          })),
      }
    })
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
