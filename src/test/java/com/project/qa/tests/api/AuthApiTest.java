package com.project.qa.tests.api;

import com.project.qa.testsupport.api.ApiValidator;
import com.project.qa.testsupport.api.models.reqres.AuthRequest;
import com.project.qa.testsupport.api.models.reqres.AuthResponse;
import com.project.qa.testsupport.api.services.AuthService;
import com.project.qa.testsupport.constants.TestGroups;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/*
 * Contract coverage for reqres.in authentication. Demonstrates positive AND negative testing:
 * a successful register/login returns a token, while a missing password is rejected with a 400 and
 * a specific error message — the kind of error-path assertion every real API suite needs.
 */
@Epic("API Testing")
@Feature("reqres.in Authentication (/api/register, /api/login)")
public class AuthApiTest extends ApiBaseTest {

	private static final Logger log = LoggerFactory.getLogger(AuthApiTest.class);

	// reqres only accepts this specific pre-registered email for its happy-path auth responses.
	private static final String KNOWN_USER = "eve.holt@reqres.in";

	private final AuthService authService = new AuthService();

	@Test(groups = TestGroups.API)
	@Description("POST /api/register with valid credentials returns 200 with an id and token")
	public void registerSucceeds() {
		AuthRequest request = new AuthRequest(KNOWN_USER, "pistol");
		log.info("===== SCENARIO: Register a known user =====");
		log.info("REQUEST : POST /api/register  with email=\"{}\" and a password", request.getEmail());

		Response response = authService.register(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: id={}, token=\"{}\"", body.getId(), body.getToken());

		log.info("VALIDATING a successful registration:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("an id was returned", body.getId() != null)
				.expectTrue("a token was returned", body.getToken() != null && !body.getToken().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: registration returned an id and token =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/register without a password returns 400 with 'Missing password'")
	public void registerWithoutPasswordFails() {
		AuthRequest request = new AuthRequest("sydney@fife", null);
		log.info("===== SCENARIO: Register with a missing password (negative test) =====");
		log.info("REQUEST : POST /api/register  with email only, no password");

		Response response = authService.register(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: error=\"{}\"", body.getError());

		log.info("VALIDATING that the API rejects the request:");
		new ApiValidator(log)
				.expect("HTTP status code (Bad Request)", 400, response.statusCode())
				.expect("error message", "Missing password", body.getError())
				.expectTrue("no token was issued", body.getToken() == null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: missing password correctly rejected with 400 =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/login with valid credentials returns 200 with a token")
	public void loginSucceeds() {
		AuthRequest request = new AuthRequest(KNOWN_USER, "cityslicka");
		log.info("===== SCENARIO: Log in a known user =====");
		log.info("REQUEST : POST /api/login  with email=\"{}\" and a password", request.getEmail());

		Response response = authService.login(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: token=\"{}\"", body.getToken());

		log.info("VALIDATING a successful login:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("a token was returned", body.getToken() != null && !body.getToken().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: login returned a token =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/login without a password returns 400 with 'Missing password'")
	public void loginWithoutPasswordFails() {
		AuthRequest request = new AuthRequest("peter@klaven", null);
		log.info("===== SCENARIO: Log in with a missing password (negative test) =====");
		log.info("REQUEST : POST /api/login  with email only, no password");

		Response response = authService.login(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		AuthResponse body = response.as(AuthResponse.class);
		log.info("RESPONSE DATA: error=\"{}\"", body.getError());

		log.info("VALIDATING that the API rejects the request:");
		new ApiValidator(log)
				.expect("HTTP status code (Bad Request)", 400, response.statusCode())
				.expect("error message", "Missing password", body.getError())
				.expectTrue("no token was issued", body.getToken() == null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: missing password correctly rejected with 400 =====");
	}
}
