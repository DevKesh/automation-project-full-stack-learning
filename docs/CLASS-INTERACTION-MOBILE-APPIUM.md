# Class Interaction: Launching an Android Session from an `mvn` Command (Appium)

> This document focuses on **how the classes collaborate** to turn a single `mvn` command into a
> running Android session — who calls whom, in what order, and what each hand-off passes along. It
> is the mobile counterpart to
> [CLASS-INTERACTION-BROWSER-LAUNCH.md](./CLASS-INTERACTION-BROWSER-LAUNCH.md) and complements:
> - [ARCHITECTURE.md](./ARCHITECTURE.md) — the framework map
> - [DEEP-DIVE-HOW-IT-WORKS.md](./DEEP-DIVE-HOW-IT-WORKS.md) — the *mechanics* (what `-D` is, JVM internals)
> - [PARALLEL-AND-CROSS-BROWSER.md](./PARALLEL-AND-CROSS-BROWSER.md) — the *design decisions & trade-offs*
> - [FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md](./FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md) — the on-failure hook

---

## The trigger

```powershell
mvn test -Pmobile
```

The `mobile` Maven profile injects `-Dplatform=android` and swaps the suite to `testng-mobile.xml`.
From this one line, the framework self-starts an Appium server, auto-detects the connected device,
and opens an Android session — with **no manual server start and no hardcoded device details**.

---

## The design payoff (read this first)

`DriverManager` stores a `ThreadLocal<WebDriver>`. Appium's `AndroidDriver` **is a** `WebDriver`
(`AndroidDriver` → `AppiumDriver` → `RemoteWebDriver` → implements `WebDriver`). Because the mobile
factory returns a plain `WebDriver`, the following classes needed **zero changes** to gain mobile
support:

| Class | Why it is untouched |
|-------|---------------------|
| `DriverManager` | Already stores any `WebDriver`, mobile included |
| `BasePage` | `WebDriverWait` + `By` locators work against `AndroidDriver` |
| `BaseTest` | `initDriver()` / `quitDriver()` lifecycle is platform-agnostic |
| `ScreenshotListener` | Casts to `TakesScreenshot`, which `AndroidDriver` implements |

The **only** platform-aware seam is `DriverFactory`. Everything mobile-specific lives behind it.

---

## The collaborators and their contracts

| Class | Receives | Produces | Talks to |
|-------|----------|----------|----------|
| `BaseTest` | TestNG lifecycle callback | (side effect: driver ready) | → `DriverFactory`, `DriverManager` |
| `DriverFactory` | *(nothing — static call)* | a stored `WebDriver` | → `ConfigReader`, `PlatformType`, `MobileDriverFactory`, `DriverManager` |
| `PlatformType` | a `String` (`"android"`) | a validated enum constant | *(pure — no collaborators)* |
| `MobileDriverFactory` | *(nothing — static call)* | an `AndroidDriver` (a `WebDriver`) | → `AppiumServerManager`, `AndroidDeviceResolver`, `AndroidTarget`, `ConfigReader` |
| `AppiumServerManager` | *(nothing — static call)* | a running server + its `URL` | → `ConfigReader`, `AndroidSdkResolver` |
| `AndroidDeviceResolver` | *(nothing — static call)* | an `AndroidDevice` record | → `AndroidSdkResolver`, `ConfigReader`, `adb` |
| `AndroidSdkResolver` | *(nothing — static call)* | the SDK-root `Path` | → environment variables |
| `AndroidTarget` | a `String` (`native`/`web`) | a validated enum constant | *(pure — no collaborators)* |
| `ConfigReader` | a key (`"platform"`, `"androidTarget"`, …) | a `String` value or `null` | → JVM system properties, `config.properties` |
| `DriverManager` | a `WebDriver` | the per-thread `WebDriver` | → `ThreadLocal` |

Read the **"Talks to"** column top-to-bottom and you have the entire mobile call graph. Notice
`AndroidSdkResolver` appears as a neighbour of **two** collaborators — it is the shared helper that
keeps SDK-root handling consistent for both the server and `adb`.

---

## The sequence diagram (the heart of this doc)

