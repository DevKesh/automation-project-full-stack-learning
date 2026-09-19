package com.project.qa.tests.mobile;

import com.project.qa.testsupport.base.*;

import com.project.qa.framework.configuration.*;
import com.project.qa.testsupport.constants.*;
import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import com.project.qa.pageobjects.common.*;
import com.project.qa.pageobjects.web.*;
import com.project.qa.pageobjects.mobile.*;
import io.appium.java_client.android.*;
import io.appium.java_client.appmanagement.*;
import org.slf4j.*;
import org.testng.*;
import org.testng.annotations.*;

import java.time.*;
import java.util.*;

/**
 * Five simple, robust end-to-end tests against the Myntra Android app.
 *
 * Zero manual setup: BaseTest builds the (bare) native session, then {@link #prepareApp()} installs
 * Myntra from the Play Store if it is missing, pre-grants common runtime permissions so no system
 * dialog blocks the run, launches the app, and skips the login wall. Every @Test then starts from a
 * known home-feed state.
 *
 * Run with:
 *   mvn test -Pmobile-myntra
 */
public class MyntraAppTest extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(MyntraAppTest.class);
	private static final String MYNTRA_PACKAGE = "com.myntra.android";

	// Common first-launch dialogs Myntra can raise; granting up front keeps the run hands-off.
	private static final List<String> COMMON_PERMISSIONS = List.of(
			"android.permission.ACCESS_FINE_LOCATION",
			"android.permission.ACCESS_COARSE_LOCATION",
			"android.permission.POST_NOTIFICATIONS",
			"android.permission.READ_MEDIA_IMAGES");

	private AndroidDriver driver;
	private MyntraAppHomePage home;

	@BeforeMethod(alwaysRun = true)
	public void prepareApp() {
		driver = (AndroidDriver) DriverManager.getDriver();

		// Install-if-missing (from the Play Store when no -Dapp apk is given), then launch. This is
		// what makes the suite run on a device that may or may not already have Myntra.
		boolean launched = AndroidAppLauncher.launch(driver, MYNTRA_PACKAGE, ConfigReader.getApp());
		Assert.assertTrue(launched, "Myntra did not reach the foreground during setup.");

		// Silence first-launch system permission dialogs before they can steal focus.
		AndroidDeviceResolver.grantRuntimePermissions(MYNTRA_PACKAGE, COMMON_PERMISSIONS);

		// Force a clean cold start so every test begins on the HOME feed. Without this, the noReset
		// session simply resumes wherever a prior test left the app (e.g. a search results page),
		// which has no home search bar — making home-dependent tests order-sensitive and flaky.
		driver.terminateApp(MYNTRA_PACKAGE);
		AndroidAppLauncher.launch(driver, MYNTRA_PACKAGE, ConfigReader.getApp());

		home = new MyntraAppHomePage().dismissLoginWallIfPresent();
	}

	@Test(groups = TestGroups.MOBILE)
	public void appLaunchesIntoForeground() {
		ApplicationState state = driver.queryAppState(MYNTRA_PACKAGE);
		Assert.assertEquals(state, ApplicationState.RUNNING_IN_FOREGROUND,
				"Myntra is not running in the foreground; actual state: " + state);
	}

	@Test(groups = TestGroups.MOBILE)
	public void correctAppPackageIsForemost() {
		Assert.assertEquals(driver.getCurrentPackage(), MYNTRA_PACKAGE,
				"A different app is foremost than the Myntra package under test.");
	}

	@Test(groups = TestGroups.MOBILE)
	public void homeSearchBarIsVisible() {
		Assert.assertTrue(home.isSearchBarDisplayed(),
				"Home search bar was not visible; the home feed did not load.");
	}

	@Test(groups = TestGroups.MOBILE)
	public void searchReturnsProductResults() {
		MyntraAppSearchPage results = home.openSearch().searchFor("shoes");
		Assert.assertTrue(results.hasResults(),
				"No product results appeared for the search term 'shoes'.");
	}

	@Test(groups = TestGroups.MOBILE)
	public void appRestoresAfterBackgrounding() {
		driver.runAppInBackground(Duration.ofSeconds(2));
		driver.activateApp(MYNTRA_PACKAGE);

		ApplicationState state = driver.queryAppState(MYNTRA_PACKAGE);
		Assert.assertEquals(state, ApplicationState.RUNNING_IN_FOREGROUND,
				"Myntra did not restore to the foreground after being backgrounded.");
	}
}
