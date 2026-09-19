package com.project.qa.testsupport.api;

import io.qameta.allure.Step;
import org.slf4j.Logger;
import org.testng.asserts.SoftAssert;

import java.util.Objects;

/*
 * Fluent, self-narrating validation helper for the API layer.
 *
 * Wraps TestNG's SoftAssert (so, like the UI suite, every mismatch is collected rather than
 * failing on the first) while logging each check as a readable line — "[PASS] <what> |
 * expected=<x> actual=<y>" — and recording it as an Allure @Step. The goal is that both the
 * console output and the report read as a plain-English account of exactly what was verified.
 */
public class ApiValidator {

	private final Logger log;
	private final SoftAssert softAssert = new SoftAssert();

	public ApiValidator(Logger log) {
		this.log = log;
	}

	@Step("Validate {what}: expected <{expected}>, got <{actual}>")
	public ApiValidator expect(String what, Object expected, Object actual) {
		boolean passed = Objects.equals(expected, actual);
		log.info("   [{}] {} | expected=<{}> actual=<{}>", passed ? "PASS" : "FAIL", what, expected, actual);
		softAssert.assertEquals(actual, expected, what);
		return this;
	}

	@Step("Validate {what}")
	public ApiValidator expectTrue(String what, boolean condition) {
		log.info("   [{}] {}", condition ? "PASS" : "FAIL", what);
		softAssert.assertTrue(condition, what);
		return this;
	}

	// Fails the aggregated soft assertions; call once at the end of each test.
	public void verifyAll() {
		log.info("   -> All validations checked; asserting results");
		softAssert.assertAll();
	}
}
