/**
 * 网关 SPI 模块：定义流水线与宿主之间的稳定契约。
 *
 * <p>本模块<strong>零协议 SDK、零 JDBC</strong>，仅包含接口与不可变模型；
 * 禁止出现 {@code com.mtfm.pipeline} 包名。运行时实现在 {@code core-runtime}，
 * 目录持久化在 {@code catalog}，南向/北向具体协议由宿主插件提供。
 *
 * <h2>包结构</h2>
 * <ul>
 *   <li>{@link com.mtfm.gateway.spi.model} — 信封、出站体、失败、schema 等不可变模型</li>
 *   <li>{@link com.mtfm.gateway.spi.plugin} — 入站/出站插件扩展点</li>
 *   <li>{@link com.mtfm.gateway.spi.capability} — Driver、Executor、Publisher 能力接口</li>
 *   <li>{@link com.mtfm.gateway.spi.port} — 流水线端口与注册表</li>
 *   <li>{@link com.mtfm.gateway.spi.catalog} — 功能与设备绑定目录端口</li>
 *   <li>{@link com.mtfm.gateway.spi.option} — 功能参数 Option 树</li>
 *   <li>{@link com.mtfm.gateway.spi.exception} — 解码与装配异常</li>
 *   <li>{@link com.mtfm.gateway.spi.metrics} — 指标回调端口</li>
 * </ul>
 */
package com.mtfm.gateway.spi;
