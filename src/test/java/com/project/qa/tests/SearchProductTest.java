package com.project.qa.tests;

import com.project.qa.constants.*;
import com.project.qa.core.*;
import com.project.qa.pages.*;
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

