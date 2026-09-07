import type { FunctionCatalogMode } from "@/lib/types"

export function catalogModeOf(mode?: FunctionCatalogMode): FunctionCatalogMode {
  return mode === "FIXED" || mode === "CONTRACT" ? mode : "OPEN"
}

export function catalogModeLabel(mode?: FunctionCatalogMode): string {
  const resolved = catalogModeOf(mode)
  if (resolved === "FIXED") {
    return "FIXED · 预置功能"
  }
  if (resolved === "CONTRACT") {
    return "CONTRACT · 契约参数"
  }
  return "OPEN · 自定义字段"
}

export function functionDialogHint(
  dialogMode: "create" | "edit",
  catalogMode: FunctionCatalogMode,
  structureLocked: boolean
): string {
  if (catalogMode === "FIXED") {
    return dialogMode === "edit"
      ? "FIXED：字段名与取值由模板锁死，仅可改说明与排序。"
      : "FIXED：从预置模板选择功能，不能自定义字段结构。"
  }
  if (catalogMode === "CONTRACT") {
    return dialogMode === "edit"
      ? "CONTRACT：参数名由契约锁死，只配来源、常量与映射。"
      : "CONTRACT：自定义业务 functionId（如 light.switch），参数名由能力契约锁死。"
  }
  if (structureLocked) {
    return "OPEN 预置无参功能：结构锁定，仅可维护说明、排序与 Topic。"
  }
  return dialogMode === "edit"
    ? "OPEN：可配置读写字段、来源与载荷编码。"
    : "OPEN：自定义 functionId，并配置协议字段与调用方式。"
}
