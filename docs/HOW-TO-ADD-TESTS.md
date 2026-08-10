# How to Add New Tests

> The single reference for extending this suite without breaking parallelism, reporting, or the
> auto-registration that keeps new tests from silently dropping out of a run. Read this before adding
> your first test. Related reading:
> - [ARCHITECTURE.md](./ARCHITECTURE.md) — the framework map
> - [COMMANDS.md](./COMMANDS.md) — every run command
> - [CLASS-INTERACTION-BROWSER-LAUNCH.md](./CLASS-INTERACTION-BROWSER-LAUNCH.md) — web launch flow

---

## 0. The 30-second version

To add a **web UI** or **API** test you write *one class* and tag it with a group. That's it — the
suite finds it automatically. No XML edit, no registration step.

```java
public class MyNewTest extends BaseTest {          // driver lifecycle for free
    @Test(groups = TestGroups.WEB)                 // this tag is what joins the suite
    public void doesSomethingUseful() {
        // arrange -> act via Page Objects -> assert in the test layer
    }
}
```

```powershell
mvn test "-Pweb"     # your new test runs — because it is tagged WEB and lives in the tests package
```

The rest of this document explains *why* it works that way and gives copy-paste recipes for each
layer (UI, data-driven, API, mobile).

---

## 1. The mental model — which layer owns what

A test is assembled from four cooperating layers. Keeping them separate is what makes the suite cheap
to extend: a UI change touches a Page Object, never a test; a new endpoint touches a Service, never a
test.

| Layer | Package | Responsibility | You touch it when… |
|-------|---------|----------------|--------------------|
| **Test** | `com.project.qa.tests` (+ `.api`) | Orchestrates the scenario, holds the **assertions** | Always — every new case |
| **Page Object** (UI) | `com.project.qa.pages` | Hides locators, exposes business actions via `BasePage` | The screen under test is new/changed |
| **Service / Endpoint** (API) | `com.project.qa.api.services` | Hides HTTP verbs + paths behind intent methods | The endpoint is new |
| **Data** | `com.project.qa.data` + `src/test/resources/*.json` | Type-safe test inputs (POJO + JSON) | The case is data-driven |

**The one rule that protects all of this:** assertions live in the **test** layer. Page Objects and
Services return data; they never assert. This keeps them reusable across many tests with different
expectations.

---

## 2. The golden rules (non-negotiable)

These are what keep the 50th test as clean as the 1st. A reviewer will send code back for any of
them.

1. **Extend the right base class.**
   - UI/web/mobile test → `extends BaseTest` (gives you `DriverFactory.initDriver()` /
     `DriverManager.quitDriver()` per method, so you never manage a driver by hand).
   - API test → `extends ApiBaseTest`.

2. **Tag every `@Test` with a group** from `TestGroups` (`WEB`, `API`, `MOBILE`). The group is not
   decoration — it is *how the test gets selected*. An untagged test runs in **no** filtered suite
   and is effectively dead code. Using the constant (not a raw string) means a typo fails to compile
   instead of silently excluding the test.

3. **Never swallow exceptions to make a test pass.** A `try { … } catch (Exception e) { log.error(…) }`
   around the body turns a real failure (broken locator, dead endpoint) into a green result — the
   worst possible outcome. Let exceptions propagate; TestNG marks the test failed and the
   `ScreenshotListener` captures the evidence. Use `SoftAssert` only to *collect multiple field
   checks*, and always finish with `softAssert.assertAll()`.

4. **Logging hygiene (SLF4J only).** No `System.out.println`. Business milestones on `INFO`; waits,
   locators, and framework noise on `DEBUG`. Name the logger after **its own class**:
   `LoggerFactory.getLogger(MyNewTest.class)`.

5. **Locators are `private final` inside the Page Object.** The test layer must not see a `By`. If a
   test needs a value from the screen, add a method that returns it.

6. **No `Thread.sleep`.** Synchronisation is already handled — go through `BasePage`'s `click` /
   `type` / `getText`, which wait on the config-driven `WebDriverWait`.

---

## 3. Recipe A — a plain web UI test

**Goal:** verify something on a page. Example: a new page + a test that checks a result.

### Step 1 — add a Page Object (only if the screen isn't modelled yet)

