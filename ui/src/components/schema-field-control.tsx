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

export type SchemaControlField = {
  name: string
  type: string
  required?: boolean
  description?: string
  label?: string
  secret?: boolean
  choices?: string[]
  value?: unknown
}

type Props = {
  field: SchemaControlField
  value: string
  onChange: (value: string) => void
  idPrefix?: string
}

/**
 * 按 schema 类型渲染控件：
 * - select / 有 choices：下拉；恰好 2 个选项时用按钮组
 * - boolean：开关
 * - list/json：多行文本
 * - 其它：普通输入
 */
export function SchemaFieldControl({ field, value, onChange, idPrefix = "field" }: Props) {
  const id = `${idPrefix}-${field.name}`
  const choices = field.choices ?? []
  const isSelect = field.type === "select" || choices.length > 0

  if (field.type === "boolean") {
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

  if (field.name.toLowerCase().includes("list") || field.type === "json") {
    return (
      <Textarea
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={field.description}
      />
    )
  }

  return (
    <Input
      id={id}
      type={
        field.secret
          ? "password"
          : field.type === "int" || field.type === "integer"
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
