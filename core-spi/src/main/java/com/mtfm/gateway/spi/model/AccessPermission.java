package com.mtfm.gateway.spi.model;

/**
 * 读写权限。存储时用合并后的二进制码，可通过 {@link #contains(int, AccessPermission)} 判断。
 */
public enum AccessPermission {
    /** 读权限，码值 1。 */
    READ(1),
    /** 写权限，码值 2。 */
    WRITE(2);

    private final int code;

    AccessPermission(int code) {
        this.code = code;
    }

    /** 返回权限码值。 */
    public int code() {
        return code;
    }

    /** 判断合并权限码是否包含指定权限。 */
    public static boolean contains(int accessCode, AccessPermission permission) {
        return (accessCode & permission.code) == permission.code;
    }
}
