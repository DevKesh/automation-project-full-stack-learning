package com.project.qa.core;

import java.util.*;

/**
 * What the Android session drives. WEB launches Chrome on the device; NATIVE launches an installed
 * app or an .apk. This is an explicit configuration value rather than something inferred from which
 * capabilities happen to be set — inference is a leaky, surprising coupling, while an explicit
 * switch is self-documenting and fails loudly on misconfiguration.
 */
public enum AndroidTarget {
	NATIVE,
	WEB;

	public static AndroidTarget from(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("androidTarget is not configured. Set 'androidTarget=native|web'.");
		}
		try {
			return AndroidTarget.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Unsupported androidTarget '" + value + "'. Supported values: " + Arrays.toString(values()));
		}
	}
}
