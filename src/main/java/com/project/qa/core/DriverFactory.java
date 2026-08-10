package com.project.qa.core;

import com.project.qa.config.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.edge.*;
import org.openqa.selenium.firefox.*;
import org.slf4j.*;

// ARCHITECTURE: Factory Pattern. This class centralizes the "how to build a driver" logic so
// tests only ask for a ready driver and never touch browser/platform construction details.
public class DriverFactory {

	// JAVA CONCEPT: SLF4J logger keyed to this class. `private static final` = one shared,
	// immutable instance per class (not per object) — the idiomatic, thread-safe logger declaration.
	private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

	public static void initDriver() {
		// ARCHITECTURE: Idempotency guard. DriverManager wraps a ThreadLocal<WebDriver>, so
		// getDriver() returns the driver for THIS thread only. Returning early avoids leaking a
		// second browser when init runs twice — critical for safe parallel (multi-thread) execution.

		if (DriverManager.getDriver() != null) {
			return;
		}

		// The platform is the only seam that knows web vs mobile; everything downstream sees a WebDriver.
		// JAVA CONCEPT: enum factory method — PlatformType.from(String) converts external config
		// text into a type-safe enum, failing fast on invalid values instead of leaking raw strings.

		PlatformType platform = PlatformType.from(ConfigReader.getPlatform());

		// JAVA CONCEPT (Java 14+): switch EXPRESSION with arrow labels. It returns a value directly,
		// has no fall-through, and the compiler enforces exhaustiveness over all enum constants —
		// so adding a new PlatformType forces you to handle it here.
		WebDriver driver = switch (platform) {
			case WEB -> createWebDriver();
			case ANDROID -> MobileDriverFactory.createDriver();
		};
		// ARCHITECTURE: Store the driver in the per-thread ThreadLocal so parallel tests never
		// share a browser instance or step on each other's state.
		DriverManager.setDriver(driver);
	}

	private static WebDriver createWebDriver() {
		BrowserType browser = BrowserType.from(ConfigReader.getBrowser());
		boolean headless = ConfigReader.isHeadless();
		// LOGGING HYGIENE: INFO records a business-level lifecycle milestone (a browser launch).
		// JAVA CONCEPT: SLF4J "{}" placeholders defer String building until the level is enabled,
		// which is cheaper and safer than manual "+" concatenation.
		log.info("Launching {} browser (headless={})", browser, headless);

		// Selenium Manager (built into Selenium 4) resolves the driver binary natively, so no
		// third-party WebDriverManager dependency is needed.
		return createDriver(browser, headless);
	}

	private static WebDriver createDriver(BrowserType browser, boolean headless) {
		// JAVA CONCEPT: another exhaustive switch expression — each branch instantiates the concrete
		// WebDriver but returns it as the WebDriver interface (programming to an abstraction).
		return switch (browser) {
			case CHROME -> new ChromeDriver(chromeOptions(headless));
			case FIREFOX -> new FirefoxDriver(firefoxOptions(headless));
			case EDGE -> new EdgeDriver(edgeOptions(headless));
		};
	}

	// ARCHITECTURE: One small, single-responsibility builder per browser keeps construction cohesive
	// and easy to extend without touching the switch above.
	private static ChromeOptions chromeOptions(boolean headless) {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized");
		if (headless) {
			options.addArguments("--headless=new", "--window-size=1920,1080");
		}
		// LOGGING HYGIENE: DEBUG for framework/setup noise so INFO-level console output stays clean.
		log.debug("Built ChromeOptions: {}", options);
		return options;
	}

	private static FirefoxOptions firefoxOptions(boolean headless) {
		FirefoxOptions options = new FirefoxOptions();
		options.addArguments("--width=1920", "--height=1080");
		if (headless) {
			options.addArguments("-headless");
		}
		log.debug("Built FirefoxOptions: {}", options);
		return options;
	}

	private static EdgeOptions edgeOptions(boolean headless) {
		EdgeOptions options = new EdgeOptions();
		options.addArguments("--start-maximized");
		if (headless) {
			options.addArguments("--headless=new", "--window-size=1920,1080");
		}
		log.debug("Built EdgeOptions: {}", options);
		return options;
	}
}
