import { ChevronDownIcon, ChevronUpIcon } from "lucide-react"

import { Button } from "@/components/ui/button"

type Props = {
  canMoveUp: boolean
  canMoveDown: boolean
  onMoveUp: () => void
  onMoveDown: () => void
}

/** 字段卡片上移 / 下移。 */
export function ReorderControls({ canMoveUp, canMoveDown, onMoveUp, onMoveDown }: Props) {
  return (
    <div className="flex items-center">
      <Button
        type="button"
        size="icon"
        variant="ghost"
        className="size-7"
        disabled={!canMoveUp}
        aria-label="上移"
        onClick={onMoveUp}
      >
        <ChevronUpIcon />
      </Button>
      <Button
        type="button"
        size="icon"
        variant="ghost"
        className="size-7"
        disabled={!canMoveDown}
        aria-label="下移"
        onClick={onMoveDown}
      >
        <ChevronDownIcon />
      </Button>
    </div>
  )
}
