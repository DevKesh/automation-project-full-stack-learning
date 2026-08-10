# Deep Dive: How Cross-Browser Configuration Actually Works

> This is a **learning document**, not a reference. It explains the *mechanics under the hood* —
> how a value you type on the command line physically reaches a variable inside Java, how each
> file interacts at runtime, and what `-D` really is. The goal is understanding, not copy-paste.
>
> Companion docs: [ARCHITECTURE.md](./ARCHITECTURE.md) (framework overview) and
> [PARALLEL-AND-CROSS-BROWSER.md](./PARALLEL-AND-CROSS-BROWSER.md) (design decisions & trade-offs).

---

## The one command we will trace

```powershell
mvn test -Dbrowser=firefox
```

We follow a single string — `"firefox"` — as it travels from your keyboard, through the JVM,
into your code, and finally becomes a running Firefox browser. Understand this journey and you
understand the whole integration.

---

## The cast: one job each (Single Responsibility Principle)

| File | Its single job |
|------|----------------|
| `config.properties` | Holds **default** values |
| `ConfigReader` | **Reads** config — command line first, file second |
| `BrowserType` | **Validates** the browser string → safe enum |
| `DriverFactory` | **Builds** the correct driver + options |
| `DriverManager` | **Stores** the driver, one per thread |
| `BaseTest` | **Triggers** the whole chain in `@BeforeMethod` |

The "integration" is nothing more than these six components talking in a chain. No magic.

---

## Part 1 — What `-D` actually is

### `-D` is a flag understood by the `java` launcher itself

Whenever Java runs, the real program that starts is the **`java` executable** (the JVM
launcher). Its command shape is:

```
java  [JVM options]  MainClass  [program arguments]
```

`-D` belongs to the **first group — JVM options**. It is **not** parsed by your code, and it is
**not** a Maven invention. It is hard-wired into the `java` launcher, which recognizes the
prefix `-D` and treats what follows as *"define a system property."*

The exact grammar the launcher looks for:

```
-D<name>=<value>

-Dbrowser=firefox
   │      │
   │      └── value → "firefox"
   └───────── name  → "browser"
```

When the JVM sees an argument starting with `-D`, **before your code runs** it:

1. Splits the text at the first `=`.
2. Treats the left side (`browser`) as the property **name**.
3. Treats the right side (`firefox`) as the property **value**.
4. Inserts that pair into an in-memory table called the **System Properties**.

So `-Dbrowser=firefox` literally means: *"JVM, before you start my code, put
`browser → firefox` into the system-properties table."*

### The table it writes into

Every JVM has one built-in `Properties` object (a key→value map). It already holds standard
entries the JVM fills in itself:

```
java.version   → 21.0.3
os.name        → Windows 11
user.home      → C:\Users\kesha
file.separator → \
```

Your `-Dbrowser=firefox` simply **adds one more row** to that same table:

```
browser        → firefox      ← yours
```

Then `System.getProperty("browser")` is just a lookup in that map. That is the entire bridge:
**`-D` writes a row at startup; `getProperty` reads that row at runtime.**

### The crucial distinction: JVM option vs. program argument

Position matters:

```
java -Dbrowser=firefox   com.example.Main   firefox
     └──── JVM option ────┘   main class     └─ program arg (String[] args)
```

- **`-Dbrowser=firefox`** comes *before* the class name → the **JVM** consumes it → becomes a
  system property → read via `System.getProperty(...)`.
- **`firefox`** at the end comes *after* → passed to your **`main(String[] args)`** → read via
  `args[0]`.

This is why the syntax is `-Dbrowser=firefox` and **not** `--browser firefox`. The launcher
only understands the `-D` form for properties. There is no `--browser` concept in Java or Maven.

### Where Maven fits in

You type `mvn test`, not `java ...`. Maven forwards the flag: the Surefire plugin **launches a
new (forked) JVM** for your tests and copies `-Dbrowser=firefox` onto that JVM's command line.
Effectively:

```
java -Dbrowser=firefox ... <surefire test runner>
```

The forked test JVM boots with `browser → firefox` already in its system properties, waiting
for `ConfigReader` to read it.

### One-line summary

> `-D` is a **JVM launcher flag** that, at startup, inserts a `name → value` pair into the JVM's
> global **system-properties table**. Java "understands" it not by parsing your code, but because
> the `java` executable is built to recognize the `-D` prefix and populate that table before
> `main` runs. `System.getProperty(name)` is just a read from that same table.

---

## Part 2 — The runtime trace, step by step

### Step 0 — Before Java starts: Maven

`-Dbrowser=firefox` becomes a **JVM system property**, living in memory for the whole test JVM,
readable anywhere via `System.getProperty("browser")`.

> Note: `-Dbrowser` and `-Dheadless` are consumed at **runtime** by your code.
> `-Dthreads` is different — Maven consumes it at **build time** to filter `testng.xml`
> (see Part 4).

