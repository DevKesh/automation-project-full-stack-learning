package com.project.qa.framework.webdriver;

import java.util.*;

/**
 * Supported local browsers. Safari is intentionally absent: SafariDriver ships only on macOS,
 * so it belongs to a future remote/grid execution path rather than this Windows-local factory.
 */
public enum BrowserType {
	CHROME,
	FIREFOX,
	EDGE;

	public static BrowserType from(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Browser is not configured. Set 'browser' in config.properties or pass -Dbrowser=<name>.");
		}
		try {
			return BrowserType.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Unsupported browser '" + value + "'. Supported values: " + Arrays.toString(values()));
		}
	}
}
