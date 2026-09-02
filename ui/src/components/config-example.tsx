import type { ReactNode } from "react"

import { cn } from "@/lib/utils"

type Props = {
  title: string
  className?: string
  children: ReactNode
}

/** 表单内的配置示例，帮助理解字段怎么填、调用方怎么传。 */
export function ConfigExample({ title, className, children }: Props) {
  return (
    <div
      className={cn(
        "rounded-md border border-dashed bg-muted/30 px-3 py-2 text-xs leading-relaxed text-muted-foreground",
        className
      )}
    >
      <p className="mb-1 font-medium text-foreground/85">{title}</p>
      <div className="space-y-1">{children}</div>
    </div>
  )
}

export function CodeSample({ children }: { children: string }) {
  return (
    <code className="rounded bg-background px-1 py-0.5 font-mono text-[11px] text-foreground">
      {children}
    </code>
  )
}
