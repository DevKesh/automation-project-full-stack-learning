package com.project.qa.core;

import com.project.qa.config.*;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Discovers the Android device to automate via adb, so nothing about a specific handset is
 * hardcoded — plugging in any authorised device is enough for a run to target it.
 *
 * A single connected device is auto-selected. When several are attached, an explicit
 * {@code -DdeviceUdid} (or config key) disambiguates; without it we fail fast rather than
 * silently guessing which device the engineer meant.
 */
public final class AndroidDeviceResolver {

	private static final Logger log = LoggerFactory.getLogger(AndroidDeviceResolver.class);

	private AndroidDeviceResolver() {
	}

	public record AndroidDevice(String udid, String name, String platformVersion) {
	}

	public static AndroidDevice resolveConnectedDevice() {
		Path adb = adbExecutable();
		List<String> online = onlineDevices(adb);

		String udid = selectUdid(online);
		String model = getProp(adb, udid, "ro.product.model");
		String version = getProp(adb, udid, "ro.build.version.release");

		log.info("Detected Android device '{}' (Android {}, udid={})", model, version, udid);
		return new AndroidDevice(udid, model, version);
	}

	// Lists user-installed (third-party) app packages via `adb shell pm list packages -3`, so a test
	// can discover what is on the device and launch one by name — no host-side apk required.
	public static List<String> listThirdPartyPackages() {
		Path adb = adbExecutable();
		List<String> packages = new ArrayList<>();
		for (String line : run(adb, "shell", "pm", "list", "packages", "-3")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("package:")) {
				packages.add(trimmed.substring("package:".length()));
			}
		}
		Collections.sort(packages);
		return packages;
	}

	// adb is the source of truth for install state. Appium's isAppInstalled() gives false negatives
	// on Android 11+ because of package-visibility filtering, so we check via `pm list packages`.
	public static boolean isPackageInstalled(String appPackage) {
		Path adb = adbExecutable();
		String expected = "package:" + appPackage;
		for (String line : run(adb, "shell", "pm", "list", "packages", appPackage)) {
			if (line.trim().equals(expected)) {
				return true;
			}
		}
		return false;
	}

	// Opens the Play Store straight to an app's listing via its market: deep link. This is the
	// app-agnostic entry point for installing anything by package name — no apk, no URL, just the id.
	public static void openPlayStoreListing(String appPackage) {
		Path adb = adbExecutable();
		run(adb, "shell", "am", "start", "-a", "android.intent.action.VIEW",
				"-d", "market://details?id=" + appPackage);
		log.info("Opened Play Store listing for '{}'", appPackage);
	}

	// Pre-grants runtime permissions via adb so first-launch system dialogs (location, notifications,
	// media) never block an unattended run. Best-effort: an app that doesn't declare a permission
	// simply rejects the grant, which we log at debug and ignore. This keeps the very first launch on
	// a fresh device hands-off, without knowing the app's exact permission set.
	public static void grantRuntimePermissions(String appPackage, List<String> permissions) {
		Path adb = adbExecutable();
		for (String permission : permissions) {
			try {
				run(adb, "shell", "pm", "grant", appPackage, permission);
			} catch (RuntimeException e) {
				log.debug("Could not grant {} to {} (app may not request it): {}",
						permission, appPackage, e.getMessage());
			}
		}
	}

	// Turns off Google Play Protect's package verifier for adb-side installs so sideloading an apk
	// completes silently instead of raising an on-device "unsafe app" prompt that would need a human
	// tap. This is the standard way CI keeps app installs hands-off; it is best-effort and any adb
	// failure is logged, not fatal, because a device may not expose these settings.
	public static void allowSilentInstalls() {
		Path adb = adbExecutable();
		try {
			run(adb, "shell", "settings", "put", "global", "verifier_verify_adb_installs", "0");
			run(adb, "shell", "settings", "put", "global", "package_verifier_enable", "0");
			log.info("Disabled Play Protect install verification for hands-off apk installs");
		} catch (RuntimeException e) {
			log.warn("Could not disable install verification; a Play Protect prompt may appear during "
					+ "install. Cause: {}", e.getMessage());
		}
	}

	private static String selectUdid(List<String> online) {
		String override = ConfigReader.getDeviceUdid();
		if (override != null) {
			if (!online.contains(override)) {
				throw new IllegalStateException(
						"Requested deviceUdid '" + override + "' is not connected. Online devices: " + online);
			}
			return override;
		}
		if (online.isEmpty()) {
			throw new IllegalStateException(
					"No Android device or emulator detected. Connect a device with USB debugging enabled "
							+ "(it must appear as 'device' in `adb devices`), or start an emulator.");
		}
		if (online.size() > 1) {
			throw new IllegalStateException(
					"Multiple Android devices connected " + online + ". Pin one with -DdeviceUdid=<udid>.");
		}
		return online.get(0);
	}

	// Parses `adb devices`, keeping only entries in the 'device' state (skips 'offline'/'unauthorized').
	private static List<String> onlineDevices(Path adb) {
		List<String> devices = new ArrayList<>();
		for (String line : run(adb, "devices")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("List of devices")) {
				continue;
			}
			String[] parts = trimmed.split("\\s+");
			if (parts.length == 2 && "device".equals(parts[1])) {
				devices.add(parts[0]);
			}
		}
		return devices;
	}

	private static String getProp(Path adb, String udid, String property) {
		List<String> output = run(adb, "-s", udid, "shell", "getprop", property);
		return output.isEmpty() ? "" : output.get(0).trim();
	}

	private static Path adbExecutable() {
		Path platformTools = AndroidSdkResolver.resolveSdkRoot().resolve("platform-tools");
		Path windows = platformTools.resolve("adb.exe");
		Path unix = platformTools.resolve("adb");
		if (Files.isRegularFile(windows)) {
			return windows;
		}
		if (Files.isRegularFile(unix)) {
			return unix;
		}
		throw new IllegalStateException("adb not found under " + platformTools + ". Install Android platform-tools.");
	}

	private static List<String> run(Path adb, String... args) {
		List<String> command = new ArrayList<>();
		command.add(adb.toString());
		command.addAll(Arrays.asList(args));
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			List<String> lines = new ArrayList<>();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			}
			process.waitFor();
			log.debug("adb {} -> {}", String.join(" ", args), lines);
			return lines;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to execute adb " + String.join(" ", args), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while executing adb " + String.join(" ", args), e);
		}
	}
}
