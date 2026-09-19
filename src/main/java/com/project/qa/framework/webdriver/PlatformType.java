package com.project.qa.framework.webdriver;

import java.util.*;

/**
 * Execution target for the suite. WEB drives desktop browsers via Selenium; ANDROID drives a
 * device/emulator via Appium. IOS is intentionally omitted until a macOS execution path exists,
 * mirroring how {@link BrowserType} scopes itself to what this environment can actually run.
 */
public enum PlatformType {
	WEB,
	ANDROID;

	// Absent config defaults to WEB so every existing desktop test keeps running untouched.
	public static PlatformType from(String value) {
		if (value == null || value.isBlank()) {
			return WEB;
		}
		try {
			return PlatformType.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Unsupported platform '" + value + "'. Supported values: " + Arrays.toString(values()));
		}
	}
}
