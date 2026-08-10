package com.project.qa.core;

import io.appium.java_client.android.*;
import io.appium.java_client.android.appmanagement.*;
import io.appium.java_client.appmanagement.*;
import org.slf4j.*;

import java.time.*;

/**
 * Launches an installed app and, when it is missing, installs the provided apk first — so a run
 * succeeds whether or not the target app was pre-installed. This is the behaviour a CI job needs:
 * you cannot assume every device already has the app under test.
 *
 * Flow: ensure installed (install apk if absent) → activate by package → wait for foreground.
 * Nothing here depends on knowing the app's launcher activity.
 */
public final class AndroidAppLauncher {

	private static final Logger log = LoggerFactory.getLogger(AndroidAppLauncher.class);

	// Cold starts pass through a launcher/splash frame, so we poll for foreground rather than
	// reading it once immediately after activation.
	private static final Duration LAUNCH_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
	// Appium's default adb-install timeout is 60s, which real apks (multi-MB, plus device-side
	// verification) routinely exceed on first install — so we install with a generous timeout.
	private static final Duration INSTALL_TIMEOUT = Duration.ofMinutes(3);
	// Play Store downloads depend on app size + network, so allow generous time before giving up.
	private static final Duration PLAY_STORE_TIMEOUT = Duration.ofMinutes(5);

	private AndroidAppLauncher() {
	}

	public static boolean launch(AndroidDriver driver, String appPackage, String apkSource) {
		ensureInstalled(driver, appPackage, apkSource);
		log.info("Launching app: {}", appPackage);
		driver.activateApp(appPackage);
		return waitUntilForeground(driver, appPackage);
	}

	private static void ensureInstalled(AndroidDriver driver, String appPackage, String apkSource) {
		if (AndroidDeviceResolver.isPackageInstalled(appPackage)) {
			log.debug("App '{}' already installed", appPackage);
			return;
		}
		// A provided apk takes precedence — it is deterministic and offline-friendly.
		if (apkSource != null && !apkSource.isBlank()) {
			installFromApk(driver, appPackage, apkSource);
			return;
		}
		// No apk given: fall back to the Play Store so a bare -DappPackage still self-installs. This
		// is what makes any Play-published app runnable without sourcing an apk by hand.
		log.info("App '{}' not installed and no apk provided; installing from the Play Store", appPackage);
		if (!PlayStoreInstaller.installFromPlayStore(driver, appPackage, PLAY_STORE_TIMEOUT)) {
			throw new IllegalStateException(
					"App '" + appPackage + "' could not be installed from the Play Store within "
							+ PLAY_STORE_TIMEOUT + ". Ensure the device is signed into a Google account, "
							+ "or pass -Dapp=<path-or-url-to-apk> to sideload it instead.");
		}
	}

	private static void installFromApk(AndroidDriver driver, String appPackage, String apkSource) {
		log.info("App '{}' not installed; installing from {}", appPackage, apkSource);
		// Silence Play Protect's sideload verifier so the install needs no on-device tap.
		AndroidDeviceResolver.allowSilentInstalls();
		// Grant permissions and replace any partial install up front so the launched app is
		// immediately usable and the run stays hands-off.
		AndroidInstallApplicationOptions installOptions = new AndroidInstallApplicationOptions()
				.withTimeout(INSTALL_TIMEOUT)
				.withReplaceEnabled()
				.withGrantPermissionsEnabled();
		driver.installApp(apkSource, installOptions);
		if (!AndroidDeviceResolver.isPackageInstalled(appPackage)) {
			throw new IllegalStateException(
					"Installed apk from '" + apkSource + "' but package '" + appPackage
							+ "' is still absent. Does the apk's package id match appPackage?");
		}
	}

	private static boolean waitUntilForeground(AndroidDriver driver, String appPackage) {
		long deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (driver.queryAppState(appPackage) == ApplicationState.RUNNING_IN_FOREGROUND) {
				return true;
			}
			sleep(POLL_INTERVAL.toMillis());
		}
		return driver.queryAppState(appPackage) == ApplicationState.RUNNING_IN_FOREGROUND;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
