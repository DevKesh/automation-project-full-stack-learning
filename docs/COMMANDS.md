# Command Cheatsheet: Running Web & Mobile Tests

> A single copy-paste reference for every command used to trigger apps and run tests in this
> framework. Commands are written for **Windows PowerShell** — note the quoting around `-D` / `-P`
> arguments (`"-DappPackage=..."`), which PowerShell requires. Related reading:
> - [ARCHITECTURE.md](./ARCHITECTURE.md) — the framework map
> - [CLASS-INTERACTION-BROWSER-LAUNCH.md](./CLASS-INTERACTION-BROWSER-LAUNCH.md) — web launch flow
> - [CLASS-INTERACTION-MOBILE-APPIUM.md](./CLASS-INTERACTION-MOBILE-APPIUM.md) — Android/Appium flow
> - [HOW-TO-ADD-TESTS.md](./HOW-TO-ADD-TESTS.md) — add a new web/API/mobile test the right way

---

## 0. Quick map — which command do I want?

| Goal | Command |
|------|---------|
| Run the web suite | `mvn test` |
| Run **only the API group** | `mvn test "-Papi"` |
| Run **only the Web group** | `mvn test "-Pweb"` |
| Run web on a specific browser | `mvn test "-Dbrowser=edge"` |
| Run the Android **mobile-web** suite (Chrome on device) | `mvn test "-Pmobile"` |
| **Discover** installed apps on the device | `mvn test "-Pmobile-native"` |
| **Launch** an installed app by package | `mvn test "-Pmobile-native" "-DappPackage=com.myntra.android"` |
| **Auto-install from the Play Store** (no apk), then launch | `mvn test "-Pmobile-native" "-DappPackage=<pkg>"` |
| **Auto-install if missing** (sideload apk), then launch | `mvn test "-Pmobile-native" "-DappPackage=<pkg>" "-Dapp=<apk-path-or-url>"` |
| Install + launch an `.apk` | `mvn test "-Pmobile-native" "-Dapp=C:/abs/path/app.apk"` |
| Run the **Myntra app suite** (5 e2e tests) | `mvn test "-Pmobile-myntra"` |
| Open the Allure report | `mvn clean test "-Preport"` |

---

## 0b. Test groups — run one category at a time (`api` / `web` / `mobile`)

Every `@Test` is now tagged with a TestNG **group** — `api`, `web`, or `mobile` — so you can trigger a
single category instead of the whole suite.

```powershell
mvn test "-Papi"            # API tests only  (PostApiTest)
mvn test "-Pweb"            # Web UI tests only (BasicLaunchTest, SearchProductTest, SearchProductsTest)
mvn test "-Pmobile"         # mobile-web session   (MobileWebSmokeTest)
mvn test "-Pmobile-native"  # native session       (LaunchDeviceAppTest / NativeAppLaunchTest)
mvn test "-Pmobile-myntra"  # Myntra native app    (MyntraAppTest)
```

### What changed

- Added a `com.project.qa.constants.TestGroups` class holding the group-name constants
  (`API`, `WEB`, `MOBILE`).
- Tagged every `@Test` with `groups = TestGroups.X`.
- Added `testng-api.xml` and `testng-web.xml`, each with a `<groups><run><include>` filter that runs
  only its category, and wired them to new Maven profiles `-Papi` / `-Pweb`. These suites (and the
  default `testng.xml`) **package-scan** their tests and select by group, so a new web/API test joins
  the run automatically — no `<class>` line to maintain. See
  [HOW-TO-ADD-TESTS.md](./HOW-TO-ADD-TESTS.md).
- Added the same `mobile` group `<include>` to the existing `testng-mobile*.xml` suites.

### Why it was done

- **Selective, faster feedback** — run just the layer you touched (e.g. only `api`) instead of the
  full suite, which shortens the local loop and lets CI split work into targeted jobs.