```java
package com.project.qa.pages;

import org.openqa.selenium.By;

public class MyntraCartPage extends BasePage {

    // Locators stay private: the test layer never sees a By.
    private final By cartItemCount = By.cssSelector(".cart-count");

    // Actions are public and read as business intent, not Selenium calls.
    public String getCartItemCount() {
        return getText(cartItemCount);   // getText() inherits the synchronized wait + Allure @Step
    }
}
```

### Step 2 — write the test

```java
package com.project.qa.tests;

import com.project.qa.constants.TestGroups;
import com.project.qa.pages.MyntraHomePage;
import com.project.qa.pages.MyntraSearchResultsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CartTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(CartTest.class);

    @Test(groups = TestGroups.WEB)
    public void searchLandsOnResults() {
        log.info("Verifying search navigates to a results page");

        // Fluent chain: open() -> search -> hand off to the next Page Object.
        MyntraSearchResultsPage results = new MyntraHomePage().open().searchForTheProduct("shoes");

        // Assertion stays in the test layer.
        Assert.assertFalse(results.getFirstCardBrand().isBlank(), "First result should have a brand");
    }
}
```

### Step 3 — run it. No registration needed.

```powershell
mvn test "-Pweb"
```

**Why nothing else is required:** `testng-web.xml` scans the whole `com.project.qa.tests` package and
filters by the `web` group. Your `@Test(groups = WEB)` matches, so it joins the run automatically.

---

## 4. Recipe B — a data-driven web test (JSON → POJO → DataProvider)

Use this when the *same* steps run against many inputs. `SearchProductsTest` is the reference
implementation.

### Step 1 — model the row as a POJO (in `com.project.qa.data`)

```java
package com.project.qa.data;

public class SearchData {
    private String keyword;                 // field name must match the JSON key
    public SearchData() { }                 // Jackson needs the no-arg constructor
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
```

### Step 2 — add the data file under `src/test/resources`

```json
[
  { "keyword": "shoes" },
  { "keyword": "socks" }
]
```

### Step 3 — feed it through a `@DataProvider`

```java
@DataProvider(name = "myntraSearchData", parallel = true)   // parallel rows honour the suite's data-provider-thread-count
public Object[][] getSearchData() throws IOException {
    List<SearchData> rows = JsonReader.getSearchDataPojo();
    Object[][] data = new Object[rows.size()][1];
    for (int i = 0; i < rows.size(); i++) {
        data[i][0] = rows.get(i);
    }
    return data;
}

@Test(dataProvider = "myntraSearchData", groups = TestGroups.WEB)
public void testMyntraProductSearch(SearchData searchData) {
    SoftAssert softAssert = new SoftAssert();   // collect field checks; do NOT wrap the body in try/catch

    MyntraSearchResultsPage results =
            new MyntraHomePage().open().searchForTheProduct(searchData.getKeyword());

    softAssert.assertFalse(results.getFirstCardBrand().trim().isEmpty(), "brand empty");
    softAssert.assertTrue(results.getFirstCardPrice().contains("Rs."), "price missing currency");

    softAssert.assertAll();   // mandatory — without this, soft failures never surface
}
```

> **Adding more inputs later is a data edit, not a code edit** — append rows to the JSON and the
> DataProvider spins up more test instances automatically.

---

## 5. Recipe C — an API test (Model → Service → Test)

The API side mirrors the UI side: a **Service** is the endpoint-level analogue of a Page Object.
`PostApiTest` + `PostService` are the reference.

### Step 1 — model the payload (`com.project.qa.api.models`)

```java
public class Comment {
    private int id;
    private String name;
    // no-arg constructor + getters/setters (Jackson)
}
```

### Step 2 — add a Service method (`com.project.qa.api.services`)

```java
public class CommentService {
    @Step("GET comment by id {id}")                       // shows up as a step in Allure
    public Response getComment(int id) {
        return given().spec(ApiSpecFactory.spec())        // shared base URI + logging + Allure filter
                .pathParam("id", id)
                .when().get("/comments/{id}");
    }
}
```

### Step 3 — write the test (assertions here, not in the Service)

```java
public class CommentApiTest extends ApiBaseTest {

    private final CommentService service = new CommentService();

    @Test(groups = TestGroups.API)
    public void getSingleComment() {
        Response response = service.getComment(1);
        Comment comment = response.as(Comment.class);

        new ApiValidator(log)                              // fluent, logs every expected-vs-actual
                .expect("HTTP status", 200, response.statusCode())
                .expect("comment id", 1, comment.getId())
                .verifyAll();
    }
}
```

