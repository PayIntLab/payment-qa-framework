# Payment QA Test Checklist for Cross-border Products (44 items: 42 runnable + 2 reserved)

> Mapped to the runnable tests in `payment-qa-framework`. All tests run against the built-in mock gateway (Stripe-style) — no real cards or real environments.

## Group A: Payment main flows (happy path)

| ID | Scenario | Key checkpoints |
|---|---|---|
| A1 | First payment (new user) | Order paid, amount/currency correct, transaction ID recorded |
| A2 | Returning user buys again | Two independent orders, no double charge |
| A3 | Cancel / abandon payment | No charge, order stays unpaid, can retry |
| A4 | Multi-currency / FX | Settlement currency matches charge, FX correct |
| A5 | Coupon / discount | Amount math correct; expired/used coupons rejected |

## Group B: Payment failures & retries

| ID | Scenario | Key checkpoints |
|---|---|---|
| B6 | Card declined | Order stays unpaid, clear message, retry succeeds |
| B7 | Insufficient funds | Failure reason explicit, order unpaid |
| B8 | 3DS verification | Approve / reject both handled correctly |
| B9 | Double submit / idempotency key | Only one charge, same result on replay |
| B10 | Zero amount / free order | Payment skipped, order succeeds |

## Group C: Subscriptions

| ID | Scenario | Key checkpoints |
|---|---|---|
| C11 | Create subscription | Status active, period_end correct |
| C12 | Periodic renewal | Success extends; failure moves to past_due |
| C13 | Cancel subscription | Cancel at period end, not immediately |
| C14 | Upgrade / downgrade proration | New plan applied, prorated charge correct |
| C15 | Trial to paid | Trial converts to active automatically |

## Group D: Webhooks & reconciliation

| ID | Scenario | Key checkpoints |
|---|---|---|
| D16 | Webhook idempotency (replay) | Same event produces side effects once |
| D17 | Webhook signature verification | Forged / missing / empty signatures all rejected |
| D18 | Dropped callback recovery | Reconciliation backfills the order |
| D19 | Refund (full) | Order state updates to refunded |
| D20 | Statement consistency | Platform statement matches local orders |

## Group E: Auth & Capture

| ID | Scenario | Key checkpoints |
|---|---|---|
| E1 | Authorize only | requires_capture, no settled charge |
| E2 | Full capture | Authorized 5000 → captured 5000, succeeded |
| E3 | Partial capture | 2000 + 3000 captures, remaining decreases correctly |
| E4 | Capture over authorization | 400, state unchanged |
| E5 | Double capture | 400 after full capture |
| E6 | Void / expire | canceled / expired; capture after terminal state → 400 |
| E7 | 3DS + manual capture | 3DS confirm → requires_capture → capture succeeds |

## Group F: Chargebacks / disputes

| ID | Scenario | Key checkpoints |
|---|---|---|
| F1 | Platform starts a dispute | Dispute created, charge → disputed, amount/reason correct |
| F2 | Partial amount dispute | 5000 charge, 2000 disputed |
| F3 | Submit evidence | Status → under_review |
| F4 | Dispute won | charge → dispute_won (funds returned) |
| F5 | Dispute lost | charge → lost (funds lost) |
| F6 | Accept dispute | charge → lost |
| F7 | State machine protection | Capture/refund on disputed → 400; terminal states locked |

## Group G: Edge cases & consistency

| ID | Scenario | Key checkpoints |
|---|---|---|
| G1 | Field length boundaries | Oversized order id / amount → 4xx, no side effects |
| G2 | Unknown event types | Ignored, no 500, no side effects |
| G3 | Out-of-order webhooks | Refund before payment → no premature side effects |
| G4 | Concurrent webhooks | Two concurrent identical events → side effect once |
| G5 | Malformed / oversized payloads | Invalid JSON → 400; oversized body → 413; server stays up |
| G6 | Sandbox vs production config | Test-mode flag, api_version, secret config present |

## Group H: Refund details & subscription dunning (H2 / H4 reserved)

| ID | Scenario | Key checkpoints |
|---|---|---|
| H1 | Partial refund + refund race | Partial amount correct; refund before capture → 400 |
| H2 | Wallets / local payment methods (reserved) | Adapter placeholder for Apple Pay / Alipay / SEPA / Klarna |
| H3 | Subscription dunning | Failed renewal → past_due; recovery → active; retry count correct |
| H4 | Payouts / settlement reconciliation (reserved) | Adapter placeholder for fees, FX, settlement |

## Usage

1. Clone and run `mvn test`
2. To wire a real platform: replace `ApiClient` with the real SDK / gateway adapter; the test logic stays
3. Reports: `target/payment-qa-report.md` / `.json`
4. Add a new scenario: add a `@Test` to the matching class and reuse the framework helpers
