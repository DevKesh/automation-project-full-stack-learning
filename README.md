# Full-Stack Test Automation Learning Project

A Java 21 automation framework for learning and demonstrating **web UI**, **REST API**, **Android mobile-web**, and **Android native-app** testing in one Maven project.

The framework combines Selenium, TestNG, REST Assured, Appium, and Allure with reusable page objects, thread-safe driver management, configurable environments, parallel execution, retry support, and failure diagnostics.

## What This Project Covers

- **Web UI automation** with Selenium WebDriver
- **Cross-browser testing** with Chrome, Firefox, and Edge
- **Parallel execution** using TestNG and `ThreadLocal<WebDriver>`
- **API testing** with REST Assured and typed models
- **Android mobile-web testing** with Appium and Chrome
- **Android native-app testing** for installed apps, APKs, and Play Store installation
- **Page Object Model** for web and mobile screens
- **Environment-based configuration** with runtime overrides
- **Data-driven testing** with JSON test data
- **Allure reporting** with test steps and request/response attachments
- **Failure diagnostics**, including screenshots and page-source capture
- **Configurable flaky-test retries**

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Build | Maven |
| Test runner | TestNG 7.9.0 |
| Web automation | Selenium 4.25.0 |
| Mobile automation | Appium Java Client 9.3.0 |
| API automation | REST Assured 5.5.0 |
| Reporting | Allure 2.29.0 |
| JSON processing | Jackson 2.17.0 |
| Logging | SLF4J 2.0.12 and Logback 1.4.14 |
| Aspect weaving | AspectJ 1.9.21 |

> Selenium is intentionally pinned to `4.25.0` for compatibility with Appium Java Client `9.3.0`.

## Project Structure

```text
.
├── .github/                           # Copilot agents, instructions, and prompts
├── docs/                              # Architecture, commands, and implementation guides
├── src/
│   ├── main/
│   │   └── java/com/project/qa/
│   │       ├── config/                # Configuration loading
│   │       ├── core/                  # WebDriver, Appium, and Android lifecycle
│   │       ├── data/                  # Test-data models and readers
│   │       ├── pages/                 # Web and native mobile page objects
│   │       └── utils/                 # Shared framework utilities
│   └── test/
│       ├── java/com/project/qa/
│       │   ├── api/                   # API specs, services, models, and validators
│       │   ├── constants/             # Test group constants
│       │   ├── listeners/             # Reporting, retries, and failure listeners
│       │   └── tests/                 # Web, API, mobile-web, and native tests
│       └── resources/
│           ├── config.properties      # Base configuration
│           ├── config-qa.properties   # QA environment overrides
│           ├── config-staging.properties
│           ├── logback-test.xml       # Test logging configuration
│           ├── searchData.json        # Data-driven test input
│           └── testng*.xml            # TestNG suites by category
├── .gitignore
└── pom.xml
```

## Prerequisites

### Required for all test types

- Java Development Kit **21**
- Maven **3.9+**
- Git

Verify your installation:

```bash
java -version
mvn -version
git --version
```

### Required for web tests

Install at least one supported browser:

- Google Chrome
- Mozilla Firefox
- Microsoft Edge

The framework uses Selenium Manager to resolve the corresponding browser driver automatically.

### Required for Android tests

- Android SDK and `adb`
- A connected Android device or running emulator
- Node.js and npm
- Appium 2
- Appium UiAutomator2 driver

Install Appium and the Android driver:

```bash
npm install -g appium
appium driver install uiautomator2
```

Verify the installation and connected device:

```bash
appium -v
appium driver list --installed
adb devices -l
```

The device must appear with the state `device`, not `offline` or `unauthorized`.

## Getting Started

Clone the repository:

```bash
git clone https://github.com/DevKesh/automation-project-full-stack-learning.git
cd automation-project-full-stack-learning
```

Compile and run the default test suite:

```bash
mvn clean test
```

The default configuration uses:

