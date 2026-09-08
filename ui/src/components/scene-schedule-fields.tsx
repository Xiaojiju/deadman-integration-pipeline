import { DateTimePicker } from "@/components/date-time-picker"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import {
  TIMEZONE_OPTIONS,
  WEEKDAY_LABELS,
  WEEKDAYS,
  changeRecurrenceKind,
  parseTimeInput,
  timeInputValue,
  type Recurrence,
  type Weekday,
} from "@/lib/scene-schedule"

const KIND_OPTIONS: Array<{ value: Recurrence["kind"]; label: string }> = [
  { value: "once", label: "只执行一次" },
  { value: "daily", label: "每天" },
  { value: "weekdays", label: "工作日（周一至周五）" },
  { value: "weekly", label: "每周" },
  { value: "monthly", label: "每月" },
  { value: "interval", label: "每隔一段时间" },
  { value: "custom", label: "自定义表达式" },
]

type Props = {
  schedule: Recurrence
  onScheduleChange: (next: Recurrence) => void
  timerAt: string
  onTimerAtChange: (timerAt: string) => void
  timezone: string
  onTimezoneChange: (timezone: string) => void
}

export function SceneScheduleFields({
  schedule,
  onScheduleChange,
  timerAt,
  onTimerAtChange,
  timezone,
  onTimezoneChange,
}: Props) {
  const zones = TIMEZONE_OPTIONS.some((item) => item.value === timezone)
    ? TIMEZONE_OPTIONS
    : [{ value: timezone, label: timezone }, ...TIMEZONE_OPTIONS]

  return (
    <>
      <Field>
        <FieldLabel>重复方式</FieldLabel>
        <Select
          value={schedule.kind}
          onValueChange={(kind) =>
            onScheduleChange(changeRecurrenceKind(schedule, kind as Recurrence["kind"]))
          }
        >
          <SelectTrigger className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {KIND_OPTIONS.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>

      {schedule.kind === "once" ? (
        <Field>
          <FieldLabel>执行时间 *</FieldLabel>
          <DateTimePicker id="scene-timer-at" value={timerAt} onChange={onTimerAtChange} />
          <FieldDescription>按本机时钟选择，须晚于现在。已执行过的单次场景保存后会重新启用。</FieldDescription>
        </Field>
      ) : null}

      {schedule.kind === "daily"
      || schedule.kind === "weekdays"
      || schedule.kind === "weekly"
      || schedule.kind === "monthly" ? (
        <Field>
          <FieldLabel>{schedule.kind === "monthly" ? "每月几号的几点" : "执行时间"}</FieldLabel>
          <div className="flex flex-wrap items-end gap-3">
            {schedule.kind === "monthly" ? (
              <Field className="w-auto min-w-28">
                <FieldLabel htmlFor="scene-month-day">日期</FieldLabel>
                <Select
                  value={String(schedule.day)}
                  onValueChange={(day) =>
                    onScheduleChange({ ...schedule, day: Number(day) })
                  }
                >
                  <SelectTrigger id="scene-month-day" className="w-28">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {Array.from({ length: 31 }, (_, index) => index + 1).map((day) => (
                        <SelectItem key={day} value={String(day)}>
                          {day} 日
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            ) : null}
            <Field className="w-auto min-w-36">
              <FieldLabel htmlFor="scene-clock">时刻</FieldLabel>
              <Input
                id="scene-clock"
                type="time"
                value={timeInputValue(schedule.hour, schedule.minute)}
                onChange={(event) => {
                  const next = parseTimeInput(event.target.value)
                  if (!next) {
                    return
                  }
                  onScheduleChange({ ...schedule, ...next })
                }}
                className="bg-background appearance-none [&::-webkit-calendar-picker-indicator]:hidden [&::-webkit-calendar-picker-indicator]:appearance-none"
              />
            </Field>
          </div>
          {schedule.kind === "monthly" ? (
            <FieldDescription>没有这一天的月份会自动跳过，例如 2 月没有 31 日。</FieldDescription>
          ) : null}
        </Field>
      ) : null}

      {schedule.kind === "weekly" ? (
        <Field>
          <FieldLabel>星期</FieldLabel>
          <ToggleGroup
            type="multiple"
            variant="outline"
            spacing={0}
            value={schedule.weekdays}
            onValueChange={(weekdays) =>
              onScheduleChange({ ...schedule, weekdays: weekdays as Weekday[] })
            }
            className="flex-wrap"
          >
            {WEEKDAYS.map((day) => (
              <ToggleGroupItem key={day} value={day}>
                {WEEKDAY_LABELS[day]}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          <FieldDescription>至少选一天，可多选。</FieldDescription>
        </Field>
      ) : null}

      {schedule.kind === "interval" ? (
        <Field>
          <FieldLabel>间隔</FieldLabel>
          <div className="flex flex-wrap items-end gap-3">
            <Field className="w-auto min-w-24">
              <FieldLabel htmlFor="scene-interval-every">每隔</FieldLabel>
              <Input
                id="scene-interval-every"
                type="number"
                min={1}
                max={schedule.unit === "minute" ? 59 : 23}
                value={schedule.every}
                onChange={(event) => {
                  const every = Number(event.target.value)
                  if (!Number.isFinite(every)) {
                    return
                  }
                  onScheduleChange({ ...schedule, every })
                }}
                className="w-24"
              />
            </Field>
            <Field className="w-auto min-w-28">
              <FieldLabel>单位</FieldLabel>
              <Select
                value={schedule.unit}
                onValueChange={(unit) =>
                  onScheduleChange({
                    ...schedule,
                    unit: unit as "minute" | "hour",
                    every: unit === "minute" ? Math.min(schedule.every, 59) : Math.min(schedule.every, 23),
                  })
                }
              >
                <SelectTrigger className="w-28">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="minute">分钟</SelectItem>
                    <SelectItem value="hour">小时</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
          </div>
          <FieldDescription>
            按钟点对齐，不是从保存时刻起算。每 30 分钟是每个小时的 00 分和 30 分；每 2 小时是 0、2、4 点。
          </FieldDescription>
        </Field>
      ) : null}

      {schedule.kind === "custom" ? (
        <Field>
          <FieldLabel>Cron（6 段）*</FieldLabel>
          <Input
            value={schedule.cronExpr}
            onChange={(event) =>
              onScheduleChange({ kind: "custom", cronExpr: event.target.value })
            }
            placeholder="0 0 8 * * *"
          />
          <FieldDescription>高级选项。顺序为秒 分 时 日 月 周，例如每天 8 点填 0 0 8 * * *。</FieldDescription>
        </Field>
      ) : null}

      {schedule.kind === "once" ? null : (
        <Field>
          <FieldLabel>时区</FieldLabel>
          <Select value={timezone} onValueChange={onTimezoneChange}>
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {zones.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <FieldDescription>重复时刻按此时区理解。</FieldDescription>
        </Field>
      )}
    </>
  )
}
