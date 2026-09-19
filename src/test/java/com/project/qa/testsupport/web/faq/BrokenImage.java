package com.project.qa.testsupport.web.faq;

/**
 * Immutable value object describing a single image that failed verification.
 *
 * A record (not a class) because this is pure data with no behaviour: every field is final, and we
 * get value-based equals/hashCode/toString for free — ideal for collecting into a List and logging.
 *
 * @param src    the fully-resolved image URL (Selenium returns getAttribute("src") already absolute).
 * @param reason a human-readable explanation of WHY it was judged broken, so a failing scan is
 *               actionable in the log/Allure report without re-running to investigate.
 */
public record BrokenImage(String src, String reason) {
}
