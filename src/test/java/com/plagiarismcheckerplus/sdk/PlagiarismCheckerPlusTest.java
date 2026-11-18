package com.plagiarismcheckerplus.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PlagiarismCheckerPlusTest {
    static final class FakeTransport implements Transport {
        final Deque<Response> responses = new ArrayDeque<>();
        final List<String[]> calls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();

        FakeTransport add(int status, String body, Map<String, String> h) {
            responses.add(new Response(status, h, body));
            return this;
        }

        @Override
        public Response send(String method, String url, Map<String, String> h, String body) {
            calls.add(new String[] {method, url, body});
            headers.add(h);
            return responses.removeFirst();
        }
    }

    private static PlagiarismCheckerPlus client(FakeTransport t) {
        return new PlagiarismCheckerPlus("pcp_test", PlagiarismCheckerPlus.DEFAULT_BASE_URL, t);
    }

    @Test
    void createScanSendsKeyAndBody() {
        FakeTransport t = new FakeTransport().add(202, "{\"id\":\"s1\",\"status\":\"processing\"}", Map.of());
        assertEquals("s1", client(t).createScan("text", List.of("plagiarism"), "draft").get("id"));
        assertEquals("POST", t.calls.get(0)[0]);
        assertEquals("https://app.plagiarismcheckerplus.com/api/public/v1/scans", t.calls.get(0)[1]);
        assertEquals("Bearer pcp_test", t.headers.get(0).get("Authorization"));
        assertEquals("{\"text\":\"text\",\"tools\":[\"plagiarism\"],\"name\":\"draft\"}", t.calls.get(0)[2]);
    }

    @Test
    void passesQueryParameters() {
        FakeTransport t = new FakeTransport().add(200, "{\"id\":\"s1\"}", Map.of()).add(200, "{\"data\":[]}", Map.of());
        PlagiarismCheckerPlus c = client(t);
        c.getScan("s1", true);
        c.listScans(2, 5);
        assertTrue(t.calls.get(0)[1].endsWith("/scans/s1?include_text=true"));
        assertTrue(t.calls.get(1)[1].endsWith("/scans?page=2&limit=5"));
    }

    @Test
    void errorsExposeStatusCodeAndDetails() {
        FakeTransport t = new FakeTransport().add(402,
                "{\"error\":{\"code\":\"trial_exhausted\",\"message\":\"Trial used up\",\"billingUrl\":\"https://app.plagiarismcheckerplus.com/developers/billing\"}}",
                Map.of());
        ApiException e = assertThrows(ApiException.class, () -> client(t).createScan("text", List.of("plagiarism"), null));
        assertEquals(402, e.getStatus());
        assertEquals(ApiException.TRIAL_EXHAUSTED, e.getErrorCode());
        assertEquals("https://app.plagiarismcheckerplus.com/developers/billing", e.getDetails().get("billingUrl"));
        assertEquals("key_cap_reached", ApiException.KEY_CAP_REACHED);
    }

    private static String sign(String secret, long t, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Test
    void verifiesWebhookSignatures() throws Exception {
        String secret = "whsec_test_secret";
        String body = "{\"id\":\"evt_1\",\"type\":\"scan.completed\",\"data\":{}}";
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        long ts = 1_800_000_000L;
        String header = "t=" + ts + ",v1=" + sign(secret, ts, body);
        assertEquals("PCP-Signature", Webhook.SIGNATURE_HEADER);
        assertTrue(Webhook.verifySignature(secret, header, raw, 300, ts + 10));
        assertTrue(Webhook.verifySignature(secret, "t=" + ts + ",v1=" + sign("other", ts, body) + ",v1=" + sign(secret, ts, body), raw, 300, ts));
        assertFalse(Webhook.verifySignature("whsec_wrong", header, raw, 300, ts));
        assertFalse(Webhook.verifySignature(secret, header, (body + " ").getBytes(StandardCharsets.UTF_8), 300, ts));
        assertFalse(Webhook.verifySignature(secret, header, raw, 300, ts + 301));
        assertFalse(Webhook.verifySignature(secret, header, raw, 300, ts - 301));
        assertTrue(Webhook.verifySignature(secret, header, raw, 600, ts + 500));
        assertFalse(Webhook.verifySignature(secret, "t=" + ts, raw, 300, ts));
        assertFalse(Webhook.verifySignature(secret, null, raw, 300, ts));
    }

    @Test
    void waitHonoursRetryAfter() {
        FakeTransport t = new FakeTransport()
                .add(200, "{\"id\":\"s1\",\"status\":\"processing\"}", Map.of())
                .add(429, "{\"error\":{\"code\":\"rate_limited\",\"message\":\"Slow down\"}}", Map.of("Retry-After", "3"))
                .add(200, "{\"id\":\"s1\",\"status\":\"completed\"}", Map.of());
        List<Double> waits = new ArrayList<>();
        Map<String, Object> scan = client(t).withSleeper(waits::add).waitForScan("s1", 300, 1, 10, false);
        assertEquals("completed", scan.get("status"));
        assertEquals(List.of(1.0, 3.0), waits);
    }

    @Test
    void waitTimesOut() {
        FakeTransport t = new FakeTransport().add(200, "{\"id\":\"s1\",\"status\":\"processing\"}", Map.of());
        assertThrows(ScanTimeoutException.class,
                () -> client(t).withSleeper(s -> { }).waitForScan("s1", 0.01, 1, 10, false));
    }
}
