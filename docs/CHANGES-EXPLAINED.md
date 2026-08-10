# Changes Explained — File-by-File Review Guide

> Purpose: help you review the uncommitted changes with confidence before approving them. Every
> added or modified file is listed with **what it is**, **why it exists**, and **how it helps run the
> automation**. Files are grouped by role, newest-feature-first.
>
> Legend: **NEW** = newly added file · **MOD** = existing file modified.

---

## 1. The one-paragraph mental model

The framework already ran **web** tests on Selenium with a `ThreadLocal<WebDriver>`. Appium's
`AndroidDriver` **is a** `WebDriver`, so mobile was added as a *sibling path* behind the same seam:
`DriverFactory` decides web-vs-mobile once, and everything downstream keeps talking to a plain
`WebDriver`. The mobile path adds three responsibilities the web path never needed — **start an
Appium server**, **find the device via adb**, and **get the app onto the device and launched**. Each
of those is one small, single-responsibility class. Nothing about the existing web tests changed.

```
Test (@Test)
  └─ BaseTest.setUp() ── DriverFactory.initDriver()
                              │  platform = web | android   (the ONLY branch point)
                    ┌─────────┴───────────┐
                 WEB │                     │ ANDROID
          ChromeDriver etc.        MobileDriverFactory.createDriver()
                                          │
             AppiumServerManager (self-start) + AndroidDeviceResolver (adb) + AndroidSdkResolver
                                          │
                                   AndroidDriver  ── IS-A ──►  WebDriver  ──► DriverManager (ThreadLocal)
                                          │
                        AndroidAppLauncher.launch(pkg, apk?)
                          ├─ isPackageInstalled? (adb)
                          ├─ install: apk (-Dapp) OR PlayStoreInstaller (by package)
                          └─ activate + wait for RUNNING_IN_FOREGROUND
```

---

## 2. Modified files (6) — what changed and why it's safe

| File | MOD | What changed | Why |
|------|-----|--------------|-----|
| `src/main/java/com/project/qa/core/DriverFactory.java` | MOD | Added a `PlatformType` switch: `WEB → createWebDriver()`, `ANDROID → MobileDriverFactory.createDriver()`. All the existing browser-building code is untouched, just moved under `createWebDriver()`. | This is the single seam where mobile plugs in. Web behaviour is byte-for-byte the same when `platform=web` (the default). |
| `pom.xml` | MOD | Added `io.appium:java-client:9.3.0`; **pinned `selenium.version=4.25.0`**; added Maven profiles `mobile`, `mobile-native`, `mobile-myntra`. | java-client brings Appium support. The Selenium pin is critical (see box below). Profiles select the right TestNG suite + set `platform`/`androidTarget` so you don't pass them by hand. |
| `src/test/resources/config.properties` | MOD | Added mobile keys (`platform`, `androidTarget`, and commented `app`, `appPackage`, `appActivity`, `appiumServerUrl`, `deviceUdid`). Defaults keep `platform=web`. | Committed defaults so a plain `mvn test` still runs web. Mobile keys are documented inline for discoverability. |
| `docs/ARCHITECTURE.md` | MOD | Cross-links to the new mobile/commands docs. | Keeps the docs navigable. |
| `src/test/resources/testng.xml` | MOD | (Web suite descriptor.) | Pre-existing web suite; unrelated to mobile behaviour. |
| `src/test/resources/logback-test.xml` | MOD | Logging config. | Controls log verbosity (business logic on INFO, framework noise on DEBUG). |
| `src/test/java/com/project/qa/tests/SearchProductsTest.java` | MOD | Existing web test. | Pre-existing; not part of the mobile feature. |

> ⚠️ **Why `selenium.version=4.25.0` must stay:** `java-client 9.3.0` declares Selenium as a soft
> range `[4.19.0, 5.0)`, so Maven would otherwise float to **4.46.0**, which **removed**
> `org.openqa.selenium.ContextAware` — a type java-client still references (e.g. `getCurrentPackage()`).
> 4.25.0 is a known-good version that still has it. Do not bump without re-checking this.

