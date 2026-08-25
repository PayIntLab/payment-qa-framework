# payment-qa-framework

> Payment QA automation framework for cross-border / overseas products.
> 支付 QA 自动化框架：把"支付上线前体检"的 42 场景清单，写成可以直接跑的自动化用例。

![CI](https://github.com/lilycyj-hub/payment-qa-framework/actions/workflows/ci.yml/badge.svg)

## 这是什么

支付集成最怕的不是"测不出来"，而是"没测过"。这个框架把一份支付测试清单（主流程 / 失败重试 / 订阅 / Webhook 对账 / 授权扣款 / 拒付争议 / 边界一致性 / 退款与续费）变成可执行代码：

- 内置一个 **Stripe 风格的模拟支付网关**（测试卡、幂等键、3DS、订阅、授权-捕获、拒付、账单导出），本地跑、CI 跑，不依赖任何真实账号
- 每个场景是一个独立测试方法，失败时给出明确断言和现场信息
- 自带报告生成器（Markdown + JSON），跑完可直接作为交付文档

## 快速开始

```bash
mvn test
```

需要 JDK 17+ 和 Maven。全部测试通过后，`target/payment-qa-report.md` 和 `target/payment-qa-report.json` 会生成示例报告。

## 场景覆盖

| 组 | 主题 | 数量 | 测试类 |
|---|---|---|---|
| A | 支付主流程（成功路径） | 5 | `A_PaymentFlowTests` |
| B | 支付失败与重试 | 5 | `B_FailureRetryTests` |
| C | 订阅 | 5 | `C_SubscriptionTests` |
| D | Webhook / 对账 | 5 | `D_WebhookReconcileTests` |
| E | 授权-扣款（Auth & Capture） | 7 | `E_AuthCaptureTests` |
| F | 拒付 / 争议（Chargeback） | 7 | `F_ChargebackTests` |
| G | 边界与一致性 | 6 | `G_EdgeConsistencyTests` |
| H | 退款细节 / 订阅 Dunning | 2（另有 2 项预留） | `H_RefundDunningTests` |

完整清单见 [docs/payment-qa-checklist.md](docs/payment-qa-checklist.md)。

## 结构

```
src/main/java/io/pqa/framework/
  ApiClient.java            # HTTP 客户端（Java 17 HttpClient）
  WebhookSigner.java        # 事件签名 / 验签（HMAC-SHA256，恒定时间比较）
  IdempotencyStore.java     # 幂等去重（事件 ID 级）
  WebhookProcessor.java     # 被测"商户逻辑"简化版（事件路由 + 状态机 + 乱序防护）
  Reconciler.java           # 对账：平台账单 vs 本地订单
  ScenarioResult.java       # 场景结果模型
  ReportWriter.java         # Markdown / JSON 报告生成
  gateway/MockGateway.java  # 内置模拟支付网关（Stripe 风格）
src/test/java/io/pqa/framework/
  AbstractPaymentQaTest.java  # 网关生命周期（随机端口，共享实例）
  A_PaymentFlowTests.java ... H_RefundDunningTests.java
  ReportGenerationTest.java   # 报告生成示例
```

## 设计原则

- **干净实现（clean-room）**：本仓库全部代码为独立编写，不包含任何公司或商业项目代码、数据或配置；场景与实现基于公开平台文档（Stripe 官方文档）、开源 issue 调研与行业通用实践整理
- **本地可跑**：不依赖真实支付账号，模拟网关 + 测试卡全内置
- **单向依赖**：测试 → 框架 → 网关，接真实平台时只换 `ApiClient` 实现，测试逻辑不动
- **报告即交付**：测试输出 Markdown / JSON 报告，直接作为"支付体检"交付物

## AI 路线图（v2，不阻塞当前版本）

- AI 报告分析：读 JSON 报告，输出风险排序 + 修复建议
- AI 失败归因：结合请求 / 响应 / 耗时快照，给出根因假设
- AI 生成测试数据：从 schema 生成边界 payload（智能 fuzz），复用 `ApiClient`
- case 元数据化：场景注册表驱动，AI 可从 changelog / issue 起草新场景
- 对话式执行：自然语言触发"跑一下 Webhook 组"

框架当前已经为这些留好接口：`ScenarioResult` 支持 JSON 导出，`MockGateway` 支持故障注入，测试与网关解耦。

## License

[MIT](LICENSE) © 2026 pqa-labs（如需改为真实姓名，改 LICENSE 首行即可）

## 关于作者

做过多年跨境支付软件开发自动化测试，目前专注出海产品支付验证：支付流程测试、掉单排查、对账兜底。

更多内容见掘金专栏《跨境支付》：字段漂移、重复入账、回调丢失、签名验证——真实 issue 案例 + 自查清单。

联系方式：【GitHub / 邮箱】
