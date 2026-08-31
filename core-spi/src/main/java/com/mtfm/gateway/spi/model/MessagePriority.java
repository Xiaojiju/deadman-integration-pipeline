package com.mtfm.gateway.spi.model;

/**
 * 出站队列优先级。HIGH 为命令回执，NORMAL 为遥测。
 */
public enum MessagePriority {
    /** 命令回执，优先投递。 */
    HIGH,
    /** 遥测/普通消息。 */
    NORMAL
}
