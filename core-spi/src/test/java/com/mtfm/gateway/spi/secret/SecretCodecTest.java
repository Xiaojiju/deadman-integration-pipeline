package com.mtfm.gateway.spi.secret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretCodecTest {

    @Test
    void identityLeavesValuesUnchanged() {
        SecretCodec codec = SecretCodec.identity();
        assertEquals("plain", codec.seal("plain"));
        assertEquals("plain", codec.open(codec.seal("plain")));
    }

    @Test
    void embedderCanPrefixWithoutTouchingOtherStrings() {
        SecretCodec codec = new SecretCodec() {
            @Override
            public String seal(String plaintext) {
                if (plaintext == null || plaintext.startsWith("enc:")) {
                    return plaintext;
                }
                return "enc:" + plaintext;
            }

            @Override
            public String open(String stored) {
                if (stored != null && stored.startsWith("enc:")) {
                    return stored.substring(4);
                }
                return stored;
            }
        };
        assertEquals("enc:secret", codec.seal("secret"));
        assertEquals("secret", codec.open("enc:secret"));
        assertEquals("broker", codec.open("broker"));
    }
}
