package com.project.qa.tests;

import com.project.qa.core.*;
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

