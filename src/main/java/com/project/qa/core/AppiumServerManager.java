package com.project.qa.core;

import com.project.qa.config.*;
import io.appium.java_client.service.local.*;
import io.appium.java_client.service.local.flags.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

/**
 * Owns the Appium server lifecycle so a mobile run needs no manually started server.
 *
 * The first mobile thread boots a single shared {@link AppiumDriverLocalService}; later threads
 * reuse it (idempotent). A JVM shutdown hook stops it, so a crashed or short suite never leaks a
 * server process. If an engineer or CI provides an external {@code appiumServerUrl}, we honour it
 * and stay out of the way.
 */
public final class AppiumServerManager {

	private static final Logger log = LoggerFactory.getLogger(AppiumServerManager.class);

	private static volatile AppiumDriverLocalService service;

	private AppiumServerManager() {
	}

	public static synchronized URL ensureStarted() {
		String external = ConfigReader.getAppiumServerUrl();
		if (external != null) {
			return toUrl(external);
		}
		if (service != null && service.isRunning()) {
			return service.getUrl();
		}
		service = buildService();
		service.start();
		if (!service.isRunning()) {
			throw new IllegalStateException(
					"Failed to start the Appium server. Ensure Appium 2.x is installed (npm i -g appium) and on PATH.");
		}
		registerShutdownHook();
		log.info("Appium server started at {}", service.getUrl());
		return service.getUrl();
	}

	private static AppiumDriverLocalService buildService() {
		// Hand the server a corrected ANDROID_HOME/ANDROID_SDK_ROOT so UiAutomator2 can locate the
		// SDK regardless of how the shell environment was set up.
		String sdkRoot = AndroidSdkResolver.resolveSdkRoot().toString();
		Map<String, String> environment = new HashMap<>(System.getenv());
		environment.put("ANDROID_HOME", sdkRoot);
		environment.put("ANDROID_SDK_ROOT", sdkRoot);

		return new AppiumServiceBuilder()
				.withIPAddress("127.0.0.1")
				.usingAnyFreePort()
				.withEnvironment(environment)
				.withArgument(GeneralServerFlag.LOG_LEVEL, "warn")
				.build();
	}

	private static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (service != null && service.isRunning()) {
				service.stop();
				log.info("Appium server stopped");
			}
		}));
	}

	private static URL toUrl(String value) {
		try {
			return URI.create(value).toURL();
		} catch (MalformedURLException | IllegalArgumentException e) {
			throw new IllegalStateException("Invalid appiumServerUrl '" + value + "'", e);
		}
	}
}
