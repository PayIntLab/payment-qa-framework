package io.pqa.framework.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Built-in mock PSP (Stripe-style) used by all scenario tests.
 * State machine: requires_3ds -> requires_capture -> succeeded | canceled | expired,
 * succeeded -> refunded | disputed -> lost | dispute_won.
 */
public final class MockGateway {

    public static final String DEFAULT_SECRET = "whsec_test_secret_123";
    public static final int MAX_BODY_BYTES = 64 * 1024;
    public static final int MAX_AMOUNT_CENTS = 99_999_999;
    public static final int MAX_ORDER_ID_LENGTH = 64;

    private static final Map<String, String> CARDS = Map.of(
            "4242424242424242", "success",
            "4000000000000002", "declined",
            "4000000000009995", "insufficient",
            "4000000000000069", "expired",
            "4000000000000127", "cvc_fail",
            "4000002500003155", "3ds");

    private final Map<String, Map<String, Object>> charges = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> refunds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> disputes = new ConcurrentHashMap<>();
    private final Map<String, Object> idempotency = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private MockGateway(HttpServer server) {
        this.server = server;
        server.createContext("/v1/", this::handle);
        server.setExecutor(executor);
    }

    public static MockGateway start() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        MockGateway gateway = new MockGateway(server);
        server.start();
        return gateway;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) {
        try {
            route(exchange);
        } catch (BadRequest e) {
            respond(exchange, e.status, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, Map.of("error", "internal_error"));
        }
    }

    private void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        if ("GET".equals(method) && path.equals("/v1/health")) {
            respond(exchange, 200, Map.of("status", "ok"));
            return;
        }
        if ("GET".equals(method) && path.equals("/v1/config")) {
            respond(exchange, 200, Map.of(
                    "mode", "test",
                    "api_version", "2026-08-01",
                    "webhook_secret_configured", true,
                    "test_cards_enabled", true));
            return;
        }
        if ("GET".equals(method) && path.equals("/v1/statement")) {
            handleStatement(exchange, query);
            return;
        }

        String[] seg = path.split("/");
        // /v1/charges
        if (seg.length == 3 && seg[2].equals("charges") && "POST".equals(method)) {
            handleCreateCharge(exchange);
            return;
        }
        // /v1/charges/{id}/{action}
        if (seg.length == 5 && seg[2].equals("charges") && "POST".equals(method)) {
            handleChargeAction(exchange, seg[3], seg[4]);
            return;
        }
        // /v1/charges/{id}
        if (seg.length == 4 && seg[2].equals("charges") && "GET".equals(method)) {
            Map<String, Object> charge = charges.get(seg[3]);
            if (charge == null) {
                throw new BadRequest(404, "charge_not_found");
            }
            respond(exchange, 200, charge);
            return;
        }
        // /v1/refunds
        if (seg.length == 3 && seg[2].equals("refunds") && "POST".equals(method)) {
            handleRefund(exchange);
            return;
        }
        // /v1/subscriptions
        if (seg.length == 3 && seg[2].equals("subscriptions") && "POST".equals(method)) {
            handleCreateSubscription(exchange);
            return;
        }
        // /v1/subscriptions/{id}/{action}
        if (seg.length == 5 && seg[2].equals("subscriptions") && "POST".equals(method)) {
            handleSubscriptionAction(exchange, seg[3], seg[4]);
            return;
        }
        // /v1/disputes
        if (seg.length == 3 && seg[2].equals("disputes") && "POST".equals(method)) {
            handleCreateDispute(exchange);
            return;
        }
        // /v1/disputes/{id}
        if (seg.length == 4 && seg[2].equals("disputes") && "GET".equals(method)) {
            handleGetDispute(exchange, seg[3]);
            return;
        }
        // /v1/disputes/{id}/{action}
        if (seg.length == 5 && seg[2].equals("disputes") && "POST".equals(method)) {
            handleDisputeAction(exchange, seg[3], seg[4]);
            return;
        }
        respond(exchange, 404, Map.of("error", "not_found"));
    }

    private void handleCreateCharge(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        long amount = num(body.get("amount_cents"), 0);
        if (amount < 0 || amount > MAX_AMOUNT_CENTS) {
            throw new BadRequest(400, "invalid_amount");
        }
        String orderId = metadataOrderId(body);
        if (orderId != null && orderId.length() > MAX_ORDER_ID_LENGTH) {
            throw new BadRequest(400, "order_id_too_long");
        }
        String key = str(body.get("idempotency_key"));
        if (key != null && !key.isBlank()) {
            Map<String, Object> cached = idempotent(key);
            if (cached != null) {
                respond(exchange, 200, cached);
                return;
            }
        }

        String card = str(body.getOrDefault("card", ""));
        String behavior = CARDS.getOrDefault(card, "declined");
        String currency = str(body.getOrDefault("currency", "USD"));
        boolean manualCapture = Boolean.TRUE.equals(body.get("capture")) == false;
        String coupon = str(body.get("coupon_code"));
        if ("PROMO10".equals(coupon)) {
            amount = amount * 9 / 10;
        }

        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("id", nextId("ch"));
        charge.put("status", "processing");
        charge.put("amount_cents", amount);
        charge.put("currency", currency);
        charge.put("capture_mode", manualCapture ? "manual" : "automatic");
        charge.put("created", Instant.now().toString());
        charge.put("created_ts", Instant.now().toEpochMilli());
        charge.put("metadata", body.get("metadata") == null ? Map.of() : body.get("metadata"));

        switch (behavior) {
            case "declined", "expired", "cvc_fail" -> {
                charge.put("status", "failed");
                charge.put("failure_code", "card_declined");
            }
            case "insufficient" -> {
                charge.put("status", "failed");
                charge.put("failure_code", "insufficient_funds");
            }
            case "3ds" -> charge.put("status", "requires_3ds");
            default -> applySettlement(charge, manualCapture);
        }

        charges.put((String) charge.get("id"), charge);
        if (key != null && !key.isBlank()) {
            idempotency.put(key, charge);
        }
        respond(exchange, 200, charge);
    }

    private void applySettlement(Map<String, Object> charge, boolean manualCapture) {
        long amount = num(charge.get("amount_cents"), 0);
        if (manualCapture) {
            charge.put("status", "requires_capture");
            charge.put("authorized_amount", amount);
            charge.put("captured_cents", 0L);
            charge.put("amount_capturable", amount);
        } else {
            charge.put("status", "succeeded");
            charge.put("captured_cents", amount);
            charge.put("fee_cents", Math.round(amount * 0.029) + 30);
            charge.put("net_cents", amount - (Math.round(amount * 0.029) + 30));
        }
    }

    private void handleChargeAction(HttpExchange exchange, String id, String action) throws Exception {
        Map<String, Object> charge = charges.get(id);
        if (charge == null) {
            throw new BadRequest(404, "charge_not_found");
        }
        switch (action) {
            case "confirm" -> {
                if (!"requires_3ds".equals(charge.get("status"))) {
                    throw new BadRequest(409, "charge_not_in_3ds");
                }
                boolean approve = Boolean.TRUE.equals(bodyOf(exchange).get("approve"));
                if (approve) {
                    if ("manual".equals(charge.get("capture_mode"))) {
                        long amount = num(charge.get("amount_cents"), 0);
                        charge.put("status", "requires_capture");
                        charge.put("authorized_amount", amount);
                        charge.put("captured_cents", 0L);
                        charge.put("amount_capturable", amount);
                    } else {
                        long amount = num(charge.get("amount_cents"), 0);
                        charge.put("status", "succeeded");
                        charge.put("captured_cents", amount);
                        charge.put("fee_cents", Math.round(amount * 0.029) + 30);
                        charge.put("net_cents", amount - (Math.round(amount * 0.029) + 30));
                    }
                } else {
                    charge.put("status", "failed");
                    charge.put("failure_code", "3ds_authentication_failed");
                }
                respond(exchange, 200, charge);
            }
            case "capture" -> {
                if (!"requires_capture".equals(charge.get("status"))) {
                    throw new BadRequest(400, "charge_not_authorized");
                }
                long authorized = num(charge.get("authorized_amount"), 0);
                long captured = num(charge.get("captured_cents"), 0);
                long remaining = authorized - captured;
                Map<String, Object> req = bodyOf(exchange);
                long requested = req.get("amount_cents") == null ? remaining : num(req.get("amount_cents"), 0);
                if (requested <= 0 || requested > remaining) {
                    throw new BadRequest(400, "capture_exceeds_authorization");
                }
                captured += requested;
                charge.put("captured_cents", captured);
                charge.put("amount_capturable", authorized - captured);
                if (captured == authorized) {
                    charge.put("status", "succeeded");
                }
                long fee = Math.round(captured * 0.029) + 30;
                charge.put("fee_cents", fee);
                charge.put("net_cents", captured - fee);
                respond(exchange, 200, charge);
            }
            case "void" -> {
                if (!"requires_capture".equals(charge.get("status"))) {
                    throw new BadRequest(400, "charge_not_authorized");
                }
                charge.put("status", "canceled");
                respond(exchange, 200, charge);
            }
            case "expire" -> {
                if (!"requires_capture".equals(charge.get("status"))) {
                    throw new BadRequest(400, "charge_not_authorized");
                }
                charge.put("status", "expired");
                respond(exchange, 200, charge);
            }
            default -> throw new BadRequest(404, "action_not_found");
        }
    }

    private void handleRefund(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        Map<String, Object> charge = charges.get(str(body.get("charge_id")));
        if (charge == null) {
            throw new BadRequest(404, "charge_not_found");
        }
        if (!"succeeded".equals(charge.get("status"))) {
            throw new BadRequest(400, "charge_not_refundable");
        }
        long amount = num(charge.get("amount_cents"), 0);
        long refunded = num(charge.get("total_refunded"), 0);
        long requested = body.get("amount_cents") == null ? amount - refunded : num(body.get("amount_cents"), 0);
        if (requested <= 0 || refunded + requested > amount) {
            throw new BadRequest(400, "refund_exceeds_balance");
        }
        refunded += requested;
        charge.put("total_refunded", refunded);
        if (refunded == amount) {
            charge.put("status", "refunded");
        }
        Map<String, Object> refund = new LinkedHashMap<>();
        refund.put("id", nextId("ref"));
        refund.put("charge_id", charge.get("id"));
        refund.put("amount_cents", requested);
        refund.put("status", "refunded");
        refund.put("created", Instant.now().toString());
        refunds.put((String) refund.get("id"), refund);
        respond(exchange, 200, refund);
    }

    private void handleCreateSubscription(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        long trialDays = num(body.get("trial_days"), 0);
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("id", nextId("sub"));
        sub.put("card", str(body.getOrDefault("card", "4242424242424242")));
        sub.put("plan", str(body.getOrDefault("plan", "starter")));
        sub.put("status", trialDays > 0 ? "trialing" : "active");
        sub.put("trial_days", trialDays);
        sub.put("period_end", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        sub.put("retry_count", 0L);
        sub.put("cancel_at_period_end", false);
        subscriptions.put((String) sub.get("id"), sub);
        respond(exchange, 200, sub);
    }

    private void handleSubscriptionAction(HttpExchange exchange, String id, String action) throws Exception {
        Map<String, Object> sub = subscriptions.get(id);
        if (sub == null) {
            throw new BadRequest(404, "subscription_not_found");
        }
        switch (action) {
            case "renew" -> {
                String behavior = CARDS.getOrDefault(str(sub.get("card")), "declined");
                if ("success".equals(behavior)) {
                    sub.put("period_end", Instant.parse((String) sub.get("period_end")).plus(30, ChronoUnit.DAYS).toString());
                    sub.put("status", "active");
                    sub.put("retry_count", 0L);
                    respond(exchange, 200, Map.of("success", true, "status", "active", "period_end", sub.get("period_end")));
                } else {
                    long retries = num(sub.get("retry_count"), 0) + 1;
                    sub.put("retry_count", retries);
                    sub.put("status", "past_due");
                    respond(exchange, 200, Map.of("success", false, "status", "past_due", "retry_count", retries));
                }
            }
            case "cancel" -> {
                sub.put("cancel_at_period_end", true);
                respond(exchange, 200, sub);
            }
            case "convert" -> {
                if (!"trialing".equals(sub.get("status"))) {
                    throw new BadRequest(409, "subscription_not_trialing");
                }
                sub.put("status", "active");
                respond(exchange, 200, sub);
            }
            case "plan" -> {
                Map<String, Object> req = readBody(exchange);
                sub.put("plan", str(req.getOrDefault("plan", sub.get("plan"))));
                long prorated = num(req.get("prorated_cents"), 0);
                respond(exchange, 200, Map.of("id", sub.get("id"), "plan", sub.get("plan"), "prorated_charge", prorated));
            }
            case "card" -> {
                Map<String, Object> req = readBody(exchange);
                sub.put("card", str(req.getOrDefault("card", sub.get("card"))));
                respond(exchange, 200, sub);
            }
            default -> throw new BadRequest(404, "action_not_found");
        }
    }

    private void handleCreateDispute(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        Map<String, Object> charge = charges.get(str(body.get("charge_id")));
        if (charge == null) {
            throw new BadRequest(404, "charge_not_found");
        }
        if (!"succeeded".equals(charge.get("status"))) {
            throw new BadRequest(400, "charge_not_disputable");
        }
        long amount = num(charge.get("amount_cents"), 0);
        long disputed = body.get("amount_cents") == null ? amount : num(body.get("amount_cents"), 0);
        if (disputed <= 0 || disputed > amount) {
            throw new BadRequest(400, "invalid_dispute_amount");
        }
        Map<String, Object> dispute = new LinkedHashMap<>();
        dispute.put("id", nextId("dp"));
        dispute.put("charge_id", charge.get("id"));
        dispute.put("reason", str(body.getOrDefault("reason", "fraudulent")));
        dispute.put("amount_cents", disputed);
        dispute.put("status", "needs_response");
        dispute.put("created", Instant.now().toString());
        disputes.put((String) dispute.get("id"), dispute);
        charge.put("status", "disputed");
        charge.put("dispute_id", dispute.get("id"));
        respond(exchange, 200, dispute);
    }

    private void handleGetDispute(HttpExchange exchange, String id) {
        Map<String, Object> dispute = disputes.get(id);
        if (dispute == null) {
            throw new BadRequest(404, "dispute_not_found");
        }
        respond(exchange, 200, dispute);
    }

    private void handleDisputeAction(HttpExchange exchange, String id, String action) throws Exception {
        Map<String, Object> dispute = disputes.get(id);
        if (dispute == null) {
            throw new BadRequest(404, "dispute_not_found");
        }
        Map<String, Object> charge = charges.get(dispute.get("charge_id"));
        switch (action) {
            case "submit_evidence" -> {
                if (!"needs_response".equals(dispute.get("status"))) {
                    throw new BadRequest(400, "dispute_not_respondable");
                }
                dispute.put("status", "under_review");
                respond(exchange, 200, dispute);
            }
            case "resolve" -> {
                String status = str(dispute.get("status"));
                if (!Set.of("needs_response", "under_review").contains(status)) {
                    throw new BadRequest(400, "dispute_already_resolved");
                }
                String outcome = str(bodyOf(exchange).get("outcome"));
                if ("won".equals(outcome)) {
                    charge.put("status", "dispute_won");
                    dispute.put("status", "resolved:won");
                } else if ("lost".equals(outcome)) {
                    charge.put("status", "lost");
                    dispute.put("status", "resolved:lost");
                } else {
                    throw new BadRequest(400, "invalid_outcome");
                }
                respond(exchange, 200, dispute);
            }
            case "accept" -> {
                charge.put("status", "lost");
                dispute.put("status", "accepted");
                respond(exchange, 200, dispute);
            }
            default -> throw new BadRequest(404, "action_not_found");
        }
    }

    private void handleStatement(HttpExchange exchange, String query) throws Exception {
        long to = Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli();
        long from = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "from".equals(kv[0])) {
                    from = Instant.parse(kv[1].replace("%3A", ":")).toEpochMilli();
                } else if (kv.length == 2 && "to".equals(kv[0])) {
                    to = Instant.parse(kv[1].replace("%3A", ":")).toEpochMilli();
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> charge : charges.values()) {
            long created = num(charge.get("created_ts"), 0);
            if (created >= from && created <= to
                    && Set.of("succeeded", "refunded", "dispute_won", "lost").contains(charge.get("status"))) {
                result.add(charge);
            }
        }
        respond(exchange, 200, Map.of("charges", result));
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length > MAX_BODY_BYTES) {
            throw new BadRequest(413, "payload_too_large");
        }
        if (bytes.length == 0) {
            return Collections.emptyMap();
        }
        try {
            return mapper.readValue(bytes, Map.class);
        } catch (Exception e) {
            throw new BadRequest(400, "invalid_json");
        }
    }

    private Map<String, Object> bodyOf(HttpExchange exchange) throws IOException {
        return readBody(exchange);
    }

    private Map<String, Object> idempotent(String key) {
        Object cached = idempotency.get(key);
        return cached == null ? null : (Map<String, Object>) cached;
    }

    private String nextId(String prefix) {
        return prefix + "_" + seq.incrementAndGet();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static long num(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private static String metadataOrderId(Map<String, Object> body) {
        Object metadata = body.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            Object orderId = map.get("order_id");
            return orderId == null ? null : orderId.toString();
        }
        return null;
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> payload) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(payload);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException e) {
            // best effort
        }
    }

    private static final class BadRequest extends RuntimeException {
        final int status;

        BadRequest(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
