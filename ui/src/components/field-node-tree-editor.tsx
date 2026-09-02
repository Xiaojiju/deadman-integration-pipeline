import { ChevronDownIcon, ChevronRightIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useEffect, useState, type MouseEvent, type ReactNode } from "react"

import { ConfigExample, CodeSample } from "@/components/config-example"
import { ReorderControls } from "@/components/reorder-controls"
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
import { isReorderClick, moveBy, swapAt } from "@/lib/reorder"
import { cn } from "@/lib/utils"

const FIELD_TYPES = ["string", "int", "boolean", "select", "object", "array", "json"] as const
const FIELD_FORMATS = ["none", "datetime_iso8601", "image_base64", "text_list"] as const
const SOURCES: { value: FieldSourceWire; label: string }[] = [
  { value: "caller", label: "调用方 CALLER" },
  { value: "platform", label: "平台 PLATFORM" },
  { value: "device", label: "设备 DEVICE" },
  { value: "constant", label: "常量 CONSTANT" },
  { value: "mapped", label: "映射 MAPPED" },
]
const VALUE_GENERATORS = [
  { value: "random_alnum_32", label: "32 位随机字符" },
  { value: "timestamp_millis", label: "毫秒时间戳" },
  { value: "timestamp_seconds", label: "秒级时间戳" },
  { value: "uuid", label: "UUID" },
] as const

type Props = {
  label: string
  description?: string
  value: FieldNodeModel
  onChange: (next: FieldNodeModel) => void
  readOnly?: boolean
  /** HEX/BINARY 时展示字节宽度与字节序 */
  showByteLayout?: boolean
}

