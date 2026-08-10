# Class Interaction: Launching Your Chosen Browser from an `mvn` Command

> This document focuses on **how the classes collaborate** — who calls whom, in what order, and
> what each hand-off passes along — to turn a single `mvn` command into a running browser of your
> choice. It complements:
> - [DEEP-DIVE-HOW-IT-WORKS.md](./DEEP-DIVE-HOW-IT-WORKS.md) — the *mechanics* (what `-D` is, JVM internals)
> - [PARALLEL-AND-CROSS-BROWSER.md](./PARALLEL-AND-CROSS-BROWSER.md) — the *design decisions & trade-offs*
> - [ARCHITECTURE.md](./ARCHITECTURE.md) — the framework map

---

## The trigger

```powershell
mvn test -Dbrowser=firefox
```

From this one line, five classes collaborate to launch Firefox. This doc is about the **arrows
between the boxes** — the messages classes send each other.

---

## The collaborators and their contracts

| Class | Receives | Produces | Talks to |
|-------|----------|----------|----------|
| `BaseTest` | TestNG lifecycle callback | (side effect: driver ready) | → `DriverFactory`, `DriverManager` |
| `DriverFactory` | *(nothing — static call)* | a stored `WebDriver` | → `ConfigReader`, `BrowserType`, `DriverManager` |
| `ConfigReader` | a key (`"browser"`) | a `String` value | → JVM system properties, `config.properties` |
| `BrowserType` | a `String` | a validated enum constant | *(pure — no collaborators)* |
| `DriverManager` | a `WebDriver` | the per-thread `WebDriver` | → `ThreadLocal` |

Read the **"Talks to"** column top-to-bottom and you have the entire call graph. Each class only
knows the few neighbours it must — no class reaches across the whole system.

---

## The sequence diagram (the heart of this doc)

```
 TestNG        BaseTest        DriverFactory      ConfigReader     BrowserType     DriverManager
   │              │                  │                 │                │                │
   │ @BeforeMethod│                  │                 │                │                │
   ├─────────────>│                  │                 │                │                │
   │              │ initDriver()     │                 │                │                │
   │              ├─────────────────>│                 │                │                │
   │              │                  │ getDriver()     │                │                │
   │              │                  ├─────────────────┼────────────────┼───────────────>│
   │              │                  │<── null ────────┼────────────────┼────────────────┤
   │              │                  │ getBrowser()    │                │                │
   │              │                  ├────────────────>│                │                │
   │              │                  │                 │ System.getProperty("browser")   │
   │              │                  │                 │──┐  → "firefox" │                │
   │              │                  │<── "firefox" ───┤<─┘              │                │
   │              │                  │ from("firefox") │                │                │
   │              │                  ├─────────────────┼───────────────>│                │
   │              │                  │<── FIREFOX ──────┼────────────────┤                │
   │              │                  │ new FirefoxDriver(firefoxOptions(headless))        │
   │              │                  │──┐  (Selenium Manager finds geckodriver)           │
   │              │                  │<─┘  → firefox driver instance                      │
   │              │                  │ setDriver(firefox)                                 │
   │              │                  ├───────────────────────────────────────────────────>│
   │              │                  │                 │                │  driver.set(...) │
   │              │<── (returns) ────┤                 │                │  [this thread]   │
   │<─ test runs ─┤                  │                 │                │                │
```

Every arrow is a **method call**. The value threading through — `"firefox"` → `FIREFOX` →
`FirefoxDriver` — is the browser choice being progressively refined from raw text into a live
object.

---

## Walking each interaction

### Interaction 1 — TestNG → `BaseTest`

TestNG invokes the lifecycle hook. Crucially it runs on the **test's own thread** (suite is
`parallel="methods"`), which is why the driver ends up in the *right* thread's storage later.

```java
// BaseTest
@BeforeMethod
public void setUp() {
    DriverFactory.initDriver();
}
```

`BaseTest` knows nothing about browsers or config. It only knows *"ask the factory to prepare a
driver."* That is its entire contribution to the collaboration.

### Interaction 2 — `BaseTest` → `DriverFactory`

```java
// DriverFactory
public static void initDriver() {
    if (DriverManager.getDriver() != null) return;                     // 2a
    BrowserType browser = BrowserType.from(ConfigReader.getBrowser()); // 2b, 2c
    boolean headless = ConfigReader.isHeadless();
    WebDriver driver = createDriver(browser, headless);                // 2d
    DriverManager.setDriver(driver);                                   // 2e
}
```

`DriverFactory` is the **coordinator** of this collaboration. It doesn't *do* the low-level work
itself; it sequences calls to three specialists (ConfigReader, BrowserType, DriverManager). This
is the "conductor, not soloist" role.

### Interaction 2a — `DriverFactory` → `DriverManager` (the guard)

