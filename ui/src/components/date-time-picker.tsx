"use client"

import * as React from "react"
import { ChevronDownIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

type Props = {
  id?: string
  value: string
  onChange: (iso: string) => void
  disabled?: boolean
}

function parseDate(raw: string): Date | undefined {
  if (!raw) {
    return undefined
  }
  const normalized = raw.includes("T") ? raw : raw.replace(" ", "T")
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return undefined
  }
  return date
}

function toTimeValue(date?: Date): string {
  if (!date) {
    return ""
  }
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function emit(date: Date): string {
  return date.toISOString()
}

function applyTime(base: Date, time: string): Date {
  const next = new Date(base)
  const [hours, minutes, seconds] = time.split(":").map((part) => Number(part))
  next.setHours(hours || 0, minutes || 0, seconds || 0, 0)
  return next
}

/** shadcn Date Picker 的 Time Picker：日历选日期 + `Input type="time"` 选时刻。 */
export function DateTimePicker({ id = "datetime", value, onChange, disabled }: Props) {
  const [open, setOpen] = React.useState(false)
  const date = parseDate(value)

  return (
    <div className="flex flex-wrap gap-4">
      <Field className="w-auto min-w-40">
        <FieldLabel htmlFor={`${id}-date`}>日期</FieldLabel>
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              id={`${id}-date`}
              disabled={disabled}
              className="w-40 justify-between font-normal"
            >
              {date ? date.toLocaleDateString("zh-CN") : "选择日期"}
              <ChevronDownIcon data-icon="inline-end" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto overflow-hidden p-0" align="start">
            <Calendar
              mode="single"
              selected={date}
              captionLayout="dropdown"
              onSelect={(next) => {
                if (!next) {
                  return
                }
                const merged = date
                  ? new Date(
                      next.getFullYear(),
                      next.getMonth(),
                      next.getDate(),
                      date.getHours(),
                      date.getMinutes(),
                      date.getSeconds()
                    )
                  : next
                onChange(emit(merged))
                setOpen(false)
              }}
            />
          </PopoverContent>
        </Popover>
      </Field>
      <Field className="w-auto min-w-36">
        <FieldLabel htmlFor={`${id}-time`}>时间</FieldLabel>
        <Input
          type="time"
          id={`${id}-time`}
          step="1"
          disabled={disabled}
          value={toTimeValue(date)}
          onChange={(event) => {
            const time = event.target.value
            if (!time) {
              return
            }
            onChange(emit(applyTime(date ?? new Date(), time)))
          }}
          className="bg-background appearance-none [&::-webkit-calendar-picker-indicator]:hidden [&::-webkit-calendar-picker-indicator]:appearance-none"
        />
      </Field>
    </div>
  )
}
