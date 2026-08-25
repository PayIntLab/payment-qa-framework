package io.pqa.framework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory event idempotency store.
 * NOTE: production systems must back this with a durable unique constraint (see README).
 */
public final class IdempotencyStore {

    private final Map<String, Long> processed = new ConcurrentHashMap<>();

    public synchronized boolean tryAcquire(String eventId) {
        if (processed.containsKey(eventId)) {
            return false;
        }
        processed.put(eventId, System.currentTimeMillis());
        return true;
    }

    public int size() {
        return processed.size();
    }
}
