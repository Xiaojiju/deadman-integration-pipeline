package com.mtfm.gateway.spi.catalog;

import com.mtfm.gateway.spi.model.FunctionDef;

import java.util.Optional;

/**
 * 功能目录端口。实现由 catalog 模块提供；核心只按 ID 查询。
 *
 * <p>使用示例：
 * <pre>{@code
 * Optional<FunctionDef> def = functionCatalog.find("dev-001", "fn.read");
 * def.ifPresent(fn -> validatePermission(fn.accessPermission()));
 * }</pre>
 */
public interface FunctionCatalog {

    /**
     * 按设备与功能 ID 查找功能定义。
     *
     * @param deviceId   设备 ID
     * @param functionId 功能 ID
     * @return 功能定义，不存在则空
     */
    Optional<FunctionDef> find(String deviceId, String functionId);

    /** 功能是否为 WRITE；缺目录项视为否。 */
    default boolean isWrite(String deviceId, String functionId) {
        return find(deviceId, functionId)
                .map(FunctionDef::accessType)
                .filter(type -> "WRITE".equalsIgnoreCase(type))
                .isPresent();
    }
}
