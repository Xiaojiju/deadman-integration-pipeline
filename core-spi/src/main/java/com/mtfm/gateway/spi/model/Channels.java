package com.mtfm.gateway.spi.model;

/**
 * 逻辑通道名常量。Correlate 只写 {@code channelHint}，不写 Publisher 类名。
 */
public final class Channels {

    /** 默认北向云通道。 */
    public static final String CLOUD = "CLOUD";

    private Channels() {
    }
}
