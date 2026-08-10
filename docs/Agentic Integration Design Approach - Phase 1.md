# Architectural Blueprint: Deterministic, Agent-Orchestrated Automation Framework
**Status:** Design finalized — pending team-lead approval
**Framework baseline:** Java 21, Selenium 4.25, TestNG, Appium (mobile), Allure (AspectJ), Jackson, SLF4J/Logback
**Scope of this doc:** end-to-end architecture + resolved design gaps. Component-level schemas (e.g. `<page>.json`) are deliberately out of scope until this is approved.

---

## 1. Core Philosophy — The Restaurant Analogy
Do not think of the AI as an engineer writing complex Java. Think of it as a **waiter**. By separating *thinking* from *cooking*, we prevent hallucinations and protect build stability.

| Analogy | Reality |
|---|---|
| The Restaurant | The live webpage / DOM |
| The Menu (`locators/<page>.json`) | A deterministic, extracted list of what is actionable — produced by a Java/Selenium script, **not** by AI |
| The Customer (Jira ticket) | Business requirement / user journey |
| The Waiter (Generator Agent) | Reads the request, looks at the Menu, writes a structured order (`scenario.yaml`). Cannot invent menu items that don't exist |
| The Kitchen (Execution Engine) | Java/Selenium backend that executes the order deterministically |

**Guiding principles:**
- **Zero hardcoded locators** — Java holds no locator literals; `LocatorRepository` is the single source of truth.
- **Maximum agent orchestration, one human gate** — QA approves once, at the end of the cycle.
- **Compiler + Selenium remain the source of truth** — the AI is structurally incapable of introducing syntax errors or invented locators into CI/CD.
- **On-demand, YAGNI** — we generate only page objects and locators that a real requirement exercises. No site-wide dumps.

---

## 2. Locked Design Decisions

| Concern | Decision | Rationale |
|---|---|---|
| Agent vs. Copilot skill | Custom **Copilot agent** (orchestrator) + Java helpers | Deliverable is code + approvals, not a runtime result |
| Single vs. multi-agent | **Single agent, staged workflow** | No coordination cost; YAGNI |
| Driver stack | **Selenium 4 only** | Playwright would fork the ThreadLocal/Appium stack for no gain |
| Platform scope | **Web first**; mobile reuses same `By`/`BasePage` seam; **API is a separate track** | Web + mobile share `WebDriver`; API has no DOM/locator concept |
| Autonomy level | **Option A** — human specifies the flow (Option B, AI-designed tests, is a later phase; A's YAML becomes B's contract) | Removes non-determinism from the critical path |
| Step format | **YAML DSL**, `pageContext` required per step | Human-readable, low error surface; `pageContext` is the structural partition key |
| Locator storage | **Zero hardcoding** — externalized to `locators/<page>.json`; `LocatorRepository` is source of truth | Enables healing + agent authorship |
| Semantic naming | **Option C** — agent proposes semantic keys, human ratifies the key list at the final gate | Max automation, stable keys |
| Locator accessors | **Generated typed getter POJOs** per page (getters only) | Readable code; wrong locator name fails compilation |
| Page partitioning | **Per-page**, bounded by `pageContext`, captured **on-demand per requirement** | No unused page objects; no giant dump |
| Human intervention | **One approval gate at the end**; steps 3 & 5 are automated quality gates | Upstream must be self-validating |
| Self-healing | **Deferred** — pipeline must run green with healing switched off | De-risks the POC |

---

## 3. The End-to-End Cycle

```
Requirement (Jira) ──► scenario.yaml  (pageContext + steps, human-authored)
        │
┌───────▼──────────────────────────────────────────────────────────┐
│ AGENT ORCHESTRATION (single agent, staged)                        │
│                                                                   │
│ 1 SCAN      LocatorScanner runs at each pageContext (on-demand)   │
│             → merges into locators/<page>.json (semantic-keyed,   │
│               deduped — reuse existing key, append only new)      │
│ 2 NAME      Agent proposes semantic keys within that page's scan  │
│ 3 VALIDATE  every key resolves to exactly ONE element  [GATE-auto]│
│ 4 GENERATE  <Page>Locators.java (getters) + <Page>.java           │
│             + <Test>.java (@Test) from scenario.yaml              │
│ 5 COMPILE   maven build must pass                       [GATE-auto]│
│ 6 RUN       mvn test -Preport                                     │
│ 7 REPORT    Allure report + run log + detected issues             │
└───────────────────────────────┬──────────────────────────────────┘
                                 ▼
                    HUMAN QA — single approval gate
        (reviews locator key lists + generated code + Allure, then merges)
```

