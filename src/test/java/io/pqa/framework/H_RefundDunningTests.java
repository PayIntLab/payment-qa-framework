package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H_RefundDunningTests extends AbstractPaymentQaTest {

    @Test
    void h1_partialRefundAndRace() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("h1")));
        Map<String, Object> first = API.post("/v1/refunds",
                Map.of("charge_id", charge.get("id"), "amount_cents", 2000));
        assertEquals("refunded", first.get("status"));
        assertEquals(2000, ((Number) first.get("amount_cents")).longValue());
        Map<String, Object> afterFirst = API.get("/v1/charges/" + charge.get("id"));
        assertEquals("succeeded", afterFirst.get("status"));
        assertEquals(2000, ((Number) afterFirst.get("total_refunded")).longValue());

        API.post("/v1/refunds", Map.of("charge_id", charge.get("id"), "amount_cents", 3000));
        assertEquals("refunded", API.get("/v1/charges/" + charge.get("id")).get("status"));

        Map<String, Object> auth = API.post("/v1/charges",
                charge("4242424242424242", 5000, "USD", orderId("h1-race"), false));
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/refunds", Map.of("charge_id", auth.get("id"), "amount_cents", 1000)));
        assertEquals(400, e.status);
    }

    @Test
    void h3_subscriptionDunning() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("card", "4000000000000002");
        body.put("plan", "starter");
        body.put("trial_days", 0);
        Map<String, Object> created = API.post("/v1/subscriptions", body);

        Map<String, Object> fail1 = API.post("/v1/subscriptions/" + created.get("id") + "/renew", Map.of());
        assertEquals("past_due", fail1.get("status"));
        assertEquals(1L, ((Number) fail1.get("retry_count")).longValue());

        Map<String, Object> fail2 = API.post("/v1/subscriptions/" + created.get("id") + "/renew", Map.of());
        assertEquals(2L, ((Number) fail2.get("retry_count")).longValue());

        API.post("/v1/subscriptions/" + created.get("id") + "/card", Map.of("card", "4242424242424242"));
        Map<String, Object> recovered = API.post("/v1/subscriptions/" + created.get("id") + "/renew", Map.of());
        assertEquals(true, recovered.get("success"));
        assertEquals("active", recovered.get("status"));
    }
}
