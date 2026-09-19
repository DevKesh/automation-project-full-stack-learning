# Onboarding — Start Here (Day 1)

Welcome. This project is a Java + Maven test-automation framework covering **Web (Selenium)**,
**Mobile (Appium)**, **API (REST Assured)**, and **Mobile flows (Maestro)**. This page is the map:
read it once and you'll know where everything lives and how to add your own test.

---

## 1. The one-sentence mental model

> **`src/main` is the ENGINE. `src/test` is the TESTS. Both are split the same way: `web / mobile / api`.**

You almost never edit the engine. You spend your day in `src/test/java/.../tests/` (the tests) and
`src/main/java/.../pageobjects/` (the page objects).

---

## 2. Where things live

| I'm looking for… | Go to |
|---|---|
| **A test to run/edit** | `src/test/java/com/project/qa/tests/{web,mobile,api,maestro}` |
| **A Page Object (locators + screen actions)** | `src/main/java/com/project/qa/pageobjects/{web,mobile,common}` |
| **How the browser/device is launched** | `src/main/java/com/project/qa/framework/{webdriver,mobiledriver}` |
| **Config / environment values** | `src/main/.../framework/configuration/ConfigReader.java` + `src/test/resources/config/` |
| **Test data (JSON)** | `src/test/resources/data/` |
| **TestNG suites (what runs in each profile)** | `src/test/resources/suites/` |
| **Maestro mobile flows (YAML)** | `src/test/resources/maestro/` |
| **Shared test helpers (no `@Test`)** | `src/test/java/com/project/qa/testsupport/` |
| **Data POJOs** | `src/main/java/com/project/qa/datamodels/` |

### Package naming is literal on purpose
Every folder name states its job: `framework.webdriver`, `framework.mobiledriver`,
`framework.configuration`, `pageobjects.web`, `tests.api`, `testsupport.listeners`. If the name says
`tests`, it contains `@Test` classes. If it says `testsupport`, it never does.

---

## 3. Trace one flow end-to-end (do this first)

Open these three files in order — this is the entire web execution path:

1. **`tests/web/SearchProductsTest.java`** — the scenario + assertions. Extends `BaseTest`.
2. **`pageobjects/web/MyntraHomePage.java`** — `open()` / `searchForTheProduct(...)`. No assertions here.
3. **`framework/webdriver/DriverManager.java`** — the `ThreadLocal<WebDriver>` every page talks to.

The same triplet holds for mobile: `tests/mobile/*` → `pageobjects/mobile/*` → `framework/mobiledriver/*`.

```
 Test (tests.web)  ──uses──▶  Page Object (pageobjects.web)  ──uses──▶  Driver (framework.webdriver)
   holds asserts               hides locators                           owns the WebDriver
```

---

## 4. Add your first test (copy-paste recipes)

### Web UI test
1. If the screen isn't modelled, add a Page Object in `pageobjects/web/` extending `BasePage`
   (locators `private final`, methods return data — **never assert here**).
2. Add the test in `tests/web/`:
   ```java
   package com.project.qa.tests.web;

   import com.project.qa.testsupport.base.BaseTest;
   import com.project.qa.testsupport.constants.TestGroups;
   import com.project.qa.pageobjects.web.MyntraHomePage;
   import org.testng.annotations.Test;

   public class MyNewWebTest extends BaseTest {
       @Test(groups = TestGroups.WEB)               // the group is how it joins the suite
       public void doesSomethingUseful() {
           new MyntraHomePage().open().searchForTheProduct("shoes");
           // ...assert in the test layer...
       }
   }
   ```
3. Run: `mvn test -Pweb`. No XML edit needed — the `web` package is auto-scanned.

### API test
- Extend `ApiBaseTest` (sets base URI, no browser). Put it in `tests/api/`, tag `TestGroups.API`.
- Reusable request/validation helpers live in `testsupport/api/`. Run: `mvn test -Papi`.

### Mobile (Appium) test
- Extend `BaseTest`, put it in `tests/mobile/`, tag `TestGroups.MOBILE`.
- Page Objects go in `pageobjects/mobile/`. Run: `mvn test -Pmobile`.

### Maestro (mobile YAML) flow
- Drop a `.yaml` into `src/test/resources/maestro/flows/`. It is **auto-discovered** — no Java needed.
- `MaestroSmokeTest` (in `tests/maestro/`) surfaces each flow as a TestNG case via the
  `framework/maestro` CLI bridge. Run: `mvn test -Pmaestro`.

---

## 5. Golden rules (a reviewer will enforce these)
1. **Assertions live in the test layer only.** Page Objects/Services return data.
2. **Every `@Test` is tagged** with a `TestGroups` constant — untagged tests run in no suite.
3. **Never swallow exceptions** to force a pass. Let them fail; the `ScreenshotListener` captures evidence.
4. **SLF4J only** — business milestones on `INFO`, framework noise on `DEBUG`. No `System.out`.
5. **No `Thread.sleep`** — use `BasePage`'s synchronized `click`/`type`/`getText`.

---

## 6. Go deeper
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — full framework map + directory tree
- [docs/HOW-TO-ADD-TESTS.md](docs/HOW-TO-ADD-TESTS.md) — detailed recipes per layer
- [docs/COMMANDS.md](docs/COMMANDS.md) — every run command
- [docs/PARALLEL-AND-CROSS-BROWSER.md](docs/PARALLEL-AND-CROSS-BROWSER.md) — `-Dbrowser`, `-Dthreads`, `-Dheadless`
- [docs/MAESTRO-ANALYSIS.md](docs/MAESTRO-ANALYSIS.md) — Maestro design & rationale
