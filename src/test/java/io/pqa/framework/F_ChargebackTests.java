package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class F_ChargebackTests extends AbstractPaymentQaTest {

    private Map<String, Object> succeededCharge(long amount) {
        return API.post("/v1/charges", charge("4242424242424242", amount, "USD", orderId("f")));
    }

    @Test
    void f1_platformStartsDispute() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "fraudulent"));
        assertEquals("needs_response", dispute.get("status"));
        assertEquals("fraudulent", dispute.get("reason"));
        assertEquals("disputed", API.get("/v1/charges/" + charge.get("id")).get("status"));
    }

    @Test
    void f2_partialAmountDispute() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "duplicate", "amount_cents", 2000));
        assertEquals(2000, ((Number) dispute.get("amount_cents")).longValue());
    }

    @Test
    void f3_submitEvidence() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "fraudulent"));
        Map<String, Object> reviewed = API.post("/v1/disputes/" + dispute.get("id") + "/submit_evidence", Map.of());
        assertEquals("under_review", reviewed.get("status"));
    }

    @Test
    void f4_disputeWon() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "fraudulent"));
        Map<String, Object> resolved = API.post("/v1/disputes/" + dispute.get("id") + "/resolve",
                Map.of("outcome", "won"));
        assertEquals("resolved:won", resolved.get("status"));
        assertEquals("dispute_won", API.get("/v1/charges/" + charge.get("id")).get("status"));
    }

    @Test
    void f5_disputeLost() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "fraudulent"));
        API.post("/v1/disputes/" + dispute.get("id") + "/resolve", Map.of("outcome", "lost"));
        assertEquals("lost", API.get("/v1/charges/" + charge.get("id")).get("status"));
    }

    @Test
    void f6_acceptDispute() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "fraudulent"));
        Map<String, Object> accepted = API.post("/v1/disputes/" + dispute.get("id") + "/accept", Map.of());
        assertEquals("accepted", accepted.get("status"));
        assertEquals("lost", API.get("/v1/charges/" + charge.get("id")).get("status"));
    }

    @Test
    void f7_stateMachineProtection() {
        Map<String, Object> charge = succeededCharge(5000);
        Map<String, Object> dispute = API.post("/v1/disputes",
                Map.of("charge_id", charge.get("id"), "reason", "fraudulent"));
        ApiClient.ApiException refund = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/refunds", Map.of("charge_id", charge.get("id"), "amount_cents", 1000)));
        assertEquals(400, refund.status);

        API.post("/v1/disputes/" + dispute.get("id") + "/accept", Map.of());
        ApiClient.ApiException resolve = assertThrows(ApiClient.ApiException.class,
                () -> API.post("/v1/disputes/" + dispute.get("id") + "/resolve", Map.of("outcome", "won")));
        assertEquals(400, resolve.status);
    }
}
