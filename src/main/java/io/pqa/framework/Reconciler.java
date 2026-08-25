package io.pqa.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reconciliation: platform statement vs local order status. */
public final class Reconciler {

    public record Issue(String orderId, String chargeId, String action) {}

    public List<Issue> reconcile(List<Map<String, Object>> statementCharges,
                                 Map<String, String> localOrderStatus) {
        List<Issue> issues = new ArrayList<>();
        for (Map<String, Object> charge : statementCharges) {
            if (!"succeeded".equals(charge.get("status"))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) charge.get("metadata");
            String orderId = metadata == null ? null : (String) metadata.get("order_id");
            if (orderId == null || !localOrderStatus.containsKey(orderId)) {
                continue;
            }
            if (!"paid".equals(localOrderStatus.get(orderId))) {
                issues.add(new Issue(orderId, (String) charge.get("id"), "BACKFILL"));
            }
        }
        return issues;
    }
}
