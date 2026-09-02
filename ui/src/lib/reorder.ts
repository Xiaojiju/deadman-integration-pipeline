/** 交换列表中两个下标的元素。 */
export function swapAt<T>(list: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) {
    return list
  }
  const next = [...list]
  const item = next[from]
  next[from] = next[to]
  next[to] = item
  return next
}

/** 将指定下标元素上移或下移一格。 */
export function moveBy<T>(list: T[], index: number, delta: number): T[] {
  return swapAt(list, index, index + delta)
}

/** 点击空白处选中卡片；点到输入/按钮时不触发换位。 */
export function isReorderClick(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  return !target.closest(
    "input, textarea, button, select, a, label, [role='combobox'], [data-slot='select-trigger']"
  )
}
