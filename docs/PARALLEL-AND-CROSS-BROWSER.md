# Parallel Execution & Cross-Browser Support

This document records the design, implementation, and reasoning behind the framework's
parallel execution and cross-browser capabilities. It is the reference for *why* the code
looks the way it does, so future changes don't accidentally break thread-safety or
configuration overrides.

---

## Core Philosophy

- **Clarity over Cleverness** — configuration and driver selection are readable at a glance.
- **YAGNI** — no WebDriverManager, no Grid/Docker, no reflection-based factories. Only what
  local cross-browser execution actually needs today.
- **Extensible, not over-built** — the design leaves clean seams (e.g. a future `SAFARI`
  branch or a remote grid) without paying for them now.

---

## Part 1 — Parallel Execution

### The strategy: method-level parallelism + parallel DataProvider

`testng.xml` drives parallelism at the suite level:

```xml
<suite name="Myntra Automation Suite" parallel="methods"
       thread-count="${threads}" data-provider-thread-count="${threads}">
```

- `parallel="methods"` — every `@Test` method runs on its own thread.
- `data-provider-thread-count` — data-driven rows (e.g. the Myntra keyword set in
  `SearchProductsTest`) run concurrently instead of one-by-one, via
  `@DataProvider(parallel = true)`.

### Why this approach (and not the alternatives)

| Option | Verdict | Reason |
|--------|---------|--------|
| `parallel="methods"` | **Chosen** | Matches the per-method driver lifecycle; parallelizes the data-driven rows that dominate runtime. |
| `parallel="tests"` | Rejected | Needs multiple `<test>` blocks — XML boilerplate for no gain here. |
| `parallel="classes"` | Rejected | Won't parallelize the data rows *within* a single data-driven test. |
| Selenium Grid / Docker | Deferred | Over-engineering for local scope (YAGNI). A future remote step. |

### Why it is thread-safe by construction

Thread-safety is a property of the *shared components*, not the suite file:

- **`DriverManager`** holds the driver in a `ThreadLocal<WebDriver>` and calls `remove()` on
  quit — no cross-thread bleed, no memory leak.
- **`BaseTest`** creates and quits a driver in `@BeforeMethod`/`@AfterMethod`. TestNG runs
  config methods on the *same thread* as their test, so each thread gets an isolated driver.
- **Page objects** keep locators as `private final` instance fields; `BasePage.wait` is an
  instance field bound to `DriverManager.getDriver()` at construction. No mutable static
  state is shared across threads.

> **Invariant to protect:** method-level driver init ↔ `parallel="methods"`.
> If someone moves `initDriver()` into `@BeforeClass` to "share" a driver, other method
> threads will see a `null` ThreadLocal driver and fail. Class-scoped drivers require
> `parallel="classes"` instead.

### Two thread pools, not one

`thread-count` (methods) and `data-provider-thread-count` are **independent** pools. A
parallel DataProvider can run rows *on top of* parallel methods, so real concurrency can
exceed `threads` — plan CI agent sizing and prefer `headless=true` accordingly.

### Logging under parallelism

`logback-test.xml` includes the executing thread name so interleaved lines stay attributable:

```xml
<pattern>%d{HH:mm:ss} [%thread] %-5level - %msg%n</pattern>
```

Without `[%thread]`, concurrent `INFO` lines interleave with no way to tell which browser
produced which line. The date was dropped as redundant (Allure and the run header carry it).

---

## Part 2 — Cross-Browser Support

### Configuration model: layered override

A single static properties file can't vary per-run or per-environment. The framework uses a
**layered override** instead:

```
System property (-Dbrowser=firefox)   ← highest priority (per-run, CI-friendly)
        ↓ falls back to
config.properties                      ← committed defaults
```

`config.properties` (on the test classpath):

```properties
browser=chrome
headless=false
threads=3
```

`ConfigReader` reads `System.getProperty(key)` first and only falls back to the file — so the
command line always wins without editing any file:

```powershell
mvn test                                   # chrome, 3 threads (defaults)
mvn test -Dbrowser=firefox                 # switch browser from the CLI
mvn test -Dbrowser=edge -Dthreads=5        # browser + thread count
mvn test -Dbrowser=chrome -Dheadless=true  # headless (ideal for parallel)
```

