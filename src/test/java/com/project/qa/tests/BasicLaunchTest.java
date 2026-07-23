package com.project.qa.tests;

import com.project.qa.core.*;
import org.testng.*;
import org.testng.annotations.*;

public class BasicLaunchTest extends BaseTest {
	@Test
	public void testMyntraHomePageTitle() {

		// 1. Navigate to the application
		DriverManager.getDriver().get("https://www.myntra.com/");

		// 2. Fetch the state of the application
		String actualTitle = DriverManager.getDriver().getTitle();

		// 3. Assert the state is correct
		Assert.assertTrue(actualTitle.toLowerCase().contains("myntra"), "Page title did not contain expected text. " +
				"Actual title: " + actualTitle);
	}
}
