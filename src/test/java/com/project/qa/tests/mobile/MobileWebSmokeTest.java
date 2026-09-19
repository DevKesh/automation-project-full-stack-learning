package com.project.qa.tests.mobile;

import com.project.qa.testsupport.base.*;

import com.project.qa.testsupport.constants.*;
import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import org.testng.*;
import org.testng.annotations.*;

/**
 * Smoke test proving the Appium mobile-web wiring end to end: the self-started server, adb device
 * detection, and Chromedriver auto-download all have to succeed for this to pass. It deliberately
 * hits a trivial, dependency-free page so a failure points at the framework, not a flaky site.
 *
 * Runs only under the `mobile` profile (mvn test -Pmobile); it is absent from the default web suite.
 */
public class MobileWebSmokeTest extends BaseTest {

	@Test(groups = TestGroups.MOBILE)
	public void androidChromeLoadsPage() {
		DriverManager.getDriver().get("https://example.com/");

		String title = DriverManager.getDriver().getTitle();
		Assert.assertTrue(title.toLowerCase().contains("example"),
				"Unexpected page title on Android Chrome. Actual: " + title);
	}
}
