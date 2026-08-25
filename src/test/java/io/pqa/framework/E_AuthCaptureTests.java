package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class E_AuthCaptureTests extends AbstractPaymentQaTest {

    @Test
    void e1_authorizeOnly() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e1"), false));
        assertEquals("requires_capture", charge.get("status"));
        assertEquals(5000, ((Number) charge.get("authorized_amount")).longValue());
        assertEquals(0, ((Number) charge.get("captured_cents")).longValue());
    }

    @Test
    void e2_fullCapture() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e2"), false));
        Map<String, Object> captured = API.post("/v1/charges/" + charge.get("id") + "/capture", Map.of());
        assertEquals("succeeded", captured.get("status"));
        assertEquals(5000, ((Number) captured.get("captured_cents")).longValue());
    }

    @Test
    void e3_partialCapture() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e3"), false));
        Map<String, Object> first = API.post("/v1/charges/" + charge.get("id") + "/capture",
                Map.of("amount_cents", 2000));
        assertEquals("requires_capture", first.get("status"));
        assertEquals(2000, ((Number) first.get("captured_cents")).longValue());
        assertEquals(3000, ((Number) first.get("amount_capturable")).longValue());

        Map<String, Object> second = API.post("/v1/charges/" + charge.get("id") + "/capture",
                Map.of("amount_cents", 3000));
        assertEquals("succeeded", second.get("status"));
        assertEquals(5000, ((Number) second.get("captured_cents")).longValue());
    }

    @Test
    void e4_captureExceedsAuthorization() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e4"), false));
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/charges/" + charge.get("id") + "/capture", Map.of("amount_cents", 6000)));
        assertEquals(400, e.status);
        Map<String, Object> still = API.get("/v1/charges/" + charge.get("id"));
        assertEquals("requires_capture", still.get("status"));
    }

    @Test
    void e5_doubleCaptureRejected() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e5"), false));
        API.post("/v1/charges/" + charge.get("id") + "/capture", Map.of());
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/charges/" + charge.get("id") + "/capture", Map.of()));
        assertEquals(400, e.status);
    }

    @Test
    void e6_voidAndExpire() {
        Map<String, Object> toVoid = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e6-1"), false));
        Map<String, Object> voided = API.post("/v1/charges/" + toVoid.get("id") + "/void", Map.of());
        assertEquals("canceled", voided.get("status"));
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/charges/" + toVoid.get("id") + "/capture", Map.of()));
        assertEquals(400, e.status);

        Map<String, Object> toExpire = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("e6-2"), false));
        Map<String, Object> expired = API.post("/v1/charges/" + toExpire.get("id") + "/expire", Map.of());
        assertEquals("expired", expired.get("status"));
    }

    @Test
    void e7_3dsWithManualCapture() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4000002500003155", 5000, "USD", orderId("e7"), false));
        assertEquals("requires_3ds", charge.get("status"));
        Map<String, Object> confirmed = API.post("/v1/charges/" + charge.get("id") + "/confirm",
                Map.of("approve", true));
        assertEquals("requires_capture", confirmed.get("status"));
        Map<String, Object> captured = API.post("/v1/charges/" + charge.get("id") + "/capture", Map.of());
        assertEquals("succeeded", captured.get("status"));
    }
}
