package com.project.qa.pageobjects.web;

import com.project.qa.pageobjects.common.*;

import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import org.openqa.selenium.*;

/**
 * Locator map + actions for the Myntra desktop Home Page header.
 *
 * Locators are intentionally private (encapsulation): the test layer drives the page through the
 * public action methods below, never through raw By references. Every locator here was harvested
 * live from www.myntra.com and anchored to Myntra's stable header sprite/class contract
 * (desktop-* classes) rather than brittle absolute XPaths, so the map survives DOM reshuffles.
 */
public class MyntraHomePage extends BasePage {

	private static final String BASE_URL = "https://www.myntra.com/";

	// --- Brand / logo -------------------------------------------------------
	private final By logo = By.cssSelector("a.desktop-logo");

	// --- Primary top navigation (mega-menu entry points) --------------------
	private final By navMen = By.cssSelector("a.desktop-main[href='/shop/men']");
	private final By navWomen = By.cssSelector("a.desktop-main[href='/shop/women']");
	private final By navKids = By.cssSelector("a.desktop-main[href='/shop/kids']");
	private final By navHome = By.cssSelector("a.desktop-main[href='/shop/home-living']");
	private final By navBeauty = By.cssSelector("a.desktop-main[href='/personal-care']");
	private final By navGenZ = By.cssSelector("a.desktop-main[href='/shop/fwd-women']");
	private final By navStudio = By.cssSelector("a.desktop-main[href='/studio/home']");

	// --- Search ------------------------------------------------------------
	private final By searchBar = By.className("desktop-searchBar");
	private final By searchIcon = By.className("desktop-submit");

	// --- Header action icons (right cluster) -------------------------------
	private final By profileIcon = By.className("desktop-iconUser");
	private final By wishlistLink = By.cssSelector("a.desktop-wishlist");
	private final By bagLink = By.cssSelector("a.desktop-cart");

	// --- Navigation --------------------------------------------------------

	public MyntraHomePage open() {
		DriverManager.getDriver().get(BASE_URL);
		return this;
	}

	public MyntraHomePage clickLogo() {
		click(logo);
		return this;
	}

	// --- Top navigation actions --------------------------------------------

	public void goToMen() {
		click(navMen);
	}

	public void goToWomen() {
		click(navWomen);
	}

	public void goToKids() {
		click(navKids);
	}

	public void goToHomeLiving() {
		click(navHome);
	}

	public void goToBeauty() {
		click(navBeauty);
	}

	public void goToGenZ() {
		click(navGenZ);
	}

	public void goToStudio() {
		click(navStudio);
	}

	// --- Search actions ----------------------------------------------------

	public void searchForProduct(String productName) {
		type(searchBar, productName);
		click(searchIcon);
	}

	public MyntraSearchResultsPage searchForTheProduct(String productName) {
		searchForProduct(productName);
		return new MyntraSearchResultsPage();
	}

	// --- Header action-icon actions ----------------------------------------

	public void openProfileMenu() {
		click(profileIcon);
	}

	public void goToWishlist() {
		click(wishlistLink);
	}

	public void goToBag() {
		click(bagLink);
	}

	// --- Queries -----------------------------------------------------------

	public String getPageTitle() {
		return DriverManager.getDriver().getTitle();
	}
}
