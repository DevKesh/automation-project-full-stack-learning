package com.project.qa.tests.api;

import com.project.qa.config.ConfigReader;
import io.restassured.RestAssured;
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
}