```java
if (DriverManager.getDriver() != null) return;
```

Before building anything, the factory asks the manager *"does this thread already have a
driver?"* If yes, it stops — one browser per thread, no accidental second launch.

### Interaction 2b/2c — `DriverFactory` → `ConfigReader` → sources

```java
// ConfigReader
private static String resolve(String key) {
    String override = System.getProperty(key);          // asks the JVM first
    return (override != null && !override.isBlank())
            ? override.trim()                            // command line wins
            : properties.getProperty(key);              // else the file
}
public static String getBrowser() { return resolve("browser"); }
```

`ConfigReader` is the **only** class that touches configuration sources. The factory doesn't know
whether the answer came from the command line or the file — it just receives `"firefox"`. That
encapsulation is why you could later add environment-variable support in *one* place without
touching the factory.

### Interaction 2c → `BrowserType` (validate + convert)

```java
// BrowserType
public static BrowserType from(String value) {
    if (value == null || value.isBlank()) throw ...;
    try   { return BrowserType.valueOf(value.trim().toUpperCase()); }  // "FIREFOX" → FIREFOX
    catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unsupported browser '" + value + "'..."); }
}
```

`BrowserType` is a **pure translator** — string in, safe enum out, no collaborators. It is the
guard rail: a bad value dies here with a clear message, so the `switch` downstream is guaranteed a
valid constant.

### Interaction 2d — `DriverFactory` builds the browser

```java
private static WebDriver createDriver(BrowserType browser, boolean headless) {
    return switch (browser) {
        case CHROME  -> new ChromeDriver(chromeOptions(headless));
        case FIREFOX -> new FirefoxDriver(firefoxOptions(headless));   // chosen
        case EDGE    -> new EdgeDriver(edgeOptions(headless));
    };
}
```

The enum drives the `switch`; the matching branch constructs the real driver. The option-builder
methods (`firefoxOptions`, etc.) are private helpers — cohesive, one per browser. **Selenium
Manager** resolves the binary here, invisibly.

### Interaction 2e — `DriverFactory` → `DriverManager` (store per thread)

```java
// DriverManager
private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();
public static void setDriver(WebDriver webDriver) { driver.set(webDriver); }
```

The finished Firefox driver is handed to the manager, which files it into **this thread's**
`ThreadLocal` compartment. From now on, anything on this thread that calls
`DriverManager.getDriver()` receives *this* Firefox instance.

### Interaction 3 — the test consumes it, browser-agnostic

```java
DriverManager.getDriver().get("https://www.myntra.com/");
```

The test asks the manager for "this thread's driver" and uses it. It never references Firefox.
That is the collaboration's ultimate purpose: **decouple "which browser" (decided once, in the
factory) from "use the browser" (everywhere else).**

---

## Why this split of responsibilities matters

Trace what changes if you want to **add a new browser** (say the future Safari, on a Mac):

| Class | Change needed? |
|-------|----------------|
| `BaseTest` | ❌ none |
| `ConfigReader` | ❌ none |
| `BrowserType` | ✅ add one enum constant |
| `DriverFactory` | ✅ add one `case` + one option builder |
| `DriverManager` | ❌ none |
| Tests / Page Objects | ❌ none |

Two small, localized edits — no ripple. That is the direct benefit of each class holding a single
responsibility and collaborating through narrow contracts rather than sharing knowledge.

Now trace what changes to **switch browser for a run**: *zero code changes* — you just add
`-Dbrowser=edge`. The collaboration was designed so the *decision* enters from outside (the
command line) and flows through the same fixed set of hand-offs.

---

## Parallel angle: the same collaboration, many threads at once

Because `BaseTest.setUp()` runs per thread and `DriverManager` stores per thread, the **exact
same sequence** runs independently on each parallel thread:

```
Thread-1:  BaseTest → DriverFactory → ... → DriverManager  (its own Firefox in compartment #1)
Thread-2:  BaseTest → DriverFactory → ... → DriverManager  (its own Firefox in compartment #2)
Thread-3:  BaseTest → DriverFactory → ... → DriverManager  (its own Firefox in compartment #3)
```

No collaborator holds shared mutable browser state, so the three sequences never collide. The
class interaction is identical whether you run 1 thread or 10.

---

## One-paragraph summary

A TestNG lifecycle callback tells **`BaseTest`** to prepare a driver. `BaseTest` delegates to
**`DriverFactory`**, which coordinates three specialists: it asks **`ConfigReader`** *which*
browser (command line beats file), asks **`BrowserType`** to validate that answer into a safe
enum, builds the matching real driver via a `switch`, and hands the result to **`DriverManager`**
to store in the current thread's `ThreadLocal`. The test then pulls "this thread's driver" from
`DriverManager` and drives it — never knowing or caring which browser it is. Change the browser by
changing one command-line flag; add a browser by editing only two classes.
