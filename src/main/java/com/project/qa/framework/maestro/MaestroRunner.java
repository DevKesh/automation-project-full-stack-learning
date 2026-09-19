package com.project.qa.framework.maestro;

import org.slf4j.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Runs a Maestro YAML flow by shelling out to the Maestro CLI.
 *
 * WHY a process bridge and not a library call: Maestro is a standalone YAML runner with no JVM API,
 * so the CLI is the integration seam. This class isolates that seam — command assembly, output
 * draining, and exit-code capture — so the TestNG layer stays a plain assertion on {@link MaestroResult}.
 * Kept dependency-free (executable + env are passed in) so it is trivially unit-testable.
 */
public final class MaestroRunner {

	private static final Logger log = LoggerFactory.getLogger(MaestroRunner.class);

	private MaestroRunner() {
		// Utility holder: no instances.
	}

	/**
	 * Executes one flow and returns its outcome.
	 *
	 * @param flow       path to the .yaml/.yml flow file
	 * @param executable the Maestro CLI command ('maestro' on PATH, or a full path)
	 * @param flowEnv    values injected into the flow as ${KEY} via Maestro's -e KEY=VALUE
	 */
	public static MaestroResult runFlow(Path flow, String executable, Map<String, String> flowEnv) {
		List<String> command = buildCommand(executable, flow, flowEnv);
		log.info("Running Maestro flow: {}", flow.getFileName());
		log.debug("Command: {}", String.join(" ", maskSecrets(command)));

		Instant start = Instant.now();
		int exitCode = execute(command);
		Duration elapsed = Duration.between(start, Instant.now());

		MaestroResult result = new MaestroResult(flow.getFileName().toString(), exitCode, elapsed);
		log.info("Flow '{}' {} in {}s (exit={})",
				result.flowName(), result.passed() ? "PASSED" : "FAILED", elapsed.toSeconds(), exitCode);
		return result;
	}

	private static List<String> buildCommand(String executable, Path flow, Map<String, String> flowEnv) {
		List<String> command = new ArrayList<>();
		// On Windows the Maestro CLI is 'maestro.bat'. Java's ProcessBuilder does not apply PATHEXT,
		// so it cannot resolve a bare 'maestro' (or run a .bat) directly — route it through cmd.exe,
		// which honours PATH + the .bat extension exactly like a human's terminal would.
		if (isWindows()) {
			command.add("cmd.exe");
			command.add("/c");
		}
		command.add(executable);
		command.add("test");
		command.add(flow.toString());
		flowEnv.forEach((key, value) -> {
			command.add("-e");
			command.add(key + "=" + value);
		});
		return command;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static int execute(List<String> command) {
		// Merge stderr into stdout so the flow's step-by-step narrative stays in order.
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		try {
			Process process = builder.start();
			drainOutput(process);
			return process.waitFor();
		} catch (IOException e) {
			throw new IllegalStateException("Could not start the Maestro CLI ('" + command.get(0)
					+ "'). Is it installed and on PATH? Override with -Dmaestro.exe=<full path>.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for the Maestro flow to finish", e);
		}
	}

	// The child pipe MUST be drained or a chatty flow can fill the buffer and deadlock. Maestro's
	// own per-step lines are framework noise at our level, so they land on DEBUG; the caller logs
	// the PASSED/FAILED milestone on INFO.
	private static void drainOutput(Process process) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				log.debug("[maestro] {}", line);
			}
		}
	}

	// Never let a credential passed via -e KEY=VALUE reach the logs.
	private static List<String> maskSecrets(List<String> command) {
		List<String> masked = new ArrayList<>(command.size());
		for (String token : command) {
			int equals = token.indexOf('=');
			boolean isEnvPair = equals > 0 && !token.startsWith("-");
			masked.add(isEnvPair ? token.substring(0, equals + 1) + "***" : token);
		}
		return masked;
	}
}
