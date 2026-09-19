# High-Level Design: Failure Analyser & Classifier (FAC)

**Status:** Design draft — pending review
**Type:** TestNG listener component (`ITestListener` + `ISuiteListener`)
**Engine:** Hybrid — deterministic rule chain first, agentic escalation only for the residue
**Diagram:** [`failure-classifier-hld.png`](./failure-classifier-hld.png)

> Related: [ARCHITECTURE.md](./ARCHITECTURE.md),
> [FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md](./FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md),
> [Agentic Integration Design Approach - Phase 1.md](./Agentic%20Integration%20Design%20Approach%20-%20Phase%201.md)

---

## 1. Objective

Every failing test currently lands in one undifferentiated bucket: **failed**. A human then reads the
stack trace and decides *whose problem it is* — exactly the manual reasoning already recorded in
`FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md` (the headless-Myntra verdict table). The FAC
**automates that verdict** at suite runtime, tagging each failure with one of four categories:

| Category | Meaning | Owner it routes to |
|---|---|---|
| **Product Bug** | The AUT is genuinely wrong — element found but behaviour/value incorrect, product API 5xx, broken business rule. | Dev team |
| **Environment Instability** | Transient/infra — network, driver/session death, Appium server, gateway 502/503, bot-challenge shell, *retry-recovered flakiness*. | Infra / CI |
| **Locator Mismatch** | The element contract broke — `NoSuchElement`, `StaleElement`, `InvalidSelector`, or a `BasePage` wait timing out on a missing element. | Automation team |
| **Logic Error** | The **test/framework** is wrong — `NPE`, `IndexOOB`, `ClassCast`, bad test data, config error. | Automation team |

**Design philosophy (inherited from the Phase-1 doc):** deterministic core is the source of truth;
the agent only earns its keep on failures the rules cannot confidently classify. Clarity > cleverness,
strict YAGNI, `ThreadLocal`-safe, and agent-consumable outputs.

---

## 2. Why a hybrid engine (the pivotal decision)

| Option | Verdict | Reason |
|---|---|---|
| Pure rules | Rejected as *sole* engine | Blind to novel/compound failures; brittle on unseen stacks. |
| Pure agent/LLM | Rejected | Non-deterministic, token cost on every failure, slow, unsafe in a parallel run — violates "compiler/Selenium as source of truth". |
| **Hybrid (chosen)** | **Adopted** | Rules classify the ~90% of failures with known signatures deterministically and for free; the agent sees only the UNKNOWN residue, **post-run, single-threaded**. Bounded cost, explainable, CI-safe. |

This mirrors the Phase-1 principle exactly: *agent only where determinism runs out.*

---

## 3. Problem decomposition (the five tiers)

The diagram bands map 1:1 to these tiers.

### Tier 0 — Trigger (the seam you already own)
`FailureClassifierListener implements ITestListener`. `onTestFailure` fires on the **failing test's
own thread**, so `DriverManager.getDriver()` and any DOM read are thread-correct under
`parallel="methods"` — identical safety model to the existing `ScreenshotListener`. Registered once
per `testng.xml` beside `AllureTestNg` / `ScreenshotListener` / `RetryTransformer`; **no `@Test`
edits**, suite-wide by registration.

### Tier 1 — Evidence Assembly
> A classifier is only as good as its evidence. This tier is where most of the engineering value sits.

Turns the raw `Throwable` into an immutable `FailureContext` record via focused, single-responsibility
extractors:

| Extractor | Signal it produces | Sourced from |
|---|---|---|
| `StackTraceAnalyzer` | Which layer threw: `BasePage` / page object / test class / driver factory | `ITestResult.getThrowable()` root frame |
| `RetryCorrelator` | Was this test retried by `RetryAnalyzer`, and did a **later attempt pass**? | Correlating retried invocations at suite finish |
| `DomAnalyzer` | Bot-challenge / blank-shell detection (the headless-Myntra case) | Page source (reused from `ScreenshotListener` capture) |
| `StateExtractors` | `driver == null` (pre-test config failure), HTTP status (API track), duration vs `explicit.wait.seconds`, platform (web/mobile/api) | `DriverManager`, REST response, `ConfigReader` |