---

## 3. NEW — Core mobile plumbing (`src/main/java/com/project/qa/core/`)

These are the heart of the mobile capability. Each does exactly one job.

### `PlatformType.java` **NEW**
Enum `WEB | ANDROID`. `from(String)` defaults to **WEB** when unset, so every existing desktop test
keeps running untouched. This is the value `DriverFactory` switches on.

### `AndroidTarget.java` **NEW**
Enum `NATIVE | WEB` — *what* an Android session drives. `WEB` = Chrome on the device; `NATIVE` = an
installed app or apk. It's an **explicit** setting (not inferred from which capabilities happen to be
set) so misconfiguration fails loudly instead of behaving surprisingly.

### `AndroidSdkResolver.java` **NEW**
Finds the Android SDK **root** and fixes the single most common setup mistake: `ANDROID_HOME` pointing
one level too deep at `…\Sdk\platform-tools`. It trims that suffix and validates the folder actually
contains `platform-tools`. **Why it helps:** a run no longer depends on a perfectly set environment
variable — it self-corrects, then hands the corrected path to the Appium server and to adb.

### `AndroidDeviceResolver.java` **NEW** — the adb workhorse
Talks to `adb` directly (adb is the source of truth). Provides:
- `resolveConnectedDevice()` — auto-selects the one connected device (or requires `-DdeviceUdid` when
  several are attached; fails fast when none). **No device details are hardcoded** — plug in any
  authorised device and it targets it.
- `listThirdPartyPackages()` — `pm list packages -3`, so a test can discover installed apps.
- `isPackageInstalled(pkg)` — reliable install check (Appium's own `isAppInstalled()` gives false
  negatives on Android 11+ due to package-visibility filtering).
- `openPlayStoreListing(pkg)` — the `market://details?id=<pkg>` deep link; the app-agnostic entry
  point for installing anything by id.
- `grantRuntimePermissions(pkg, perms)` — best-effort `pm grant` so first-launch permission dialogs
  can't block an unattended run.
- `allowSilentInstalls()` — disables Play Protect's sideload verifier so an apk install completes with
  no on-device "unsafe app" tap (standard CI practice).

### `AppiumServerManager.java` **NEW**
Owns the Appium server lifecycle so **you never start a server by hand**. The first mobile thread
boots one shared local server on a free port; later threads reuse it; a JVM shutdown hook stops it so
nothing leaks. If you provide an external `appiumServerUrl`, it steps aside and uses yours. It also
injects the corrected `ANDROID_HOME`/`ANDROID_SDK_ROOT` (from `AndroidSdkResolver`) into the server
environment so UiAutomator2 can find the SDK.

### `MobileDriverFactory.java` **NEW**
Builds the `AndroidDriver`. It wires together the server URL, the auto-detected device coordinates,
and the target (WEB vs NATIVE), then returns a plain `WebDriver`. Key details:
- **WEB target** sets `chromedriverAutodownload=true` — kills the #1 mobile-web failure (Chrome vs
  Chromedriver version skew).
- **NATIVE target** (`applyNativeApp`) chooses a session shape by what you provided: package+activity
  → launch that activity; package only → a *bare* session (so the launcher/test decides install vs
  activate); apk only → install+launch; nothing → bare session for discovery.
- `newCommandTimeout=300s` so long installs/debugging don't get the session reaped.

### `AndroidAppLauncher.java` **NEW** — "get the app running, whatever it takes"
`launch(driver, pkg, apkSource)` orchestrates the hands-off guarantee:
1. `ensureInstalled` — if already installed (adb check), skip. Else, if an apk (`-Dapp`) was given,
   **sideload it** (silence Play Protect, 3-min timeout, auto-grant permissions, re-verify). Else,
   **fall back to `PlayStoreInstaller`** (install by package id).
