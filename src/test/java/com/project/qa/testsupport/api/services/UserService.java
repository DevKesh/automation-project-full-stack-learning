package com.project.qa.testsupport.api.services;

import com.project.qa.testsupport.api.ApiSpecFactory;
import com.project.qa.testsupport.api.models.reqres.CreateUserRequest;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/*
 * Endpoint object for the reqres.in /api/users resource — the API-layer analogue of a Page Object.
 * Verbs and paths are hidden behind intent-revealing methods so tests read as business actions.
 * Every call reuses the shared reqres spec (base URI + x-api-key), and assertions stay in the tests.
 */
public class UserService {

	private static final String USERS = "/api/users";
	private static final String USER_BY_ID = "/api/users/{id}";

	@Step("GET users on page {page}")
	public Response listUsers(int page) {
		return given().spec(ApiSpecFactory.reqres())
				.queryParam("page", page)
				.when().get(USERS);
	}

	@Step("GET user by id {id}")
	public Response getUser(int id) {
		return given().spec(ApiSpecFactory.reqres())
				.pathParam("id", id)
				.when().get(USER_BY_ID);
	}

	@Step("POST create user")
	public Response createUser(CreateUserRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.body(request)
				.when().post(USERS);
	}

	@Step("PUT update user id {id}")
	public Response updateUser(int id, CreateUserRequest request) {
		return given().spec(ApiSpecFactory.reqres())
				.pathParam("id", id)
				.body(request)
				.when().put(USER_BY_ID);
	}

	@Step("DELETE user id {id}")
	public Response deleteUser(int id) {
		return given().spec(ApiSpecFactory.reqres())
				.pathParam("id", id)
				.when().delete(USER_BY_ID);
	}
}
