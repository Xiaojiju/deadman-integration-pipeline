package com.mtfm.gateway.spi.secret;

/**
 * 连接机密加解密钩子。本模块不内置算法。
 *
 * <p>默认恒等，明文落库。嵌入系统提供 Spring {@code SecretCodec} Bean 即可接管
 * {@link #seal} / {@link #open}。{@link #open} 必须对无法识别的字符串原样返回，
 * 以便运行时对全部 connection 字段调用而不误伤 host 等非密文。
 *
 * <pre>{@code
 * &#64;Bean
 * SecretCodec secretCodec() {
 *     return new AesSecretCodec(key);
 * }
 * }</pre>
 */
public interface SecretCodec {

    /**
     * 写入前处理明文。已是本实现密文时应幂等返回。
     */
    default String seal(String plaintext) {
        return plaintext;
    }

    /**
     * 运行时还原明文。非本实现密文必须原样返回。
     */
    default String open(String stored) {
        return stored;
    }

    static SecretCodec identity() {
        return new SecretCodec() {
        };
    }
}