| Setting | Default |
| --- | --- |
| Browser | Chrome |
| Headless | `false` |
| Parallel threads | `3` |
| Environment | `qa` |
| Explicit-wait timeout | 10 seconds |
| Retry count | 1 additional attempt |
| Platform | Web |
| API base URI | `https://jsonplaceholder.typicode.com` |

These values are defined in `src/test/resources/config.properties`.

## Running Tests

> The quoted Maven options shown below work in Windows PowerShell. In Bash or zsh, the quotes around `-D` and `-P` arguments are generally optional.

### Quick Command Reference

| Goal | Command |
| --- | --- |
| Run the default web suite | `mvn test` |
| Run only web tests | `mvn test "-Pweb"` |
| Run only API tests | `mvn test "-Papi"` |
| Run Android mobile-web tests | `mvn test "-Pmobile"` |
| Run generic native Android tests | `mvn test "-Pmobile-native"` |
| Run the Myntra native app suite | `mvn test "-Pmobile-myntra"` |
| Run tests and open Allure | `mvn clean test "-Preport"` |

### Web UI Tests

Run the default web suite:

```powershell
mvn test
```

Run only tests in the TestNG `web` group:

```powershell
mvn test "-Pweb"
```

Choose a browser:

```powershell
mvn test "-Dbrowser=chrome"
mvn test "-Dbrowser=firefox"
mvn test "-Dbrowser=edge"
```

Run in headless mode:

```powershell
mvn test "-Dheadless=true"
```

Change the parallel thread count:

```powershell
mvn test "-Dthreads=5"
```

Combine multiple options:

```powershell
mvn test "-Pweb" "-Dbrowser=edge" "-Dheadless=true" "-Dthreads=4"
```

> The web suite targets Myntra's desktop site. Headless execution may trigger anti-bot behavior. See [Failure Diagnostics and Headless Findings](docs/FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md).

### API Tests

Run only tests in the TestNG `api` group:

```powershell
mvn test "-Papi"
```

The default API target is JSONPlaceholder. Override the base URI at runtime when needed:

```powershell
mvn test "-Papi" "-DapiBaseUri=https://staging.example.com"
```

API requests and responses are attached to the Allure results through the Allure REST Assured filter.

### Android Mobile-Web Tests

The `mobile` profile launches Chrome on a connected Android device:

```powershell
mvn test "-Pmobile"
```

Select a device when multiple devices are connected:

```powershell
mvn test "-Pmobile" "-DdeviceUdid=<device-id>"
```

Reuse an externally managed Appium server:

```powershell
mvn test "-Pmobile" "-DappiumServerUrl=http://127.0.0.1:4723"
```

Without `appiumServerUrl`, the framework manages the Appium server lifecycle itself.

### Android Native-App Tests

#### Discover installed applications

Run the native profile without an application target:

```powershell
mvn test "-Pmobile-native"
```

The framework lists user-installed packages on the connected device.

#### Launch an installed application

Provide its package name:

```powershell
mvn test "-Pmobile-native" "-DappPackage=com.myntra.android"
```

If the application is not installed and is available from Google Play, the framework can attempt to install it before launching it.

> The connected device must be signed in to a Google account for Play Store installation.

#### Install and launch an APK

Provide an absolute path to an APK:

```powershell
mvn test "-Pmobile-native" "-Dapp=C:/absolute/path/to/app.apk"
```

#### Install from an APK only when the package is missing

The APK source may be a local path or direct download URL:

```powershell
mvn test "-Pmobile-native" "-DappPackage=<package>" "-Dapp=<apk-path-or-url>"
```

Example:

```powershell
mvn test "-Pmobile-native" "-DappPackage=io.appium.android.apis" "-Dapp=https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk"
```

#### Launch a specific activity

Use an explicit package and activity when the default launcher activity is not appropriate:

```powershell
mvn test "-Pmobile-native" "-DappPackage=com.example.app" "-DappActivity=com.example.app.MainActivity"
```

### Myntra Native Android Suite

