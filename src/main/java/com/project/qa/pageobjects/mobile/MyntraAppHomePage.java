package com.project.qa.pageobjects.mobile;

import com.project.qa.pageobjects.common.*;

import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import io.appium.java_client.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.slf4j.*;

import java.time.*;

/**
 * Page Object for the Myntra Android app's home feed. Locators use content-desc (Appium
 * accessibilityId) because the app is React Native and does not expose stable resource-ids here —
 * content-desc values like "HPSearchBar" are the reliable, human-meaningful anchors.
 *
 * Mirrors the web {@link MyntraHomePage}: private locators, public intent-revealing actions, and
 * fluent hand-off to the next page. Reuses {@link BasePage}'s synchronized click/type engine.
 */
public class MyntraAppHomePage extends BasePage {

	private static final Logger log = LoggerFactory.getLogger(MyntraAppHomePage.class);

	private final By searchBar = AppiumBy.accessibilityId("HPSearchBar");
	private final By loginSkipButton = AppiumBy.accessibilityId("login_skip_button");

	// First launch can show a login/signup wall; skipping it lands on the home feed. On later runs
	// the app remembers the skip (noReset keeps app state), so the button is absent — which is the
	// normal case, not an error. A short wait keeps the happy path fast.
	public MyntraAppHomePage dismissLoginWallIfPresent() {
		WebDriverWait shortWait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(8));
		try {
			shortWait.until(ExpectedConditions.elementToBeClickable(loginSkipButton)).click();
			log.info("Skipped the Myntra login wall");
		} catch (TimeoutException alreadyHome) {
			log.debug("No login wall shown; already on the home feed");
		}
		return this;
	}

	public boolean isSearchBarDisplayed() {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(searchBar)).isDisplayed();
	}

	public MyntraAppSearchPage openSearch() {
		click(searchBar);
		return new MyntraAppSearchPage();
	}
}