> Syntax note: it is `-Dbrowser=firefox` (a JVM system property), **not** `--browser`.
> Maven has no `--browser` flag.

### Driver selection: enum + factory switch

`BrowserType` (`CHROME`, `FIREFOX`, `EDGE`) parses the configured value case-insensitively and
fails fast with a clear message on a typo or unsupported value.

`DriverFactory` builds a **fresh** driver per call and stores it in the existing
`ThreadLocal`, so parallel threads can even run *different* browsers safely:

```java
return switch (browser) {
    case CHROME  -> new ChromeDriver(chromeOptions(headless));
    case FIREFOX -> new FirefoxDriver(firefoxOptions(headless));
    case EDGE    -> new EdgeDriver(edgeOptions(headless));
};
```

Each branch delegates to a small, single-responsibility option builder. Logging hygiene is
enforced: `INFO` only for `"Launching <browser>"`; option details at `DEBUG`.

### Why no new dependencies

**Selenium Manager** (built into Selenium 4) resolves the Chrome/Firefox/Edge driver binaries
natively. Adding WebDriverManager would be redundant — rejected on YAGNI grounds.

> Selenium Manager fetches **drivers**, not **browsers**. Firefox and Edge must be installed
> on the machine for local runs.

### Why Safari is excluded

`SafariDriver` ships only with Safari on macOS; Apple discontinued Safari for Windows in 2012.
On this Windows-local setup a `SAFARI` branch would compile but always fail at runtime. It
belongs to a future macOS runner or a cloud grid (`RemoteWebDriver`). The enum + switch are
structured so adding it later is a ~3-line change with no refactor.

---

## Part 3 — Making `threads` Configurable from the CLI

### The problem

TestNG reads `thread-count` from the **suite XML before any test code runs**, and Surefire's
own `parallel`/`threadCount` settings are **ignored when `suiteXmlFiles` is used**. So a plain
`-Dthreads` cannot reach the XML directly.

### The solution: Maven resource filtering

1. `testng.xml` is tokenized: `thread-count="${threads}"`.
2. `pom.xml` declares a default: `<threads>3</threads>`.
3. Maven **filters** `testng.xml` at build time, substituting the value; a CLI `-Dthreads=N`
   overrides the pom default.
4. Surefire runs the **filtered copy** from `target/test-classes`, not the source file:

```xml
<suiteXmlFile>${project.build.testOutputDirectory}/testng.xml</suiteXmlFile>
```

Only `testng.xml` is filtered; all other test resources are copied unfiltered to avoid
accidental token substitution.

---

## Files Changed

| File | Role |
|------|------|
| `src/test/resources/config/config.properties` | Committed defaults (`browser`, `headless`, `threads`) |
| `src/main/java/com/project/qa/framework/configuration/ConfigReader.java` | Config loader with system-property override |
| `src/main/java/com/project/qa/framework/webdriver/BrowserType.java` | Supported-browser enum with safe parsing |
| `src/main/java/com/project/qa/framework/webdriver/DriverFactory.java` | Per-browser option builders + factory switch |
| `src/test/resources/suites/testng.xml` | Parallel attributes + `${threads}` token |
| `src/test/resources/logback-test.xml` | `[%thread]` in log pattern |
| `pom.xml` | `threads` property, `testng.xml` filtering, filtered-copy suite path, `report` profile |

**Deliberately untouched:** `DriverManager`, `BaseTest`, and all page objects — the
`ThreadLocal` abstraction absorbed cross-browser support with zero cascading rewrites.

---

## Quick Reference

```powershell
# Run in parallel (defaults: chrome, 3 threads)
mvn clean test

# Switch browser
mvn test -Dbrowser=firefox
mvn test -Dbrowser=edge

# Tune parallelism and mode
mvn test -Dthreads=5
mvn test -Dbrowser=chrome -Dheadless=true -Dthreads=4

# Run and auto-open the Allure report (opt-in profile; Ctrl+C to stop the server)
mvn clean verify -Preport
```
