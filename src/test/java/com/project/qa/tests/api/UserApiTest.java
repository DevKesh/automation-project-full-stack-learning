package com.project.qa.tests.api;

import com.project.qa.testsupport.api.ApiValidator;
import com.project.qa.testsupport.api.models.reqres.CreateUserRequest;
import com.project.qa.testsupport.api.models.reqres.SingleUserResponse;
import com.project.qa.testsupport.api.models.reqres.User;
import com.project.qa.testsupport.api.models.reqres.UserListResponse;
import com.project.qa.testsupport.api.models.reqres.UserMutationResponse;
import com.project.qa.testsupport.api.services.UserService;
import com.project.qa.testsupport.constants.TestGroups;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/*
 * Contract coverage for reqres.in /api/users. Exercises the scenarios JSONPlaceholder cannot:
 * pagination metadata, a genuine 404, a String-typed created id, and a 204-no-content delete.
 * Each test narrates request -> response -> validations in the log and Allure report.
 */
@Epic("API Testing")
@Feature("reqres.in Users resource (/api/users)")
public class UserApiTest extends ApiBaseTest {

	private static final Logger log = LoggerFactory.getLogger(UserApiTest.class);
	private static final long MAX_RESPONSE_MS = 5000;

	private final UserService userService = new UserService();

	@Test(groups = TestGroups.API)
	@Description("GET /api/users?page=2 returns the second page with correct pagination metadata")
	public void listUsersSecondPage() {
		log.info("===== SCENARIO: List users, page 2 =====");
		log.info("REQUEST : GET /api/users?page=2  (retrieve the second page of users)");

		Response response = userService.listUsers(2);
		logResponseSummary(log, response);
		UserListResponse body = response.as(UserListResponse.class);
		log.info("RESPONSE DATA: page={}, perPage={}, total={}, totalPages={}, itemsOnPage={}",
				body.getPage(), body.getPerPage(), body.getTotal(), body.getTotalPages(), body.getData().size());

		log.info("VALIDATING the pagination contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.expect("current page", 2, body.getPage())
				.expect("items per page", 6, body.getPerPage())
				.expect("total users", 12, body.getTotal())
				.expect("total pages", 2, body.getTotalPages())
				.expect("users returned on this page", 6, body.getData().size())
				.expectTrue("support block is present", body.getSupport() != null)
				.expectTrue("first user has a reqres.in email",
						body.getData().get(0).getEmail().endsWith("@reqres.in"))
				.verifyAll();
		log.info("===== SCENARIO PASSED: page 2 returned the expected paginated payload =====");
	}

	@Test(groups = TestGroups.API)
	@Description("GET /api/users/2 returns 200 with the expected single user under 'data'")
	public void getSingleUser() {
		log.info("===== SCENARIO: Fetch a single user =====");
		log.info("REQUEST : GET /api/users/2  (retrieve the user whose id is 2)");

		Response response = userService.getUser(2);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		User user = response.as(SingleUserResponse.class).getData();
		log.info("RESPONSE DATA: id={}, email={}, name=\"{} {}\"",
				user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());

		log.info("VALIDATING the single-user contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expect("user id in body", 2, user.getId())
				.expect("email", "janet.weaver@reqres.in", user.getEmail())
				.expect("first name", "Janet", user.getFirstName())
				.expectTrue("avatar URL is present", user.getAvatar() != null && !user.getAvatar().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: single user matched the expected contract =====");
	}

	@Test(groups = TestGroups.API)
	@Description("GET /api/users/23 returns 404 for a non-existent user")
	public void getMissingUserReturns404() {
		log.info("===== SCENARIO: Fetch a non-existent user =====");
		log.info("REQUEST : GET /api/users/23  (an id that does not exist)");

		Response response = userService.getUser(23);
		logResponseSummary(log, response);

		log.info("VALIDATING that the API reports the resource as missing:");
		new ApiValidator(log)
				.expect("HTTP status code (Not Found)", 404, response.statusCode())
				.expectTrue("response body is empty for a 404", response.getBody().asString().isBlank()
						|| response.getBody().asString().equals("{}"))
				.verifyAll();
		log.info("===== SCENARIO PASSED: missing user correctly returned 404 =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /api/users creates a resource and returns 201 with a new id and createdAt")
	public void createUser() {
		CreateUserRequest request = new CreateUserRequest("morpheus", "leader");
		log.info("===== SCENARIO: Create a new user =====");
		log.info("REQUEST : POST /api/users  with body -> name=\"{}\", job=\"{}\"",
				request.getName(), request.getJob());

		Response response = userService.createUser(request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		UserMutationResponse created = response.as(UserMutationResponse.class);
		log.info("RESPONSE DATA: server assigned id={}, createdAt={}", created.getId(), created.getCreatedAt());

		log.info("VALIDATING that the server accepted and echoed our data:");
		new ApiValidator(log)
				.expect("HTTP status code (201 Created)", 201, response.statusCode())
				.expect("echoed name matches what we sent", request.getName(), created.getName())
				.expect("echoed job matches what we sent", request.getJob(), created.getJob())
				.expectTrue("a new id was assigned", created.getId() != null && !created.getId().isBlank())
				.expectTrue("createdAt timestamp is present", created.getCreatedAt() != null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: user created with a new id and timestamp =====");
	}

	@Test(groups = TestGroups.API)
	@Description("PUT /api/users/2 updates the resource and returns 200 with an updatedAt timestamp")
	public void updateUser() {
		CreateUserRequest request = new CreateUserRequest("morpheus", "zion resident");
		log.info("===== SCENARIO: Update an existing user =====");
		log.info("REQUEST : PUT /api/users/2  changing job to \"{}\"", request.getJob());

		Response response = userService.updateUser(2, request);
		logResponseSummary(log, response);
		logResponseBody(log, response);
		UserMutationResponse updated = response.as(UserMutationResponse.class);
		log.info("RESPONSE DATA: job is now \"{}\", updatedAt={}", updated.getJob(), updated.getUpdatedAt());

		log.info("VALIDATING that our change was applied:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expect("job reflects the update", request.getJob(), updated.getJob())
				.expectTrue("updatedAt timestamp is present", updated.getUpdatedAt() != null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: user reflects the updated job =====");
	}

	@Test(groups = TestGroups.API)
	@Description("DELETE /api/users/2 removes the resource and returns 204 No Content")
	public void deleteUser() {
		log.info("===== SCENARIO: Delete a user =====");
		log.info("REQUEST : DELETE /api/users/2  (remove the user whose id is 2)");

		Response response = userService.deleteUser(2);
		logResponseSummary(log, response);

		log.info("VALIDATING that the delete returned No Content:");
		new ApiValidator(log)
				.expect("HTTP status code (204 No Content)", 204, response.statusCode())
				.expectTrue("response body is empty", response.getBody().asString().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: delete accepted with 204 and no body =====");
	}
}