### Step 1 — `BaseTest` fires the starting gun

```java
@BeforeMethod
public void setUp() {
    DriverFactory.initDriver();   // runs on the test's OWN thread
}
```

Because the suite is `parallel="methods"`, TestNG runs this `@BeforeMethod` **on the same
thread** as the test it precedes. Everything downstream therefore happens per-thread — the
foundation of thread-safety (payoff in Step 6).

### Step 2 — `DriverFactory.initDriver()` orchestrates

```java
public static void initDriver() {
    if (DriverManager.getDriver() != null) return;                      // (a) guard

    BrowserType browser = BrowserType.from(ConfigReader.getBrowser());  // (b)
    boolean headless = ConfigReader.isHeadless();                       // (c)
    log.info("Launching {} browser (headless={})", browser, headless);

    WebDriver driver = createDriver(browser, headless);                 // (d)
    DriverManager.setDriver(driver);                                    // (e)
}
```

`initDriver` does not know *how* to read config or *how* to build Firefox. It **delegates** —
line (b) asks `ConfigReader`, line (d) asks the factory method. Delegation keeps it readable.

### Step 3 — `ConfigReader` decides: command line or file?

```java
private static String resolve(String key) {
    String override = System.getProperty(key);        // "firefox" (from -D)
    return (override != null && !override.isBlank())
            ? override.trim()                          // CLI wins → "firefox"
            : properties.getProperty(key);             // else the file → "chrome"
}
```

Line-by-line:

- `System.getProperty(key)` → checks the system-properties table for `"browser"`.
  With `-Dbrowser=firefox` it returns `"firefox"`. **Without** any `-D`, it returns `null`.
- The ternary: if `override` is present (not null, not blank) → **use the command-line value**;
  otherwise → **fall back to the file**.

Where did `properties` come from? Loaded **once**, at class-load time:

```java
private static final Properties properties = load();   // runs one time, ever
```

`load()` reads `/config.properties` off the classpath. `static final` + one-time load = read the
file once, reuse forever, thread-safe because it is read-only after loading.

**Output of Step 3:** the plain string `"firefox"`.

### Step 4 — `BrowserType` turns a risky string into a safe type

```java
public static BrowserType from(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(...);
    try {
        return BrowserType.valueOf(value.trim().toUpperCase());  // "FIREFOX" → FIREFOX
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unsupported browser '" + value + "'...");
    }
}
```

Why convert `String` → `enum`?

1. **Fail fast, fail clear.** `-Dbrowser=fierfox` (typo) dies immediately with
   *"Unsupported browser 'fierfox'. Supported values: [CHROME, FIREFOX, EDGE]"* — not a
   confusing `NullPointerException` ten lines later.
2. **The `switch` becomes exhaustive.** With an enum, the compiler guarantees every case is
   handled — no silent "unknown browser" fall-through.

**Output:** the enum constant `FIREFOX`.

### Step 5 — `DriverFactory` builds the actual browser

```java
private static WebDriver createDriver(BrowserType browser, boolean headless) {
    return switch (browser) {
        case CHROME  -> new ChromeDriver(chromeOptions(headless));
        case FIREFOX -> new FirefoxDriver(firefoxOptions(headless));   // taken
        case EDGE    -> new EdgeDriver(edgeOptions(headless));
    };
}

private static FirefoxOptions firefoxOptions(boolean headless) {
    FirefoxOptions options = new FirefoxOptions();
    options.addArguments("--width=1920", "--height=1080");
    if (headless) options.addArguments("-headless");
    log.debug("Built FirefoxOptions: {}", options);   // DEBUG = framework noise
    return options;
}
```

- Each browser has its **own** builder because their option syntax differs (Firefox uses
  `-headless`; Chrome/Edge use `--headless=new`). One method = one browser = SRP.
- **Where is the driver `.exe`?** Nowhere in your code — **Selenium Manager** (built into
  Selenium 4) auto-locates/downloads `geckodriver` when `new FirefoxDriver(...)` runs. That is
  why **no new dependencies** were needed.

**Output:** a live `FirefoxDriver` — a real browser session.

### Step 6 — `DriverManager` stores it *per thread*

```java
private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();

public static void setDriver(WebDriver webDriver) { driver.set(webDriver); }
public static WebDriver getDriver()              { return driver.get(); }
```

`ThreadLocal` is a box with a **separate compartment per thread**. `driver.set(firefox)` places
the driver into *this thread's* compartment only. A different thread running Edge has its own
compartment — they cannot see each other.

This is why cross-browser + parallel compose for free: nothing here is a shared
`static WebDriver`; it is a `static ThreadLocal<WebDriver>` (per-thread). Chrome on thread-1 and
Firefox on thread-2 can run simultaneously without interfering.

### Step 7 — The test uses it, blind to the browser

