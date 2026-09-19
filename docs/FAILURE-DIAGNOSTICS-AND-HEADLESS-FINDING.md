# Failure Diagnostics & the Headless Myntra Finding

This document records two related things:

1. The **on-failure diagnostics hook** (`ScreenshotListener`) — what it does and how to view it.
2. A concrete **investigation**: why the suite passes headed but fails headless against Myntra,
   and the evidence that proves it is a *product/environment* issue, not a framework bug.

> Related: [ARCHITECTURE.md](./ARCHITECTURE.md),
> [PARALLEL-AND-CROSS-BROWSER.md](./PARALLEL-AND-CROSS-BROWSER.md).

---

## Part 1 — The failure-diagnostics hook

### What it is

`src/test/java/com/project/qa/listeners/ScreenshotListener.java` — a TestNG `ITestListener` that,
**only on test failure**, attaches two artifacts to the Allure report for the failing test:

- **Screenshot on failure** (`image/png`)
- **Page source on failure** (`text/html`)

It is registered in `testng.xml` alongside the Allure listener:

```xml
<listener class-name="io.qameta.allure.testng.AllureTestNg" />
<listener class-name="com.project.qa.testsupport.listeners.ScreenshotListener" />
```

### Why it is built this way

- **Failure-only capture** — `onTestFailure` is the sole hook, so passing tests add no artifacts
  (avoids Allure bloat, per the framework's reporting standard).
- **Thread-safe** — `onTestFailure` runs on the *failing test's own thread*, so
  `DriverManager.getDriver()` returns that thread's driver. Correct under parallel execution.
- **Null-guarded** — if a failure occurs before the driver exists (e.g. a data-provider or config
  error), it logs at `DEBUG` and skips, rather than throwing.
- **Never masks the real failure** — screenshot/page-source capture is wrapped so a
  `WebDriverException` during capture can't replace the actual test exception.
- **Lives in `src/test/java`** — because `allure-testng` (and the `io.qameta.allure.Allure` API)
  is a `test`-scoped dependency.

### How to view the screenshots

`allure-results` stores **raw** data (JSON + attachment blobs with GUID filenames). Screenshots
become visible only after the report is **rendered**:

```powershell
mvn allure:serve            # render + open a temporary report
mvn clean verify -Preport   # run tests, then auto-open the report
```

In the report: **failed test → attachments → "Screenshot on failure" / "Page source on failure"**.

---

## Part 2 — Investigation: headless passes headed, fails headless

### Symptom

Running headless:

```powershell
mvn test -Dheadless=true
```

produced 6–7 failures, every one the same:

```
org.openqa.selenium.TimeoutException:
Expected condition failed: waiting for visibility of element located by
By.className: desktop-searchBar (tried for 10 second(s) with 500 milliseconds interval)
    at com.project.qa.pageobjects.common.BasePage.type(BasePage.java:24)
```

The browser launched fine; the **Myntra search bar never appeared within 10s**, so `type()` timed
out. The same tests pass in headed mode.

### Red herring: the CDP warnings

The log also showed:

```
WARNING: Unable to find CDP implementation matching 150
WARNING: Unable to find version of CDP to use for 150.0.7871.187 ...
```

These are **unrelated**. Chrome is v150 but Selenium 4.18.1 bundles older DevTools mappings. They
are `WARNING`s (not errors), the tests do not use CDP, and they print in headed mode too. Not the
cause.

### The decisive reasoning

Only **one variable changed** between pass and fail: the headless flag. Same code, same locator,
same 10s wait. That eliminates code, locator, and timing as the cause and points squarely at how
the target site treats headless clients.

### The evidence (captured by the new hook)

The `ScreenshotListener` page-source attachment was inspected:

| Check | Normal Myntra homepage | Captured headless page |
|-------|------------------------|------------------------|
| `<title>` | "Online Shopping Site..." | `www.myntra.com` (stripped) |
| `desktop-searchBar` in DOM | present | **absent** |
| Page length | full app | ~188 KB challenge/blocked shell |

The search bar is genuinely **not in the DOM** — so the explicit wait was *correct* to time out.
Myntra served a **bot-challenge / blocked page** to the headless browser.

### Root cause

Myntra runs **anti-bot detection**. Headless Chrome emits automation signals
(`navigator.webdriver=true`, no real GPU/plugins, automation-controlled flags), so Myntra returns
a challenge/blocked page instead of the real homepage. No search bar → timeout.

### Categorisation

| Layer | Verdict | Reason |
|-------|---------|--------|
| Architecture | Sound | Headless toggle, ThreadLocal, factory all worked; Chrome launched headless per thread as designed |
| Framework code | Correct | Same locator + wait pass headed; timing out on a genuinely missing element is correct behaviour |
| Product / site | **The cause** | Myntra's anti-automation serves a different DOM to headless clients |

**Conclusion:** a product/environment issue, not a code or architecture defect. The framework
behaved correctly and — via the new hook — produced the evidence proving it.

---

## Part 3 — Options on the table (decision deferred)

Recorded for a later decision:

1. **Keep UI tests headed** — proven to pass; honest for a suite against a bot-protected site.
2. **Harden headless** — add anti-detection flags
   (`--disable-blink-features=AutomationControlled`, a realistic user-agent, `--lang`, window
   size). Reduces detection but is an arms race; may still be blocked.
3. **Target an automation-friendly site for headless/CI** (e.g. `saucedemo.com`,
   `automationexercise.com`) — best if the goal is exercising the *framework* rather than Myntra.

Status: **deferred** — diagnostics hook is in place; the headless target strategy will be chosen
later.
