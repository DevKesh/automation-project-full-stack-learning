package com.project.qa.testsupport.api;

import com.project.qa.framework.configuration.ConfigReader;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/*
 * Single source of truth for HTTP request configuration — the API-layer analogue of DriverFactory.
 *
 * Every service call reuses one immutable, pre-built spec so base URI, headers, reporting and
 * logging are guaranteed identical across the suite. The AllureRestAssured filter attaches each
 * request/response to the Allure report. Console logging of the raw request/response is emitted
 * only when a validation fails (LogDetail.ALL) — this keeps passing runs clean while giving full
 * forensics on failure, matching the framework's "artifacts on failure" philosophy. The spec is
 * stateless and therefore safe to share across TestNG's parallel method threads.
 */
public final class ApiSpecFactory {

	private static final RestAssuredConfig LOG_ON_FAILURE = RestAssuredConfig.config()
			.logConfig(LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL));

	private static final RequestSpecification SPEC = new RequestSpecBuilder()
			.setBaseUri(ConfigReader.getApiBaseUri())
			.setConfig(LOG_ON_FAILURE)
			.setContentType(ContentType.JSON)
			.setAccept(ContentType.JSON)
			.addFilter(new AllureRestAssured())
			.build();

	// A second immutable spec for reqres.in. It differs from SPEC only in base URI and the mandatory
	// x-api-key header, but keeps the identical config/reporting so both API targets behave uniformly.
	private static final RequestSpecification REQRES = new RequestSpecBuilder()
			.setBaseUri(ConfigReader.getReqresBaseUri())
			.setConfig(LOG_ON_FAILURE)
			.setContentType(ContentType.JSON)
			.setAccept(ContentType.JSON)
			.addHeader("x-api-key", ConfigReader.getReqresApiKey())
			.addFilter(new AllureRestAssured())
			.build();

	private ApiSpecFactory() {
	}

	public static RequestSpecification spec() {
		return SPEC;
	}

	public static RequestSpecification reqres() {
		return REQRES;
	}
}