```
 TestNG   BaseTest   DriverFactory  PlatformType  MobileDriverFactory  AppiumServerManager  AndroidDeviceResolver  DriverManager
   │         │             │             │                │                    │                     │                  │
   │@BeforeM │             │             │                │                    │                     │                  │
   ├────────>│             │             │                │                    │                     │                  │
   │         │initDriver() │             │                │                    │                     │                  │
   │         ├────────────>│             │                │                    │                     │                  │
   │         │             │getDriver()  │                │                    │                     │                  │
   │         │             ├─────────────┼────────────────┼────────────────────┼─────────────────────┼─────────────────>│
   │         │             │<── null ────┼────────────────┼────────────────────┼─────────────────────┼──────────────────┤
   │         │             │from(getPlatform())            │                    │                     │                  │
   │         │             ├────────────>│                │                    │                     │                  │
   │         │             │<─ ANDROID ──┤                │                    │                     │                  │
   │         │             │createDriver()                │                    │                     │                  │
   │         │             ├─────────────┼───────────────>│                    │                     │                  │
   │         │             │             │  ensureStarted()│                   │                     │                  │
   │         │             │             │                ├───────────────────>│                     │                  │
   │         │             │             │                │  resolveSdkRoot() → corrected ANDROID_HOME│                  │
   │         │             │             │                │  start AppiumDriverLocalService           │                  │
   │         │             │             │                │  + JVM shutdown hook                      │                  │
   │         │             │             │                │<── server URL ─────┤                     │                  │
   │         │             │             │  resolveConnectedDevice()           │                     │                  │
   │         │             │             │                ├────────────────────┼────────────────────>│                  │
   │         │             │             │                │  adb devices / getprop (model, version)   │                  │
   │         │             │             │                │<── AndroidDevice(udid,name,ver) ──────────┤                  │
   │         │             │             │  AndroidTarget.from(getAndroidTarget()) → WEB|NATIVE       │                  │
   │         │             │             │  build UiAutomator2Options (+ chromedriverAutodownload)    │                  │
   │         │             │             │  new AndroidDriver(serverURL, options)                     │                  │
   │         │             │<── AndroidDriver (a WebDriver) ─┤                  │                     │                  │
   │         │             │setDriver(androidDriver)         │                  │                     │                  │
   │         │             ├─────────────┼─────────────────┼───────────────────┼─────────────────────┼─────────────────>│
   │         │<─(returns)──┤             │                 │                   │                     │  driver.set(...) │
   │<─test───┤             │             │                 │                   │                     │  [this thread]   │
```

Every arrow is a **method call**. The value threading through — `"android"` → `ANDROID` →
`AndroidDriver` — is the platform choice being progressively refined from raw text into a live
session, exactly mirroring the web flow's `"firefox"` → `FIREFOX` → `FirefoxDriver`.

---

## Walking each interaction

### Interaction 1 — TestNG → `BaseTest`

Identical to the web flow. TestNG invokes the lifecycle hook on the **test's own thread**, so the
driver lands in the right thread's storage.

```java
// BaseTest — unchanged for mobile
@BeforeMethod
public void setUp() {
    DriverFactory.initDriver();
}
```

`BaseTest` knows nothing about Appium, devices, or servers. It only knows *"ask the factory to
prepare a driver."*

### Interaction 2 — `BaseTest` → `DriverFactory` (the platform seam)

```java
// DriverFactory
public static void initDriver() {
    if (DriverManager.getDriver() != null) return;                 // 2a guard
    PlatformType platform = PlatformType.from(ConfigReader.getPlatform());
    WebDriver driver = switch (platform) {
        case WEB     -> createWebDriver();                         // existing Selenium path
        case ANDROID -> MobileDriverFactory.createDriver();        // 2b delegate
    };
    DriverManager.setDriver(driver);                               // 2c store
}
```

`DriverFactory` is the **conductor**. It decides web vs mobile and delegates the actual mobile
orchestration to `MobileDriverFactory` — it does not itself know what an Appium server is. Both
branches yield a `WebDriver`, so line `2c` is identical regardless of platform.

### Interaction 2b — `MobileDriverFactory` orchestrates four specialists

```java
// MobileDriverFactory
public static WebDriver createDriver() {
    URL server = AppiumServerManager.ensureStarted();                 // (i)
    AndroidTarget target = AndroidTarget.from(ConfigReader.getAndroidTarget());

    UiAutomator2Options options = baseOptions();                      // (ii) device coordinates
    applyTarget(options, target);                                     // (iii) native vs web

    return new AndroidDriver(server, options);                        // (iv) live session
}
```

