package com.project.qa.core;

import com.project.qa.config.*;
import io.appium.java_client.android.*;
import io.appium.java_client.android.options.*;
import org.openqa.selenium.*;
import org.slf4j.*;

import java.net.*;
import java.time.*;

/**
 * Builds an Android {@link WebDriver} session. Device coordinates are auto-detected and the Appium
 * server is self-managed, so this factory only decides *what* the session drives (native app vs
 * mobile web) and hands back a plain {@link WebDriver} — the rest of the framework never learns it
 * is talking to a phone.
 */
public final class MobileDriverFactory {

	private static final Logger log = LoggerFactory.getLogger(MobileDriverFactory.class);

	private MobileDriverFactory() {
	}

	public static WebDriver createDriver() {
		URL server = AppiumServerManager.ensureStarted();
		AndroidTarget target = AndroidTarget.from(ConfigReader.getAndroidTarget());

		UiAutomator2Options options = baseOptions();
		applyTarget(options, target);

		log.info("Starting Android session (target={})", target);
		log.debug("UiAutomator2 capabilities: {}", options.asMap());
		return new AndroidDriver(server, options);
	}

	private static UiAutomator2Options baseOptions() {
		AndroidDeviceResolver.AndroidDevice device = AndroidDeviceResolver.resolveConnectedDevice();
		UiAutomator2Options options = new UiAutomator2Options();
		options.setDeviceName(device.name());
		options.setUdid(device.udid());
		options.setPlatformVersion(device.platformVersion());
		// Keep the session alive through Play Store installs and human-paced debugging instead of the
		// server reaping it; the install poll also issues keepalive queries within this window.
		options.setNewCommandTimeout(Duration.ofSeconds(300));
		return options;
	}

	private static void applyTarget(UiAutomator2Options options, AndroidTarget target) {
		switch (target) {
			case WEB -> {
				options.withBrowserName("Chrome");
				// Let Appium fetch the exact Chromedriver matching the device's Chrome build. This
				// removes the most common mobile-web failure: Chrome/Chromedriver version skew.
				options.setCapability("appium:chromedriverAutodownload", true);
			}
			case NATIVE -> applyNativeApp(options);
		}
	}

	private static void applyNativeApp(UiAutomator2Options options) {
		String app = ConfigReader.getApp();
		String appPackage = ConfigReader.getAppPackage();
		String appActivity = ConfigReader.getAppActivity();

		// Explicit package + activity launches a specific installed app at session start.
		if (appPackage != null && appActivity != null) {
			options.setAppPackage(appPackage);
			options.setAppActivity(appActivity);
			return;
		}

		// A targeted package (with no activity) uses a bare session so the test/launcher can decide
		// whether to install a fallback apk (-Dapp) or just activate an already-installed app —
		// this is what lets a run succeed on a device that may or may not have the app.
		if (appPackage != null) {
			options.setNoReset(true);
			return;
		}

		// No package given: an apk alone installs + launches at session start (pure apk flow).
		if (app != null) {
			options.setApp(app);
			return;
		}

		// Nothing specified: a bare device session (e.g. app discovery).
		options.setNoReset(true);
	}
}
