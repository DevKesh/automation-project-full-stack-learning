package com.project.qa.tests;

import com.project.qa.core.*;
import com.project.qa.data.*;
import com.project.qa.pages.*;
import com.project.qa.utils.*;
import org.slf4j.*;
import org.testng.annotations.*;
import org.testng.asserts.*;

import java.io.*;
import java.util.*;

public class SearchProductsTest extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(SearchProductTest.class);

	@DataProvider(name = "myntraSearchData")
	public Object[][] getSearchData() throws IOException {
		List<SearchData> dataList = JsonReader.getSearchDataPojo();
		Object[][] data = new Object[dataList.size()][1];

		for (int i = 0; i < dataList.size(); i++) {
			data[i][0] = dataList.get(i);
		}
		return data;
	}

	@Test(dataProvider = "myntraSearchData")
	public void testMyntraProductSearch(SearchData searchData) {
		log.info("Starting test for keyword: {}", searchData.getKeyword().toUpperCase());
		// 1. Initialize SoftAssert for this specific test run
		SoftAssert softAssert = new SoftAssert();

		// 1. Fluent Execution
		MyntraSearchResultsPage resultsPage = new MyntraHomePage().open().searchForTheProduct(searchData.getKeyword());

		// 2. Data Extraction via Page Object methods
		log.info("--- Results for: " + searchData.getKeyword().toUpperCase() + " ---");
		log.info("Brand: " + resultsPage.getFirstCardBrand());
		log.info("Item:  " + resultsPage.getFirstCardName());
		log.info("Price: " + resultsPage.getFirstCardPrice());

		// Next Step: Add TestNG Assertions here

		// 3. Data Extraction
		String brand = resultsPage.getFirstCardBrand();
		String item = resultsPage.getFirstCardName();
		String price = resultsPage.getFirstCardPrice();

		// 4. TestNG Soft Assertions

		// Brand checks
		softAssert.assertNotNull(brand, "Product brand should not be null");
		softAssert.assertFalse(brand.trim().isEmpty(), "Product brand should not be empty");

		// Item Name checks
		softAssert.assertNotNull(item, "Product name should not be null");
		softAssert.assertFalse(item.trim().isEmpty(), "Product name should not be empty");

		// Price checks
		softAssert.assertNotNull(price, "Product price should not be null");
		softAssert.assertTrue(price.contains("Rs."), "Price text should contain the 'Rs.' currency identifier. Actual:" +
				" " + price);

		// 5. Collate Results
		log.info("Asserting all soft assertions for {}", searchData.getKeyword());
		// This is mandatory. If you forget this step, the test will always pass even if assertions failed!
		softAssert.assertAll();
	}
}
