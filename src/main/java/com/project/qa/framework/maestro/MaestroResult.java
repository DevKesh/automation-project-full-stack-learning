package com.project.qa.framework.maestro;

import java.time.*;

/**
 * Immutable outcome of a single Maestro flow execution.
 *
 * A record (not a POJO) because it is pure, read-only test evidence: the flow name, the CLI exit
 * code, and how long it took. {@link #passed()} centralises the one rule that matters — Maestro
 * returns 0 only when every step of the flow succeeded.
 */
public record MaestroResult(String flowName, int exitCode, Duration duration) {

	public boolean passed() {
		return exitCode == 0;
	}
}
