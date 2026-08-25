package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class B_FailureRetryTests extends AbstractPaymentQaTest {

    @Test
    void b6_cardDeclinedThenRetry() {
        Map<String, Object> declined = API.post("/v1/charges",
                charge("4000000000000002", 1990, "USD", orderId("b6-1")));
        assertEquals("failed", declined.get("status"));
        assertEquals("card_declined", declined.get("failure_code"));
        Map<String, Object> retry = API.post("/v1/charges",
                charge("4242424242424242", 1990, "USD", orderId("b6-2")));
        assertEquals("succeeded", retry.get("status"));
    }

    @Test
    void b7_insufficientFunds() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4000000000009995", 1990, "USD", orderId("b7")));
        assertEquals("failed", charge.get("status"));
        assertEquals("insufficient_funds", charge.get("failure_code"));
    }

    @Test
    void b8_3dsApproveAndReject() {
        Map<String, Object> approve = API.post("/v1/charges",
                charge("4000002500003155", 1990, "USD", orderId("b8-1")));
        assertEquals("requires_3ds", approve.get("status"));
        Map<String, Object> confirmed = API.post("/v1/charges/" + approve.get("id") + "/confirm",
                Map.of("approve", true));
        assertEquals("succeeded", confirmed.get("status"));

        Map<String, Object> reject = API.post("/v1/charges",
                charge("4000002500003155", 1990, "USD", orderId("b8-2")));
        Map<String, Object> rejected = API.post("/v1/charges/" + reject.get("id") + "/confirm",
                Map.of("approve", false));
        assertEquals("failed", rejected.get("status"));
        assertEquals("3ds_authentication_failed", rejected.get("failure_code"));
    }

    @Test
    void b9_doubleSubmitIdempotencyKey() {
        String key = "idem-" + orderId("b9");
        Map<String, Object> body = new java.util.LinkedHashMap<>(
                charge("4242424242424242", 1990, "USD", orderId("b9")));
        body.put("idempotency_key", key);
        Map<String, Object> first = API.post("/v1/charges", body);
        Map<String, Object> replay = API.post("/v1/charges", body);
        assertEquals(first.get("id"), replay.get("id"));
    }

    @Test
    void b10_zeroAmountFreeOrder() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 0, "USD", orderId("b10")));
        assertEquals("succeeded", charge.get("status"));
        assertEquals(0, ((Number) charge.get("amount_cents")).longValue());
    }
}
