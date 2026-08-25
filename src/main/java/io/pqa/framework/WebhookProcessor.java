package io.pqa.framework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified "merchant logic" under test: event routing, idempotency,
 * order state machine, and out-of-order protection.
 */
public final class WebhookProcessor {

    private final IdempotencyStore store = new IdempotencyStore();
    private final Map<String, String> orderStatus = new ConcurrentHashMap<>();
    private final AtomicInteger fulfillments = new AtomicInteger();
    private final AtomicInteger ignoredUnknown = new AtomicInteger();
    private final AtomicInteger outOfOrder = new AtomicInteger();

    public String handle(WebhookSigner.SignedEvent event, String eventId, String eventType, String orderId) {
        synchronized (this) {
            if (!store.tryAcquire(eventId)) {
                return "duplicate";
            }
            switch (eventType) {
                case "payment.succeeded":
                    orderStatus.put(orderId, "paid");
                    fulfillments.incrementAndGet();
                    return "processed";
                case "refund.succeeded":
                    if (!"paid".equals(orderStatus.get(orderId))) {
                        outOfOrder.incrementAndGet();
                        return "out_of_order_pending";
                    }
                    orderStatus.put(orderId, "refunded");
                    return "processed";
                default:
                    ignoredUnknown.incrementAndGet();
                    return "ignored";
            }
        }
    }

    public String orderStatus(String orderId) {
        return orderStatus.get(orderId);
    }

    public int fulfillments() {
        return fulfillments.get();
    }

    public int ignoredUnknown() {
        return ignoredUnknown.get();
    }

    public int outOfOrder() {
        return outOfOrder.get();
    }
}
