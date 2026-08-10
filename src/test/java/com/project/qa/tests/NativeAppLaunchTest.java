package com.project.qa.tests;

import com.project.qa.constants.*;
import com.project.qa.core.*;
import io.appium.java_client.android.*;
import org.testng.*;
import org.testng.annotations.*;

/**
 * Proves a native Android app launches end to end. By the time this test body runs, BaseTest.setUp
 * has already built the Appium session — which installs the apk (app=) or starts the installed app
 * (appPackage+appActivity) — so reaching here confirms the session was created. We then assert an
 * app is actually in the foreground, which is a real launch check independent of any app's internal
 * UI (no app-specific locators, so it works for any apk).
 *
 * Runs only under the `mobile-native` profile (mvn test -Pmobile-native ...). It performs no browser
 * calls, so it is valid on a native — non-web — session.
 */
public class NativeAppLaunchTest extends BaseTest {

	@Test(groups = TestGroups.MOBILE)
	public void nativeAppLaunches() {
		AndroidDriver driver = (AndroidDriver) DriverManager.getDriver();

		String foregroundPackage = driver.getCurrentPackage();
		Assert.assertNotNull(foregroundPackage, "No app is in the foreground; the app failed to launch.");
		Assert.assertFalse(foregroundPackage.isBlank(), "Foreground package was blank; the app failed to launch.");
	}
}
