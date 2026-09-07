import { DateTimePicker } from "@/components/date-time-picker"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { FieldFormat, FieldType } from "@/lib/types"

export type SchemaControlField = {
  name: string
  type: FieldType | string
  required?: boolean
  description?: string
  label?: string
  secret?: boolean
  choices?: string[]
  format?: FieldFormat | string
  value?: unknown
}

type Props = {
  field: SchemaControlField
  value: string
  onChange: (value: string) => void
  idPrefix?: string
}

/**
 * 按 schema type + format 渲染控件：
 * - format=datetime_iso8601：日期时间选择
 * - format=image_base64：本地选图 → Base64
 * - format=text_list / type=json：多行文本
 * - select / choices：下拉或按钮组
 * - boolean：开关
 * - 其它：普通输入
 */
export function SchemaFieldControl({ field, value, onChange, idPrefix = "field" }: Props) {
  const id = `${idPrefix}-${field.name}`
  const choices = field.choices ?? []
  const format = (field.format ?? "none").toLowerCase()
  const type = (field.type ?? "string").toLowerCase()
  const isSelect = type === "select" || choices.length > 0

  if (format === "datetime_iso8601" || format === "datetime") {
    return <DateTimePicker id={id} value={value} onChange={onChange} />
  }

  if (format === "image_base64" || format === "image") {
    return (
      <div className="space-y-2">
        <Input
          id={id}
          type="file"
          accept="image/*"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (!file) {
              onChange("")
              return
            }
            const reader = new FileReader()
            reader.onload = () => {
              const result = typeof reader.result === "string" ? reader.result : ""
              // 服务端通常要纯 Base64；若含 dataURL 前缀则剥掉
              const comma = result.indexOf(",")
              onChange(comma >= 0 ? result.slice(comma + 1) : result)
            }
            reader.readAsDataURL(file)
          }}
        />
        {value ? (
          <p className="truncate text-xs text-muted-foreground">
            已选择图片（Base64 {value.length} 字符）
          </p>
        ) : (
          <p className="text-xs text-muted-foreground">{field.description || "选择本地图片后自动转 Base64"}</p>
        )}
      </div>
    )
  }

  if (type === "boolean") {
    const checked = value === "true" || value === "1"
    return (
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm text-muted-foreground">{field.description}</span>
        <Switch
          id={id}
          checked={checked}
          onCheckedChange={(next) => onChange(next ? "true" : "false")}
        />
      </div>
    )
  }

  if (isSelect && choices.length === 2) {
    return (
      <div className="flex flex-wrap gap-2">
        {choices.map((choice) => (
          <Button
            key={choice}
            type="button"
            size="sm"
            variant={value === choice ? "default" : "outline"}
            onClick={() => onChange(choice)}
          >
            {labelForChoice(choice)}
          </Button>
        ))}
      </div>
    )
  }

  if (isSelect && choices.length > 0) {
    return (
      <Select value={value || undefined} onValueChange={onChange}>
        <SelectTrigger id={id}>
          <SelectValue placeholder={field.description || "请选择"} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {choices.map((choice) => (
              <SelectItem key={choice} value={choice}>
                {labelForChoice(choice)}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    )
  }

  if (
    format === "text_list" ||
    type === "json" ||
    type === "array" ||
    field.name.toLowerCase().includes("list")
  ) {
    return (
      <Textarea
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={
          type === "array"
            ? field.description || 'JSON 数组，例如 ["a","b"] 或 [1,2]'
            : field.description
        }
      />
    )
  }

  return (
    <Input
      id={id}
      type={
        field.secret || type === "password"
          ? "password"
          : type === "int" || type === "integer"
            ? "number"
            : "text"
      }
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={field.description}
    />
  )
}

function labelForChoice(choice: string): string {
  const map: Record<string, string> = {
    open: "开门 open",
    close: "关门 close",
    on: "开 on",
    off: "关 off",
  }
  return map[choice] ?? choice
}
