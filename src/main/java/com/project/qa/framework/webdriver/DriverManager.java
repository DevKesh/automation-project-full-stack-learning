package com.project.qa.framework.webdriver;

import org.openqa.selenium.*;

public class DriverManager {

	private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();

	// Private constructor prevents external instantiation (Singleton pattern concept)
	private DriverManager() {
	}

	public static WebDriver getDriver() {
		return driver.get();
	}

	public static void setDriver(WebDriver webDriver) {
		driver.set(webDriver);
	}

	public static void quitDriver() {
		if (driver.get() != null) {
			driver.get().quit();
			driver.remove(); // Prevents memory leaks
		}
	}
}