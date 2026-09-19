package com.project.qa.testsupport.web.faq;

/**
 * Immutable value object describing a single link that failed verification.
 *
 * <p>A record because it is pure data: final fields, value-based equality, and a free toString — the
 * right shape for collecting into a report list.
 *
 * @param href   the fully-resolved link URL (Selenium returns getAttribute("href") already absolute).
 * @param reason human-readable cause (e.g. "HTTP 404"), so the log alone is enough to triage.
 */
public record BrokenLink(String href, String reason) {
}