Like `DriverFactory`, this class is a **coordinator, not a soloist**. It sequences four specialists
and assembles their outputs into the final `AndroidDriver`.

#### (i) `MobileDriverFactory` → `AppiumServerManager` — get a server, once

```java
// AppiumServerManager
public static synchronized URL ensureStarted() {
    String external = ConfigReader.getAppiumServerUrl();
    if (external != null) return toUrl(external);                     // honour an external server
    if (service != null && service.isRunning()) return service.getUrl(); // idempotent reuse

    String sdkRoot = AndroidSdkResolver.resolveSdkRoot().toString();  // corrected ANDROID_HOME
    // ... inject sdkRoot into the server environment, then:
    service.start();
    registerShutdownHook();                                           // auto-stop on JVM exit
    return service.getUrl();
}
```

This is the **server lifecycle owner**. It is `synchronized` and idempotent, so under parallel
threads the *first* thread boots one shared server and the rest reuse it. It leans on
`AndroidSdkResolver` so the server process sees a correct SDK root regardless of the shell
environment. The JVM shutdown hook guarantees the server never outlives the run.

#### (ii) `MobileDriverFactory` → `AndroidDeviceResolver` — discover the device

```java
// AndroidDeviceResolver
public static AndroidDevice resolveConnectedDevice() {
    Path adb = adbExecutable();                                       // via AndroidSdkResolver
    List<String> online = onlineDevices(adb);                         // parse `adb devices`
    String udid = selectUdid(online);                                 // auto-pick or -DdeviceUdid
    String model   = getProp(adb, udid, "ro.product.model");
    String version = getProp(adb, udid, "ro.build.version.release");
    return new AndroidDevice(udid, model, version);
}
```

This is the **device discovery specialist** — nothing about a specific handset is hardcoded. A
single connected device is auto-selected; multiple devices require `-DdeviceUdid` to disambiguate;
none produces a **fail-fast** `IllegalStateException` with a plain-language message *before* any
session is attempted.

#### (iii) `MobileDriverFactory` → `AndroidTarget` — decide what the session drives

```java
// applyTarget(...)
switch (target) {
    case WEB    -> { options.withBrowserName("Chrome");
                     options.setCapability("appium:chromedriverAutodownload", true); }
    case NATIVE -> applyNativeApp(options);   // setApp(apk) OR appPackage + appActivity
}
```

`AndroidTarget` is a **pure translator** (string → safe enum), the mobile mirror of `BrowserType`.
The explicit `chromedriverAutodownload` flag is what removes the single most common mobile-web
failure: Chrome/Chromedriver version skew.

#### (iv) Build the session

`new AndroidDriver(server, options)` performs the W3C handshake against the Appium server and
returns a live session — typed as `WebDriver` the moment it leaves this method.

### Interaction 2c — `DriverFactory` → `DriverManager` (store per thread)

```java
// DriverManager — unchanged for mobile
private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();
public static void setDriver(WebDriver webDriver) { driver.set(webDriver); }
```

The finished `AndroidDriver` is filed into **this thread's** `ThreadLocal` compartment — exactly
the same slot a `ChromeDriver` would occupy.

### Interaction 3 — the test consumes it, platform-agnostic

```java
DriverManager.getDriver().get("https://example.com/");
```

The test pulls "this thread's driver" and uses it. It never references Appium or Android. That is
the collaboration's ultimate purpose: **decouple "which platform" (decided once, in the factory)
from "use the driver" (everywhere else).**

---

## The shared helper: `AndroidSdkResolver`

Two collaborators depend on it, which is why it exists as its own class rather than as duplicated
logic:

```
AppiumServerManager  ──► resolveSdkRoot()  (so the server can find the SDK)
AndroidDeviceResolver ─► resolveSdkRoot()  (so it can locate adb.exe)
```

It shields runs from the single most common environment mistake — pointing `ANDROID_HOME` at
`...\Sdk\platform-tools` instead of the SDK root `...\Sdk`. It trims the accidental suffix and
falls back to the default SDK location, so a working session does not depend on a perfectly set
environment variable.

---

## Why this split of responsibilities matters

Trace what changes to **add iOS** support later:

