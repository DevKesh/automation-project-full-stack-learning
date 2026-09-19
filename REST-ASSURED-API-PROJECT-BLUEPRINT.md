# REST Assured API Automation — Project Blueprint

This single file is a **complete, self-contained blueprint** for a fresh, standalone REST Assured + TestNG + Allure API automation project. It retains every API flow and design pattern from the parent framework (JSONPlaceholder CRUD + reqres.in users/auth), but strips out the UI/mobile/Maestro parts so the result is a clean, browser-free API project.

## How to use this file

**Option A — with an AI coding agent:** Upload this file to a new, empty repository and prompt:
> "Create every file exactly as specified in this blueprint at the given paths, then run `mvn -q test` and fix any issues until BUILD SUCCESS."

**Option B — manually:** Create each file at the path shown in its heading, copy the code verbatim, then run the commands in Section 9.

**Requirements:** JDK 17+ (21 recommended), Maven 3.9+, internet access (tests hit public sandbox APIs).

---

## 1. What you get

| Layer | Classes | Purpose |
|---|---|---|
| **Config** | `ConfigReader` + `config.properties` | Env-aware config with `-D` override precedence |
| **Spec** | `ApiSpecFactory` | Pre-built, immutable request specs (base URI, headers, Allure, logging) |
| **Validator** | `ApiValidator` | Fluent soft-assert helper with self-narrating logs + Allure steps |
| **Models (POJOs)** | `Post`, `User`, `Support`, `SingleUserResponse`, `UserListResponse`, `CreateUserRequest`, `UserMutationResponse`, `AuthRequest`, `AuthResponse` | Typed request/response bodies |
| **Services (endpoint objects)** | `PostService`, `UserService`, `AuthService` | Hide HTTP verbs/paths behind intent-revealing methods |
| **Tests** | `PostApiTest`, `UserApiTest`, `AuthApiTest` | 15 scenarios: CRUD, pagination, 404, auth, negative paths |
| **Support** | `ApiBaseTest`, `TestGroups`, `RetryAnalyzer`, `RetryTransformer` | Base class, group constants, flaky-test retry |
| **Suite/Report** | `testng-api.xml`, `logback-test.xml`, `pom.xml` | Runner, logging, build |

