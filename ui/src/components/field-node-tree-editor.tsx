import { ChevronDownIcon, ChevronRightIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useState, type ReactNode } from "react"

import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  emptyFieldNode,
  type FieldNodeModel,
  type FieldSourceWire,
} from "@/lib/payload-form"

const FIELD_TYPES = ["string", "int", "boolean", "select", "object", "array", "json"] as const
const FIELD_FORMATS = ["none", "datetime_iso8601", "image_base64", "text_list"] as const
const SOURCES: { value: FieldSourceWire; label: string }[] = [
  { value: "caller", label: "调用方 CALLER" },
  { value: "platform", label: "平台 PLATFORM" },
  { value: "device", label: "设备 DEVICE" },
  { value: "constant", label: "常量 CONSTANT" },
]
const VALUE_GENERATORS = [
  { value: "random_alnum_32", label: "32 位随机字符" },
  { value: "timestamp_millis", label: "毫秒时间戳" },
  { value: "uuid", label: "UUID" },
] as const

type Props = {
  label: string
  description?: string
  value: FieldNodeModel
  onChange: (next: FieldNodeModel) => void
  readOnly?: boolean
}

export function FieldNodeTreeEditor({ label, description, value, onChange, readOnly = false }: Props) {
  function updateChild(index: number, patch: Partial<FieldNodeModel>) {
    if (readOnly) return
    const children = [...(value.children ?? [])]
    children[index] = { ...children[index], ...patch }
    onChange({ ...value, children })
  }

  function removeChild(index: number) {
    if (readOnly) return
    onChange({ ...value, children: (value.children ?? []).filter((_, i) => i !== index) })
  }

  function addChild() {
    if (readOnly) return
    onChange({ ...value, children: [...(value.children ?? []), emptyFieldNode()] })
  }

  function addNestedChild(parentIndex: number) {
    if (readOnly) return
    const children = [...(value.children ?? [])]
    const parent = children[parentIndex]
    children[parentIndex] = {
      ...parent,
      type: "object",
      children: [...(parent.children ?? []), emptyFieldNode("nested")],
    }
    onChange({ ...value, children })
  }

  function updateNested(parentIndex: number, childIndex: number, patch: Partial<FieldNodeModel>) {
    if (readOnly) return
    const children = [...(value.children ?? [])]
    const parent = { ...children[parentIndex] }
    const nested = [...(parent.children ?? [])]
    nested[childIndex] = { ...nested[childIndex], ...patch }
    parent.children = nested
    children[parentIndex] = parent
    onChange({ ...value, children })
  }

  function removeNested(parentIndex: number, childIndex: number) {
    if (readOnly) return
    const children = [...(value.children ?? [])]
    const parent = { ...children[parentIndex] }
    parent.children = (parent.children ?? []).filter((_, i) => i !== childIndex)
    children[parentIndex] = parent
    onChange({ ...value, children })
  }

  return (
    <Field>
      <div className="mb-2 flex items-center justify-between gap-2">
        <div>
          <FieldLabel>{label}</FieldLabel>
          {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {!readOnly ? (
          <Button type="button" size="sm" variant="outline" onClick={addChild}>
            <PlusIcon data-icon="inline-start" />
            添加字段
          </Button>
        ) : null}
      </div>
      {(value.children ?? []).length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          配置协议 JSON 字段树；object 可嵌套子字段。
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {(value.children ?? []).map((row, index) => (
            <FieldNodeCard
              key={`${row.name}-${index}`}
              row={row}
              index={index}
              readOnly={readOnly}
              onUpdate={(patch) => updateChild(index, patch)}
              onRemove={() => removeChild(index)}
              onAddNested={() => addNestedChild(index)}
              onUpdateNested={(ci, patch) => updateNested(index, ci, patch)}
              onRemoveNested={(ci) => removeNested(index, ci)}
            />
          ))}
        </div>
      )}
    </Field>
  )
}

