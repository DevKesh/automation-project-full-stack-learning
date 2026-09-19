package com.project.qa.testsupport.api.models.reqres;

/*
 * Request body for POST/PUT /api/users — reqres only reads {name, job}. A dedicated request POJO
 * (separate from the richer response models) keeps the sent payload explicit and minimal.
 */
public class CreateUserRequest {

	private String name;
	private String job;

	public CreateUserRequest() {
	}

	public CreateUserRequest(String name, String job) {
		this.name = name;
		this.job = job;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getJob() {
		return job;
	}

	public void setJob(String job) {
		this.job = job;
	}
}
