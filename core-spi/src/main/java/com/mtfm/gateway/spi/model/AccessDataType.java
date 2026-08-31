package com.mtfm.gateway.spi.model;

/**
 * 访问数据类型。在旧网关 INT16…BOOLEAN 上增加 OBJECT、ARRAY，供 {@link com.mtfm.gateway.spi.option.Option} 使用。
 */
public enum AccessDataType {
    /** 16 位整数。 */
    INT16,
    /** 32 位整数。 */
    INT32,
    /** 64 位整数。 */
    INT64,
    /** 32 位浮点。 */
    FLOAT32,
    /** 64 位浮点。 */
    FLOAT64,
    /** 字符串。 */
    CHAR16,
    /** 布尔值。 */
    BOOLEAN,
    /** 对象节点。 */
    OBJECT,
    /** 数组节点。 */
    ARRAY
}