`FailureContext` fields (immutable Java `record`):
`testName · testClass · throwable · exceptionType · message · stackRootFrame · durationMs ·
wasRetried · driverPresent · pageSourceRef · httpStatus · platform · threadName`.

### Tier 2 — Deterministic Rule Engine (primary)
An **ordered chain-of-responsibility** of `ClassificationRule` implementations. Each returns
`Optional<Classification>`; **first confident match wins**, and each attaches its evidence + a
confidence score. Ordering is specific → general:

```
1  EnvironmentRule   retry-recovered · WebDriver/Session/SocketTimeout/UnknownHost
                     · HTTP 502/503/504 · driver==null · bot-challenge DOM   → Environment Instability
2  LocatorRule       NoSuchElement · StaleElement · InvalidSelector
                     · Timeout thrown from BasePage waiting on an element     → Locator Mismatch
3  ProductBugRule    business AssertionError (element found, value wrong)
                     · product API 5xx · genuine contract 4xx                 → Product Bug
4  LogicErrorRule    NPE · IndexOOB · ClassCast · NumberFormat · IllegalState
                     originating in test/page code · config exception         → Logic Error
5  (fallthrough)     nothing matched confidently                              → UNKNOWN
```

**Why this order:** environment and retry signals are the most *decisive* and least ambiguous, so they
short-circuit first (a retry-recovered failure is unambiguously flaky). `LocatorRule` precedes
`ProductBugRule` because a `TimeoutException` from `BasePage` is a locator/sync problem, not a product
assertion. `LogicErrorRule` is last among the confident rules because its exception types (`NPE` etc.)
are the clearest "our code is wrong" tell and shouldn't mask a more specific upstream signal.

### Tier 3 — Agentic Escalation (residue only)
Invoked **only** for `UNKNOWN` / low-confidence results, and **only post-run, single-threaded** at
suite finish — so there is **zero LLM cost mid-parallel-run** (mirrors the Phase-1 concurrency rule:
writes/AI happen post-run, single-threaded). `AgenticClassifier` packages the `FailureContext`
(exception, sanitized DOM excerpt, HTTP status) into a structured prompt over MCP. **Guardrails:**
output must map to one of the four categories or stay `UNKNOWN`; every agent verdict is flagged
`agent-assisted · needs ratification` (your one human gate), and the whole tier is a `ConfigReader`
toggle — **the pipeline must run green with the agent switched off.**

### Tier 4 — Classification Sink & Reporting (`ISuiteListener.onFinish`)
Makes the verdict actionable for three audiences:

| Sink | Audience | Detail |
|---|---|---|
| **Allure `categories.json`** + per-test label (`failureType=…`) + rationale attachment | Humans | Allure already *buckets* failures by category — we feed it *real* categories instead of generic "Product defects / Test defects". |
| **`failure-classification.json`** (per run) | CI / agents | Machine-readable; feeds dashboards and downstream LLM debugging (agentic-readiness). |
| **Console summary** (`INFO`) | Console | Counts per category at suite finish. Rule mechanics stay on `DEBUG` — console-cleanliness standard. |

---

## 4. End-to-end workflow

```
test fails
  └─► onTestFailure (failing thread)
        └─► Tier 1 assembles FailureContext (exception · stack · DOM · retry · duration · platform)
              └─► Tier 2 rule chain — first confident ClassificationRule wins
                    ├─ matched  → tag Allure immediately + push to thread-safe ResultStore
                    └─ UNKNOWN  → hold for post-run escalation
suite finishes
  └─► onFinish (single thread)
        ├─ RetryCorrelator finalises retry-recovered verdicts
        ├─ UNKNOWN residue → AgenticClassifier (batch, single-threaded, if enabled)
        └─ Sink: categories.json + failure-classification.json + INFO summary
Allure report renders failures already bucketed into the 4 categories.
```

---

## 5. Concurrency & thread-safety

- **Per-failure classification** runs on the failing test's own thread — no shared driver access,
  identical model to `ScreenshotListener`.
- The only cross-thread structure is a **`ConcurrentLinkedQueue<Classification>` ResultStore**;
  results are *appended*, never mutated.
