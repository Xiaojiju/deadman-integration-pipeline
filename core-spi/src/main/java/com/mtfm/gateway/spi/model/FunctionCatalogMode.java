package com.mtfm.gateway.spi.model;

/**
 * 能力功能目录模式：决定产品能否自定义 functionId，以及参数名是否锁死。
 *
 * <ul>
 *   <li>{@link #FIXED} — 预置功能为封闭集（如海康 ISAPI）；产品只能挂载模板中的 functionId。</li>
 *   <li>{@link #CONTRACT} — 可自定义业务 functionId，但参数名锁死为按 accessType 匹配的模板
 *       （如 Modbus 的 area/offset/quantity/dataType/value）。</li>
 *   <li>{@link #OPEN} — 可自定义 functionId 与字段结构（如 MQTT）。</li>
 * </ul>
 */
public enum FunctionCatalogMode {
    FIXED,
    CONTRACT,
    OPEN
}