Run the dedicated Myntra native-app suite:

```powershell
mvn test "-Pmobile-myntra"
```

The suite covers:

1. Application launch and foreground state
2. Foreground package verification
3. Home-screen search bar visibility
4. Product search results
5. Application restoration after backgrounding

The corresponding mobile page objects include:

- `MyntraAppHomePage`
- `MyntraAppSearchPage`

## Maven Profiles

| Profile | Purpose |
| --- | --- |
| Default | Runs the standard TestNG suite |
| `web` | Runs tests in the TestNG `web` group |
| `api` | Runs tests in the TestNG `api` group |
| `mobile` | Runs Android mobile-web tests |
| `mobile-native` | Runs generic Android native-app tests |
| `mobile-myntra` | Runs the Myntra native-app end-to-end suite |
| `report` | Serves the Allure report after test execution |

The suite definitions are stored under `src/test/resources`:

- `testng.xml`
- `testng-web.xml`
- `testng-api.xml`
- `testng-mobile.xml`
- `testng-mobile-native.xml`
- `testng-mobile-myntra.xml`

## Configuration

Base settings live in:

```text
src/test/resources/config.properties
```

Environment-specific overlays include:

```text
src/test/resources/config-qa.properties
src/test/resources/config-staging.properties
```

Select an environment at runtime:

```powershell
# Load config-qa.properties
mvn test "-Denv=qa"

# Load config-staging.properties
mvn test "-Denv=staging"
```

System properties override committed configuration values:

```powershell
mvn test "-Denv=staging" "-Dbrowser=edge" "-Dthreads=4"
```

### Supported Runtime Properties

| Property | Description | Example |
| --- | --- | --- |
| `browser` | Desktop browser | `-Dbrowser=edge` |
| `headless` | Enable headless browser execution | `-Dheadless=true` |
| `threads` | TestNG parallel thread count | `-Dthreads=4` |
| `env` | Environment overlay | `-Denv=staging` |
| `explicit.wait.seconds` | Explicit-wait timeout | `-Dexplicit.wait.seconds=15` |
| `retry.count` | Additional attempts after failure | `-Dretry.count=0` |
| `apiBaseUri` | API suite base URI | `-DapiBaseUri=https://api.example.com` |
| `platform` | Execution platform | `-Dplatform=android` |
| `androidTarget` | Android web or native target | `-DandroidTarget=native` |
| `deviceUdid` | Android device identifier | `-DdeviceUdid=emulator-5554` |
| `appiumServerUrl` | External Appium server | `-DappiumServerUrl=http://127.0.0.1:4723` |
| `appPackage` | Android package to launch | `-DappPackage=com.myntra.android` |
| `appActivity` | Android activity to launch | `-DappActivity=.MainActivity` |
| `app` | APK path or download URL | `-Dapp=C:/apps/example.apk` |

The mobile Maven profiles set `platform` and `androidTarget` automatically.

## Test Groups

Tests are organized with TestNG groups:

- `web`
- `api`
- `mobile`

Group names are centralized as constants to avoid silent failures caused by misspelled group names.

The web and API suites use package scanning and group filters, so correctly tagged tests are included without requiring a new `<class>` entry in the suite file.

Mobile tests share the `mobile` group but remain split into separate suites because mobile-web and native-app tests require different Appium session capabilities.

## Allure Reports

Run tests and open the report:

```powershell
mvn clean test "-Preport"
```

You can also run and serve separately:

```powershell
mvn test
mvn allure:serve
```

Raw results are written to:

```text
target/allure-results
```

The Maven lifecycle clears stale Allure results at the beginning of every test run. This prevents results from multiple executions from being mixed into one report.

Running `mvn allure:serve` alone does not execute tests. It only serves the results produced by the most recent test run.

## Architecture Highlights

### Thread-Safe Driver Lifecycle

Each parallel test thread receives an isolated driver through `ThreadLocal<WebDriver>`.

Driver responsibilities are separated across dedicated classes:

