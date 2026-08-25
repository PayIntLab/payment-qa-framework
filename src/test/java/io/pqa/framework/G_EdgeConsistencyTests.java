package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class G_EdgeConsistencyTests extends AbstractPaymentQaTest {

    private int rawPost(String path, String rawBody) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(GATEWAY.baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rawBody))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void g1_fieldLengthBoundary() {
        Map<String, Object> tooLong = charge("4242424242424242", 1000, "USD", orderId("g1"));
        tooLong.put("metadata", Map.of("order_id", "o".repeat(65)));
        ApiClient.ApiException e1 = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/charges", tooLong));
        assertEquals(400, e1.status);

        Map<String, Object> tooBig = charge("4242424242424242", 100_000_000, "USD", orderId("g1b"));
        ApiClient.ApiException e2 = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/charges", tooBig));
        assertEquals(400, e2.status);
    }

    @Test
    void g2_unknownEventType() {
        WebhookProcessor processor = new WebhookProcessor();
        String eventId = "evt-" + orderId("g2");
        WebhookSigner.SignedEvent event = WebhookSigner.sign(
                eventId, "payment.unknown_future_type", Map.of("order_id", "x"), WebhookSigner.DEFAULT_SECRET);
        assertEquals("ignored", processor.handle(event, eventId, "payment.unknown_future_type", "x"));
        assertEquals(1, processor.ignoredUnknown());
        assertEquals(0, processor.fulfillments());
    }

    @Test
    void g3_outOfOrderWebhook() {
        WebhookProcessor processor = new WebhookProcessor();
        String orderId = orderId("g3");
        WebhookSigner.SignedEvent refund = WebhookSigner.sign(
                "evt-r1", "refund.succeeded", Map.of("order_id", orderId), WebhookSigner.DEFAULT_SECRET);
        assertEquals("out_of_order_pending",
                processor.handle(refund, "evt-r1", "refund.succeeded", orderId));
        assertEquals(1, processor.outOfOrder());
        assertNotEquals("refunded", processor.orderStatus(orderId));

        WebhookSigner.SignedEvent payment = WebhookSigner.sign(
                "evt-p1", "payment.succeeded", Map.of("order_id", orderId), WebhookSigner.DEFAULT_SECRET);
        assertEquals("processed", processor.handle(payment, "evt-p1", "payment.succeeded", orderId));
        assertEquals("paid", processor.orderStatus(orderId));
    }

    @Test
    void g4_concurrentWebhooks() throws Exception {
        WebhookProcessor processor = new WebhookProcessor();
        String eventId = "evt-" + orderId("g4");
        WebhookSigner.SignedEvent event = WebhookSigner.sign(
                eventId, "payment.succeeded", Map.of("order_id", "ord-c"), WebhookSigner.DEFAULT_SECRET);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            ConcurrentHashMap.KeySetView<String, Boolean> results = ConcurrentHashMap.newKeySet();
            Runnable task = () -> {
                try {
                    latch.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                results.add(processor.handle(event, eventId, "payment.succeeded", "ord-c"));
            };
            pool.submit(task);
            pool.submit(task);
            latch.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            assertEquals(2, results.size());
            assertTrue(results.contains("processed"));
            assertTrue(results.contains("duplicate"));
            assertEquals(1, processor.fulfillments());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void g5_malformedAndOversizedPayload() throws Exception {
        assertEquals(400, rawPost("/v1/charges", "{not-json"));
        assertEquals(413, rawPost("/v1/charges", "{\"x\":\"" + "a".repeat(70_000) + "\"}"));
    }

    @Test
    void g6_sandboxProductionConfig() {
        Map<String, Object> config = API.get("/v1/config");
        assertEquals("test", config.get("mode"));
        assertNotNull(config.get("api_version"));
        assertEquals(true, config.get("webhook_secret_configured"));
    }
}
