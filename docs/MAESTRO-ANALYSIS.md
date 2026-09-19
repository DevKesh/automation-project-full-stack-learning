# Maestro — Evaluation & Integration Analysis

> A pragmatic assessment of the [Maestro](https://docs.maestro.dev/) mobile/web UI
> automation framework, written specifically against **this** project's current
> Appium + Selenium + TestNG stack (`pom.xml`, `com.project.qa.framework.mobiledriver.*`,
> `MyntraAppTest`, the `-Pmobile*` profiles).
>
> **TL;DR** — Maestro is excellent for *fast, low-maintenance end-to-end mobile
> flows* written as YAML. It is **not** a drop-in replacement for Appium inside our
> Java/TestNG framework; it is a **parallel tool** with its own runner. Integrating
> it is *easy to pilot* (a single binary + a few `.yaml` files) but *architecturally
> separate* from our `ThreadLocal<WebDriver>` page-object world. Recommendation:
> adopt it as a **complementary smoke/e2e layer**, invoked from Maven/CI, rather
> than trying to fold it into the existing Appium session model.

---

## 1. What Maestro is

Maestro is an open-source UI automation framework for **mobile (Android/iOS),
React Native, Flutter, and web browsers**. Tests ("Flows") are written in
**declarative YAML**, not code:

```yaml
# flows/myntra_search.yaml
appId: com.myntra.android
---
- launchApp
- tapOn: "Search for products"
- inputText: "shoes"
- pressKey: Enter
- assertVisible: "shoes"
```

That five-line file is the functional equivalent of our
`MyntraAppTest.searchReturnsProductResults()` plus its page objects
(`MyntraAppHomePage`, `MyntraAppSearchPage`) and the launch/permission scaffolding
in `prepareApp()`.

### The core idea: "arm's length" black-box control

| Aspect | Appium (our current stack) | Maestro |
|---|---|---|
| How it drives the app | WebDriver/UiAutomator2 session, W3C protocol | Reads the OS **accessibility tree**, sends device-level taps/swipes/text |
| Test language | Java (Selenium/Appium client) | YAML flows (+ optional JS snippets) |
| App knowledge | Black-box, but via driver session | Black-box, "pilots the device, not the app" |
| Waiting model | Explicit waits / `WebDriverWait` | **Implicit** — auto-waits for the UI to "settle", no `sleep()` |
| Cross-app / system UI | Limited, driver-scoped | Can touch system dialogs, settings, notifications |

Because it works off the accessibility layer, the **same flow runs across Native,
RN, and Flutter** without framework-specific bindings — this is Maestro's headline
advantage over Appium.

---

## 2. How it works (architecture)

1. **Maestro CLI** — the open-source engine. It parses your `.yaml` flows and, on a
   connected device/emulator, **installs a small companion "driver" app** on the
   device to inspect the screen and perform taps/swipes/text input.
   - On Android it talks over **ADB** (same `adb`/platform-tools our
     `AndroidDeviceResolver` and `AndroidSdkResolver` already depend on).
   - On iOS it drives Xcode Simulators (macOS only).
2. **Maestro Cloud** — hosted device farm for running flows in parallel across many
   virtual devices, with a first-class GitHub Actions integration. This is the paid
   scaling backend (analogous to BrowserStack/Sauce for our Selenium/Appium tests).

### Built-in resilience (the real selling point vs Appium)

- **Zero-wait intelligence** — no manual `sleep()`/explicit waits; it waits for
  network + animations automatically. This directly targets the class of flakiness
  our `MyntraAppTest` works around manually (the forced cold-restart in
  `prepareApp()`, `newCommandTimeout(300s)`, permission pre-granting).
- **Built-in tolerance / retries** — it "embraces instability" and re-attempts
  settling before failing.
- **`scrollUntilVisible`, hooks (`onFlowStart`/`onFlowComplete`), sub-flows** —
  reusable login/setup blocks without page-object boilerplate.
- **No compilation** — flows run interpreted; edit-and-rerun is instant (contrast
  with our `mvn test` compile cycle).

---

## 3. Prerequisites

### 3.1 To run Maestro at all (Android, on this Windows machine)

| Requirement | Status in this project | Notes |
|---|---|---|
| **Java 17+**, `JAVA_HOME` set | ✅ We build on **Java 21** (`maven.compiler.target=21`) | Already satisfied. |
| **Android SDK + `adb` / platform-tools** on PATH | ✅ Resolved by `AndroidSdkResolver` / `AndroidDeviceResolver` | Maestro uses the same ADB bridge. |
| A **connected device or running emulator** | ✅ Our mobile profiles already require this | Same device works for both tools. |
| **Maestro CLI** installed | ❌ Not installed | See install note below. |
| **Node/Appium server** | ⚠️ Required by *our* stack, **not** by Maestro | Maestro does **not** need Appium, Node, or the `AppiumServerManager`. |

**Windows install** (per official docs — there is no `curl | bash` on native
Windows):

1. Download `maestro.zip` from the GitHub releases page.
2. Extract to a stable path, e.g. `C:\maestro`.
3. Add `C:\maestro\bin` to `PATH`.
4. Verify: `maestro --help`.

> Note: Maestro's richest tooling (the `curl` installer, iOS simulator support) is
> macOS-first. On Windows, Android flows are fully supported; **iOS is not**
> (needs macOS + Xcode). Our project is currently Android-only, so this is a
> non-issue today.

### 3.2 To author flows efficiently

- Flows are plain `.yaml` files, hand-authored in any editor.
- Stable element selectors. Maestro matches primarily by **visible text**,
  then **accessibility id / resource-id / content-description / testTag**. Our
  existing `MyntraAppSearchPage`/`MyntraAppHomePage` locators (resource-ids,
  text) translate almost 1:1 into Maestro selectors.

### 3.3 To scale (optional)

- A **Maestro Cloud** account + API key for parallel/CI-hosted execution, or
  self-hosted emulators in CI.

---

## 4. How easily does it integrate with *our* framework?

This is the crux. There are two very different questions:

### 4.1 "Can I run Maestro alongside this repo?" → **Very easy** ✅

Maestro is a **single binary + YAML files**. It shares our existing device/ADB
prerequisites and needs nothing from the Java layer. A realistic, low-risk pilot:

```
src/test/resources/maestro/
  config.yaml
  flows/
    myntra_launch.yaml
    myntra_search.yaml
```

Run locally:

```bash
maestro test src/test/resources/maestro/flows
```

Wire it into Maven with a dedicated profile, mirroring our existing `-Pmobile-*`
profiles, using `exec-maven-plugin` (or `maven-antrun`) to shell out to `maestro`:

```bash
mvn -Pmaestro verify     # runs the Maestro flows, no Appium/TestNG involved
```

CI is equally simple — Maestro publishes an official **GitHub Actions** step, and
it emits **JUnit XML** (`maestro test --format junit`) so results can surface in
the same CI test reporting our Surefire output uses.

### 4.2 "Can Maestro plug into our Java page-object / `ThreadLocal<WebDriver>` / Allure model?" → **No, and that's by design** ❌

Key architectural mismatches to be honest about:

| Our framework assumes… | Maestro provides… | Consequence |
|---|---|---|
| Tests are **Java + TestNG** (`BaseTest`, groups, `RetryAnalyzer`) | Tests are **YAML flows**, run by its own engine | No shared test lifecycle; Maestro flows don't see `BaseTest`/listeners. |
| One **`WebDriver` per thread** (`DriverManager`), Appium session | Its **own on-device driver app**, no WebDriver | Cannot reuse `MobileDriverFactory`, `AppiumServerManager`, or page objects. |
| **Allure + AspectJ** reporting via `@Step` | Its own console/JUnit/HTML output | Reports live in a separate place unless we write a bridge (parse JUnit XML → Allure). |
| **Parallelism** via TestNG `thread-count` + `ThreadLocal` | Parallelism via **Maestro Cloud** or multiple CLI invocations | Local parallel story is weaker than our TestNG model. |
| Rich **imperative logic** (REST setup, JSON data, custom Java) | Declarative YAML + **sandboxed JS** (no filesystem access) | Complex data-driven / API-seeded scenarios are harder in pure Maestro. |

**Bottom line:** you do **not** refactor `MyntraAppTest` *into* Maestro; you
**re-express** those flows as YAML and run them as a separate suite. The two
coexist; they don't merge.

---

## 5. Where Maestro wins vs where our Appium stack wins

**Prefer Maestro for:**
- Fast, readable **happy-path e2e / smoke flows** (login, search, checkout).
- Reducing the exact flakiness scaffolding we hand-rolled in `MyntraAppTest`
  (`prepareApp()` restart dance, `newCommandTimeout`, permission pre-grants).
- QA/non-Java contributors authoring tests (plain YAML, no compile step).
- Cross-stack (Native/RN/Flutter) reuse of the same flow.

**Keep Appium/our framework for:**
- Tests needing **imperative logic**, REST-Assured API seeding, JSON data
  models (`SearchData`, `Post`), and shared Java utilities.
- **Unified Allure + AspectJ reporting** across web + API + mobile in one run.
- **TestNG-managed parallelism**, retries (`RetryAnalyzer`), groups, and the
  cross-cutting web/API suites that Maestro simply doesn't cover the same way.

---

## 6. Recommended adoption path (low risk, incremental)

1. **Pilot (½ day):** Install Maestro CLI on Windows, connect the existing
   Android device, and reproduce the 5 `MyntraAppTest` scenarios as YAML flows.
   Compare authoring speed and flakiness head-to-head.
2. **Wire a `-Pmaestro` Maven profile** that shells out to `maestro test … --format
   junit`, mirroring the `-Pmobile-myntra` profile so both are one-command runs.
3. **CI:** add a separate Maestro job (GitHub Actions) that runs flows on an
   emulator and publishes the JUnit results. Keep it **parallel to**, not merged
   with, the Surefire/TestNG job.
4. **Decide the boundary:** use Maestro for broad mobile **smoke/e2e**; keep
   Appium for **data-driven / logic-heavy** mobile tests and the web+API suites.
5. **(Optional) Reporting bridge:** if a single dashboard is required, convert
   Maestro's JUnit XML into Allure results so it appears next to our existing
   report.

---

## 7. Effort & risk summary

| Dimension | Rating | Why |
|---|---|---|
| Install / prerequisites | 🟢 Easy | Java 21 ✅, ADB/SDK ✅ already present; only the CLI binary is new. |
| Authoring first flows | 🟢 Easy | Hand-written YAML; our locators map over cleanly. |
| Running alongside repo (Maven/CI) | 🟢 Easy | Single binary, JUnit output, official GH Action. |
| Merging into Java/TestNG/Allure model | 🔴 Hard / not intended | Separate engine; no WebDriver/PageObject/Allure reuse. |
| iOS on this Windows host | 🔴 Not possible | iOS needs macOS + Xcode (Android-only here anyway). |
| Complex data-driven scenarios | 🟡 Moderate | Sandboxed JS only; heavy logic stays in Appium. |

**Verdict:** Adopt Maestro as a **complementary, YAML-based mobile e2e/smoke
layer** run from its own Maven profile + CI job. It's cheap to trial and pays off
in flakiness reduction and authoring speed, but treat it as a **sibling** to the
Appium framework — not a replacement for the `ThreadLocal<WebDriver>` page-object
architecture.

---

### Sources
- Maestro docs — What is Maestro, How Maestro works, CLI installation, Supported
  platforms: <https://docs.maestro.dev/>