**APIs exercised (no keys/signup needed beyond reqres's free key, already included):**
- `https://jsonplaceholder.typicode.com` — `/posts` CRUD
- `https://reqres.in` — `/api/users` (pagination, CRUD, 404) and `/api/register`, `/api/login`

---

## 2. Directory structure

```
rest-assured-api-tests/
├── pom.xml
├── README.md
└── src/
    └── test/
        ├── java/
        │   └── com/project/qa/
        │       ├── framework/
        │       │   └── configuration/
        │       │       └── ConfigReader.java
        │       ├── testsupport/
        │       │   ├── constants/
        │       │   │   └── TestGroups.java
        │       │   ├── listeners/
        │       │   │   ├── RetryAnalyzer.java
        │       │   │   └── RetryTransformer.java
        │       │   └── api/
        │       │       ├── ApiSpecFactory.java
        │       │       ├── ApiValidator.java
        │       │       ├── models/
        │       │       │   ├── Post.java
        │       │       │   └── reqres/
        │       │       │       ├── User.java
        │       │       │       ├── Support.java
        │       │       │       ├── SingleUserResponse.java
        │       │       │       ├── UserListResponse.java
        │       │       │       ├── CreateUserRequest.java
        │       │       │       ├── UserMutationResponse.java
        │       │       │       ├── AuthRequest.java
        │       │       │       └── AuthResponse.java
        │       │       └── services/
        │       │           ├── PostService.java
        │       │           ├── UserService.java
        │       │           └── AuthService.java
        │       └── tests/
        │           └── api/
        │               ├── ApiBaseTest.java
        │               ├── PostApiTest.java
        │               ├── UserApiTest.java
        │               └── AuthApiTest.java
        └── resources/
            ├── config/
            │   └── config.properties
            ├── logback-test.xml
            └── suites/
                └── testng-api.xml
```

> **Note:** Everything lives under `src/test`. REST Assured is a test dependency, and an API-only project has no production artifact — so there is no `src/main`. The package names are kept identical to the parent framework so the Java files are byte-for-byte reusable; rename `com.project.qa` freely if you prefer.

---

## 3. Build file — `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.project.qa</groupId>
    <artifactId>rest-assured-api-tests</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <testng.version>7.9.0</testng.version>
        <restassured.version>5.5.0</restassured.version>
        <allure.version>2.29.0</allure.version>
        <jackson.version>2.17.0</jackson.version>
        <aspectj.version>1.9.21</aspectj.version>
        <!-- Default parallel thread count; override per run with -Dthreads=N -->
        <threads>3</threads>
    </properties>

    <dependencies>
        <!-- TestNG: the test runner. -->
        <dependency>
            <groupId>org.testng</groupId>
            <artifactId>testng</artifactId>
            <version>${testng.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- REST Assured: HTTP transport for the API layer. Ships Jackson-backed
             (de)serialization, so response.as(Post.class) works out of the box. -->
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <version>${restassured.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Explicit Jackson databind: a test uses ObjectMapper directly for pretty-printing. -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Allure TestNG: report engine + @Step annotation (via transitive allure-java-commons). -->
        <dependency>
            <groupId>io.qameta.allure</groupId>
            <artifactId>allure-testng</artifactId>
            <version>${allure.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Bridges REST Assured into Allure: the AllureRestAssured filter auto-attaches
             each request + response to the report. -->
        <dependency>
            <groupId>io.qameta.allure</groupId>
            <artifactId>allure-rest-assured</artifactId>
            <version>${allure.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- SLF4J API (the logging interface). -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.12</version>
            <scope>test</scope>
        </dependency>

        <!-- Logback (the logging engine). -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.4.14</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <!--
          Filter testng-api.xml so ${threads} is substituted at build time from the Maven
          property. Surefire runs the filtered copy from target/test-classes, letting
          `-Dthreads=N` control parallelism (TestNG reads thread-count from the suite XML).
        -->
        <testResources>
            <testResource>
                <directory>src/test/resources</directory>
                <filtering>true</filtering>
                <includes>
                    <include>suites/testng-api.xml</include>
                </includes>
            </testResource>
            <testResource>
                <directory>src/test/resources</directory>
                <filtering>false</filtering>
                <excludes>
                    <exclude>suites/testng-api.xml</exclude>
                </excludes>
            </testResource>
        </testResources>

        <plugins>
            <!--
              Wipe stale Allure results before each test run. Surefire writes one UUID-named
              json per test and never overwrites, so without this the folder accumulates every
              run and `mvn allure:serve` shows a cumulative, mostly-stale report. Scoped to
              delete ONLY allure-results, not the rest of target/.
            -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-clean-plugin</artifactId>
                <version>3.3.2</version>
                <executions>
                    <execution>
                        <id>clean-allure-results</id>
                        <phase>initialize</phase>
                        <goals>
                            <goal>clean</goal>
                        </goals>
                        <configuration>
                            <excludeDefaultDirectories>true</excludeDefaultDirectories>
                            <filesets>
                                <fileset>
                                    <directory>${project.build.directory}/allure-results</directory>
                                </fileset>
                            </filesets>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <suiteXmlFiles>
                        <suiteXmlFile>${project.build.testOutputDirectory}/suites/testng-api.xml</suiteXmlFile>
                    </suiteXmlFiles>
                    <systemPropertyVariables>
                        <allure.results.directory>${project.build.directory}/allure-results</allure.results.directory>
                    </systemPropertyVariables>
                    <!-- AspectJ weaver hook is what powers Allure @Step interception. -->
                    <argLine>
                        -javaagent:"${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar"
                    </argLine>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.aspectj</groupId>
                        <artifactId>aspectjweaver</artifactId>
                        <version>${aspectj.version}</version>
                    </dependency>
                </dependencies>
            </plugin>

            <plugin>
                <groupId>io.qameta.allure</groupId>
                <artifactId>allure-maven</artifactId>
                <version>2.15.2</version>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <!--
          'report' profile: opt-in auto-open of the Allure report.
          `mvn clean verify -Preport` runs the suite, then serves + opens the report.
          Kept in a profile so plain `mvn test` and CI never launch a blocking web server.
        -->
        <profile>
            <id>report</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>io.qameta.allure</groupId>
                        <artifactId>allure-maven</artifactId>
                        <version>2.15.2</version>
                        <configuration>
                            <reportVersion>2.30.0</reportVersion>
                        </configuration>
                        <executions>
                            <execution>
                                <id>serve-allure-report</id>
                                <phase>post-integration-test</phase>
                                <goals>
                                    <goal>serve</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

---

## 4. Resources

### `src/test/resources/config/config.properties`

```properties
# Committed defaults. Override any value at runtime via -D, e.g. `mvn test -DapiBaseUri=...`.

# --- Environment selection ---
# Selects which config-{env}.properties overlay layers on top of these base defaults.
# Leave blank to run against these base defaults only.
env=

# --- Flaky-test retry ---
# Extra attempts for a failed test before it is reported as failed. 0 disables retries.
retry.count=1

# --- API: JSONPlaceholder ---
# Base URI for the /posts CRUD suite. Override e.g. `mvn test -DapiBaseUri=https://staging...`.
apiBaseUri=https://jsonplaceholder.typicode.com

# --- API: reqres.in ---
# The free tier requires an API key sent as the x-api-key header.
reqresBaseUri=https://reqres.in
reqresApiKey=reqres-free-v1
```

### `src/test/resources/logback-test.xml`

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <!-- [%thread] makes each line attributable when tests run in parallel -->
            <pattern>%d{HH:mm:ss} [%thread] %-5level - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

### `src/test/resources/suites/testng-api.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<!-- API-only suite. The <groups><run><include> filter runs just the @Test methods tagged "api".
     Browser-free REST Assured calls are stateless, so parallel="methods" is safe.
     thread-count is filtered from the Maven ${threads} property at build time. -->
<suite name="API Suite" verbose="1" parallel="methods" thread-count="${threads}">

    <listeners>
        <listener class-name="io.qameta.allure.testng.AllureTestNg"/>
        <!-- Auto-attaches RetryAnalyzer to every @Test; retry.count in config controls attempts -->
        <listener class-name="com.project.qa.testsupport.listeners.RetryTransformer"/>
    </listeners>

    <groups>
        <run>
            <include name="api"/>
        </run>
    </groups>

    <!-- Package scan, not a hand-maintained <class> list. Any @Test(groups = API) under this
         package auto-joins the suite. Adding an API test needs zero edits here. -->
    <test name="API Tests">
        <packages>
            <package name="com.project.qa.tests.api"/>
        </packages>
    </test>

</suite>
```

---

## 5. Framework support classes

### `src/test/java/com/project/qa/framework/configuration/ConfigReader.java`

```java
package com.project.qa.framework.configuration;

import org.slf4j.*;

import java.io.*;
import java.util.*;

/*
 * Central access point for runtime configuration.
 *
 * Two-layer, environment-aware resolution per key:
 *   1. JVM System property (-Dkey=value)      — highest precedence (CI / command line)
 *   2. config-{env}.properties (env overlay)   — environment-specific values
 *   3. config.properties (base defaults)        — shared, committed defaults
 *
 * The active environment is chosen by `-Denv=<name>` (or an `env=` entry in the base file).
 */
public final class ConfigReader {

	private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);
	private static final String BASE_CONFIG_FILE = "/config/config.properties";

	private static final Properties baseProperties = loadRequired(BASE_CONFIG_FILE);
	private static final Properties envProperties = loadEnvOverlay();

	private ConfigReader() {
	}

	private static Properties loadRequired(String resource) {
		Properties props = new Properties();
		try (InputStream stream = ConfigReader.class.getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IllegalStateException(resource + " not found on the classpath");
			}
			props.load(stream);
			log.debug("Loaded configuration from {}", resource);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load " + resource, e);
		}
		return props;
	}

	// Resolve the active environment, then load its overlay. A configured-but-missing overlay is a
	// hard failure: silently running against base defaults would mask a mistyped `-Denv` in CI.
	private static Properties loadEnvOverlay() {
		String env = System.getProperty("env");
		if (env == null || env.isBlank()) {
			env = baseProperties.getProperty("env");
		}
		if (env == null || env.isBlank()) {
			log.debug("No environment selected; using base configuration only");
			return new Properties();
		}

		String overlayFile = "/config/config-" + env.trim() + ".properties";
		log.info("Active environment: {} (overlay {})", env.trim(), overlayFile);
		return loadRequired(overlayFile);
	}

	// Precedence: System property > env overlay > base defaults.
	private static String resolve(String key) {
		String value = System.getProperty(key);
		if (value == null || value.isBlank()) {
			value = envProperties.getProperty(key);
		}
		if (value == null || value.isBlank()) {
			value = baseProperties.getProperty(key);
		}
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	private static String resolveOrDefault(String key, String fallback) {
		String value = resolve(key);
		return value == null ? fallback : value;
	}

	// Fails fast on a non-numeric config value rather than defaulting silently, so a typo surfaces
	// immediately instead of altering test behaviour unnoticed.
	private static int resolveInt(String key, int fallback) {
		String value = resolve(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalStateException("Config key '" + key + "' must be an integer, but was: " + value, e);
		}
	}

	public static String getEnv() {
		return resolveOrDefault("env", "default");
	}

	// Extra attempts for a failed test before it is reported as failed. 0 disables retries.
	public static int getRetryCount() {
		return resolveInt("retry.count", 0);
	}

	// --- API (REST Assured) ---

	public static String getApiBaseUri() {
		return resolve("apiBaseUri");
	}

	public static String getReqresBaseUri() {
		return resolve("reqresBaseUri");
	}

	public static String getReqresApiKey() {
		return resolve("reqresApiKey");
	}
}
```

### `src/test/java/com/project/qa/testsupport/constants/TestGroups.java`

```java
package com.project.qa.testsupport.constants;

/**
 * Single source of truth for TestNG group names.
 *
 * WHY a constants class: group names are referenced from every @Test annotation and must match the
 * strings used in the testng.xml <include> filters exactly. Centralising them here means a typo
 * fails to compile on the Java side instead of silently excluding a test from a run.
 */
public final class TestGroups {

	public static final String API = "api";

	private TestGroups() {
		// Utility holder: no instances.
	}
}
```

### `src/test/java/com/project/qa/testsupport/listeners/RetryAnalyzer.java`

```java
package com.project.qa.testsupport.listeners;

import com.project.qa.framework.configuration.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/*
 * Re-runs a failed test up to `retry.count` times before reporting it as failed.
 *
 * WHY: environment flakiness (a slow network hop, a transient 5xx) should not fail an otherwise-
 * correct suite. A bounded retry absorbs that noise while still surfacing genuine, repeatable
 * failures. The ceiling is config-driven so it can be tightened in CI and loosened locally.
 *
 * TestNG creates a fresh analyzer instance per test method, so `attempts` is naturally per-test
 * state and is safe under parallel execution — no sharing across threads.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(RetryAnalyzer.class);
	private static final int MAX_RETRIES = ConfigReader.getRetryCount();

	private int attempts = 0;

	@Override
	public boolean retry(ITestResult result) {
		if (attempts < MAX_RETRIES) {
			attempts++;
			log.info("Retrying '{}' (attempt {} of {}) after failure", result.getName(), attempts, MAX_RETRIES);
			return true;
		}
		return false;
	}
}
```

### `src/test/java/com/project/qa/testsupport/listeners/RetryTransformer.java`

```java
package com.project.qa.testsupport.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/*
 * Attaches RetryAnalyzer to every @Test at load time, so the retry policy is applied suite-wide
 * without annotating each method individually. A single registration in each testng.xml keeps the
 * policy centralised and impossible to forget on a new test.
 */
public class RetryTransformer implements IAnnotationTransformer {

	@Override
	public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
		annotation.setRetryAnalyzer(RetryAnalyzer.class);
	}
}
```

---

## 6. API layer — spec, validator, base test

### `src/test/java/com/project/qa/testsupport/api/ApiSpecFactory.java`

```java
package com.project.qa.testsupport.api;

import com.project.qa.framework.configuration.ConfigReader;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/*
 * Single source of truth for HTTP request configuration.
 *
 * Every service call reuses one immutable, pre-built spec so base URI, headers, reporting and
 * logging are guaranteed identical across the suite. The AllureRestAssured filter attaches each
 * request/response to the Allure report. Console logging of the raw request/response is emitted
 * only when a validation fails (LogDetail.ALL) — clean passing runs, full forensics on failure.
 * The specs are stateless and therefore safe to share across TestNG's parallel method threads.
 */
public final class ApiSpecFactory {

	private static final RestAssuredConfig LOG_ON_FAILURE = RestAssuredConfig.config()
			.logConfig(LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL));

	private static final RequestSpecification SPEC = new RequestSpecBuilder()
			.setBaseUri(ConfigReader.getApiBaseUri())
			.setConfig(LOG_ON_FAILURE)
			.setContentType(ContentType.JSON)
			.setAccept(ContentType.JSON)
			.addFilter(new AllureRestAssured())
			.build();

	// A second immutable spec for reqres.in. It differs from SPEC only in base URI and the mandatory
	// x-api-key header, but keeps the identical config/reporting so both API targets behave uniformly.
	private static final RequestSpecification REQRES = new RequestSpecBuilder()
			.setBaseUri(ConfigReader.getReqresBaseUri())
			.setConfig(LOG_ON_FAILURE)
			.setContentType(ContentType.JSON)
			.setAccept(ContentType.JSON)
			.addHeader("x-api-key", ConfigReader.getReqresApiKey())
			.addFilter(new AllureRestAssured())
			.build();

	private ApiSpecFactory() {
	}

	public static RequestSpecification spec() {
		return SPEC;
	}

	public static RequestSpecification reqres() {
		return REQRES;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/ApiValidator.java`

```java
package com.project.qa.testsupport.api;

import io.qameta.allure.Step;
import org.slf4j.Logger;
import org.testng.asserts.SoftAssert;

import java.util.Objects;

/*
 * Fluent, self-narrating validation helper for the API layer.
 *
 * Wraps TestNG's SoftAssert (so every mismatch is collected rather than failing on the first) while
 * logging each check as a readable line — "[PASS] <what> | expected=<x> actual=<y>" — and recording
 * it as an Allure @Step. Both the console output and the report read as a plain-English account of
 * exactly what was verified.
 */
public class ApiValidator {

	private final Logger log;
	private final SoftAssert softAssert = new SoftAssert();

	public ApiValidator(Logger log) {
		this.log = log;
	}

	@Step("Validate {what}: expected <{expected}>, got <{actual}>")
	public ApiValidator expect(String what, Object expected, Object actual) {
		boolean passed = Objects.equals(expected, actual);
		log.info("   [{}] {} | expected=<{}> actual=<{}>", passed ? "PASS" : "FAIL", what, expected, actual);
		softAssert.assertEquals(actual, expected, what);
		return this;
	}

	@Step("Validate {what}")
	public ApiValidator expectTrue(String what, boolean condition) {
		log.info("   [{}] {}", condition ? "PASS" : "FAIL", what);
		softAssert.assertTrue(condition, what);
		return this;
	}

	// Fails the aggregated soft assertions; call once at the end of each test.
	public void verifyAll() {
		log.info("   -> All validations checked; asserting results");
		softAssert.assertAll();
	}
}
```

### `src/test/java/com/project/qa/tests/api/ApiBaseTest.java`

```java
package com.project.qa.tests.api;

import com.project.qa.framework.configuration.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.testng.annotations.BeforeClass;

/*
 * Thin base for API tests. Sets RestAssured.baseURI once and provides shared response-logging
 * helpers so every test's console output reads like a transaction ledger.
 */
public class ApiBaseTest {

	// alwaysRun = true so the base URI is still set when the suite is filtered to the "api" group.
	@BeforeClass(alwaysRun = true)
	public void configureBaseUri() {
		RestAssured.baseURI = ConfigReader.getApiBaseUri();
	}

	// Shared, one-line response summary. Takes the caller's Logger so each line is attributed to the
	// concrete test class, not this base.
	protected void logResponseSummary(Logger log, Response response) {
		log.info("RESPONSE: status={} ({}), timeMs={}, contentType={}",
				response.statusCode(), response.statusLine(), response.time(), response.contentType());
	}

	// Full pretty-printed body for single-resource responses, so the exact payload is visible in logs.
	protected void logResponseBody(Logger log, Response response) {
		String body = response.getBody().asPrettyString();
		log.info("RESPONSE BODY:\n{}", body.isBlank() ? "(empty body)" : body);
	}
}
```

---

## 7. Models (POJOs)

### `src/test/java/com/project/qa/testsupport/api/models/Post.java`

```java
package com.project.qa.testsupport.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Type-safe view of the /posts resource. REST Assured deserializes responses straight into this via
 * Jackson, so tests assert on real fields instead of brittle JSON path strings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Post {

	private int userId;
	private int id;
	private String title;
	private String body;

	public Post() {
	}

	public Post(int userId, String title, String body) {
		this.userId = userId;
		this.title = title;
		this.body = body;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/User.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * Type-safe view of a reqres.in user. The API uses snake_case JSON (first_name/last_name); the
 * @JsonProperty bindings map those onto idiomatic camelCase Java fields so tests stay clean.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

	private int id;
	private String email;

	@JsonProperty("first_name")
	private String firstName;

	@JsonProperty("last_name")
	private String lastName;

	private String avatar;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/Support.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * The "support" block reqres.in appends to most responses. Modelled so tests can assert the
 * envelope is present, demonstrating validation of nested objects, not just top-level fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Support {

	private String url;
	private String text;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/SingleUserResponse.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Envelope for GET /api/users/{id}: the user sits under "data", with a sibling "support" block.
 * Deserializing the whole envelope (rather than reaching in with JsonPath) keeps the nesting typed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SingleUserResponse {

	private User data;
	private Support support;

	public User getData() {
		return data;
	}

	public void setData(User data) {
		this.data = data;
	}

	public Support getSupport() {
		return support;
	}

	public void setSupport(Support support) {
		this.support = support;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/UserListResponse.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/*
 * Envelope for GET /api/users?page=N. Carries the pagination metadata (page/per_page/total/
 * total_pages) alongside the page's "data" list — the canonical shape for asserting paginated APIs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserListResponse {

	private int page;

	@JsonProperty("per_page")
	private int perPage;

	private int total;

	@JsonProperty("total_pages")
	private int totalPages;

	private List<User> data;
	private Support support;

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getPerPage() {
		return perPage;
	}

	public void setPerPage(int perPage) {
		this.perPage = perPage;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	public List<User> getData() {
		return data;
	}

	public void setData(List<User> data) {
		this.data = data;
	}

	public Support getSupport() {
		return support;
	}

	public void setSupport(Support support) {
		this.support = support;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/CreateUserRequest.java`

```java
package com.project.qa.testsupport.api.models.reqres;

/*
 * Request body for POST/PUT /api/users — reqres only reads {name, job}. A dedicated request POJO
 * (separate from the richer response models) keeps the sent payload explicit and minimal.
 */
public class CreateUserRequest {

	private String name;
	private String job;

	public CreateUserRequest() {
	}

	public CreateUserRequest(String name, String job) {
		this.name = name;
		this.job = job;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getJob() {
		return job;
	}

	public void setJob(String job) {
		this.job = job;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/UserMutationResponse.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Shared response for the write endpoints: POST returns {name, job, id, createdAt} and PUT returns
 * {name, job, updatedAt}. One tolerant POJO covers both — the timestamp not returned is simply null.
 *
 * NOTE: reqres serialises the created "id" as a STRING ("260"), not a number, so it is typed as
 * String here. A deliberate, real-world reminder that response types must match the wire.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMutationResponse {

	private String name;
	private String job;
	private String id;
	private String createdAt;
	private String updatedAt;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getJob() {
		return job;
	}

	public void setJob(String job) {
		this.job = job;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/AuthRequest.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonInclude;

/*
 * Request body for /api/register and /api/login: {email, password}. JsonInclude.NON_NULL omits a
 * null field entirely, so a negative test can send email-only and reliably trigger the API's
 * "Missing password" 400 instead of sending password:null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthRequest {

	private String email;
	private String password;

	public AuthRequest() {
	}

	public AuthRequest(String email, String password) {
		this.email = email;
		this.password = password;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/models/reqres/AuthResponse.java`

```java
package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Unified response for register/login. Success carries {id?, token} (login omits id); failure
 * carries {error}. Modelling all three in one tolerant POJO lets both positive and negative tests
 * deserialize the same type and assert on whichever field is relevant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {

	private Integer id;
	private String token;
	private String error;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}
}
```

---

## 8. Services and Tests

### `src/test/java/com/project/qa/testsupport/api/services/PostService.java`

```java
package com.project.qa.testsupport.api.services;

import com.project.qa.testsupport.api.ApiSpecFactory;
import com.project.qa.testsupport.api.models.Post;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/*
 * Endpoint object for the /posts resource — the API-layer analogue of a Page Object.
 * Hides HTTP verbs and paths behind intent-revealing methods. Assertions live in the tests,
 * keeping this layer a thin, reusable transport.
 */
public class PostService {

	private static final String POSTS = "/posts";
	private static final String POST_BY_ID = "/posts/{id}";

	@Step("GET all posts")
	public Response getAllPosts() {
		return given().spec(ApiSpecFactory.spec())
				.when().get(POSTS);
	}

	@Step("GET post by id {id}")
	public Response getPost(int id) {
		return given().spec(ApiSpecFactory.spec())
				.pathParam("id", id)
				.when().get(POST_BY_ID);
	}

	@Step("POST create a new post")
	public Response createPost(Post post) {
		return given().spec(ApiSpecFactory.spec())
				.body(post)
				.when().post(POSTS);
	}

	@Step("PUT update post id {id}")
	public Response updatePost(int id, Post post) {
		return given().spec(ApiSpecFactory.spec())
				.pathParam("id", id)
				.body(post)
				.when().put(POST_BY_ID);
	}

	@Step("DELETE post id {id}")
	public Response deletePost(int id) {
		return given().spec(ApiSpecFactory.spec())
				.pathParam("id", id)
				.when().delete(POST_BY_ID);
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/services/UserService.java`

```java
package com.project.qa.testsupport.api.services;

import com.project.qa.testsupport.api.ApiSpecFactory;
import com.project.qa.testsupport.api.models.reqres.CreateUserRequest;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/*
 * Endpoint object for the reqres.in /api/users resource. Every call reuses the shared reqres spec
 * (base URI + x-api-key), and assertions stay in the tests.
 */
public class UserService {

	private static final String USERS = "/api/users";
	private static final String USER_BY_ID = "/api/users/{id}";

	@Step("GET users on page {page}")
	public Response listUsers(int page) {
		return given().spec(ApiSpecFactory.reqres())
				.queryParam("page", page)
				.when().get(USERS);
	}

	@Step("GET user by id {id}")
	public Response getUser(int id) {
		return given().spec(ApiSpecFactory.reqres())
				.pathParam("id", id)
				.when().get(USER_BY_ID);
	}

	@Step("POST create user")
	public Response createUser(CreateUserRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.body(request)
				.when().post(USERS);
	}

	@Step("PUT update user id {id}")
	public Response updateUser(int id, CreateUserRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.pathParam("id", id)
				.body(request)
				.when().put(USER_BY_ID);
	}

	@Step("DELETE user id {id}")
	public Response deleteUser(int id) {
		return given().spec(ApiSpecFactory.reqres())
				.pathParam("id", id)
				.when().delete(USER_BY_ID);
	}
}
```

### `src/test/java/com/project/qa/testsupport/api/services/AuthService.java`

```java
package com.project.qa.testsupport.api.services;

import com.project.qa.testsupport.api.ApiSpecFactory;
import com.project.qa.testsupport.api.models.reqres.AuthRequest;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/*
 * Endpoint object for reqres.in authentication (/api/register, /api/login). Kept separate from
 * UserService so each service maps to a single resource concern.
 */
public class AuthService {

	private static final String REGISTER = "/api/register";
	private static final String LOGIN = "/api/login";

	@Step("POST register")
	public Response register(AuthRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.body(request)
				.when().post(REGISTER);
	}

	@Step("POST login")
	public Response login(AuthRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.body(request)
				.when().post(LOGIN);
	}
}
```

### `src/test/java/com/project/qa/tests/api/PostApiTest.java`

```java
package com.project.qa.tests.api;

import com.project.qa.testsupport.api.ApiValidator;
import com.project.qa.testsupport.api.models.Post;
import com.project.qa.testsupport.api.services.PostService;
import com.project.qa.testsupport.constants.TestGroups;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.List;

/*
 * End-to-end contract coverage for the /posts resource against JSONPlaceholder.
 */
@Epic("API Testing")
@Feature("Posts resource (/posts)")
public class PostApiTest extends ApiBaseTest {

	private static final Logger log = LoggerFactory.getLogger(PostApiTest.class);
	private static final long MAX_RESPONSE_MS = 5000;
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	private final PostService postService = new PostService();

	@Test(groups = TestGroups.API)
	@Description("GET /posts/1 returns 200 with the expected post payload")
	public void getSinglePost() {
		log.info("===== SCENARIO: Fetch a single post =====");
		log.info("REQUEST : GET /posts/1  (retrieve the post whose id is 1)");

		Response response = postService.getPost(1);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		Post post = response.as(Post.class);
		log.info("RESPONSE DATA: id={}, userId={}, title=\"{}\"", post.getId(), post.getUserId(), post.getTitle());

		log.info("VALIDATING the response contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Content-Type is JSON (actual: " + response.contentType() + ")",
						response.contentType().contains("application/json"))
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.expect("post id in body", 1, post.getId())
				.expect("userId in body", 1, post.getUserId())
				.expectTrue("title is present", post.getTitle() != null && !post.getTitle().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: single post matched the expected contract =====");
	}

	@Test(groups = TestGroups.API)
	@Description("GET /posts returns 200 with the full collection of 100 posts")
	public void getAllPosts() {
		log.info("===== SCENARIO: Fetch the whole posts collection =====");
		log.info("REQUEST : GET /posts  (retrieve every post)");

		Response response = postService.getAllPosts();
		logResponseSummary(log, response);
		List<Post> posts = List.of(response.as(Post[].class));
		log.info("RESPONSE DATA: received {} posts; first post -> id={}, title=\"{}\"",
				posts.size(), posts.get(0).getId(), posts.get(0).getTitle());
		log.info("RESPONSE BODY (first item of {}, rest omitted to keep the log readable):\n{}",
				posts.size(), prettyFirstElement(response));

		log.info("VALIDATING the response contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.expect("total number of posts returned", 100, posts.size())
				.expectTrue("first post has a title", posts.get(0).getTitle() != null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: collection returned all 100 posts =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /posts creates a resource and echoes it back with a new id (201)")
	public void createPost() {
		Post newPost = new Post(7, "Automation Contract", "Validating REST Assured integration");
		log.info("===== SCENARIO: Create a new post =====");
		log.info("REQUEST : POST /posts  with body -> userId={}, title=\"{}\", body=\"{}\"",
				newPost.getUserId(), newPost.getTitle(), newPost.getBody());

		Response response = postService.createPost(newPost);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		Post created = response.as(Post.class);
		log.info("RESPONSE DATA: server assigned id={}, echoed title=\"{}\"", created.getId(), created.getTitle());

		log.info("VALIDATING that the server accepted and echoed our data:");
		new ApiValidator(log)
				.expect("HTTP status code (201 Created)", 201, response.statusCode())
				.expect("newly assigned id", 101, created.getId())
				.expect("echoed userId matches what we sent", newPost.getUserId(), created.getUserId())
				.expect("echoed title matches what we sent", newPost.getTitle(), created.getTitle())
				.expect("echoed body matches what we sent", newPost.getBody(), created.getBody())
				.verifyAll();
		log.info("===== SCENARIO PASSED: post created and payload echoed back correctly =====");
	}

	@Test(groups = TestGroups.API)
	@Description("PUT /posts/1 updates the resource and returns the modified payload (200)")
	public void updatePost() {
		Post update = new Post(1, "Updated Title", "Updated body content");
		log.info("===== SCENARIO: Update an existing post =====");
		log.info("REQUEST : PUT /posts/1  changing title to \"{}\" and body to \"{}\"",
				update.getTitle(), update.getBody());

		Response response = postService.updatePost(1, update);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		Post updated = response.as(Post.class);
		log.info("RESPONSE DATA: id={}, title is now \"{}\"", updated.getId(), updated.getTitle());

		log.info("VALIDATING that our changes were applied:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expect("post id is unchanged", 1, updated.getId())
				.expect("title reflects the update", update.getTitle(), updated.getTitle())
				.expect("body reflects the update", update.getBody(), updated.getBody())
				.verifyAll();
		log.info("===== SCENARIO PASSED: post reflects the updated values =====");
	}

	@Test(groups = TestGroups.API)
	@Description("DELETE /posts/1 removes the resource and returns 200")
	public void deletePost() {
		log.info("===== SCENARIO: Delete a post =====");
		log.info("REQUEST : DELETE /posts/1  (remove the post whose id is 1)");

		Response response = postService.deletePost(1);
		logResponseSummary(log, response);
		logResponseBody(log, response);

		log.info("VALIDATING that the delete was accepted:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.verifyAll();
		log.info("===== SCENARIO PASSED: delete request accepted with 200 =====");
	}

	// The collection response is large (100 items); showing only the first element keeps the log
	// readable while still revealing the exact JSON shape of a single record.
	private String prettyFirstElement(Response response) {
		try {
			Object firstElement = response.jsonPath().getList("$").get(0);
			return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(firstElement);
		} catch (Exception e) {
			// Never let log formatting mask the real assertions; fall back to the raw body.
			return response.getBody().asPrettyString();
		}
	}
}
```

### `src/test/java/com/project/qa/tests/api/UserApiTest.java`

```java
package com.project.qa.tests.api;

import com.project.qa.testsupport.api.ApiValidator;
import com.project.qa.testsupport.api.models.reqres.CreateUserRequest;
import com.project.qa.testsupport.api.models.reqres.SingleUserResponse;
import com.project.qa.testsupport.api.models.reqres.User;
import com.project.qa.testsupport.api.models.reqres.UserListResponse;
import com.project.qa.testsupport.api.models.reqres.UserMutationResponse;
import com.project.qa.testsupport.api.services.UserService;
import com.project.qa.testsupport.constants.TestGroups;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/*
 * Contract coverage for reqres.in /api/users. Exercises the scenarios JSONPlaceholder cannot:
 * pagination metadata, a genuine 404, a String-typed created id, and a 204-no-content delete.
 */
@Epic("API Testing")
@Feature("reqres.in Users resource (/api/users)")
public class UserApiTest extends ApiBaseTest {

	private static final Logger log = LoggerFactory.getLogger(UserApiTest.class);
	private static final long MAX_RESPONSE_MS = 5000;

	private final UserService userService = new UserService();

	@Test(groups = TestGroups.API)
	@Description("GET /api/users?page=2 returns the second page with correct pagination metadata")
	public void listUsersSecondPage() {
		log.info("===== SCENARIO: List users, page 2 =====");
		log.info("REQUEST : GET /api/users?page=2  (retrieve the second page of users)");

		Response response = userService.listUsers(2);
		logResponseSummary(log, response);
		UserListResponse body = response.as(UserListResponse.class);
		log.info("RESPONSE DATA: page={}, perPage={}, total={}, totalPages={}, itemsOnPage={}",
				body.getPage(), body.getPerPage(), body.getTotal(), body.getTotalPages(), body.getData().size());

		log.info("VALIDATING the pagination contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.expect("current page", 2, body.getPage())
				.expect("items per page", 6, body.getPerPage())
				.expect("total users", 12, body.getTotal())
				.expect("total pages", 2, body.getTotalPages())
				.expect("users returned on this page", 6, body.getData().size())
				.expectTrue("support block is present", body.getSupport() != null)
				.expectTrue("first user has a reqres.in email",
						body.getData().get(0).getEmail().endsWith("@reqres.in"))
				.verifyAll();
		log.info("===== SCENARIO PASSED: page 2 returned the expected paginated payload =====");
	}

	@Test(groups = TestGroups.API)
	@Description("GET /api/users/2 returns 200 with the expected single user under 'data'")
	public void getSingleUser() {
		log.info("===== SCENARIO: Fetch a single user =====");
		log.info("REQUEST : GET /api/users/2  (retrieve the user whose id is 2)");

		Response response = userService.getUser(2);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		User user = response.as(SingleUserResponse.class).getData();
		log.info("RESPONSE DATA: id={}, email={}, name=\"{} {}\"",
				user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());

		log.info("VALIDATING the single-user contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expect("user id in body", 2, user.getId())
				.expect("email", "janet.weaver@reqres.in", user.getEmail())
				.expect("first name", "Janet", user.getFirstName())
				.expectTrue("avatar URL is present", user.getAvatar() != null && !user.getAvatar().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: single user matched the expected contract =====");
	}

	@Test(groups = TestGroups.API)
	@Description("GET /api/users/23 returns 404 for a non-existent user")
	public void getMissingUserReturns404() {
		log.info("===== SCENARIO: Fetch a non-existent user =====");
		log.info("REQUEST : GET /api/users/23  (an id that does not exist)");

		Response response = userService.getUser(23);
		logResponseSummary(log, response);

		log.info("VALIDATING that the API reports the resource as missing:");
		new ApiValidator(log)
				.expect("HTTP status code (Not Found)", 404, response.statusCode())
				.expectTrue("response body is empty for a 404", response.getBody().asString().isBlank()
						|| response.getBody().asString().equals("{}"))
				.verifyAll();
		log.info("===== SCENARIO PASSED: missing user correctly returned 404 =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/users creates a resource and returns 201 with a new id and createdAt")
	public void createUser() {
		CreateUserRequest request = new CreateUserRequest("morpheus", "leader");
		log.info("===== SCENARIO: Create a new user =====");
		log.info("REQUEST : POST /api/users  with body -> name=\"{}\", job=\"{}\"",
				request.getName(), request.getJob());

		Response response = userService.createUser(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		UserMutationResponse created = response.as(UserMutationResponse.class);
		log.info("RESPONSE DATA: server assigned id={}, createdAt={}", created.getId(), created.getCreatedAt());

		log.info("VALIDATING that the server accepted and echoed our data:");
		new ApiValidator(log)
				.expect("HTTP status code (201 Created)", 201, response.statusCode())
				.expect("echoed name matches what we sent", request.getName(), created.getName())
				.expect("echoed job matches what we sent", request.getJob(), created.getJob())
				.expectTrue("a new id was assigned", created.getId() != null && !created.getId().isBlank())
				.expectTrue("createdAt timestamp is present", created.getCreatedAt() != null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: user created with a new id and timestamp =====");
	}

	@Test(groups = TestGroups.API)
	@Description("PUT /api/users/2 updates the resource and returns 200 with an updatedAt timestamp")
	public void updateUser() {
		CreateUserRequest request = new CreateUserRequest("morpheus", "zion resident");
		log.info("===== SCENARIO: Update an existing user =====");
		log.info("REQUEST : PUT /api/users/2  changing job to \"{}\"", request.getJob());

		Response response = userService.updateUser(2, request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		UserMutationResponse updated = response.as(UserMutationResponse.class);
		log.info("RESPONSE DATA: job is now \"{}\", updatedAt={}", updated.getJob(), updated.getUpdatedAt());

		log.info("VALIDATING that our change was applied:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expect("job reflects the update", request.getJob(), updated.getJob())
				.expectTrue("updatedAt timestamp is present", updated.getUpdatedAt() != null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: user reflects the updated job =====");
	}

	@Test(groups = TestGroups.API)
	@Description("DELETE /api/users/2 removes the resource and returns 204 No Content")
	public void deleteUser() {
		log.info("===== SCENARIO: Delete a user =====");
		log.info("REQUEST : DELETE /api/users/2  (remove the user whose id is 2)");

		Response response = userService.deleteUser(2);
		logResponseSummary(log, response);

		log.info("VALIDATING that the delete returned No Content:");
		new ApiValidator(log)
				.expect("HTTP status code (204 No Content)", 204, response.statusCode())
				.expectTrue("response body is empty", response.getBody().asString().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: delete accepted with 204 and no body =====");
	}
}
```

### `src/test/java/com/project/qa/tests/api/AuthApiTest.java`

```java
package com.project.qa.tests.api;

import com.project.qa.testsupport.api.ApiValidator;
import com.project.qa.testsupport.api.models.reqres.AuthRequest;
import com.project.qa.testsupport.api.models.reqres.AuthResponse;
import com.project.qa.testsupport.api.services.AuthService;
import com.project.qa.testsupport.constants.TestGroups;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/*
 * Contract coverage for reqres.in authentication. Demonstrates positive AND negative testing:
 * a successful register/login returns a token, while a missing password is rejected with a 400 and
 * a specific error message.
 */
@Epic("API Testing")
@Feature("reqres.in Authentication (/api/register, /api/login)")
public class AuthApiTest extends ApiBaseTest {

	private static final Logger log = LoggerFactory.getLogger(AuthApiTest.class);

	// reqres only accepts this specific pre-registered email for its happy-path auth responses.
	private static final String KNOWN_USER = "eve.holt@reqres.in";

	private final AuthService authService = new AuthService();

	@Test(groups = TestGroups.API)
	@Description("POST /api/register with valid credentials returns 200 with an id and token")
	public void registerSucceeds() {
		AuthRequest request = new AuthRequest(KNOWN_USER, "pistol");
		log.info("===== SCENARIO: Register a known user =====");
		log.info("REQUEST : POST /api/register  with email=\"{}\" and a password", request.getEmail());

		Response response = authService.register(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: id={}, token=\"{}\"", body.getId(), body.getToken());

		log.info("VALIDATING a successful registration:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("an id was returned", body.getId() != null)
				.expectTrue("a token was returned", body.getToken() != null && !body.getToken().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: registration returned an id and token =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/register without a password returns 400 with 'Missing password'")
	public void registerWithoutPasswordFails() {
		AuthRequest request = new AuthRequest("sydney@fife", null);
		log.info("===== SCENARIO: Register with a missing password (negative test) =====");
		log.info("REQUEST : POST /api/register  with email only, no password");

		Response response = authService.register(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: error=\"{}\"", body.getError());

		log.info("VALIDATING that the API rejects the request:");
		new ApiValidator(log)
				.expect("HTTP status code (Bad Request)", 400, response.statusCode())
				.expect("error message", "Missing password", body.getError())
				.expectTrue("no token was issued", body.getToken() == null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: missing password correctly rejected with 400 =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/login with valid credentials returns 200 with a token")
	public void loginSucceeds() {
		AuthRequest request = new AuthRequest(KNOWN_USER, "cityslicka");
		log.info("===== SCENARIO: Log in a known user =====");
		log.info("REQUEST : POST /api/login  with email=\"{}\" and a password", request.getEmail());

		Response response = authService.login(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: token=\"{}\"", body.getToken());

		log.info("VALIDATING a successful login:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("a token was returned", body.getToken() != null && !body.getToken().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: login returned a token =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/login without a password returns 400 with 'Missing password'")
	public void loginWithoutPasswordFails() {
		AuthRequest request = new AuthRequest("peter@klaven", null);
		log.info("===== SCENARIO: Log in with a missing password (negative test) =====");
		log.info("REQUEST : POST /api/login  with email only, no password");

		Response response = authService.login(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: error=\"{}\"", body.getError());

		log.info("VALIDATING that the API rejects the request:");
		new ApiValidator(log)
				.expect("HTTP status code (Bad Request)", 400, response.statusCode())
				.expect("error message", "Missing password", body.getError())
				.expectTrue("no token was issued", body.getToken() == null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: missing password correctly rejected with 400 =====");
	}
}
```

---

## 9. Build & Run

From the project root:

```bash
# Run the full API suite (all 15 tests)
mvn -q test

# Run a single test class
mvn test -Dtest=UserApiTest

# Run specific classes
mvn test -Dtest=UserApiTest,AuthApiTest

# Control parallelism
mvn test -Dthreads=5

# Point a target at another environment at runtime
mvn test -DapiBaseUri=https://jsonplaceholder.typicode.com -DreqresBaseUri=https://reqres.in

# Run and auto-open the Allure report in a browser
mvn clean verify -Preport

# Or generate/serve the report manually after a run
mvn allure:serve
```

**Expected result:** `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.
(5 Post tests + 6 User tests + 4 Auth tests.)

---

## 10. Optional `README.md` for the new repo

```markdown
# REST Assured API Automation

A browser-free API automation suite built with REST Assured 5, TestNG 7, Allure, and Jackson.

## Design
- ApiSpecFactory — one immutable, shared request spec per API (base URI, JSON headers, Allure, logging).
- Services (endpoint objects) — PostService, UserService, AuthService hide HTTP verbs/paths.
- Models (POJOs) — typed request/response bodies via Jackson (response.as(Post.class)).
- ApiValidator — fluent soft-assert helper with self-narrating logs and Allure steps.
- Config — ConfigReader resolves -Dkey > env overlay > config.properties.
- Retry — RetryTransformer attaches a config-driven RetryAnalyzer to every test.

## Run
    mvn test                     # full suite
    mvn test -Dtest=UserApiTest  # one class
    mvn clean verify -Preport    # run + open Allure report

## APIs under test
- JSONPlaceholder /posts (CRUD)
- reqres.in /api/users (pagination, CRUD, 404) and /api/register, /api/login
```

---

## 11. How to extend (next steps for a growing suite)

1. **New endpoint:** add a POJO in `models/`, a method in the relevant `Service`, and a `@Test` in the matching test class. The suite auto-discovers it (package scan + `groups="api"`).
2. **New API target:** add `xxxBaseUri` to `config.properties`, a `getXxxBaseUri()` in `ConfigReader`, and an `xxx()` spec in `ApiSpecFactory`.
3. **JSON Schema validation:** add `io.rest-assured:json-schema-validator`, drop a schema in `resources/schemas/`, assert `response.then().body(matchesJsonSchemaInClasspath("schemas/x.json"))`.
4. **Offline/deterministic CI:** stand up WireMock in a `@BeforeSuite`, stub the endpoints, and override `-DreqresBaseUri=http://localhost:<port>` — no test code changes needed.
5. **Data-driven tests:** feed payloads via a TestNG `@DataProvider`.
6. **Auth token flow:** fetch a token in `@BeforeSuite` and add `.addHeader("Authorization", "Bearer " + token)` to the spec.

---

## 12. Gotchas baked into this blueprint (real lessons)

- **reqres requires the `x-api-key` header** on the free tier — already wired into the `reqres()` spec.
- **reqres returns the created user `id` as a String** (`"260"`), not a number — hence `UserMutationResponse.id` is `String`.
- **`login` success returns only `{token}`** (no `id`); **register** returns `{id, token}` — one tolerant `AuthResponse` covers both.
- **Always call `verifyAll()`** on `ApiValidator` — without it, soft assertions never fail the test.
- **`@JsonIgnoreProperties(ignoreUnknown = true)`** on every response POJO keeps deserialization resilient when the API adds fields (e.g. reqres's `_meta`/`support`).
- **AspectJ weaver `argLine`** in Surefire is what makes Allure `@Step` annotations record — don't remove it.
