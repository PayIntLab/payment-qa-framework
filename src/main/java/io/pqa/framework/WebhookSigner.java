package io.pqa.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** HMAC-SHA256 webhook signing / verification (constant-time comparison, raw-body based). */
public final class WebhookSigner {

    public static final String DEFAULT_SECRET = "whsec_test_secret_123";

    public record SignedEvent(String rawBody, String signature) {}

    private static final ObjectMapper CANONICAL =
            new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private WebhookSigner() {}

    public static SignedEvent sign(String eventId, String eventType, Map<String, Object> data, String secret) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", eventId);
        envelope.put("type", eventType);
        envelope.put("data", data);
        String raw;
        try {
            raw = CANONICAL.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new SignedEvent(raw, "sha256=" + hmac(raw, secret));
    }

    public static boolean verify(String rawBody, String signature, String secret) {
        if (signature == null || signature.isBlank() || rawBody == null) {
            return false;
        }
        String prefix = "sha256=";
        if (!signature.startsWith(prefix)) {
            return false;
        }
        String expected = hmac(rawBody, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.substring(prefix.length()).getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