- **A single source of truth** — group names live as constants in `TestGroups`, so a typo fails to
  compile instead of silently dropping a test from a run. The `<include>` in each suite XML must match
  those exact strings.
- **Mobile stays split by session type on purpose** — all mobile tests share the `mobile` tag for
  filtering/reporting, but they can't run in one suite: `MobileWebSmokeTest` needs a **browser**
  session while the native tests need a **native** session (set once per run via `platform` /
  `androidTarget`). Keeping them under their existing profiles preserves the correct Appium session
  for each while still honouring the group filter.

---

## 1. Web tests (desktop browsers)

```powershell
# Default: Chrome, non-headless, 3 threads (values from config.properties)
mvn test

# Pick a browser (chrome | firefox | edge)
mvn test "-Dbrowser=firefox"

# Headless
mvn test "-Dheadless=true"

# Control parallelism (thread-count in the suite)
mvn test "-Dthreads=5"

# Combine overrides
mvn test "-Dbrowser=edge" "-Dheadless=true" "-Dthreads=4"
```

> Note: the web suite targets Myntra's **desktop** site. Headless is known to trip Myntra's
> anti-bot page — see
> [FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md](./FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md).

---

## 2. Android — mobile web (Chrome on the device)

Launches Chrome on the connected device; Appium auto-downloads the matching Chromedriver.

```powershell
# Runs testng-mobile.xml (MobileWebSmokeTest)
mvn test "-Pmobile"

# Pin a device when several are connected
mvn test "-Pmobile" "-DdeviceUdid=65041XEA35H152"

# Reuse an already-running Appium server instead of the self-managed one
mvn test "-Pmobile" "-DappiumServerUrl=http://127.0.0.1:4723"
```

---

## 3. Android — native apps (no apk required)

The `mobile-native` profile starts a **bare device session**; you then launch installed apps by
package name. No `.apk` on the host, no activity to look up.

### 3a. Discover what's installed (no launch)

```powershell
mvn test "-Pmobile-native"
```

Logs every user-installed package, then stops. Use the list to pick a package for 3b.

### 3b. Launch an installed app by package (the main one)

```powershell
mvn test "-Pmobile-native" "-DappPackage=<package>"
```

Ready-to-run examples for **this device** (Pixel 10a):

| App | Command |
|-----|---------|
| Myntra | `mvn test "-Pmobile-native" "-DappPackage=com.myntra.android"` |
| Instagram | `mvn test "-Pmobile-native" "-DappPackage=com.instagram.android"` |
| Spotify | `mvn test "-Pmobile-native" "-DappPackage=com.spotify.music"` |
| WhatsApp | `mvn test "-Pmobile-native" "-DappPackage=com.whatsapp"` |
| Swiggy | `mvn test "-Pmobile-native" "-DappPackage=in.swiggy.android"` |
| Zomato | `mvn test "-Pmobile-native" "-DappPackage=com.application.zomato"` |
| PhonePe | `mvn test "-Pmobile-native" "-DappPackage=com.phonepe.app"` |
| Amazon | `mvn test "-Pmobile-native" "-DappPackage=in.amazon.mShop.android.shopping"` |
| Netflix | `mvn test "-Pmobile-native" "-DappPackage=com.netflix.mediaclient"` |
| LinkedIn | `mvn test "-Pmobile-native" "-DappPackage=com.linkedin.android"` |
| Zerodha Kite | `mvn test "-Pmobile-native" "-DappPackage=com.zerodha.kite3"` |
| ChatGPT | `mvn test "-Pmobile-native" "-DappPackage=com.openai.chatgpt"` |

### 3c. Install and launch from an `.apk`

```powershell
mvn test "-Pmobile-native" "-Dapp=C:/absolute/path/to/app.apk"
```

Runs `NativeAppLaunchTest` semantics via the same profile — Appium installs the apk and launches its
manifest launcher activity (no activity needed).

