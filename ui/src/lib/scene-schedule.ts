import type { SceneTriggerView } from "@/lib/types"

export const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const

export type Weekday = (typeof WEEKDAYS)[number]

export type Recurrence =
  | { kind: "once" }
  | { kind: "daily"; hour: number; minute: number }
  | { kind: "weekdays"; hour: number; minute: number }
  | { kind: "weekly"; hour: number; minute: number; weekdays: Weekday[] }
  | { kind: "monthly"; hour: number; minute: number; day: number }
  | { kind: "interval"; every: number; unit: "minute" | "hour" }
  | { kind: "custom"; cronExpr: string }

export const TIMEZONE_OPTIONS = [
  { value: "Asia/Shanghai", label: "中国（上海）" },
  { value: "Asia/Hong_Kong", label: "中国（香港）" },
  { value: "Asia/Taipei", label: "中国（台北）" },
  { value: "UTC", label: "UTC" },
] as const

export const WEEKDAY_LABELS: Record<Weekday, string> = {
  MON: "周一",
  TUE: "周二",
  WED: "周三",
  THU: "周四",
  FRI: "周五",
  SAT: "周六",
  SUN: "周日",
}

const WEEKDAY_ORDER: Record<Weekday, number> = {
  MON: 1,
  TUE: 2,
  WED: 3,
  THU: 4,
  FRI: 5,
  SAT: 6,
  SUN: 7,
}

const WEEKDAY_BY_NAME: Record<string, Weekday> = {
  MON: "MON",
  TUE: "TUE",
  WED: "WED",
  THU: "THU",
  FRI: "FRI",
  SAT: "SAT",
  SUN: "SUN",
}

const WEEKDAY_BY_NUMBER: Record<string, Weekday> = {
  "0": "SUN",
  "1": "MON",
  "2": "TUE",
  "3": "WED",
  "4": "THU",
  "5": "FRI",
  "6": "SAT",
  "7": "SUN",
}

export function defaultRecurrence(): Recurrence {
  return { kind: "daily", hour: 8, minute: 0 }
}

export function recurrenceFromTrigger(trigger: Pick<SceneTriggerView, "timerKind" | "cronExpr">): Recurrence {
  if ((trigger.timerKind || "ONCE").toUpperCase() !== "CRON") {
    return { kind: "once" }
  }
  if (!trigger.cronExpr?.trim()) {
    return defaultRecurrence()
  }
  return parseCron(trigger.cronExpr)
}

export function changeRecurrenceKind(prev: Recurrence, kind: Recurrence["kind"]): Recurrence {
  const time = timeOf(prev)
  switch (kind) {
    case "once":
      return { kind: "once" }
    case "daily":
      return { kind: "daily", ...time }
    case "weekdays":
      return { kind: "weekdays", ...time }
    case "weekly":
      return { kind: "weekly", ...time, weekdays: weekdaysOf(prev) }
    case "monthly":
      return { kind: "monthly", ...time, day: dayOf(prev) }
    case "interval":
      return intervalOf(prev)
    case "custom":
      return { kind: "custom", cronExpr: safeCompile(prev) ?? "0 0 8 * * *" }
  }
}

export function compileCron(recurrence: Recurrence): string {
  switch (recurrence.kind) {
    case "once":
      throw new Error("单次定时不使用周期表达式")
    case "daily":
      return `0 ${minuteOf(recurrence.minute)} ${hourOf(recurrence.hour)} * * *`
    case "weekdays":
      return `0 ${minuteOf(recurrence.minute)} ${hourOf(recurrence.hour)} ? * MON-FRI`
    case "weekly": {
      const days = uniqueWeekdays(recurrence.weekdays)
      if (days.length === 0) {
        throw new Error("每周至少选择一天")
      }
      return `0 ${minuteOf(recurrence.minute)} ${hourOf(recurrence.hour)} ? * ${days.join(",")}`
    }
    case "monthly":
      return `0 ${minuteOf(recurrence.minute)} ${hourOf(recurrence.hour)} ${dayOfMonth(recurrence.day)} * ?`
    case "interval":
      if (recurrence.unit === "minute") {
        return `0 */${step(recurrence.every, 1, 59, "分钟")} * * * *`
      }
      return `0 0 */${step(recurrence.every, 1, 23, "小时")} * * *`
    case "custom": {
      const expr = recurrence.cronExpr.trim()
      if (!expr) {
        throw new Error("请填写执行时间")
      }
      if (expr.split(/\s+/).length !== 6) {
        throw new Error("自定义表达式须为 6 段：秒 分 时 日 月 周")
      }
      return expr
    }
  }
}

