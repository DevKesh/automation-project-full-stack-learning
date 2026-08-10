package com.project.qa.constants;

/**
 * Single source of truth for TestNG group names.
 *
 * WHY a constants class: group names are referenced from every @Test annotation and must match the
 * strings used in the testng.xml <include> filters exactly. Centralising them here means a typo
 * fails to compile on the Java side instead of silently excluding a test from a run.
 *
 * Values are compile-time constants (String literals on `public static final`) because annotation
 * attributes such as @Test(groups = ...) can only accept constant expressions.
 */
public final class TestGroups {

	public static final String API = "api";
	public static final String WEB = "web";
	public static final String MOBILE = "mobile";

	private TestGroups() {
		// Utility holder: no instances.
	}
}