### 3c-play. Auto-install from the Play Store (no apk at all)

```powershell
# Just give the package id — the framework installs it straight from the Play Store if missing
mvn test "-Pmobile-native" "-DappPackage=com.myntra.android"
```

This is the most hands-off path and works for **any** Play-published app without sourcing an apk.
When the package is absent, `PlayStoreInstaller` (the reusable "master command" class) drives the
store itself:

1. Opens the app's listing via the `market://details?id=<pkg>` deep link
   (`AndroidDeviceResolver.openPlayStoreListing`).
2. Taps the store's own **Install** button (matched by text/content-desc; the visible label sits on a
   non-clickable node, so a tap on it hits the underlying button).
3. Polls adb until the package is installed (issuing keepalive queries so Appium's session isn't
   reaped during a multi-minute download).

> **Prerequisite:** the device must be signed into a Google account (as any real handset is). If the
> app isn't on the Play Store, use the sideload path (3c / 3c-auto) with `-Dapp` instead.

### 3c-auto. Auto-install if missing, then launch (fully hands-off)

```powershell
# apk source can be a local path OR a direct download URL
mvn test "-Pmobile-native" "-DappPackage=<package>" "-Dapp=<apk-path-or-url>"

# example (Appium's ApiDemos sample):
mvn test "-Pmobile-native" "-DappPackage=io.appium.android.apis" "-Dapp=https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk"
```

This is the CI-grade path for **any** device — you cannot assume the app is pre-installed:

1. `AndroidAppLauncher` checks install state via adb (`pm list packages <pkg>`).
2. **If present** → skips install and just activates the app.
3. **If absent** → disables Play Protect's sideload verifier (so no on-device "unsafe app" prompt
   blocks the run), installs the `-Dapp` apk with a 3-minute timeout + auto-granted permissions,
   re-verifies, then activates.
4. Polls for `RUNNING_IN_FOREGROUND` before passing.

> **Play Protect note:** installing *any* sideloaded apk normally raises an on-device confirmation.
> The framework disables the package verifier via
> `adb shell settings put global verifier_verify_adb_installs 0` (and `package_verifier_enable 0`)
> so installs are silent — this is standard CI practice and leaves the device in a hands-off state.
> Re-enable anytime with the same commands using `1`.

### 3d. Launch a specific package + activity (explicit)

```powershell
mvn test "-Pmobile-native" "-DappPackage=com.example.app" "-DappActivity=com.example.app.MainActivity"
```

Use this only when you need a **specific** activity rather than the default launcher.

### 3e. Run the Myntra app test suite (5 end-to-end tests)

```powershell
mvn test "-Pmobile-myntra"
```

Zero manual setup. Each test's setup installs Myntra from the Play Store if it's missing, pre-grants
common runtime permissions (so no system dialog blocks the run), cold-starts the app onto the home
feed, and skips the login wall. The five tests (`MyntraAppTest`):

| # | Test | Asserts |
|---|------|---------|
| 1 | `appLaunchesIntoForeground` | Myntra is `RUNNING_IN_FOREGROUND` |
| 2 | `correctAppPackageIsForemost` | foreground package is `com.myntra.android` |
| 3 | `homeSearchBarIsVisible` | home feed's search bar renders |
| 4 | `searchReturnsProductResults` | searching "shoes" yields product tiles |
| 5 | `appRestoresAfterBackgrounding` | app returns to foreground after backgrounding |

Locators live in the mobile Page Objects `MyntraAppHomePage` / `MyntraAppSearchPage`
(content-desc based, since the app is React Native).

---

## 4. Reporting (Allure)

```powershell
# Run tests, then build + serve the Allure report in a browser
mvn clean test "-Preport"

# Mobile run with report
mvn clean test "-Pmobile-native" "-DappPackage=com.myntra.android" "-Preport"
```

Raw results land in `target/allure-results`.

