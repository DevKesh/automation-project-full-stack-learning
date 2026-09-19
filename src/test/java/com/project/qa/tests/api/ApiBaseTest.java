package com.project.qa.tests.api;

import com.project.qa.framework.configuration.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.testng.annotations.BeforeClass;

/*
 * Thin base for API tests. Deliberately does NOT extend the UI BaseTest — an API test needs no
 * WebDriver, so starting a browser per method would be pure waste. Setting RestAssured.baseURI
 * once here keeps the individual services free of base-path concerns and honours the same
 * -DapiBaseUri override chain as the rest of the framework.
 */
public class ApiBaseTest {

	// alwaysRun = true so the base URI is still set when the suite is filtered to the "api" group.
	@BeforeClass(alwaysRun = true)
	public void configureBaseUri() {
		RestAssured.baseURI = ConfigReader.getApiBaseUri();
	}

	// Shared, one-line response summary so every API test's log reads like a transaction ledger.
	// Takes the caller's Logger so each line is attributed to the concrete test class, not this base.
	protected void logResponseSummary(Logger log, Response response) {
		log.info("RESPONSE: status={} ({}), timeMs={}, contentType={}",
				response.statusCode(), response.statusLine(), response.time(), response.contentType());
	}

	// Full pretty-printed body for single-resource responses, so the exact payload is visible in logs.
	protected void logResponseBody(Logger log, Response response) {
		String body = response.getBody().asPrettyString();
		log.info("RESPONSE BODY:\n{}", body.isBlank() ? "(empty body)" : body);
	}
}