function FieldNodeCard({
  row,
  index,
  readOnly,
  onUpdate,
  onRemove,
  onAddNested,
  onUpdateNested,
  onRemoveNested,
}: {
  row: FieldNodeModel
  index: number
  readOnly: boolean
  onUpdate: (patch: Partial<FieldNodeModel>) => void
  onRemove: () => void
  onAddNested: () => void
  onUpdateNested: (childIndex: number, patch: Partial<FieldNodeModel>) => void
  onRemoveNested: (childIndex: number) => void
}) {
  const isObject = row.type === "object" || row.type === "json"
  const [open, setOpen] = useState(isObject)

  function setSource(source: FieldSourceWire) {
    onUpdate({
      source,
      valueGenerator: source === "platform" ? row.valueGenerator || "random_alnum_32" : null,
    })
  }

  return (
    <div className="rounded-md border p-3">
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-1">
          {isObject ? (
            <Button type="button" size="icon" variant="ghost" className="size-7" onClick={() => setOpen(!open)}>
              {open ? <ChevronDownIcon className="size-4" /> : <ChevronRightIcon className="size-4" />}
            </Button>
          ) : null}
          <span className="text-xs font-medium text-muted-foreground">字段 {index + 1}</span>
        </div>
        {!readOnly ? (
          <Button type="button" size="icon" variant="ghost" onClick={onRemove}>
            <Trash2Icon className="size-4" />
          </Button>
        ) : null}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <FieldCell label="name *">
          <Input
            value={row.name}
            disabled={readOnly}
            placeholder="command / seq"
            onChange={(e) => onUpdate({ name: e.target.value })}
          />
        </FieldCell>
        <FieldCell label="说明">
          <Input
            value={row.description ?? ""}
            disabled={readOnly}
            onChange={(e) => onUpdate({ description: e.target.value })}
          />
        </FieldCell>
        <FieldCell label="type">
          <Select value={row.type || "string"} disabled={readOnly} onValueChange={(v) => onUpdate({ type: v })}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {FIELD_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </FieldCell>
        <FieldCell label="source">
          <Select
            value={(row.source as FieldSourceWire) || "caller"}
            disabled={readOnly}
            onValueChange={(v) => setSource(v as FieldSourceWire)}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {SOURCES.map((s) => (
                  <SelectItem key={s.value} value={s.value}>
                    {s.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </FieldCell>
        {row.source === "platform" ? (
          <FieldCell label="valueGenerator">
            <Select
              value={row.valueGenerator ?? "random_alnum_32"}
              disabled={readOnly}
              onValueChange={(v) => onUpdate({ valueGenerator: v || null })}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {VALUE_GENERATORS.map((g) => (
                    <SelectItem key={g.value} value={g.value}>
                      {g.label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </FieldCell>
        ) : row.source === "constant" ? (
          <FieldCell label="constant">
            <Input
              value={row.constant == null ? "" : String(row.constant)}
              disabled={readOnly}
              onChange={(e) => onUpdate({ constant: e.target.value })}
            />
          </FieldCell>
        ) : (
          <FieldCell label="format">
            <Select value={row.format || "none"} disabled={readOnly} onValueChange={(v) => onUpdate({ format: v })}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {FIELD_FORMATS.map((f) => (
                    <SelectItem key={f} value={f}>
                      {f}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </FieldCell>
        )}
      </div>
      {isObject && open ? (
        <div className="mt-3 space-y-2 border-l-2 border-muted pl-3">
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">嵌套字段</span>
            {!readOnly ? (
              <Button type="button" size="sm" variant="outline" onClick={onAddNested}>
                <PlusIcon data-icon="inline-start" />
                子字段
              </Button>
            ) : null}
          </div>
          {(row.children ?? []).map((child, ci) => (
            <div
              key={`${child.name}-${ci}`}
              className="grid gap-2 rounded border border-dashed p-2 sm:grid-cols-[1fr_1fr_auto]"
            >
              <Input
                placeholder="子字段名"
                value={child.name}
                disabled={readOnly}
                onChange={(e) => onUpdateNested(ci, { name: e.target.value })}
              />
              <Input
                placeholder="说明"
                value={child.description ?? ""}
                disabled={readOnly}
                onChange={(e) => onUpdateNested(ci, { description: e.target.value })}
              />
              {!readOnly ? (
                <Button type="button" size="icon" variant="ghost" onClick={() => onRemoveNested(ci)}>
                  <Trash2Icon className="size-4" />
                </Button>
              ) : null}
              <Select
                value={child.source || "caller"}
                disabled={readOnly}
                onValueChange={(v) =>
                  onUpdateNested(ci, {
                    source: v as FieldSourceWire,
                    valueGenerator: v === "platform" ? child.valueGenerator || "random_alnum_32" : null,
                  })
                }
              >
                <SelectTrigger className="sm:col-span-2">
                  <SelectValue placeholder="source" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {SOURCES.map((s) => (
                      <SelectItem key={s.value} value={s.value}>
                        {s.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function FieldCell({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <FieldLabel className="text-xs">{label}</FieldLabel>
      {children}
    </div>
  )
}
