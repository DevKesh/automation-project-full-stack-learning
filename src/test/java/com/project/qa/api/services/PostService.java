package com.project.qa.api.services;

import com.project.qa.api.ApiSpecFactory;
import com.project.qa.api.models.Post;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/*
 * Endpoint object for the /posts resource — the API-layer analogue of a Page Object.
 *
 * It hides HTTP verbs and paths behind intent-revealing methods so tests read as business actions,
 * not transport details. Each method is an Allure @Step, giving the report a readable call timeline.
 * Assertions deliberately live in the tests, keeping this layer a thin, reusable transport.
 */
public class PostService {

	private static final String POSTS = "/posts";
	private static final String POST_BY_ID = "/posts/{id}";

	@Step("GET all posts")
	public Response getAllPosts() {
		return given().spec(ApiSpecFactory.spec())
				.when().get(POSTS);
	}

	@Step("GET post by id {id}")
	public Response getPost(int id) {
		return given().spec(ApiSpecFactory.spec())
				.pathParam("id", id)
				.when().get(POST_BY_ID);
	}

	@Step("POST create a new post")
	public Response createPost(Post post) {
		return given().spec(ApiSpecFactory.spec())
				.body(post)
				.when().post(POSTS);
	}

	@Step("PUT update post id {id}")
	public Response updatePost(int id, Post post) {
		return given().spec(ApiSpecFactory.spec())
				.pathParam("id", id)
				.body(post)
				.when().put(POST_BY_ID);
	}

	@Step("DELETE post id {id}")
	public Response deletePost(int id) {
		return given().spec(ApiSpecFactory.spec())
				.pathParam("id", id)
				.when().delete(POST_BY_ID);
	}
}
