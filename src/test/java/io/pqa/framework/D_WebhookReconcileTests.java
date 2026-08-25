package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class D_WebhookReconcileTests extends AbstractPaymentQaTest {

    @Test
    void d16_webhookIdempotency() {
        WebhookProcessor processor = new WebhookProcessor();
        String eventId = "evt-" + orderId("d16");
        WebhookSigner.SignedEvent event = WebhookSigner.sign(
                eventId, "payment.succeeded", Map.of("order_id", "ord-1"), WebhookSigner.DEFAULT_SECRET);
        assertEquals("processed", processor.handle(event, eventId, "payment.succeeded", "ord-1"));
        assertEquals("duplicate", processor.handle(event, eventId, "payment.succeeded", "ord-1"));
        assertEquals(1, processor.fulfillments());
    }

    @Test
    void d17_signatureVerification() {
        WebhookSigner.SignedEvent event = WebhookSigner.sign(
                "evt-1", "payment.succeeded", Map.of("order_id", "ord-1"), WebhookSigner.DEFAULT_SECRET);
        assertTrue(WebhookSigner.verify(event.rawBody(), event.signature(), WebhookSigner.DEFAULT_SECRET));
        assertFalse(WebhookSigner.verify(event.rawBody() + "x", event.signature(), WebhookSigner.DEFAULT_SECRET));
        assertFalse(WebhookSigner.verify(event.rawBody(), "sha256=deadbeef", WebhookSigner.DEFAULT_SECRET));
        assertFalse(WebhookSigner.verify(event.rawBody(), null, WebhookSigner.DEFAULT_SECRET));
        assertFalse(WebhookSigner.verify(event.rawBody(), "", WebhookSigner.DEFAULT_SECRET));
        assertFalse(WebhookSigner.verify(event.rawBody(), "sha256=", WebhookSigner.DEFAULT_SECRET));
    }

    @Test
    void d18_droppedCallbackReconciliation() {
        String orderId = orderId("d18");
        API.post("/v1/charges", charge("4242424242424242", 1990, "USD", orderId));
        List<Map<String, Object>> statement =
                (List<Map<String, Object>>) API.get("/v1/statement?from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z")
                        .get("charges");
        Reconciler reconciler = new Reconciler();
        List<Reconciler.Issue> issues = reconciler.reconcile(statement, Map.of(orderId, "pending"));
        assertEquals(1, issues.size());
        assertEquals("BACKFILL", issues.get(0).action());
    }

    @Test
    void d19_fullRefund() {
        Map<String, Object> charge = API.post("/v1/charges",
                charge("4242424242424242", 1990, "USD", orderId("d19")));
        Map<String, Object> refund = API.post("/v1/refunds",
                Map.of("charge_id", charge.get("id"), "amount_cents", 1990));
        assertEquals("refunded", refund.get("status"));
        Map<String, Object> updated = API.get("/v1/charges/" + charge.get("id"));
        assertEquals("refunded", updated.get("status"));
        assertEquals(1990, ((Number) updated.get("total_refunded")).longValue());
    }

    @Test
    void d20_statementConsistency() {
        String o1 = orderId("d20-1");
        String o2 = orderId("d20-2");
        String o3 = orderId("d20-3");
        API.post("/v1/charges", charge("4242424242424242", 1000, "USD", o1));
        API.post("/v1/charges", charge("4242424242424242", 2000, "USD", o2));
        API.post("/v1/charges", charge("4242424242424242", 3000, "USD", o3));
        List<Map<String, Object>> statement =
                (List<Map<String, Object>>) API.get("/v1/statement?from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z")
                        .get("charges");
        List<String> mine = List.of(o1, o2, o3);
        long mineCount = statement.stream()
                .filter(c -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> md = (Map<String, Object>) c.get("metadata");
                    return md != null && mine.contains(md.get("order_id"));
                })
                .count();
        assertEquals(3, mineCount);
        Reconciler reconciler = new Reconciler();
        List<Reconciler.Issue> issues = reconciler.reconcile(statement,
                Map.of(o1, "paid", o2, "paid", o3, "paid"));
        assertTrue(issues.isEmpty());
    }
}