- `DriverFactory` creates desktop browser sessions.
- `MobileDriverFactory` creates Appium sessions.
- `DriverManager` stores and releases each thread's driver.
- `AppiumServerManager` manages the Appium server lifecycle.

This prevents parallel tests from overwriting a shared static driver and ensures references are removed after execution.

### Unified Web and Mobile Driver Model

Appium's Android driver implements Selenium's `WebDriver` interface. This lets desktop and Android sessions share the driver-management abstraction while keeping platform-specific creation logic isolated.

### Page Object Model

Browser and mobile interactions live in page classes such as:

- `BasePage`
- `MyntraHomePage`
- `MyntraSearchResultsPage`
- `MyntraAppHomePage`
- `MyntraAppSearchPage`

Tests focus on behavior and assertions instead of locator, wait, and interaction details.

### Android Lifecycle Support

The core Android layer includes responsibilities for:

- Discovering connected devices
- Resolving the Android SDK and `adb`
- Starting and stopping Appium
- Creating mobile-web and native sessions
- Checking whether an application is installed
- Installing applications from APK sources
- Opening Google Play listings
- Launching packages and activities
- Verifying foreground application state

### Layered API Automation

The API test layer separates:

- Reusable request specifications
- Service methods
- Request and response models
- Shared response validation
- Test scenarios

REST Assured requests and responses integrate with the same Allure reporting pipeline used by UI tests.

### Failure Diagnostics

When supported UI tests fail, framework listeners can attach useful diagnostic artifacts such as:

- Screenshots
- Page source
- Test metadata
- API requests and responses

This makes local and report-based investigation easier.

### Retry Support

Failed tests can be retried through the configured retry analyzer.

The default configuration allows one additional attempt:

```properties
retry.count=1
```

Disable retries when investigating deterministic failures:

```powershell
mvn test "-Dretry.count=0"
```

## Existing Test Coverage

### Web UI

The web layer includes examples for:

- Basic browser launch
- Myntra homepage validation
- Product search
- Data-driven product searches
- Search-results validation

### API

The API layer demonstrates:

- Shared REST Assured specifications
- Service abstraction
- Typed response models
- Reusable validators
- Allure request and response attachments

### Mobile Web

The mobile-web suite demonstrates launching Chrome through Appium on a connected Android device.

### Native Android

The native layer demonstrates:

- Installed-app discovery
- Package-based launch
- APK installation
- Play Store installation
- Myntra native-app page objects
- Foreground and background lifecycle checks
- Native product search

## Documentation

Detailed guides are available in the [`docs`](docs/) directory:

- [Architecture](docs/ARCHITECTURE.md)
- [Command Cheatsheet](docs/COMMANDS.md)
- [How to Add Tests](docs/HOW-TO-ADD-TESTS.md)
- [Parallel and Cross-Browser Execution](docs/PARALLEL-AND-CROSS-BROWSER.md)
- [Browser Launch Class Interaction](docs/CLASS-INTERACTION-BROWSER-LAUNCH.md)
- [Mobile and Appium Class Interaction](docs/CLASS-INTERACTION-MOBILE-APPIUM.md)
- [Deep Dive: How It Works](docs/DEEP-DIVE-HOW-IT-WORKS.md)
- [Failure Diagnostics and Headless Findings](docs/FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md)
- [Changes Explained](docs/CHANGES-EXPLAINED.md)
- [Agentic Integration Design Approach](docs/Agentic%20Integration%20Design%20Approach%20-%20Phase%201.md)

## Adding Tests

### Add a web test

1. Add or update a page object under `src/main/java/com/project/qa/pages`.
2. Add the test under `src/test/java/com/project/qa/tests`.
3. Assign it to the `web` TestNG group.
4. Extend the shared test lifecycle where appropriate.
5. Run:

```powershell
mvn test "-Pweb"
```

### Add an API test

1. Add models under the API model package.
2. Add reusable calls to the API service layer.
3. Add shared validation to the validator layer when appropriate.
4. Add the test under the API test package.
5. Assign it to the `api` TestNG group.
6. Run:

