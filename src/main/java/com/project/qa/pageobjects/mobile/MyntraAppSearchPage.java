package com.project.qa.pageobjects.mobile;

import com.project.qa.pageobjects.common.*;

import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import io.appium.java_client.*;
import io.appium.java_client.android.*;
import io.appium.java_client.android.nativekey.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.time.*;

/**
 * Page Object for the Myntra app's search experience. Typing into the search box and submitting
 * yields a product grid whose cards carry content-desc values beginning with "PRODUCT_GRID" — we
 * assert on that pattern rather than any single volatile product id.
 */
public class MyntraAppSearchPage extends BasePage {

	private final By searchInput = AppiumBy.accessibilityId("search_default_search_text_input");
	// On the search results (product listing) page, each product card's content-desc begins with
	// "PRODUCT_TILE"; match that stable prefix rather than any single volatile product id. (The
	// home/landing feed uses "PRODUCT_GRID" instead, so this specifically confirms a results page.)
	private final By anyProductCard =
			AppiumBy.androidUIAutomator("new UiSelector().descriptionContains(\"PRODUCT_TILE\")");

	public MyntraAppSearchPage searchFor(String term) {
		type(searchInput, term);
		// The search box commits on the IME action; ENTER triggers it reliably across app versions.
		((AndroidDriver) DriverManager.getDriver()).pressKey(new KeyEvent(AndroidKey.ENTER));
		return this;
	}

	// Waits for at least one product card to render, so callers get a definitive pass/fail rather
	// than racing the network-backed grid.
	public boolean hasResults() {
		try {
			new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(15))
					.until(ExpectedConditions.presenceOfElementLocated(anyProductCard));
			return true;
		} catch (TimeoutException noResults) {
			return false;
		}
	}
}
