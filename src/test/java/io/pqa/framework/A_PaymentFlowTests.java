package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A_PaymentFlowTests extends AbstractPaymentQaTest {

    @Test
    void a1_firstPaymentSucceeds() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 1990, "USD", orderId("a1")));
        assertEquals("succeeded", charge.get("status"));
        assertEquals(1990, ((Number) charge.get("amount_cents")).longValue());
        assertTrue(((Number) charge.get("fee_cents")).longValue() > 0);
        assertNotNull(charge.get("id"));
    }

    @Test
    void a2_loggedInUserBuysAgain() {
        Map<String, Object> first = API.post("/v1/charges",
                charge("4242424242424242", 990, "USD", orderId("a2-1")));
        Map<String, Object> second = API.post("/v1/charges",
                charge("4242424242424242", 990, "USD", orderId("a2-2")));
        assertEquals("succeeded", first.get("status"));
        assertEquals("succeeded", second.get("status"));
        assertNotEquals(first.get("id"), second.get("id"));
    }

    @Test
    void a3_abandonPaymentHasNoCharge() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4000002500003155", 1990, "USD", orderId("a3")));
        assertEquals("requires_3ds", charge.get("status"));
        Map<String, Object> rejected = API.post("/v1/charges/" + charge.get("id") + "/confirm",
                Map.of("approve", false));
        assertEquals("failed", rejected.get("status"));
        assertEquals("3ds_authentication_failed", rejected.get("failure_code"));
    }

    @Test
    void a4_multiCurrency() {
        Map<String, Object> usd = API.post("/v1/charges",
                charge("4242424242424242", 1990, "USD", orderId("a4-usd")));
        Map<String, Object> eur = API.post("/v1/charges",
                charge("4242424242424242", 1990, "EUR", orderId("a4-eur")));
        assertEquals("USD", usd.get("currency"));
        assertEquals("EUR", eur.get("currency"));
        assertNotEquals(usd.get("id"), eur.get("id"));
    }

    @Test
    void a5_couponDiscount() {
        Map<String, Object> full = API.post("/v1/charges",
                charge("4242424242424242", 1990, "USD", orderId("a5-full")));
        Map<String, Object> body = new LinkedHashMap<>(
                charge("4242424242424242", 1990, "USD", orderId("a5-coupon")));
        body.put("coupon_code", "PROMO10");
        Map<String, Object> discounted = API.post("/v1/charges", body);
        assertEquals(1990, ((Number) full.get("amount_cents")).longValue());
        assertEquals(1791, ((Number) discounted.get("amount_cents")).longValue());
    }
}
