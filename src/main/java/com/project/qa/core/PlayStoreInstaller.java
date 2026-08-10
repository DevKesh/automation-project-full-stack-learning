package com.project.qa.core;

import io.appium.java_client.*;
import io.appium.java_client.android.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.slf4j.*;

import java.time.*;
import java.util.*;

/**
 * Installs any app straight from the Google Play Store by package name — no apk file or download URL
 * required. It is the app-agnostic counterpart to a sideloaded {@code -Dapp} apk: given only a
 * package id, it opens that app's Play listing, taps the store's own Install button, and waits until
 * the device reports the package present.
 *
 * Why Play Store instead of only sideloading: many apps (Myntra included) ship as signed App Bundles
 * with no publicly downloadable single apk. Driving the official store is the industry-standard way
 * to obtain them, and it lets the same one-line call install an unbounded set of apps for practice.
 *
 * Prerequisite: the device must be signed into a Google account (as any real handset is). The
 * Install-button tap is best-effort across Play Store UI variants; installation is always confirmed
 * against adb, which is the source of truth.
 */
public final class PlayStoreInstaller {

	private static final Logger log = LoggerFactory.getLogger(PlayStoreInstaller.class);

	// The store page renders over the network on a cold open, so give the Install button generous
	// time to appear before we conclude it isn't there.
	private static final Duration BUTTON_TIMEOUT = Duration.ofSeconds(90);
	// Store downloads vary with app size and network, so we poll adb rather than guess a fixed wait.
	private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

	private PlayStoreInstaller() {
	}

	/**
	 * Opens the app's Play listing, taps Install, and blocks until the package is installed.
	 *
	 * @return true if the package is present on the device within {@code timeout}.
	 */
	public static boolean installFromPlayStore(AndroidDriver driver, String appPackage, Duration timeout) {
		log.info("Installing '{}' from the Play Store", appPackage);
		AndroidDeviceResolver.openPlayStoreListing(appPackage);
		tapInstallButton(driver);
		return waitUntilInstalled(driver, appPackage, timeout);
	}

	// Play Store's action button shows the text "Install" when the app isn't installed. On modern
	// (Compose) store builds the visible "Install" is a NON-clickable TextView/View whose clickable
	// ancestor carries no text — so we must NOT require clickable(true). Matching the text (or its
	// content-desc) and tapping it dispatches a tap at that element's centre, which lands on the
	// underlying button. adb verification is what ultimately confirms success.
	private static void tapInstallButton(AndroidDriver driver) {
		WebDriverWait wait = new WebDriverWait(driver, BUTTON_TIMEOUT);
		try {
			WebElement install = wait.until(ExpectedConditions.presenceOfElementLocated(
					AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"(?i)install\")")));
			install.click();
			log.info("Tapped the Play Store 'Install' button (by text)");
			return;
		} catch (TimeoutException byText) {
			log.debug("No 'Install' text node found; trying content-desc.");
		}
		try {
			WebElement install = driver.findElement(AppiumBy.accessibilityId("Install"));
			install.click();
			log.info("Tapped the Play Store 'Install' button (by content-desc)");
		} catch (org.openqa.selenium.NoSuchElementException byDesc) {
			// Not fatal: the listing may already show Open/Update or be mid-download. adb
			// verification below is what actually decides success.
			log.warn("Did not find an 'Install' control within {} — the app may already be "
					+ "installing, or the listing shows Open/Update.", BUTTON_TIMEOUT);
		}
	}

	// Polls adb (the source of truth) until the package appears or the timeout elapses. Each cycle
	// also issues a cheap driver command so Appium's newCommandTimeout does not reap the session
	// during a multi-minute download (the poll itself talks to adb, not the driver).
	private static boolean waitUntilInstalled(AndroidDriver driver, String appPackage, Duration timeout) {
		long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (AndroidDeviceResolver.isPackageInstalled(appPackage)) {
				log.info("'{}' is now installed", appPackage);
				return true;
			}
			keepSessionAlive(driver, appPackage);
			sleep(POLL_INTERVAL.toMillis());
		}
		return AndroidDeviceResolver.isPackageInstalled(appPackage);
	}

	// A no-op-ish query whose only purpose is to reset Appium's idle timer while we wait on the store.
	private static void keepSessionAlive(AndroidDriver driver, String appPackage) {
		try {
			driver.queryAppState(appPackage);
		} catch (RuntimeException e) {
			log.debug("Keepalive query failed (non-fatal): {}", e.getMessage());
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