export function FieldNodeTreeEditor({
  label,
  description,
  value,
  onChange,
  readOnly = false,
  showByteLayout = false,
}: Props) {
  const [pickedIndex, setPickedIndex] = useState<number | null>(null)
  const [pickedNested, setPickedNested] = useState<{ parent: number; child: number } | null>(null)

  function reorderChildren(next: FieldNodeModel[]) {
    setPickedIndex(null)
    onChange({ ...value, children: next })
  }

  function pickChild(index: number) {
    if (readOnly) return
    setPickedNested(null)
    if (pickedIndex == null) {
      setPickedIndex(index)
      return
    }
    if (pickedIndex === index) {
      setPickedIndex(null)
      return
    }
    reorderChildren(swapAt(value.children ?? [], pickedIndex, index))
  }

  function moveChild(index: number, delta: number) {
    if (readOnly) return
    reorderChildren(moveBy(value.children ?? [], index, delta))
  }

  function pickNested(parentIndex: number, childIndex: number) {
    if (readOnly) return
    setPickedIndex(null)
    if (pickedNested == null || pickedNested.parent !== parentIndex) {
      setPickedNested({ parent: parentIndex, child: childIndex })
      return
    }
    if (pickedNested.child === childIndex) {
      setPickedNested(null)
      return
    }
    reorderNested(parentIndex, swapAt(value.children?.[parentIndex]?.children ?? [], pickedNested.child, childIndex))
  }

  function moveNested(parentIndex: number, childIndex: number, delta: number) {
    if (readOnly) return
    reorderNested(parentIndex, moveBy(value.children?.[parentIndex]?.children ?? [], childIndex, delta))
  }

  function reorderNested(parentIndex: number, nextChildren: FieldNodeModel[]) {
    const children = [...(value.children ?? [])]
    const parent = children[parentIndex]
    const isArray = parent.type === "array"
    children[parentIndex] = {
      ...parent,
      children: isArray ? nextChildren.map((child, i) => ({ ...child, name: String(i) })) : nextChildren,
    }
    setPickedNested(null)
    onChange({ ...value, children })
  }
  function updateChild(index: number, patch: Partial<FieldNodeModel>) {
    if (readOnly) return
    const children = [...(value.children ?? [])]
    children[index] = { ...children[index], ...patch }
    onChange({ ...value, children })
  }

  function removeChild(index: number) {
    if (readOnly) return
    setPickedIndex(null)
    setPickedNested(null)
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
    const isArray = parent.type === "array"
    const nextName = isArray ? String((parent.children ?? []).length) : "nested"
    children[parentIndex] = {
      ...parent,
      type: isArray ? "array" : parent.type === "json" ? "json" : "object",
      children: [...(parent.children ?? []), emptyFieldNode(nextName)],
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
    const remaining = (parent.children ?? []).filter((_, i) => i !== childIndex)
    parent.children =
      parent.type === "array" ? remaining.map((child, i) => ({ ...child, name: String(i) })) : remaining
    children[parentIndex] = parent
    setPickedNested(null)
    onChange({ ...value, children })
  }

  return (
    <Field>
      <div className="mb-2 flex items-center justify-between gap-2">
        <div>
          <FieldLabel>{label}</FieldLabel>
          {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
          {!readOnly ? (
            <p className="text-xs text-muted-foreground">
              点选一张卡片，再点另一张即可交换顺序；也可点上下箭头逐格移动。
            </p>
          ) : null}
        </div>
        {!readOnly ? (
          <Button type="button" size="sm" variant="outline" onClick={addChild}>
            <PlusIcon data-icon="inline-start" />
            添加字段
          </Button>
        ) : null}
      </div>
      <ConfigExample title="示例 · 控门协议字段">
        <p>
          <CodeSample>devId</CodeSample> device ·{" "}
          <CodeSample>devPsw</CodeSample> constant=0 ·{" "}
          <CodeSample>at</CodeSample> platform/秒级时间戳 ·{" "}
          <CodeSample>seq</CodeSample> platform/随机串 ·{" "}
          <CodeSample>op</CodeSample> constant
        </p>
        <p>
          <CodeSample>params</CodeSample> 类型选 array，添加元素 0、1…：0 为 mapped（调用方字段 lock），1 为 device（手机号）。
        </p>
      </ConfigExample>
      {(value.children ?? []).length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          先添加根字段。object / array 可再点「添加元素」嵌套；数组元素名用 0、1、2。
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {(value.children ?? []).map((row, index) => (
            <FieldNodeCard
              key={`field-${index}`}
              row={row}
              index={index}
              count={(value.children ?? []).length}
              readOnly={readOnly}
              showByteLayout={showByteLayout}
              selected={pickedIndex === index}
              onPick={() => pickChild(index)}
              onMoveUp={() => moveChild(index, -1)}
              onMoveDown={() => moveChild(index, 1)}
              onUpdate={(patch) => updateChild(index, patch)}
              onRemove={() => removeChild(index)}
              onAddNested={() => addNestedChild(index)}
              onUpdateNested={(ci, patch) => updateNested(index, ci, patch)}
              onRemoveNested={(ci) => removeNested(index, ci)}
              pickedNestedIndex={pickedNested?.parent === index ? pickedNested.child : null}
              onPickNested={(ci) => pickNested(index, ci)}
              onMoveNested={(ci, delta) => moveNested(index, ci, delta)}
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
  count,
  readOnly,
  showByteLayout,
  selected,
  onPick,
  onMoveUp,
  onMoveDown,
  onUpdate,
  onRemove,
  onAddNested,
  onUpdateNested,
  onRemoveNested,
  pickedNestedIndex,
  onPickNested,
  onMoveNested,
}: {
  row: FieldNodeModel
  index: number
  count: number
  readOnly: boolean
  showByteLayout: boolean
  selected: boolean
  onPick: () => void
  onMoveUp: () => void
  onMoveDown: () => void
  onUpdate: (patch: Partial<FieldNodeModel>) => void
  onRemove: () => void
  onAddNested: () => void
  onUpdateNested: (childIndex: number, patch: Partial<FieldNodeModel>) => void
  onRemoveNested: (childIndex: number) => void
  pickedNestedIndex: number | null
  onPickNested: (childIndex: number) => void
  onMoveNested: (childIndex: number, delta: number) => void
}) {
  const isContainer = row.type === "object" || row.type === "json" || row.type === "array"
  const [open, setOpen] = useState(isContainer)

  useEffect(() => {
    if (isContainer) {
      setOpen(true)
    }
  }, [isContainer, row.type])

  function setSource(source: FieldSourceWire) {
    onUpdate({
      source,
      valueGenerator: source === "platform" ? row.valueGenerator || "random_alnum_32" : null,
    })
  }

  function handleCardClick(event: MouseEvent<HTMLDivElement>) {
    if (readOnly || !isReorderClick(event.target)) return
    onPick()
  }

  return (
    <div
      className={cn(
        "rounded-md border p-3",
        !readOnly && "cursor-pointer",
        selected && "bg-muted/40 ring-2 ring-primary"
      )}
      onClick={handleCardClick}
    >
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-1">
          {isContainer ? (
            <Button type="button" size="icon" variant="ghost" className="size-7" onClick={() => setOpen(!open)}>
              {open ? <ChevronDownIcon className="size-4" /> : <ChevronRightIcon className="size-4" />}
            </Button>
          ) : null}
          <span className="text-xs font-medium text-muted-foreground">
            字段 {index + 1}
            {selected ? " · 已选中，再点另一张交换" : ""}
          </span>
        </div>
        {!readOnly ? (
          <div className="flex items-center">
            <ReorderControls
              canMoveUp={index > 0}
              canMoveDown={index < count - 1}
              onMoveUp={onMoveUp}
              onMoveDown={onMoveDown}
            />
            <Button type="button" size="icon" variant="ghost" onClick={onRemove}>
              <Trash2Icon className="size-4" />
            </Button>
          </div>
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
          <Select
            value={row.type || "string"}
            disabled={readOnly}
            onValueChange={(v) => {
              onUpdate({ type: v })
              if (v === "array" || v === "object" || v === "json") {
                setOpen(true)
              }
            }}
          >
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
        ) : row.source === "mapped" ? (
          <FieldCell label="调用方字段 callerField">
            <Input
              value={row.callerField || "value"}
              disabled={readOnly}
              placeholder="value / lock"
              onChange={(e) => onUpdate({ callerField: e.target.value })}
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
        {showByteLayout && !isContainer ? (
          <ByteLayoutFields
            byteLength={row.byteLength}
            byteOrder={row.byteOrder}
            readOnly={readOnly}
            onUpdate={onUpdate}
          />
        ) : null}
      </div>
      {isContainer && open ? (
        <div className="mt-3 space-y-2 border-l-2 border-muted pl-3">
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">
              {row.type === "array" ? "数组元素（name 填下标 0、1、2…）" : "嵌套字段"}
            </span>
            {!readOnly ? (
              <Button type="button" size="sm" variant="outline" onClick={onAddNested}>
                <PlusIcon data-icon="inline-start" />
                {row.type === "array" ? "添加元素" : "子字段"}
              </Button>
            ) : null}
          </div>
          {(row.children ?? []).length === 0 && row.type === "array" ? (
            <p className="text-xs text-muted-foreground">
              点击「添加元素」配置 params.0、params.1… 的来源（映射 / 设备 / 常量）。
            </p>
          ) : null}
          {(row.children ?? []).map((child, ci) => (
            <div
              key={`nested-${ci}`}
              className={cn(
                "grid gap-2 rounded border border-dashed p-2 sm:grid-cols-2",
                !readOnly && "cursor-pointer",
                pickedNestedIndex === ci && "bg-muted/40 ring-2 ring-primary"
              )}
              onClick={(event) => {
                event.stopPropagation()
                if (readOnly || !isReorderClick(event.target)) return
                onPickNested(ci)
              }}
            >
              <Input
                placeholder={row.type === "array" ? "下标 0" : "子字段名"}
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
                <SelectTrigger>
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
              {child.source === "constant" ? (
                <Input
                  placeholder="常量值"
                  value={child.constant == null ? "" : String(child.constant)}
                  disabled={readOnly}
                  onChange={(e) => onUpdateNested(ci, { constant: e.target.value })}
                />
              ) : child.source === "platform" ? (
                <Select
                  value={child.valueGenerator || "random_alnum_32"}
                  disabled={readOnly}
                  onValueChange={(v) => onUpdateNested(ci, { valueGenerator: v })}
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
              ) : child.source === "mapped" ? (
                <Input
                  placeholder="调用方字段，如 lock"
                  value={child.callerField || "value"}
                  disabled={readOnly}
                  onChange={(e) => onUpdateNested(ci, { callerField: e.target.value })}
                />
              ) : (
                <p className="self-center text-xs text-muted-foreground">
                  {child.source === "device" ? "由本设备参数填入" : "由调用方传入"}
                </p>
              )}
              {showByteLayout ? (
                <ByteLayoutFields
                  byteLength={child.byteLength}
                  byteOrder={child.byteOrder}
                  readOnly={readOnly}
                  onUpdate={(patch) => onUpdateNested(ci, patch)}
                />
              ) : null}
              {!readOnly ? (
                <div className="sm:col-span-2 flex items-center justify-end">
                  <ReorderControls
                    canMoveUp={ci > 0}
                    canMoveDown={ci < (row.children ?? []).length - 1}
                    onMoveUp={() => onMoveNested(ci, -1)}
                    onMoveDown={() => onMoveNested(ci, 1)}
                  />
                  <Button type="button" size="icon" variant="ghost" onClick={() => onRemoveNested(ci)}>
                    <Trash2Icon className="size-4" />
                  </Button>
                </div>
              ) : null}
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

function ByteLayoutFields({
  byteLength,
  byteOrder,
  readOnly,
  onUpdate,
}: {
  byteLength?: number | null
  byteOrder?: string | null
  readOnly: boolean
  onUpdate: (patch: Partial<FieldNodeModel>) => void
}) {
  return (
    <>
      <FieldCell label="字节数 byteLength">
        <Input
          type="number"
          min={1}
          max={32}
          placeholder="1"
          value={byteLength ?? ""}
          disabled={readOnly}
          onChange={(e) =>
            onUpdate({
              byteLength: e.target.value ? Number(e.target.value) : undefined,
            })
          }
        />
      </FieldCell>
      <FieldCell label="字节序">
        <Select
          value={byteOrder || "big"}
          disabled={readOnly}
          onValueChange={(v) => onUpdate({ byteOrder: v })}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="big">大端 big</SelectItem>
              <SelectItem value="little">小端 little</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      </FieldCell>
    </>
  )
}
