package com.project.qa.tests.api;

import com.project.qa.api.ApiValidator;
import com.project.qa.api.models.Post;
import com.project.qa.api.services.PostService;
import com.project.qa.constants.TestGroups;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.List;

/*
 * End-to-end contract coverage for the /posts resource against JSONPlaceholder.
 *
 * Each test tells a story in the log: the request sent, the actual response data received, and
 * every validation with its expected-vs-actual outcome. The same detail is captured per-test in
 * the Allure report (request/response bodies + a step per validation) via the shared spec.
 */
@Epic("API Testing")
@Feature("Posts resource (/posts)")
public class PostApiTest extends ApiBaseTest {

	private static final Logger log = LoggerFactory.getLogger(PostApiTest.class);
	private static final long MAX_RESPONSE_MS = 5000;
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	private final PostService postService = new PostService();

	@Test(groups = TestGroups.API)
	@Description("GET /posts/1 returns 200 with the expected post payload")
	public void getSinglePost() {
		log.info("===== SCENARIO: Fetch a single post =====");
		log.info("REQUEST : GET /posts/1  (retrieve the post whose id is 1)");

		Response response = postService.getPost(1);
		logResponseSummary(response);
		logResponseBody(response);
		Post post = response.as(Post.class);
		log.info("RESPONSE DATA: id={}, userId={}, title=\"{}\"", post.getId(), post.getUserId(), post.getTitle());

		log.info("VALIDATING the response contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Content-Type is JSON (actual: " + response.contentType() + ")",
						response.contentType().contains("application/json"))
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.expect("post id in body", 1, post.getId())
				.expect("userId in body", 1, post.getUserId())
				.expectTrue("title is present", post.getTitle() != null && !post.getTitle().isBlank())
				.verifyAll();
		log.info("===== SCENARIO PASSED: single post matched the expected contract =====");
	}

	@Test(groups = TestGroups.API)
	@Description("GET /posts returns 200 with the full collection of 100 posts")
	public void getAllPosts() {
		log.info("===== SCENARIO: Fetch the whole posts collection =====");
		log.info("REQUEST : GET /posts  (retrieve every post)");

		Response response = postService.getAllPosts();
		logResponseSummary(response);
		List<Post> posts = List.of(response.as(Post[].class));
		log.info("RESPONSE DATA: received {} posts; first post -> id={}, title=\"{}\"",
				posts.size(), posts.get(0).getId(), posts.get(0).getTitle());
		log.info("RESPONSE BODY (first item of {}, rest omitted to keep the log readable):\n{}",
				posts.size(), prettyFirstElement(response));

		log.info("VALIDATING the response contract:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.expect("total number of posts returned", 100, posts.size())
				.expectTrue("first post has a title", posts.get(0).getTitle() != null)
				.verifyAll();
		log.info("===== SCENARIO PASSED: collection returned all 100 posts =====");
	}

	@Test(groups = TestGroups.API)
	@Description("POST /posts creates a resource and echoes it back with a new id (201)")
	public void createPost() {
		Post newPost = new Post(7, "Automation Contract", "Validating REST Assured integration");
		log.info("===== SCENARIO: Create a new post =====");
		log.info("REQUEST : POST /posts  with body -> userId={}, title=\"{}\", body=\"{}\"",
				newPost.getUserId(), newPost.getTitle(), newPost.getBody());

		Response response = postService.createPost(newPost);
		logResponseSummary(response);
		logResponseBody(response);
		Post created = response.as(Post.class);
		log.info("RESPONSE DATA: server assigned id={}, echoed title=\"{}\"", created.getId(), created.getTitle());

		log.info("VALIDATING that the server accepted and echoed our data:");
		new ApiValidator(log)
				.expect("HTTP status code (201 Created)", 201, response.statusCode())
				.expect("newly assigned id", 101, created.getId())
				.expect("echoed userId matches what we sent", newPost.getUserId(), created.getUserId())
				.expect("echoed title matches what we sent", newPost.getTitle(), created.getTitle())
				.expect("echoed body matches what we sent", newPost.getBody(), created.getBody())
				.verifyAll();
		log.info("===== SCENARIO PASSED: post created and payload echoed back correctly =====");
	}

	@Test(groups = TestGroups.API)
	@Description("PUT /posts/1 updates the resource and returns the modified payload (200)")
	public void updatePost() {
		Post update = new Post(1, "Updated Title", "Updated body content");
		log.info("===== SCENARIO: Update an existing post =====");
		log.info("REQUEST : PUT /posts/1  changing title to \"{}\" and body to \"{}\"",
				update.getTitle(), update.getBody());

		Response response = postService.updatePost(1, update);
		logResponseSummary(response);
		logResponseBody(response);
		Post updated = response.as(Post.class);
		log.info("RESPONSE DATA: id={}, title is now \"{}\"", updated.getId(), updated.getTitle());

		log.info("VALIDATING that our changes were applied:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expect("post id is unchanged", 1, updated.getId())
				.expect("title reflects the update", update.getTitle(), updated.getTitle())
				.expect("body reflects the update", update.getBody(), updated.getBody())
				.verifyAll();
		log.info("===== SCENARIO PASSED: post reflects the updated values =====");
	}

	@Test(groups = TestGroups.API)
	@Description("DELETE /posts/1 removes the resource and returns 200")
	public void deletePost() {
		log.info("===== SCENARIO: Delete a post =====");
		log.info("REQUEST : DELETE /posts/1  (remove the post whose id is 1)");

		Response response = postService.deletePost(1);
		logResponseSummary(response);
		logResponseBody(response);

		log.info("VALIDATING that the delete was accepted:");
		new ApiValidator(log)
				.expect("HTTP status code", 200, response.statusCode())
				.expectTrue("Response time under " + MAX_RESPONSE_MS + "ms (actual: " + response.time() + "ms)",
						response.time() < MAX_RESPONSE_MS)
				.verifyAll();
		log.info("===== SCENARIO PASSED: delete request accepted with 200 =====");
	}

	// Consistent one-line summary of every response so the log reads like a transaction ledger.
	private void logResponseSummary(Response response) {
		log.info("RESPONSE: status={} ({}), timeMs={}, contentType={}",
				response.statusCode(), response.statusLine(), response.time(), response.contentType());
	}

	// Full pretty-printed JSON body for single-resource responses, so the exact payload is visible.
	private void logResponseBody(Response response) {
		String body = response.getBody().asPrettyString();
		log.info("RESPONSE BODY:\n{}", body.isBlank() ? "(empty body)" : body);
	}

	// The collection response is large (100 items); showing only the first element keeps the log
	// readable while still revealing the exact JSON shape of a single record.
	private String prettyFirstElement(Response response) {
		try {
			Object firstElement = response.jsonPath().getList("$").get(0);
			return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(firstElement);
		} catch (Exception e) {
			// Never let log formatting mask the real assertions; fall back to the raw body.
			return response.getBody().asPrettyString();
		}
	}
}
