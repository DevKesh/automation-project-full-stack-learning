package com.project.qa.framework.configuration;

import org.slf4j.*;

import java.io.*;
import java.util.*;

/*
 * Central access point for runtime configuration.
 *
 * Two-layer, environment-aware resolution per key:
 *   1. JVM System property (-Dkey=value)      — highest precedence (CI / command line)
 *   2. config-{env}.properties (env overlay)   — environment-specific values
 *   3. config.properties (base defaults)        — shared, committed defaults
 *
 * The active environment is chosen by `-Denv=<name>` (or an `env=` entry in the base file). This
 * lets one build target dev/staging/prod without editing files: only the differing keys live in the
 * overlay, while everything common stays in the base — no duplicated config, no drift.
 */
public final class ConfigReader {

	private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);
	private static final String BASE_CONFIG_FILE = "/config/config.properties";

	// Base holds shared defaults; the overlay holds only the keys that differ for the active env.
	private static final Properties baseProperties = loadRequired(BASE_CONFIG_FILE);
	private static final Properties envProperties = loadEnvOverlay();

	private ConfigReader() {
	}

	private static Properties loadRequired(String resource) {
		Properties props = new Properties();
		try (InputStream stream = ConfigReader.class.getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IllegalStateException(resource + " not found on the classpath");
			}
			props.load(stream);
			log.debug("Loaded configuration from {}", resource);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load " + resource, e);
		}
		return props;
	}

	// Resolve the active environment, then load its overlay. A configured-but-missing overlay is a
	// hard failure: silently running against base defaults would mask a mistyped `-Denv` in CI.
	private static Properties loadEnvOverlay() {
		String env = System.getProperty("env");
		if (env == null || env.isBlank()) {
			env = baseProperties.getProperty("env");
		}
		if (env == null || env.isBlank()) {
			log.debug("No environment selected; using base configuration only");
			return new Properties();
		}

		String overlayFile = "/config/config-" + env.trim() + ".properties";
		log.info("Active environment: {} (overlay {})", env.trim(), overlayFile);
		return loadRequired(overlayFile);
	}

	// Precedence: System property > env overlay > base defaults.
	private static String resolve(String key) {
		String value = System.getProperty(key);
		if (value == null || value.isBlank()) {
			value = envProperties.getProperty(key);
		}
		if (value == null || value.isBlank()) {
			value = baseProperties.getProperty(key);
		}
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	private static String resolveOrDefault(String key, String fallback) {
		String value = resolve(key);
		return value == null ? fallback : value;
	}

	// Fails fast on a non-numeric config value rather than defaulting silently, so a typo in a
	// timeout or retry count surfaces immediately instead of altering test behaviour unnoticed.
	private static int resolveInt(String key, int fallback) {
		String value = resolve(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalStateException("Config key '" + key + "' must be an integer, but was: " + value, e);
		}
	}

	public static String getEnv() {
		return resolveOrDefault("env", "default");
	}

	public static String getBrowser() {
		return resolve("browser");
	}

	public static boolean isHeadless() {
		return Boolean.parseBoolean(resolve("headless"));
	}

	// Explicit-wait ceiling for the page layer. Centralised so synchronization tuning is a config
	// change, not a code edit, and can be widened per-environment (e.g. a slower staging box).
	public static int getExplicitWaitSeconds() {
		return resolveInt("explicit.wait.seconds", 10);
	}

	// Extra attempts for a failed test before it is reported as failed. 0 disables retries.
	public static int getRetryCount() {
		return resolveInt("retry.count", 0);
	}

	// --- Mobile (Appium) ---

	public static String getPlatform() {
		return resolve("platform");
	}

	public static String getAndroidTarget() {
		return resolveOrDefault("androidTarget", "web");
	}

	// Null lets AppiumServerManager self-manage a local server; a value reuses an external one.
	public static String getAppiumServerUrl() {
		return resolve("appiumServerUrl");
	}

	// Null lets AndroidDeviceResolver auto-select the single connected device.
	public static String getDeviceUdid() {
		return resolve("deviceUdid");
	}

	public static String getApp() {
		return resolve("app");
	}

	public static String getAppPackage() {
		return resolve("appPackage");
	}

	public static String getAppActivity() {
		return resolve("appActivity");
	}

	// --- API (REST Assured) ---

	public static String getApiBaseUri() {
		return resolve("apiBaseUri");
	}

	public static String getReqresBaseUri() {
		return resolve("reqresBaseUri");
	}

	public static String getReqresApiKey() {
		return resolve("reqresApiKey");
	}

	// --- Maestro (CLI-backed UI flows) ---

	// The Maestro CLI command. Defaults to 'maestro' (expects it on PATH); override a non-PATH
	// install with -Dmaestro.exe=C:\...\maestro.bat
	public static String getMaestroExecutable() {
		return resolveOrDefault("maestro.exe", "maestro");
	}

	// Folder of .yaml/.yml flows; each becomes one TestNG test case.
	public static String getMaestroFlowsDir() {
		return resolveOrDefault("maestro.flows", "src/test/resources/maestro/flows");
	}

	// Credentials injected into flows as ${TC2_USERNAME}/${TC2_PASSWORD}. Read from -Dtc2.username /
	// -Dtc2.password or the TC2_USERNAME / TC2_PASSWORD environment variables, so no secret is ever
	// committed. Only non-blank values are forwarded, leaving credential-free flows unaffected.
	public static Map<String, String> getMaestroFlowEnv() {
		Map<String, String> flowEnv = new LinkedHashMap<>();
		putIfPresent(flowEnv, "TC2_USERNAME", resolveSecret("tc2.username", "TC2_USERNAME"));
		putIfPresent(flowEnv, "TC2_PASSWORD", resolveSecret("tc2.password", "TC2_PASSWORD"));
		return flowEnv;
	}

	private static void putIfPresent(Map<String, String> target, String key, String value) {
		if (value != null) {
			target.put(key, value);
		}
	}

	// System property first (so -D wins in CI), then the environment variable fallback.
	private static String resolveSecret(String propertyKey, String envKey) {
		String value = resolve(propertyKey);
		if (value == null) {
			String fromEnv = System.getenv(envKey);
			value = (fromEnv == null || fromEnv.isBlank()) ? null : fromEnv.trim();
		}
		return value;
	}
}
