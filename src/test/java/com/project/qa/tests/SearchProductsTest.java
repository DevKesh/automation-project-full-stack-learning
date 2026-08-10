package com.project.qa.tests;

import com.project.qa.constants.*;
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

	private static final Logger log = LoggerFactory.getLogger(SearchProductsTest.class);

	@DataProvider(name = "myntraSearchData", parallel = true)
	public Object[][] getSearchData() throws IOException {
		List<SearchData> dataList = JsonReader.getSearchDataPojo();
		Object[][] data = new Object[dataList.size()][1];

		for (int i = 0; i < dataList.size(); i++) {
			data[i][0] = dataList.get(i);
		}
		return data;
	}

	@Test(dataProvider = "myntraSearchData", groups = TestGroups.WEB)
	public void testMyntraProductSearch(SearchData searchData) {
		log.info("Starting test for keyword: {}", searchData.getKeyword().toUpperCase());

		// SoftAssert collects the field-level checks so one empty attribute doesn't mask the others.
		// A real driver/page exception is intentionally NOT caught here: letting it propagate fails
		// the test honestly instead of logging-and-passing, which would hide broken locators.
		SoftAssert softAssert = new SoftAssert();

		// 1. Fluent Execution
		MyntraSearchResultsPage resultsPage = new MyntraHomePage().open().searchForTheProduct(searchData.getKeyword());

		// 2. Data Extraction via Page Object methods
		String brand = resultsPage.getFirstCardBrand();
		String item = resultsPage.getFirstCardName();
		String price = resultsPage.getFirstCardPrice();
		log.info("--- Results for {} --- Brand: {} | Item: {} | Price: {}",
				searchData.getKeyword().toUpperCase(), brand, item, price);

		// 3. Soft assertions on the first result card
		softAssert.assertNotNull(brand, "Product brand should not be null");
		softAssert.assertFalse(brand.trim().isEmpty(), "Product brand should not be empty");

		softAssert.assertNotNull(item, "Product name should not be null");
		softAssert.assertFalse(item.trim().isEmpty(), "Product name should not be empty");

		softAssert.assertNotNull(price, "Product price should not be null");
		softAssert.assertTrue(price.contains("Rs."),
				"Price text should contain the 'Rs.' currency identifier. Actual: " + price);

		// Mandatory: without assertAll() the collected soft failures never surface.
		log.info("Asserting all soft assertions for {}", searchData.getKeyword());
		softAssert.assertAll();
	}
}