> **Results are auto-cleared each run.** Surefire writes one UUID-named JSON per test and never
> overwrites, so results used to pile up across every run and `mvn allure:serve` showed a stale,
> cumulative report. A `maven-clean-plugin` execution bound to the `initialize` phase now wipes
> **only** `target/allure-results` at the start of every `mvn test`, so the served report always
> reflects just the latest execution.
>
> Correct workflow — run the tests first, then serve:
> ```powershell
> mvn test            # (or -Papi / -Pweb / -Pmobile ...) — refreshes target/allure-results
> mvn allure:serve    # serves the results from that run
> ```
> Running `mvn allure:serve` on its own does **not** execute tests; it only re-serves whatever the
> last `mvn test` produced.

---

## 5. Device & tooling helpers (adb / appium)

### Device checks

```powershell
adb devices -l                                   # must show '<serial>   device' (not unauthorized/offline)
adb shell getprop ro.product.model               # device model
adb shell getprop ro.build.version.release       # Android version
```

### App discovery on the device

```powershell
adb shell pm list packages -3                    # user-installed (third-party) apps
adb shell pm list packages                       # all packages (incl. system)
adb shell pm list packages | Select-String myntra   # search for a package
```

### Find an app's launchable activity (for section 3d)

```powershell
adb shell cmd package resolve-activity --brief com.myntra.android   # prints package/activity
```

### See what's currently in the foreground

```powershell
adb shell dumpsys activity activities | Select-String "mResumedActivity"
```

### Appium environment

```powershell
appium -v                                        # server version
appium driver list --installed                   # installed drivers (need: uiautomator2)
appium driver update uiautomator2                # update the Android driver
```

---

## 6. Configuration keys (override any with `-Dkey=value`)

Committed defaults live in `src/test/resources/config.properties`; the `mobile` / `mobile-native`
profiles set `platform`/`androidTarget` for you.

| Key | Purpose | Example |
|-----|---------|---------|
| `browser` | Desktop browser | `-Dbrowser=edge` |
| `headless` | Headless desktop run | `-Dheadless=true` |
| `threads` | Suite thread-count | `-Dthreads=4` |
| `platform` | `web` or `android` | (set by profiles) |
| `androidTarget` | `web` or `native` | (set by profiles) |
| `appPackage` | Installed app to launch | `-DappPackage=com.spotify.music` |
| `appActivity` | Specific activity | `-DappActivity=.MainActivity` |
| `app` | `.apk` path to install+launch | `-Dapp=C:/path/app.apk` |
| `deviceUdid` | Pin a device (multi-device) | `-DdeviceUdid=65041XEA35H152` |
| `appiumServerUrl` | Reuse an external server | `-DappiumServerUrl=http://127.0.0.1:4723` |

---

## 7. Cheatsheet in one screen

```powershell
# WEB
mvn test                                                        # default web suite
mvn test "-Dbrowser=firefox" "-Dheadless=true"                  # web overrides

# RUN BY GROUP (api | web | mobile)
mvn test "-Papi"                                               # API tests only
mvn test "-Pweb"                                               # Web UI tests only

# ANDROID — MOBILE WEB
mvn test "-Pmobile"                                             # Chrome on device

# ANDROID — NATIVE
mvn test "-Pmobile-native"                                      # discover installed apps
mvn test "-Pmobile-native" "-DappPackage=com.myntra.android"    # install-from-Play-Store-if-missing, then launch
mvn test "-Pmobile-native" "-DappPackage=<pkg>" "-Dapp=<apk>"   # sideload apk if missing, then launch
mvn test "-Pmobile-native" "-Dapp=C:/path/app.apk"             # install + launch apk
mvn test "-Pmobile-myntra"                                      # Myntra app suite (5 e2e tests)

# REPORT
mvn clean test "-Preport"                                       # Allure report

# DEVICE
adb devices -l                                                  # verify device
adb shell pm list packages -3                                   # list installed apps
```
