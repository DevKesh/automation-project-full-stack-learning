package com.project.qa.testsupport.api.services;

import com.project.qa.testsupport.api.ApiSpecFactory;
import com.project.qa.testsupport.api.models.reqres.AuthRequest;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/*
 * Endpoint object for reqres.in authentication (/api/register, /api/login). Kept separate from
 * UserService so each service maps to a single resource concern — the same single-responsibility
 * discipline the UI page objects follow.
 */
public class AuthService {

	private static final String REGISTER = "/api/register";
	private static final String LOGIN = "/api/login";

	@Step("POST register")
	public Response register(AuthRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.body(request)
				.when().post(REGISTER);
	}

	@Step("POST login")
	public Response login(AuthRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.body(request)
				.when().post(LOGIN);
	}
}
