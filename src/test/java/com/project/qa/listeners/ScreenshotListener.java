package com.project.qa.listeners;

import com.project.qa.core.*;
import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.slf4j.*;
import org.testng.*;

import java.io.*;

/*
 * Captures diagnostics strictly on failure to keep artifacts lean.
 *
 * Runs on the failing test's own thread, so DriverManager.getDriver() returns that thread's
 * driver — safe under parallel execution. Attachments land in the Allure report against the
 * exact failing test.
 */
public class ScreenshotListener implements ITestListener {

	private static final Logger log = LoggerFactory.getLogger(ScreenshotListener.class);

	@Override
	public void onTestFailure(ITestResult result) {
		WebDriver driver = DriverManager.getDriver();
		if (driver == null) {
			// Failure happened before the driver was created (e.g. data provider / config error).
			log.debug("No active driver for '{}'; skipping screenshot capture", result.getName());
			return;
		}

		log.info("Capturing failure diagnostics for '{}'", result.getName());
		attachScreenshot(driver);
		attachPageSource(driver);
	}

	private void attachScreenshot(WebDriver driver) {
		try {
			byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Allure.addAttachment("Screenshot on failure", "image/png", new ByteArrayInputStream(png), "png");
		} catch (WebDriverException e) {
			// Never let diagnostics capture mask the real test failure.
			log.debug("Screenshot capture failed: {}", e.getMessage());
		}
	}

	private void attachPageSource(WebDriver driver) {
		try {
			Allure.addAttachment("Page source on failure", "text/html", driver.getPageSource(), ".html");
		} catch (WebDriverException e) {
			log.debug("Page source capture failed: {}", e.getMessage());
		}
	}
}
