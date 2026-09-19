package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Unified response for register/login. Success carries {id?, token} (login omits id); failure
 * carries {error}. Modelling all three in one tolerant POJO lets both positive and negative tests
 * deserialize the same type and assert on whichever field is relevant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {

	private Integer id;
	private String token;
	private String error;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}
}