| Class | Change needed? |
|-------|----------------|
| `BaseTest` | ❌ none |
| `DriverManager` | ❌ none |
| `BasePage` / Page Objects / Tests | ❌ none |
| `PlatformType` | ✅ add `IOS` constant |
| `DriverFactory` | ✅ add one `case IOS` |
| A new `IosDriverFactory` | ✅ its own file (mirrors `MobileDriverFactory`) |

The mobile-web-vs-native decision, device discovery, and server lifecycle are each isolated, so a
new platform is additive — no ripple into the existing web or Android paths.

Now trace what changes to **switch a run from web to mobile**: *zero code changes* — you add
`-Pmobile` (or `-Dplatform=android`). The *decision* enters from outside and flows through the same
fixed set of hand-offs.

---

## Runtime lifecycle across a mobile suite

The **server is suite-scoped**; each **driver session is per-test**:

```
Suite starts
  Test 1: setUp → initDriver → ANDROID
            → AppiumServerManager starts THE server (once)      ← shared, idempotent
            → new AndroidDriver session #1
          test body uses DriverManager.getDriver()
          tearDown → quitDriver  (ends session #1; server stays up)
  Test 2: setUp → new AndroidDriver session #2 (reuses running server)
          ...
Suite ends / JVM exits → shutdown hook stops the Appium server  ← no leaked process
```

Starting Appium is expensive, so it is amortised across the suite; each test still gets a clean,
isolated device session via `BaseTest`'s `@BeforeMethod` / `@AfterMethod`.

---

## Parallel angle: one server, many sessions

`AppiumServerManager.ensureStarted()` is `synchronized` and idempotent, and `DriverManager` stores
per thread. So under parallel execution:

```
Thread-1:  ... → MobileDriverFactory → AppiumServerManager (starts THE server) → AndroidDriver #1 → compartment #1
Thread-2:  ... → MobileDriverFactory → AppiumServerManager (reuses the server) → AndroidDriver #2 → compartment #2
```

One shared server, one driver per thread, never crossed wires. Note the practical limit: one
**physical device** serves one session at a time, so `testng-mobile.xml` runs `thread-count=1` by
design. True parallel mobile needs one device (or emulator) per thread — a device-farm concern, not
a default.

---

## Fail-fast telemetry (the "no cryptic errors" guarantee)

If anything is missing, the failing collaborator throws a plain-language `IllegalStateException`
*before* a session is created:

| Missing thing | Where it fails | Message |
|---------------|----------------|---------|
| No device connected | `AndroidDeviceResolver` | "No Android device or emulator detected. Connect a device with USB debugging enabled…" |
| Ambiguous devices | `AndroidDeviceResolver` | "Multiple Android devices connected […]. Pin one with -DdeviceUdid=<udid>." |
| Bad/missing SDK | `AndroidSdkResolver` | "Invalid Android SDK … Expected a folder containing 'platform-tools'." |
| Server won't boot | `AppiumServerManager` | "Failed to start the Appium server. Ensure Appium 2.x is installed…" |
| Native target without app | `MobileDriverFactory` | "Native Android target requires 'appPackage' (or set 'app' to an .apk path)…" |

Each guard fires at the earliest possible point, so failures read as one clear line instead of a
stack-trace avalanche.

---

## One-paragraph summary

A TestNG lifecycle callback tells **`BaseTest`** to prepare a driver. `BaseTest` delegates to
**`DriverFactory`**, which asks **`ConfigReader`** for the platform, validates it via
**`PlatformType`**, and — for `ANDROID` — delegates to **`MobileDriverFactory`**. That coordinator
sequences four specialists: **`AppiumServerManager`** self-starts (or reuses) one shared Appium
server, **`AndroidDeviceResolver`** auto-detects the connected device through `adb`, **`AndroidTarget`**
decides native app vs Chrome, and (both leaning on the shared **`AndroidSdkResolver`** for a correct
SDK root) it builds an `AndroidDriver`. Because that driver *is* a `WebDriver`, `DriverFactory` files
it into **`DriverManager`**'s `ThreadLocal` exactly like a browser, and the test drives it without
ever knowing it is a phone. Switch a run to mobile by adding one flag; add a whole new platform by
editing only `PlatformType`, one `DriverFactory` case, and a sibling factory.
