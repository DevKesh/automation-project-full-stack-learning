package com.project.qa.pages;

import org.openqa.selenium.*;

public class MyntraSearchResultsPage extends BasePage {
	// Locator for the first product image in the results grid
	private final By firstProductImage = By.cssSelector("li.product-base a");

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
