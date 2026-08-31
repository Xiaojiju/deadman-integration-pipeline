package com.mtfm.gateway.spi.model;

/**
 * 南向执行状态。
 */
public enum ExecutionStatus {
    /** 执行成功。 */
    SUCCESS,
    /** 执行失败。 */
    FAILED,
    /** 执行超时。 */
    TIMEOUT,
    /** 业务拒绝（权限/参数等）。 */
    REJECTED
}