- **Aggregation, retry-correlation, and the agent tier run single-threaded** in `onFinish`. No race on
  `failure-classification.json` or `categories.json` — the same discipline the Phase-1 doc applies to
  locator writes.

---

## 6. Components: new vs reused

| Component | Status | Reuses |
|---|---|---|
| `FailureClassifierListener` (`ITestListener`, `ISuiteListener`) | **NEW** | `DriverManager`, `ConfigReader` |
| `FailureContext` (record) + `FailureContextBuilder` | **NEW** | — |
| `StackTraceAnalyzer`, `RetryCorrelator`, `DomAnalyzer`, `StateExtractors` | **NEW (small, cohesive)** | `ScreenshotListener` page-source capture pattern |
| `ClassificationRule` interface + 4 rule impls | **NEW** | — |
| `FailureCategory` enum | **NEW** | — |
| `AgenticClassifier` (Tier 3) | **NEW, config-gated** | MCP wiring (Phase-1 direction) |
| `ClassificationSink` / `ClassificationAggregator` | **NEW** | Allure API, `JsonReader` (Jackson) pattern |
| `RetryAnalyzer` / `RetryTransformer`, `ScreenshotListener`, Allure/AspectJ, `ConfigReader`, `DriverManager`, `BasePage` | **Reused untouched** | — |

Net footprint on existing code: **near zero** — the FAC bolts onto the listener seam; core
driver/POM/reporting are untouched.

---

## 7. Extensibility (open/closed)

- **New signal** → add one extractor, add a field to `FailureContext`.
- **New rule / re-prioritise** → add a `ClassificationRule` to the ordered chain; existing rules untouched.
- **New category** → extend `FailureCategory` enum; rules opt in.

A wrong category name can never silently exist — it's an enum, compiler-enforced (same guarantee the
Phase-1 doc gets from typed locator getters).

---

## 8. Configuration (`ConfigReader` keys)

| Key | Purpose | Default |
|---|---|---|
| `fac.enabled` | Master switch for the whole listener | `true` |
| `fac.agent.enabled` | Tier-3 agentic escalation on/off | `false` |
| `fac.confidence.threshold` | Below this, a rule verdict is treated as residue for the agent | `0.6` |
| `fac.report.path` | Output path for `failure-classification.json` | `target/` |

---

## 9. Trade-offs a reviewer will raise

**Q: Rules will misclassify edge cases.**
Yes — that is *why* every verdict carries a confidence score and evidence list, and why low-confidence
results escalate to the agent rather than being trusted blindly. Misclassifications are visible and
correctable (add/re-order a rule), not silent.

**Q: Isn't `Timeout` ambiguous (locator vs product vs environment)?**
That is exactly why Tier 1 exists. A `Timeout` from `BasePage` on a *present-but-slow* element differs
from one on a *missing* element (LocatorRule) which differs from one on a *bot-challenge shell*
(DomAnalyzer → EnvironmentRule). The extractors disambiguate before the rules vote.

**Q: Does the agent make the build non-deterministic?**
No. It runs post-run, single-threaded, is config-gated off by default, and its verdicts are flagged for
human ratification. The green/red build result never depends on it.

**Q: How much of the framework changes?**
One listener + a handful of small classes. `DriverFactory`, `DriverManager`, `BasePage`, Allure,
`RetryAnalyzer`, and `ScreenshotListener` are untouched.

---

## 10. POC Definition of Done

1. Register `FailureClassifierListener` in `testng-web.xml`.
2. Run the suite headless against Myntra (the known bot-challenge case): the headless search-bar
   timeouts are classified **Environment Instability** via `DomAnalyzer` — automatically reproducing
   the manual verdict in `FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md`.
3. A deliberately broken locator classifies as **Locator Mismatch**; a forced `NPE` in a test as
   **Logic Error**; a false business assertion as **Product Bug**.
4. Allure report shows the four native categories; `target/failure-classification.json` is produced;
   `INFO` console prints per-category counts.
5. Whole pipeline runs green with `fac.agent.enabled=false`.

---

## 11. Explicitly out of scope (until this is approved)

- Exact rule signature catalogue and confidence-scoring formula.
- `AgenticClassifier` prompt/response schema and MCP contract.
- `failure-classification.json` schema versioning.
- Historical trend storage / flakiness scoring across runs.
