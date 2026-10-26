package com.smartcheckout.risk.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of the payment token: equal tokens give equal fingerprints, but the token cannot be read back.
 */
final class CardFingerprints {

    private CardFingerprints() {
    }

    static String of(String cardToken) {
        if (cardToken == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cardToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
