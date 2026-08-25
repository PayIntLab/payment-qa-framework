package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C_SubscriptionTests extends AbstractPaymentQaTest {

    private Map<String, Object> createSub(String card, long trialDays) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("card", card);
        body.put("plan", "starter");
        body.put("trial_days", trialDays);
        return API.post("/v1/subscriptions", body);
    }

    @Test
    void c11_createSubscription() {
        Map<String, Object> sub = createSub("4242424242424242", 0);
        assertEquals("active", sub.get("status"));
        assertTrue(Instant.parse((String) sub.get("period_end")).isAfter(Instant.now()));
    }

    @Test
    void c12_renewSuccessAndFailure() {
        Map<String, Object> good = createSub("4242424242424242", 0);
        Map<String, Object> renewOk = API.post("/v1/subscriptions/" + good.get("id") + "/renew", Map.of());
        assertEquals(true, renewOk.get("success"));
        assertEquals("active", renewOk.get("status"));

        Map<String, Object> bad = createSub("4000000000000002", 0);
        Map<String, Object> renewFail = API.post("/v1/subscriptions/" + bad.get("id") + "/renew", Map.of());
        assertEquals(false, renewFail.get("success"));
        assertEquals("past_due", renewFail.get("status"));
    }

    @Test
    void c13_cancelSubscription() {
        Map<String, Object> sub = createSub("4242424242424242", 0);
        Map<String, Object> canceled = API.post("/v1/subscriptions/" + sub.get("id") + "/cancel", Map.of());
        assertEquals(true, canceled.get("cancel_at_period_end"));
    }

    @Test
    void c14_planChangeProrated() {
        Map<String, Object> sub = createSub("4242424242424242", 0);
        Map<String, Object> changed = API.post("/v1/subscriptions/" + sub.get("id") + "/plan",
                Map.of("plan", "pro", "prorated_cents", 500));
        assertEquals("pro", changed.get("plan"));
        assertEquals(500, ((Number) changed.get("prorated_charge")).longValue());
    }

    @Test
    void c15_trialConvertToPaid() {
        Map<String, Object> sub = createSub("4242424242424242", 7);
        assertEquals("trialing", sub.get("status"));
        Map<String, Object> converted = API.post("/v1/subscriptions/" + sub.get("id") + "/convert", Map.of());
        assertEquals("active", converted.get("status"));
    }
}
