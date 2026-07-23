package com.project.qa.pages;

import com.project.qa.core.*;
import org.openqa.selenium.*;

public class MyntraHomePage extends BasePage {
	// 1. Locators are strictly private. The test/step definition layer cannot see them.
	private final By searchBar = By.className("desktop-searchBar");
	private final By searchIcon = By.className("desktop-submit");

	// 2. Actions are public and utilize the synchronized engine from BasePage.
	public void searchForProduct(String productName) {
		type(searchBar, productName);
		click(searchIcon);
	}

	public MyntraHomePage open() {
		// Navigating directly here keeps the test class clean
		DriverManager.getDriver().get("https://www.myntra.com/");
		return this; // Return 'this' to allow immediate chaining
	}

	// Notice the return type is no longer void.
	// It physically returns the next page object in the workflow.
	public MyntraSearchResultsPage searchForTheProduct(String productName) {
		searchForProduct(productName);
		// This is the core of Fluent Chaining: handing off control.
		return new MyntraSearchResultsPage();
	}

	public String getPageTitle() {
		return DriverManager.getDriver().getTitle();
	}
}
