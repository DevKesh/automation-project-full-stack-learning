package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Shared response for the write endpoints: POST returns {name, job, id, createdAt} and PUT returns
 * {name, job, updatedAt}. One tolerant POJO covers both — the timestamp not returned is simply null.
 *
 * NOTE: reqres serialises the created "id" as a STRING ("260"), not a number, so it is typed as
 * String here. This is a deliberate, real-world reminder that response types must match the wire.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMutationResponse {

	private String name;
	private String job;
	private String id;
	private String createdAt;
	private String updatedAt;

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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}
}
