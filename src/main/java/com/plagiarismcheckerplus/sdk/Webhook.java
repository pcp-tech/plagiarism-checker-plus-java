package com.plagiarismcheckerplus.sdk;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies webhook deliveries. Every delivery carries
 * {@code PCP-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<raw body>")>}.
 */
public final class Webhook {
    /** Header that carries the signature. */
    public static final String SIGNATURE_HEADER = "PCP-Signature";
    /** How old a signed timestamp may be, in seconds, before it is rejected. */
    public static final long DEFAULT_TOLERANCE_SECONDS = 300;

    private Webhook() { }

    /** Checks the header against the raw body (UTF-8 text, before JSON parsing), now, with a 300 s tolerance. */
    public static boolean verifySignature(String secret, String header, String body) {
        if (body == null) return false;
        return verifySignature(secret, header, body.getBytes(StandardCharsets.UTF_8));
    }

    /** Checks the header against the raw body bytes, now, with a 300 s tolerance. */
    public static boolean verifySignature(String secret, String header, byte[] body) {
        return verifySignature(secret, header, body, DEFAULT_TOLERANCE_SECONDS, System.currentTimeMillis() / 1000);
    }

    /**
     * Checks a PCP-Signature header against the raw body bytes. Returns false for a missing or
     * malformed header, a wrong signature, or a timestamp more than {@code toleranceSeconds} from
     * {@code nowSeconds} (unix seconds). Signatures are compared in constant time.
     */
    public static boolean verifySignature(String secret, String header, byte[] body, long toleranceSeconds, long nowSeconds) {
        if (secret == null || secret.isEmpty() || header == null || header.isEmpty() || body == null) {
            return false;
        }
        Long timestamp = null;
        List<byte[]> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.equals("t") && value.matches("\\d{1,18}")) {
                timestamp = Long.parseLong(value);
            } else if (key.equals("v1")) {
                byte[] sig = decodeHex(value);
                if (sig != null) signatures.add(sig);
            }
        }
        if (timestamp == null || signatures.isEmpty()) return false;
        if (Math.abs(nowSeconds - timestamp) > toleranceSeconds) return false;

        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            expected = mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
        boolean ok = false;
        for (byte[] sig : signatures) {
            ok |= MessageDigest.isEqual(sig, expected);
        }
        return ok;
    }

    private static byte[] decodeHex(String value) {
        if (value.isEmpty() || value.length() % 2 != 0) return null;
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(value.charAt(2 * i), 16);
            int lo = Character.digit(value.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
