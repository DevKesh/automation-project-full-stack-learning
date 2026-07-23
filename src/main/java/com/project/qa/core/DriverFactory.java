package com.project.qa.core;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class DriverFactory {
	public static void initDriver() {
		if (DriverManager.getDriver() == null) {
			ChromeOptions options = new ChromeOptions();
			options.addArguments("--start-maximized");
			// YAGNI: We let Selenium Manager handle the binary mapping natively.
			WebDriver driver = new ChromeDriver(options);
			DriverManager.setDriver(driver);
		}
	}
}