### Step 4 — run it.

```powershell
mvn test "-Papi"
```

`testng-api.xml` package-scans `com.project.qa.tests.api` and filters by the `api` group — your test
joins automatically.

---

## 6. Recipe D — a mobile test (the one exception to auto-registration)

Mobile tests **must be added to the correct `testng-mobile*.xml` `<classes>` list by hand.** This is
deliberate, not an oversight.

**Why mobile is different:** the three mobile suites (`testng-mobile.xml`,
`testng-mobile-native.xml`, `testng-mobile-myntra.xml`) all share the single `mobile` group but need
**different Appium session types** — `MobileWebSmokeTest` needs a *browser* session while the native
tests need a *native* session (set once per run via `platform` / `androidTarget`). A group filter
alone can't tell them apart, so each suite pins its exact classes. If they all package-scanned the
`mobile` group, every mobile test would try to run under the wrong session type.

So, for a mobile test:

1. `extends BaseTest`, `@Test(groups = TestGroups.MOBILE)`, Page Objects in `com.project.qa.pages`
   (content-desc locators for the React Native app — see `MyntraAppHomePage`).
2. **Register the class** under the matching suite:

```xml
<test name="Myntra Native App Tests">
    <classes>
        <class name="com.project.qa.tests.MyntraAppTest"/>
        <class name="com.project.qa.tests.MyNewMobileTest"/>   <!-- add this line -->
    </classes>
</test>
```

---

## 7. How auto-registration actually works (and why)

The web and API suites use TestNG **package scanning** filtered by group, instead of a hand-kept
`<class>` list:

```xml
<groups>
    <run><include name="web"/></run>       <!-- membership filter -->
</groups>
<test name="Web UI Tests">
    <packages>
        <package name="com.project.qa.tests"/>   <!-- scans the package; the filter above narrows it -->
    </packages>
</test>
```

**Why this design:**

- **A new test can't be forgotten.** With a manual `<class>` list, omitting a line means the test
  silently never runs — a green build with zero coverage, the worst failure mode. Package scan
  removes that trap entirely.
- **The group filter is the real gate.** Mobile tests physically live in `com.project.qa.tests` too,
  but they're tagged `mobile`, so the `web` filter skips them. Selection is driven by an intentional
  annotation, not by file placement.
- **Zero-friction growth.** Adding the 20th web test is identical to adding the 2nd: write the class,
  tag it, done.

The default `testng.xml` (plain `mvn test`) applies the same idea with **both** `web` and `api`
included — so it runs the full non-mobile suite and never accidentally pulls a device-dependent
mobile test into a headless run.

---

## 8. Pre-commit checklist

- [ ] Class extends `BaseTest` (UI/mobile) or `ApiBaseTest` (API).
- [ ] Every `@Test` tagged with a `TestGroups` constant.
- [ ] Assertions are in the test; Page Objects / Services only return data.
- [ ] No `try/catch` that swallows failures; `SoftAssert` (if used) ends with `assertAll()`.
- [ ] SLF4J only, logger named after its own class; no `System.out.println`.
- [ ] Locators are `private final By` inside the Page Object.
- [ ] No `Thread.sleep` — synchronisation goes through `BasePage`.
- [ ] Mobile only: class added to the correct `testng-mobile*.xml`.
- [ ] Ran it locally: `mvn test "-Pweb"` / `"-Papi"` (or the mobile profile) is green.

---

## 9. Anti-patterns (rejected in review)

| Anti-pattern | Why it's harmful | Do instead |
|--------------|------------------|------------|
| `try/catch` around the whole test body | Turns real failures into false passes | Let it throw; use `SoftAssert` for multi-field checks |
| Forgetting `softAssert.assertAll()` | Test always passes even when checks fail | Always call `assertAll()` last |
| `System.out.println(...)` | Bypasses log levels & Allure | `log.info(...)` / `log.debug(...)` |
| Logger named after another class | Misattributed logs, hard debugging | `getLogger(ThisClass.class)` |
| `public` locators / `By` in the test | Leaks UI structure into tests; brittle | `private final By` + a Page Object method |
| `Thread.sleep(...)` | Flaky, slow, non-deterministic | Go through `BasePage`'s synchronized actions |
| Asserting inside a Page Object | Not reusable across tests with different expectations | Return the value; assert in the test |