2. `activateApp(pkg)`.
3. `waitUntilForeground` — polls `queryAppState == RUNNING_IN_FOREGROUND` for 15s (a cold start
   flashes the launcher/splash first, so a single read would be flaky).

### `PlayStoreInstaller.java` **NEW** — the reusable "master command"
Installs **any Play-published app by package id alone** — no apk, no URL. This is the class you asked
for around `market://details?id=…`:
1. `openPlayStoreListing(pkg)` — the deep link.
2. `tapInstallButton` — taps the store's own **Install** control. (The visible "Install" label is a
   *non-clickable* node whose clickable ancestor has no text, so we match by text/content-desc and let
   the centre-tap land on the button. Falls back to accessibility-id.)
3. `waitUntilInstalled` — polls adb until the package appears, issuing **keepalive queries** so
   Appium's idle timer doesn't reap the session during a multi-minute download.

### `BrowserType.java` **NEW**
Enum `CHROME | FIREFOX | EDGE` for the web path. Foundational (used by `DriverFactory.createWebDriver`).

---

## 4. NEW — Config access (`src/main/java/com/project/qa/config/`)

### `ConfigReader.java` **NEW**
Central config gateway. Resolution order per key: **`-Dkey` system property first, then
`config.properties`** — so CLI/CI can override committed defaults without editing files. Added the
mobile getters: `getPlatform`, `getAndroidTarget`, `getAppiumServerUrl`, `getDeviceUdid`, `getApp`,
`getAppPackage`, `getAppActivity`. `resolve()` returns `null` when a key is unset/blank, which is what
lets "package only" trigger discovery/Play-Store paths.

---

## 5. NEW — Mobile Page Objects (`src/main/java/com/project/qa/pages/`)

Mirrors the existing web POM style (private locators, public intent-revealing actions, fluent
hand-off). Locators are **content-desc** based because the Myntra app is React Native and doesn't
expose stable resource-ids.

### `MyntraAppHomePage.java` **NEW**
Home-feed page. `dismissLoginWallIfPresent()` (taps `login_skip_button` if the login wall shows —
absence is normal, not an error), `isSearchBarDisplayed()` (waits on `HPSearchBar`), and
`openSearch()` (taps the search bar, returns the search page).

### `MyntraAppSearchPage.java` **NEW**
Search page. `searchFor(term)` types into `search_default_search_text_input` and presses ENTER;
`hasResults()` waits for any product tile (content-desc starting `PRODUCT_TILE`) — note the results
(PLP) page uses `PRODUCT_TILE_*`, distinct from the home feed's `PRODUCT_GRID_*`.

---

## 6. NEW — Tests (`src/test/java/com/project/qa/tests/`)

| File | NEW | What it proves | Profile |
|------|-----|----------------|---------|
| `MobileWebSmokeTest.java` | NEW | Appium mobile-web wiring end-to-end (self-started server + adb detection + Chromedriver autodownload) by loading `example.com` in device Chrome. | `-Pmobile` |
| `NativeAppLaunchTest.java` | NEW | A native app launches (asserts *something* is foreground) — app-agnostic, works for any apk/package. | `-Pmobile-native` |
| `LaunchDeviceAppTest.java` | NEW | Discovers installed apps and (optionally) launches one by `-DappPackage`, delegating to `AndroidAppLauncher` (so it auto-installs if missing). | `-Pmobile-native` |
| `MyntraAppTest.java` | NEW | **The 5 Myntra end-to-end tests** (launch, correct package, home search bar, search returns results, background/restore). Setup is fully hands-off: install-if-missing → grant permissions → cold-start to home → skip login. | `-Pmobile-myntra` |

