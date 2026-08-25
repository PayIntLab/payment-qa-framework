package io.pqa.framework;

import io.pqa.framework.gateway.MockGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractPaymentQaTest {

    protected static MockGateway GATEWAY;
    protected static ApiClient API;

    @BeforeAll
    static void startGateway() throws Exception {
        GATEWAY = MockGateway.start();
        API = new ApiClient(GATEWAY.baseUrl());
    }

    @AfterAll
    static void stopGateway() {
        GATEWAY.stop();
    }

    protected static String orderId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static Map<String, Object> charge(String card, long amount, String currency, String orderId) {
        return charge(card, amount, currency, orderId, true);
    }

    protected static Map<String, Object> charge(String card, long amount, String currency, String orderId, boolean capture) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("card", card);
        body.put("amount_cents", amount);
        body.put("currency", currency);
        body.put("capture", capture);
        body.put("metadata", Map.of("order_id", orderId));
        return body;
    }
}