Steps 3 and 5 are **hard automated gates** that replace the mid-cycle human: a locator that doesn't resolve, or code that doesn't compile, never reaches the reviewer. This is what makes "human only at the end" safe.

---

## 4. Deterministic DOM Extraction (Creating the Menu)

A lightweight Java utility (`LocatorScanner`) reuses the existing `DriverFactory` to launch the requested browser, navigates, and scrapes actionable elements via `JavascriptExecutor` + Selenium.

**Locator strategy priority chain** (critical — the real AUT has almost no `id`/`data-testid`; existing page objects use `className`/`cssSelector`/`xpath`):

```
data-testid → id → name → aria-label → stable className → relative XPath
```

Output shape matches the strategies the framework already uses, so it drops straight into the repository:

```json
// locators/myntra_home.json  (agent-written, human-approved)
{
  "searchBar":  { "type": "className", "value": "desktop-searchBar" },
  "searchIcon": { "type": "className", "value": "desktop-submit" }
}
```

---

## 5. AI Requirement Translation (Taking the Order)

The agent maps business requirements to available locators, emitting a strict YAML DSL. Because it processes only lightweight JSON/YAML — never source code — token cost and latency are near zero.

```yaml
scenarioName: User searches for a product
pageContext: myntra_home          # REQUIRED — the partition boundary
steps:
  - action: input
    elementKey: searchBar
    data: "running shoes"
  - action: click
    elementKey: searchIcon
  - action: verifyText
    elementKey: resultsTitle
    expected: "shoes"
```

`elementKey` references the **semantic key** (= the page object field name), never a raw attribute — so a changed attribute never invalidates the scenario.

---

## 6. Zero-Hardcoding Locator Model

**Before (today):**
```java
private final By searchBar = By.className("desktop-searchBar");   // literal in Java
```

**After (target) — typed, generated getters:**
```java
// GENERATED from locators/myntra_home.json — do not hand-edit
public final class MyntraHomeLocators {
    private final By searchBar;
    private final By searchIcon;
    public By searchBar()  { return searchBar; }
    public By searchIcon() { return searchIcon; }
}
```

Page object stays clean and literal-free, still using the synchronized `BasePage` engine:
```java
public class MyntraHomePage extends BasePage {
    private final MyntraHomeLocators loc = LocatorRepository.load(MyntraHomeLocators.class);

    public void searchForProduct(String product) {
        type(loc.searchBar(), product);   // BasePage WebDriverWait — synchronized
        click(loc.searchIcon());
    }
}
```

**Design notes:**
- **Getters only, no setters** — locators are read-only at runtime; healing rewrites the JSON and regenerates the POJO. Preserves `ThreadLocal` safety.
- A wrong/invented locator name **fails compilation** — the "no random dumping" rule is compiler-enforced.

---

## 7. Deterministic Execution Engine (Cooking the Order)

`StepTranslator` maps DSL actions to the **existing `BasePage` API** — it never calls raw `driver.findElement`, so it inherits the framework's explicit-wait synchronization and Allure `@Step` instrumentation.

```
action "input"       → basePage.type(by, data)        // visibilityOf wait
action "click"       → basePage.click(by)             // elementToBeClickable wait
action "verifyText"  → assert basePage.getText(by).contains(expected)
```

`LocatorRepository.toBy(type, value)` must cover every strategy the framework uses: `className`, `cssSelector`, `xpath`, `id`, `name`.

---

## 8. Reporting & Concurrency