> `BaseTest.java` (pre-existing) is unchanged in spirit: `@BeforeMethod` builds the driver via
> `DriverFactory.initDriver()`, `@AfterMethod` quits it. Because `AndroidDriver` is a `WebDriver`, it
> needed no mobile-specific edits.

---

## 7. NEW — TestNG suites & the profiles that pick them

| File | NEW | Role |
|------|-----|------|
| `src/test/resources/testng-mobile.xml` | NEW | Mobile-web suite (`MobileWebSmokeTest`). Selected by `-Pmobile`. |
| `src/test/resources/testng-mobile-native.xml` | NEW | Native suite (`LaunchDeviceAppTest`). Selected by `-Pmobile-native`. |
| `src/test/resources/testng-mobile-myntra.xml` | NEW | Myntra suite (`MyntraAppTest`). Selected by `-Pmobile-myntra`. |

Each `thread-count=1` (one physical device = one session) and registers the Allure + screenshot
listeners. The matching Maven profile in `pom.xml` overrides the surefire `suiteXmlFile` and sets
`platform=android` + the right `androidTarget`, so a single `-P…` flag configures the whole run.

---

## 8. NEW — Listener (`src/test/java/com/project/qa/listeners/`)

### `ScreenshotListener.java` **NEW**
TestNG listener that captures a screenshot on failure (works for web and mobile alike since both are
`WebDriver`/`TakesScreenshot`). Foundational; attaches evidence to Allure.

---

## 9. NEW — Documentation (`docs/`)

| File | NEW | Contents |
|------|-----|----------|
| `docs/COMMANDS.md` | NEW | Copy-paste command cheatsheet for every web/mobile run, including auto-install and the Myntra suite. |
| `docs/CLASS-INTERACTION-MOBILE-APPIUM.md` | NEW | End-to-end mobile flow and how the classes interact. |
| `docs/CLASS-INTERACTION-BROWSER-LAUNCH.md` | NEW | The equivalent for the web browser-launch flow. |
| `docs/DEEP-DIVE-HOW-IT-WORKS.md` | NEW | Deeper architectural walkthrough. |
| `docs/PARALLEL-AND-CROSS-BROWSER.md` | NEW | How parallelism + cross-browser execution work. |
| `docs/FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md` | NEW | Notes on the Myntra headless anti-bot finding and diagnostics. |
| `docs/CHANGES-EXPLAINED.md` | NEW | **This file.** |

Docs are documentation only — nothing here affects a build or test run.

---

## 10. NEW — `.github/` tooling (not automation runtime)

These configure AI-assistant behaviour/standards for the repo; they do **not** run in your tests.

| File | NEW | Role |
|------|-----|------|
| `.github/instructions/sdet-automation-standards.instructions.md` | NEW | Coding standards applied to `**/*.java`. |
| `.github/agents/principal-sdet-mentor.agent.md` | NEW | Custom agent definition. |
| `.github/agents/designer-assistant.agent.md` | NEW | Custom agent definition. |
| `.github/prompts/implement-automation-task.prompt.md` | NEW | Reusable prompt. |

---

## 11. Suggested review order

1. **`DriverFactory.java`** (MOD) — see the single web/mobile seam; confirm web is unchanged.
2. **`pom.xml`** (MOD) — the dependency, the Selenium pin, the three profiles.
3. **`MobileDriverFactory` → `AppiumServerManager` → `AndroidDeviceResolver` → `AndroidSdkResolver`** —
   how a session is built with no manual setup.
4. **`AndroidAppLauncher` + `PlayStoreInstaller`** — the install-if-missing guarantee.
5. **`MyntraAppTest` + the two Page Objects** — the 5 tests and their locators.
6. **`testng-mobile-myntra.xml`** — how the suite is selected.

## 12. How to verify before approving

```powershell
mvn -q clean test-compile          # everything compiles
mvn test                           # web suite still green (proves no web regression)
mvn test "-Pmobile-myntra"         # the 5 Myntra tests (device connected) → 5/5
```
