package com.mtfm.gateway.spi.model;

/**
 * 能力功能目录模式：决定产品能否自定义 functionId。
 *
 * <ul>
 *   <li>{@link #FIXED} — 预置功能为封闭集（如海康 ISAPI）；产品只能挂载模板中的 functionId，
 *       调用方主要配置通道 connection 与端点 address。</li>
 *   <li>{@link #OPEN} — 模板仅为协议原语/表单提示（如 Modbus 读写作、MQTT publish）；
 *       产品可按业务定义独立 functionId、参数与 protocolMapping。</li>
 * </ul>
 */
public enum FunctionCatalogMode {
    FIXED,
    OPEN
}
