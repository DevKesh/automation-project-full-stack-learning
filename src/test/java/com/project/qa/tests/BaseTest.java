package com.project.qa.tests;

import com.project.qa.core.*;
import org.testng.annotations.*;

public class BaseTest {
	@BeforeMethod
	public void setUp() {
		DriverFactory.initDriver();
	}

	@AfterMethod
	public void tearDown() {
		DriverManager.quitDriver();
	}
}

