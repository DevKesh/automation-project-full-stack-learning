package com.project.qa.testsupport.base;

import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import org.testng.annotations.*;

public class BaseTest {
	// alwaysRun = true: under a group-filtered suite (e.g. -Pweb runs only the "web" group), TestNG
	// skips config methods that aren't in the included group unless they opt in here. Without it the
	// driver is never created and every test hits a null WebDriver.
	@BeforeMethod(alwaysRun = true)
	public void setUp() {
		DriverFactory.initDriver();
	}

	@AfterMethod(alwaysRun = true)
	public void tearDown() {
		DriverManager.quitDriver();
	}
}

