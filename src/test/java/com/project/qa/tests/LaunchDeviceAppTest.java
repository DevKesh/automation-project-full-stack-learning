package com.project.qa.tests;

import com.project.qa.config.*;
import com.project.qa.constants.*;
import com.project.qa.core.*;
import io.appium.java_client.android.*;
import org.slf4j.*;
import org.testng.*;
import org.testng.annotations.*;

import java.util.*;

/**
 * Discovers the device's installed apps and launches one by preference — with no apk on the host
 * and no activity to look up. It runs against a bare Appium session (no app launched at start), then
 * uses AndroidDriver.activateApp(package) to bring the chosen app to the foreground.
 *
 * Two-phase usage:
 *   Discovery : mvn test -Pmobile-native
 *               → logs every user-installed package, then stops (no launch).
 *   Launch    : mvn test -Pmobile-native -DappPackage=com.myntra.android
 *               → logs the list, activates the chosen app, and asserts it is in the foreground.
 */
public class LaunchDeviceAppTest extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(LaunchDeviceAppTest.class);

	@Test(groups = TestGroups.MOBILE)
	public void listInstalledAppsAndLaunchPreferred() {
		List<String> installedApps = AndroidDeviceResolver.listThirdPartyPackages();
		log.info("Found {} user-installed apps on the device:", installedApps.size());
		installedApps.forEach(pkg -> log.info("  {}", pkg));

		String preferredApp = ConfigReader.getAppPackage();
		if (preferredApp == null) {
			log.info("No -DappPackage provided — discovery-only run. "
					+ "Re-run with -DappPackage=<one of the packages above> to launch it "
					+ "(add -Dapp=<apk> to auto-install it if missing).");
			return;
		}

		// The launcher installs the -Dapp apk if the package is absent, so this passes whether or
		// not the target app was already on the device.
		AndroidDriver driver = (AndroidDriver) DriverManager.getDriver();
		boolean launched = AndroidAppLauncher.launch(driver, preferredApp, ConfigReader.getApp());

		Assert.assertTrue(launched, "App '" + preferredApp + "' did not reach the foreground. "
				+ "Current package: " + driver.getCurrentPackage());
		log.info("'{}' is now in the foreground.", preferredApp);
	}
}