```java
DriverManager.getDriver().get("https://www.myntra.com/");
```

Tests and page objects only ask for "this thread's driver." They never mention Chrome or
Firefox. That is the payoff: cross-browser support required changing **only** `DriverFactory` —
tests, `DriverManager`, `BaseTest`, and page objects were untouched.

### Step 8 — Teardown

```java
public static void quitDriver() {
    if (driver.get() != null) {
        driver.get().quit();   // closes the physical browser process
        driver.remove();       // clears THIS thread's compartment (prevents leaks)
    }
}
```

`quit()` kills the browser; `remove()` empties the ThreadLocal compartment so the pooled thread
starts clean next time. Skipping `remove()` is the classic parallel memory leak.

---

## Part 3 — Precedence: command line beats the file

Highest priority at the top:

```
1. mvn test -Dbrowser=firefox   ← command line  (WINS if present)
2. config.properties            ← file default  (fallback only)
```

**Run A: `mvn test -Dbrowser=firefox`**

```java
String override = System.getProperty("browser");  // "firefox"  ← not null
return (override != null && ...) ? override        // returns "firefox"
                                 : properties...;   // never reached
```
Runs **Firefox**. The file's `browser=chrome` is ignored.

**Run B: `mvn test`** (no `-D`)

```java
String override = System.getProperty("browser");  // null  ← nothing on CLI
return (override != null && ...) ? override
                                 : properties.getProperty("browser");  // "chrome"
```
Runs **Chrome**, from the file.

### The mental model

- `config.properties` = *"what happens if I say nothing"* (a default, not a lock).
- `-Dbrowser=...` = *"just for THIS run, do this instead"* — it does **not** edit the file.

Committed sensible defaults everyone shares, plus per-run flexibility for you and CI, without
anyone editing (and accidentally committing) a changed file. The same rule applies to
`-Dheadless=true` (beats `headless=false`) and `-Dthreads=5` (beats `threads=3`).

---

## Part 4 — Why `-Dthreads` takes a different path

`-Dbrowser` and `-Dheadless` are read by **your code at runtime** via `System.getProperty`.
`-Dthreads` cannot work that way, because **TestNG reads `thread-count` from the suite XML
before any of your code runs**, and Surefire's own `parallel`/`threadCount` are ignored when
`suiteXmlFiles` is used.

So `-Dthreads` is consumed earlier, at **build time**, through Maven **resource filtering**:

1. `testng.xml` is tokenized: `thread-count="${threads}"`.
2. `pom.xml` declares a default: `<threads>3</threads>`.
3. Maven substitutes the value while copying `testng.xml` into `target/test-classes`; a CLI
   `-Dthreads=N` overrides the pom default.
4. Surefire runs the **filtered copy**:
   `<suiteXmlFile>${project.build.testOutputDirectory}/testng.xml</suiteXmlFile>`.

Same "command line overrides default" philosophy, different machinery — because the value is
needed before the test JVM's code executes.

---

## The whole chain in one picture

```
-Dbrowser=firefox  →  JVM system-properties table  (browser → firefox)
   → BaseTest.@BeforeMethod            [runs per thread]
      → DriverFactory.initDriver()
         → ConfigReader.getBrowser()   [System.getProperty first → CLI beats file] → "firefox"
         → BrowserType.from(...)       [validate string → enum]                    → FIREFOX
         → switch → new FirefoxDriver(firefoxOptions())
                    [Selenium Manager finds geckodriver — no dependency needed]
         → DriverManager.setDriver()   [store in THIS thread's ThreadLocal box]
   → test → DriverManager.getDriver()  [browser-agnostic]
   → @AfterMethod → quit() + remove()
```

**Design pattern:** a **Factory** (`DriverFactory` decides *which* object) feeding a
**thread-scoped registry** (`DriverManager`), fed by a **layered config source**
(`ConfigReader`). Each piece is swappable without touching the others — the "clean seams" idea.

---

## Glossary (quick recall)

- **JVM launcher (`java`)** — the executable that starts every Java program; it parses JVM
  options like `-D` before your code runs.
- **System property** — a `name → value` entry in the JVM's global properties table; set with
  `-Dname=value`, read with `System.getProperty("name")`.
- **Program argument** — text placed *after* the main class; delivered to `main(String[] args)`.
  Different from a system property.
- **Forked JVM** — the separate Java process Surefire starts to run tests; it receives the `-D`
  flags Maven forwards.
- **Resource filtering** — Maven substituting `${...}` tokens in resource files at build time
  (how `-Dthreads` reaches `testng.xml`).
- **`ThreadLocal`** — a variable whose value is private to each thread; the reason parallel
  browsers never collide.
- **Selenium Manager** — Selenium 4's built-in resolver that downloads/locates driver binaries
  (`chromedriver`, `geckodriver`, `msedgedriver`) automatically.
