package com.project.qa.pageobjects.web;

import com.project.qa.pageobjects.common.*;

import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import com.project.qa.datamodels.*;
import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.util.*;

public class MyntraSearchResultsPage extends BasePage {
	// Locator for the first product image in the results grid
	private final By firstProductImage = By.cssSelector("li.product-base a");

	// Grid-level locator used to page the first N cards without brittle indexed XPaths.
	private final By productCards = By.cssSelector("li.product-base");

	// Card-relative locators, resolved against each card element (never document-wide) so row N's
	// brand can never bleed into row M. Discounted price is the primary; ~3 in 50 cards carry no
	// active discount and expose only the plain div.product-price ("Rs. 999"), so we fall back to it
	// rather than reporting an empty price. (Verified live: div.product-price on a discounted card
	// concatenates strike+percent, so it is used ONLY as the no-discount fallback.)
	private final By cardBrand = By.cssSelector("h3.product-brand");
	private final By cardName = By.cssSelector("h4.product-product");
	private final By cardDiscountedPrice = By.cssSelector("span.product-discountedPrice");
	private final By cardPrice = By.cssSelector("div.product-price");

	// Locators explicitly targeting the first item in the results grid
	private final By firstCardBrand = By.xpath("(//li[contains(@class,'product-base')])" +
			"[1]//h3[@class='product-brand']");
	private final By firstCardName = By.xpath("(//li[contains(@class,'product-base')])" +
			"[1]//h4[@class='product-product']");
	private final By firstCardPrice = By.xpath("(//li[contains(@class,'product-base')])[1]//span[@class='product-discountedPrice']");

	public String getFirstCardBrand() {
		return getText(firstCardBrand);
	}

	public String getFirstCardName() {
		return getText(firstCardName);
	}

	public String getFirstCardPrice() {
		return getText(firstCardPrice);
	}

	/**
	 * Returns up to {@code limit} product cards from the top of the results grid.
	 *
	 * Waits on grid presence first so the read races neither the initial paint nor lazy hydration.
	 * Reads are card-scoped and null-safe: a missing sub-element yields an empty string instead of
	 * throwing, keeping one absent price from aborting retrieval of the other four rows.
	 */
	@Step("Retrieve first {limit} search results")
	public List<SearchResult> getFirstResults(int limit) {
		wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(productCards, 0));
		List<WebElement> cards = DriverManager.getDriver().findElements(productCards);

		List<SearchResult> results = new ArrayList<>();
		for (WebElement card : cards) {
			if (results.size() == limit) {
				break;
			}
			results.add(new SearchResult(
					readWithin(card, cardBrand),
					readWithin(card, cardName),
					readPrice(card)));
		}
		return results;
	}

	// Discounted price is preferred; when absent (no active offer) we fall back to the plain price.
	private String readPrice(WebElement card) {
		String discounted = readWithin(card, cardDiscountedPrice);
		return discounted.isEmpty() ? readWithin(card, cardPrice) : discounted;
	}

	// Card-scoped, exception-free read: absent nodes are normal grid variance, not test failures.
	private String readWithin(WebElement card, By locator) {
		List<WebElement> found = card.findElements(locator);
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	// This method stays void if clicking it opens a new tab,
	// or returns a ProductDetailsPage if it navigates in the same window.
	public void clickFirstProduct() {
		click(firstProductImage);
	}

	// A quick method to help our assertions later
	public String getSearchPageTitle() {
		return getText(By.className("title-title"));
	}
}