export function parseCron(expr: string): Recurrence {
  const raw = expr.trim()
  const parts = raw.split(/\s+/)
  if (parts.length !== 6) {
    return { kind: "custom", cronExpr: raw }
  }
  const [second, minute, hour, dayOfMonthField, month, dayOfWeek] = parts
  if (second !== "0" || month !== "*") {
    return { kind: "custom", cronExpr: raw }
  }
  if (isNumber(minute) && isNumber(hour) && dayOfMonthField === "*" && dayOfWeek === "*") {
    return { kind: "daily", hour: Number(hour), minute: Number(minute) }
  }
  const weekdays = parseWeekdays(dayOfWeek)
  if (
    isNumber(minute)
    && isNumber(hour)
    && (dayOfMonthField === "?" || dayOfMonthField === "*")
    && weekdays
  ) {
    if (sameDays(weekdays, ["MON", "TUE", "WED", "THU", "FRI"])) {
      return { kind: "weekdays", hour: Number(hour), minute: Number(minute) }
    }
    return { kind: "weekly", hour: Number(hour), minute: Number(minute), weekdays }
  }
  if (
    isNumber(minute)
    && isNumber(hour)
    && isNumber(dayOfMonthField)
    && (dayOfWeek === "?" || dayOfWeek === "*")
  ) {
    return { kind: "monthly", hour: Number(hour), minute: Number(minute), day: Number(dayOfMonthField) }
  }
  const minuteStep = starStep(minute)
  if (minuteStep && hour === "*" && dayOfMonthField === "*" && dayOfWeek === "*") {
    return { kind: "interval", every: minuteStep, unit: "minute" }
  }
  const hourStep = starStep(hour)
  if (minute === "0" && hourStep && dayOfMonthField === "*" && dayOfWeek === "*") {
    return { kind: "interval", every: hourStep, unit: "hour" }
  }
  return { kind: "custom", cronExpr: raw }
}

export function describeRecurrence(recurrence: Recurrence): string {
  switch (recurrence.kind) {
    case "once":
      return "单次"
    case "daily":
      return `每天 ${formatClock(recurrence.hour, recurrence.minute)}`
    case "weekdays":
      return `每工作日 ${formatClock(recurrence.hour, recurrence.minute)}`
    case "weekly":
      return `${formatWeeklyPrefix(recurrence.weekdays)} ${formatClock(recurrence.hour, recurrence.minute)}`
    case "monthly":
      return `每月 ${recurrence.day} 日 ${formatClock(recurrence.hour, recurrence.minute)}`
    case "interval":
      return recurrence.unit === "minute" ? `每 ${recurrence.every} 分钟` : `每 ${recurrence.every} 小时`
    case "custom":
      return recurrence.cronExpr.trim() ? `自定义 ${recurrence.cronExpr.trim()}` : "自定义定时"
  }
}

export function describeTrigger(trigger: SceneTriggerView | null | undefined): string {
  if (!trigger) {
    return ""
  }
  if ((trigger.mode || "").toUpperCase() === "LISTEN") {
    if (trigger.listenDeviceCode && trigger.listenFunctionId) {
      return `监听 ${trigger.listenDeviceCode} / ${trigger.listenFunctionId}`
    }
    return "监听设备"
  }
  if ((trigger.timerKind || "ONCE").toUpperCase() !== "CRON") {
    return trigger.timerAt ? `单次 ${formatDateTime(trigger.timerAt)}` : "单次定时"
  }
  return describeRecurrence(parseCron(trigger.cronExpr || ""))
}

export function timeInputValue(hour: number, minute: number): string {
  return `${pad(hour)}:${pad(minute)}`
}

export function assertFutureTimerAt(timerAt: string): void {
  const at = Date.parse(timerAt.includes("T") ? timerAt : timerAt.replace(" ", "T"))
  if (Number.isNaN(at)) {
    throw new Error("请选择合法的执行时间")
  }
  if (at <= Date.now()) {
    throw new Error("执行时间须晚于现在")
  }
}

export function parseTimeInput(value: string): { hour: number; minute: number } | null {
  const match = value.match(/^(\d{1,2}):(\d{2})/)
  if (!match) {
    return null
  }
  const hour = Number(match[1])
  const minute = Number(match[2])
  if (hour > 23 || minute > 59) {
    return null
  }
  return { hour, minute }
}

