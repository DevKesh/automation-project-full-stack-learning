package com.project.qa.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Type-safe view of the /posts resource. Mirrors the framework's existing POJO-over-string-parsing
 * discipline (see SearchData): REST Assured deserializes responses straight into this via Jackson,
 * so tests assert on real fields instead of brittle JSON path strings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Post {

	private int userId;
	private int id;
	private String title;
	private String body;

	public Post() {
	}

	public Post(int userId, String title, String body) {
		this.userId = userId;
		this.title = title;
		this.body = body;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