```powershell
mvn test "-Papi"
```

### Add a mobile test

1. Determine whether the test needs a mobile-web or native session.
2. Add reusable screen interactions to a mobile page object.
3. Add the test under the test package.
4. Assign it to the `mobile` TestNG group.
5. Use the Maven profile matching the required session:

```powershell
mvn test "-Pmobile"
```

or:

```powershell
mvn test "-Pmobile-native"
```

See [How to Add Tests](docs/HOW-TO-ADD-TESTS.md) for the complete workflow.

## Troubleshooting

### No Android device is detected

Run:

```bash
adb devices -l
```

Confirm that:

- USB debugging is enabled.
- The authorization prompt has been accepted.
- The device state is `device`.
- The Android SDK platform tools are available on `PATH`.

### UiAutomator2 is missing

Install it with:

```bash
appium driver install uiautomator2
```

Verify it with:

```bash
appium driver list --installed
```

### Appium cannot be started

Confirm that Appium is installed globally:

```bash
appium -v
```

If you manage the server yourself, start it separately and pass its URL:

```bash
appium
mvn test "-Pmobile" "-DappiumServerUrl=http://127.0.0.1:4723"
```

### Headless web tests fail against Myntra

Myntra may return anti-bot behavior during headless execution.

Re-run in headed mode:

```powershell
mvn test "-Dheadless=false"
```

Then inspect the screenshot, page source, logs, and Allure report. See [Failure Diagnostics and Headless Findings](docs/FAILURE-DIAGNOSTICS-AND-HEADLESS-FINDING.md).

### The Allure report shows old results

Run the tests before serving the report:

```bash
mvn test
mvn allure:serve
```

The framework clears stale results when a new Maven test lifecycle begins.

### Parallel tests interfere with one another

Make sure new tests use the shared `DriverManager` and test lifecycle instead of introducing a static shared driver.

To simplify debugging, temporarily run with one thread:

```powershell
mvn test "-Dthreads=1"
```

### A failed test passes on retry

Disable retries to expose the original failure consistently:

```powershell
mvn test "-Dretry.count=0"
```

## Useful Android Commands

List connected devices:

```powershell
adb devices -l
```

List user-installed packages:

```powershell
adb shell pm list packages -3
```

Search for an installed package:

```powershell
adb shell pm list packages | Select-String myntra
```

Resolve an application's launchable activity:

```powershell
adb shell cmd package resolve-activity --brief com.myntra.android
```

Inspect the foreground activity:

```powershell
adb shell dumpsys activity activities | Select-String "mResumedActivity"
```

Check the device model and Android version:

```powershell
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
```

## Design Principles

This project emphasizes:

- **Clarity over cleverness**
- **Single Responsibility Principle**
- **Minimal unnecessary abstraction**
- **Configuration over hard-coded values**
- **Thread-safe execution**
- **Reusable page and service layers**
- **Useful diagnostics over silent failure**
- **Tests that remain independent of one another**

## Learning Goals

This repository is intended to make framework design decisions visible and explainable. It demonstrates how to:

- Build a maintainable Java automation framework
- Support UI, API, and mobile testing in one project
- Separate framework infrastructure from test scenarios
- Execute browser tests safely in parallel
- Switch environments and platforms through runtime configuration
- Integrate reporting and diagnostic artifacts
- Design reusable page objects and API service layers
- Manage Android application installation and launch workflows

## Contributing

Contributions and learning experiments are welcome.

When contributing:

1. Keep each change focused.
2. Follow the existing package responsibilities.
3. Put UI actions and locators in page objects.
4. Put reusable API operations in service classes.
5. Avoid creating shared static drivers.
6. Assign every test to the correct TestNG group.
7. Document new configuration keys and Maven profiles.
8. Run the relevant test profile before submitting the change.

For more guidance, see [How to Add Tests](docs/HOW-TO-ADD-TESTS.md).
