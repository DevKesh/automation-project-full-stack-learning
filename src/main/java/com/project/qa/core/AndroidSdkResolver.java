package com.project.qa.core;

import org.slf4j.*;

import java.nio.file.*;

/**
 * Resolves the Android SDK root and shields runs from the single most common environment mistake.
 *
 * Appium's UiAutomator2 driver expects ANDROID_HOME to point at the SDK *root* — the folder that
 * contains platform-tools/ and build-tools/. Developers frequently point it one level too deep at
 * platform-tools/ (because that is what adb lives in), which makes session creation fail with an
 * opaque error. We normalise that here so a working session does not depend on a perfectly set
 * environment variable.
 */
public final class AndroidSdkResolver {

	private static final Logger log = LoggerFactory.getLogger(AndroidSdkResolver.class);
	private static final String PLATFORM_TOOLS = "platform-tools";

	private AndroidSdkResolver() {
	}

	public static Path resolveSdkRoot() {
		String candidate = firstNonBlank(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"));
		if (candidate == null) {
			candidate = defaultSdkPath();
		}
		if (candidate == null) {
			throw new IllegalStateException(
					"Android SDK not found. Set ANDROID_HOME to the SDK root (the folder containing 'platform-tools').");
		}

		Path root = Path.of(candidate);
		if (isPlatformToolsFolder(root)) {
			root = root.getParent(); // trim the accidental \platform-tools suffix
		}

		if (root == null || !Files.isDirectory(root.resolve(PLATFORM_TOOLS))) {
			throw new IllegalStateException(
					"Invalid Android SDK at '" + candidate + "'. Expected a folder containing 'platform-tools'. "
							+ "Set ANDROID_HOME to the SDK root.");
		}

		log.debug("Resolved Android SDK root: {}", root);
		return root;
	}

	private static boolean isPlatformToolsFolder(Path path) {
		Path name = path.getFileName();
		return name != null && name.toString().equalsIgnoreCase(PLATFORM_TOOLS);
	}

	private static String defaultSdkPath() {
		String localAppData = System.getenv("LOCALAPPDATA");
		return localAppData == null ? null : Path.of(localAppData, "Android", "Sdk").toString();
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}
}