- **Reporting:** execution flows through `BasePage`, so the existing **AspectJ/Allure** wiring and `ScreenshotListener` capture it automatically. Each DSL step becomes an Allure `@Step`.
- **Concurrency (`-Dthreads=N`):** `LocatorRepository` is **read-only during a run** (load once, ThreadLocal-safe, mirrors `DriverManager`). Any locator writes (future healing) happen **post-run, single-threaded** against a staging file — no race on `locators/<page>.json` mid-suite.

---

## 9. Self-Healing (Deferred — Future Phase)

Documented for direction; **not built in the POC.** The pipeline must run green with healing off.

On `NoSuchElementException`/`Timeout` during execution:
1. Engine halts, triggers `LocatorScanner` on the current live DOM.
2. Healer agent proposes a new `{type,value}` for the failed **semantic key** → writes to a **staging file** (never the live repo).
3. **Human approval gate** → on approval, `LocatorRepository` is patched and the scenario **replays from the start** (mid-flow resume is infeasible; browser state is lost).
4. The heal (old vs. new value) surfaces in Allure.

Because the key is stable and decoupled from the attribute, a changed id/class heals without invalidating any scenario.

---

## 10. Components: New vs. Reused

| Component | Status | Reuses |
|---|---|---|
| `LocatorScanner` | **NEW** | `DriverFactory`, `DriverManager` |
| `LocatorRepository` (load/deserialize + `toBy`) | **NEW** | Jackson (`JsonReader` pattern) |
| `StepTranslator` | **NEW (thin)** | `BasePage.type/click/getText` |
| `<Page>Locators` POJO generator | **NEW (template)** | Jackson |
| Copilot orchestrator agent config | **NEW** | — |
| `DriverFactory`, `DriverManager`, `BasePage` | Reused untouched | — |
| Allure/AspectJ, `ScreenshotListener`, TestNG, `ConfigReader` | Reused untouched | — |

Net footprint on existing code: **near zero** — new capability bolts on at the locator + step layer; core driver/POM/reporting untouched.

---

## 11. Questions a Team Lead Will Ask — and Our Answers

**Q: Isn't a single source of truth fragile — if the repository is wrong, every test is wrong at once?**
Yes, the blast radius is the whole suite by design. Mitigation: two hard automated gates — locator *resolve-check* (step 3) and *compile-check* (step 5) — plus the human ratification gate. A bad locator cannot reach a generated test.

**Q: How do you avoid dumping the entire site's locators into one file?**
Capture is **on-demand and per-`pageContext`**. One scan → one page file → one page object. New elements merge into the existing file by semantic key (dedup); we never generate page objects a requirement doesn't use.

**Q: Where does the AI actually run, and how do we control token cost?**
The AI reads only lightweight JSON/YAML, never framework source. Token cost and latency are near zero. It runs at authoring time in the IDE/CLI, not inside the JVM.

**Q: Does this bypass our existing synchronization / thread-safety?**
No. Execution routes through `BasePage` (explicit waits) and `DriverManager` (ThreadLocal). Locator files are read-only during a run.

**Q: How much of our existing framework changes?**
Three new classes + a generator template + externalized locator JSON. `DriverFactory`, `DriverManager`, `BasePage`, Allure, and listeners are untouched.

**Q: Why not Playwright / a full AI test-writer now?**
YAGNI. Selenium already covers extraction and execution; Option A keeps the human specifying flows so there's no non-determinism in the critical path. AI-designed tests (Option B) are a fast-follow, using A's YAML as its contract.

---

## 12. POC Definition of Done
Author a `scenario.yaml` (browser + url + steps) for the Myntra search flow → agent scans the home page, proposes semantic keys, you ratify → agent generates `MyntraHomeLocators`, page object, and `@Test` → `mvn test -Preport` runs green → Allure report + run log produced. Renaming a step's `elementKey` to something not on the page is caught at the resolve/compile gate before any test is generated.

---

## 13. Explicitly Out of Scope (until this doc is approved)
- Exact `<page>.json` schema and versioning rules
- Full DSL action vocabulary and grammar
- `LocatorRepository.toBy()` strategy-coverage spec
- Healer agent prompt/response contract
- Multi-page navigation state handling
