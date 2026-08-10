package com.project.qa.listeners;

import com.project.qa.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/*
 * Re-runs a failed test up to `retry.count` times before reporting it as failed.
 *
 * WHY: environment flakiness (a slow network hop, a transient element re-render) should not fail an
 * otherwise-correct suite. A bounded retry absorbs that noise while still surfacing genuine, repeatable
 * failures. The ceiling is config-driven so it can be tightened in CI and loosened locally.
 *
 * TestNG creates a fresh analyzer instance per test method, so `attempts` is naturally per-test state
 * and is safe under parallel execution — no sharing across threads.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(RetryAnalyzer.class);
	private static final int MAX_RETRIES = ConfigReader.getRetryCount();

	private int attempts = 0;

	@Override
	public boolean retry(ITestResult result) {
		if (attempts < MAX_RETRIES) {
			attempts++;
			log.info("Retrying '{}' (attempt {} of {}) after failure", result.getName(), attempts, MAX_RETRIES);
			return true;
		}
		return false;
	}
}