function timeOf(prev: Recurrence): { hour: number; minute: number } {
  if (
    prev.kind === "daily"
    || prev.kind === "weekdays"
    || prev.kind === "weekly"
    || prev.kind === "monthly"
  ) {
    return { hour: prev.hour, minute: prev.minute }
  }
  return { hour: 8, minute: 0 }
}

function weekdaysOf(prev: Recurrence): Weekday[] {
  if (prev.kind === "weekly" && prev.weekdays.length > 0) {
    return uniqueWeekdays(prev.weekdays)
  }
  if (prev.kind === "weekdays") {
    return ["MON", "TUE", "WED", "THU", "FRI"]
  }
  return ["MON"]
}

function dayOf(prev: Recurrence): number {
  return prev.kind === "monthly" ? prev.day : 1
}

function intervalOf(prev: Recurrence): Recurrence {
  if (prev.kind === "interval") {
    return prev
  }
  return { kind: "interval", every: 30, unit: "minute" }
}

function safeCompile(prev: Recurrence): string | undefined {
  if (prev.kind === "once") {
    return undefined
  }
  if (prev.kind === "custom") {
    return prev.cronExpr.trim() || undefined
  }
  try {
    return compileCron(prev)
  } catch {
    return undefined
  }
}

function uniqueWeekdays(days: Weekday[]): Weekday[] {
  return [...new Set(days)].sort((left, right) => WEEKDAY_ORDER[left] - WEEKDAY_ORDER[right])
}

function parseWeekdays(field: string): Weekday[] | null {
  if (!field || field === "*" || field === "?") {
    return null
  }
  const days: Weekday[] = []
  for (const token of field.split(",")) {
    const range = token.split("-")
    if (range.length === 2) {
      const start = weekdayToken(range[0])
      const end = weekdayToken(range[1])
      if (!start || !end) {
        return null
      }
      const startIndex = WEEKDAY_ORDER[start]
      const endIndex = WEEKDAY_ORDER[end]
      if (startIndex > endIndex) {
        return null
      }
      for (const day of WEEKDAYS) {
        if (WEEKDAY_ORDER[day] >= startIndex && WEEKDAY_ORDER[day] <= endIndex) {
          days.push(day)
        }
      }
      continue
    }
    const day = weekdayToken(token)
    if (!day) {
      return null
    }
    days.push(day)
  }
  return days.length > 0 ? uniqueWeekdays(days) : null
}

function weekdayToken(raw: string): Weekday | undefined {
  const token = raw.trim().toUpperCase()
  return WEEKDAY_BY_NAME[token] ?? WEEKDAY_BY_NUMBER[token]
}

function formatWeeklyPrefix(days: Weekday[]): string {
  const unique = uniqueWeekdays(days)
  if (unique.length === 0) {
    return "每周"
  }
  if (sameDays(unique, ["MON", "TUE", "WED", "THU", "FRI"])) {
    return "每工作日"
  }
  if (unique.length === 7) {
    return "每天"
  }
  return `每周${unique.map((day) => WEEKDAY_LABELS[day].slice(1)).join("、")}`
}

function sameDays(left: Weekday[], right: Weekday[]): boolean {
  return left.length === right.length && left.every((day, index) => day === right[index])
}

function formatClock(hour: number, minute: number): string {
  return timeInputValue(hour, minute)
}

function formatDateTime(value: string): string {
  const date = new Date(value.includes("T") ? value : value.replace(" ", "T"))
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString("zh-CN", { hour12: false })
}

function hourOf(hour: number): number {
  if (!Number.isInteger(hour) || hour < 0 || hour > 23) {
    throw new Error("小时须在 0–23 之间")
  }
  return hour
}

function minuteOf(minute: number): number {
  if (!Number.isInteger(minute) || minute < 0 || minute > 59) {
    throw new Error("分钟须在 0–59 之间")
  }
  return minute
}

function dayOfMonth(day: number): number {
  if (!Number.isInteger(day) || day < 1 || day > 31) {
    throw new Error("日期须在 1–31 之间")
  }
  return day
}

function step(value: number, min: number, max: number, label: string): number {
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${label}间隔须在 ${min}–${max} 之间`)
  }
  return value
}

function isNumber(value: string): boolean {
  return /^\d+$/.test(value)
}

function starStep(value: string): number | null {
  const match = value.match(/^\*\/(\d+)$/)
  return match ? Number(match[1]) : null
}

function pad(value: number): string {
  return String(value).padStart(2, "0")
}
