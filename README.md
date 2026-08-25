# payment-qa-framework

> Payment QA automation framework for cross-border / overseas products.
> Turns a 42-scenario payment test checklist into runnable automation.

![CI](https://github.com/pqa-labs/payment-qa-framework/actions/workflows/ci.yml/badge.svg)

## What is this?

Payment integrations rarely fail because "nobody can test" — they fail because "nobody tested." This framework turns a payment test checklist (main flows / failure & retry / subscriptions / webhooks & reconciliation / auth & capture / chargebacks / edge cases / refunds & dunning) into executable code:

- Built-in **Stripe-style mock payment gateway** (test cards, idempotency keys, 3DS, subscriptions, auth-capture, disputes, statement export) — runs locally and in CI, no real payment accounts needed
- Each scenario is an isolated test method with clear assertions
- Report generator outputs Markdown + JSON — ready to be handed over as a delivery artifact

## Quick start

```bash
mvn test
```

Requires JDK 17+ and Maven. After a green run, `target/payment-qa-report.md` and `target/payment-qa-report.json` are generated as sample reports.

## Scenario coverage

| Group | Topic | # | Test class |
|---|---|---|---|
| A | Payment main flows (happy path) | 5 | `A_PaymentFlowTests` |
| B | Payment failures & retries | 5 | `B_FailureRetryTests` |
| C | Subscriptions | 5 | `C_SubscriptionTests` |
| D | Webhooks / reconciliation | 5 | `D_WebhookReconcileTests` |
| E | Auth & Capture | 7 | `E_AuthCaptureTests` |
| F | Chargebacks / disputes | 7 | `F_ChargebackTests` |
| G | Edge cases & consistency | 6 | `G_EdgeConsistencyTests` |
| H | Refund details / subscription dunning | 2 (2 more reserved) | `H_RefundDunningTests` |

Full checklist: [docs/payment-qa-checklist.md](docs/payment-qa-checklist.md).

## Structure

```
src/main/java/io/pqa/framework/
  ApiClient.java            # HTTP client (Java 17 HttpClient)
  WebhookSigner.java        # event signing / verification (HMAC-SHA256, constant-time)
  IdempotencyStore.java     # event-level idempotency
  WebhookProcessor.java     # simplified merchant logic: routing, state machine, out-of-order guard
  Reconciler.java           # reconciliation: platform statement vs local orders
  ScenarioResult.java       # scenario result model
  ReportWriter.java         # Markdown / JSON report generation
  gateway/MockGateway.java  # built-in mock PSP (Stripe-style)
src/test/java/io/pqa/framework/
  AbstractPaymentQaTest.java  # gateway lifecycle (random port, shared instance)
  A_PaymentFlowTests.java ... H_RefundDunningTests.java
  ReportGenerationTest.java   # report generation sample
```

## Design principles

- **Clean-room**: all code independently written; no company or commercial project code, data, or configuration. Scenarios are based on public platform documentation (Stripe docs), open-source issue research, and industry common practice
- **Runs anywhere**: no real payment accounts required — mock gateway + test cards built in
- **One-way dependency**: tests → framework → gateway. To wire a real platform, replace `ApiClient` only; the test logic stays
- **Report as deliverable**: Markdown / JSON output that can be handed to customers directly

## AI roadmap (v2, non-blocking)

- AI report analysis: read the JSON report, rank risks, suggest fixes
- AI failure attribution: use request/response/timing snapshots to hypothesize root causes
- AI test-data generation: schema-driven boundary payloads (smart fuzzing), reusing `ApiClient`
- Scenario-as-data: registry-driven cases; AI can draft new scenarios from changelogs / issues
- Conversational execution: natural-language triggers like "run the webhook group"

The framework already keeps interfaces ready for these: `ScenarioResult` exports JSON, `MockGateway` supports fault injection, and tests are decoupled from the gateway.

## License

[MIT](LICENSE) © 2026 pqa-labs

## About the author

Automation testing engineer focused on cross-border payment verification: payment flow testing, dropped-order troubleshooting, reconciliation.

More content in my Juejin column on cross-border payment pitfalls: field drift, duplicate processing, lost callbacks, signature verification — real issue cases and checklists.

Contact: [GitHub](https://github.com/pqa-labs) / email: ____
