package com.project.qa.tests.web;

import com.project.qa.testsupport.base.*;

import com.project.qa.testsupport.constants.*;
import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import com.project.qa.pageobjects.common.*;
import com.project.qa.pageobjects.web.*;
import com.project.qa.pageobjects.mobile.*;
import org.testng.*;
import org.testng.annotations.*;

import java.util.*;

public class SearchProductTest extends BaseTest {

	@Test(groups = TestGroups.WEB)
	public void searchProduct() {
		// Code to search for a product on Myntra
		String productName = "Shoes";
		System.out.println("Searching for product: " + productName);
		// 1. Setup / Navigate
		DriverManager.getDriver().get("https://www.myntra.com/");
		MyntraHomePage homePage = new MyntraHomePage();

		// 2. Execute Business Logic via POM
		//		homePage.searchForProduct(productName);

		// The Fluent Chain
		// searchForProduct returns the SearchResultsPage, allowing us to immediately
		// call clickFirstProduct() without creating a new variable.
		homePage.searchForTheProduct(productName)
				.clickFirstProduct();

		// Assertions remain in the test layer
		Assert.assertTrue(Objects.requireNonNull(DriverManager.getDriver().getCurrentUrl()).contains("shoes"),
				"URL did not update to reflect the search parameters.");
	}
}

