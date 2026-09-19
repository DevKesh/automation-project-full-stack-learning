package com.project.qa.tests.web;

import com.project.qa.testsupport.base.*;

import com.project.qa.testsupport.constants.*;
import com.project.qa.datamodels.*;
import com.project.qa.pageobjects.common.*;
import com.project.qa.pageobjects.web.*;
import com.project.qa.pageobjects.mobile.*;
import org.slf4j.*;
import org.testng.annotations.*;
import org.testng.asserts.*;

import java.util.*;

/**
 * Verifies the search-and-retrieve flow for the four core apparel keywords (shirt, pant, shoes,
 * coat). Each case searches from the Home Page and pulls the first five result cards, asserting the
 * grid returns a full, well-formed top-five slice.
 *
 * Data-driven (not four copy-pasted methods) so a new keyword is one row, and parallel=true lets the
 * four cases fan out across the suite's isolated ThreadLocal drivers.
 */
public class SearchResultsRetrievalTest extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(SearchResultsRetrievalTest.class);

	private static final int RESULTS_TO_RETRIEVE = 5;

	@DataProvider(name = "apparelKeywords", parallel = true)
	public Object[][] apparelKeywords() {
		return new Object[][]{
				{"Shirt"},
				{"Pant"},
				{"Shoes"},
				{"Coat"}
		};
	}

	@Test(dataProvider = "apparelKeywords", groups = TestGroups.WEB)
	public void retrievesFirstFiveResultsForKeyword(String keyword) {
		log.info("Searching Myntra for '{}' and retrieving the first {} results", keyword, RESULTS_TO_RETRIEVE);

		MyntraSearchResultsPage resultsPage = new MyntraHomePage().open().searchForTheProduct(keyword);
		List<SearchResult> topResults = resultsPage.getFirstResults(RESULTS_TO_RETRIEVE);

		logResults(keyword, topResults);

		// SoftAssert so a single malformed card surfaces every problem in one run instead of masking
		// the rest behind the first hard failure.
		SoftAssert softAssert = new SoftAssert();
		softAssert.assertEquals(topResults.size(), RESULTS_TO_RETRIEVE,
				"Expected " + RESULTS_TO_RETRIEVE + " results for '" + keyword + "' but got " + topResults.size());

		for (int i = 0; i < topResults.size(); i++) {
			SearchResult result = topResults.get(i);
			int position = i + 1;

			// Brand + Rs. price are the invariants every genuine result card carries. Product name is
			// best-effort: verified live, some real listings (e.g. brand-only cards) ship an empty
			// product-product node, so an empty name is logged as data variance, not failed on.
			softAssert.assertFalse(result.brand().isEmpty(),
					"Result " + position + " for '" + keyword + "' has an empty brand");
			softAssert.assertTrue(result.price().contains("Rs."),
					"Result " + position + " for '" + keyword + "' should carry an 'Rs.' price. Actual: " + result.price());

			if (result.name().isEmpty()) {
				log.warn("Result {} for '{}' ({}) has no product name on the listing card", position, keyword, result.brand());
			}
		}

		softAssert.assertAll();
	}

	private void logResults(String keyword, List<SearchResult> results) {
		log.info("Top {} results for '{}':", results.size(), keyword);
		for (int i = 0; i < results.size(); i++) {
			SearchResult result = results.get(i);
			log.info("  {}. {} | {} | {}", i + 1, result.brand(), result.name(), result.price());
		}
	}
}
