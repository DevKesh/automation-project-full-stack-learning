package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonInclude;

/*
 * Request body for /api/register and /api/login: {email, password}. JsonInclude.NON_NULL omits a
 * null field entirely, so a negative test can send email-only and reliably trigger the API's
 * "Missing password" 400 instead of sending password:null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthRequest {

	private String email;
	private String password;

	public AuthRequest() {
	}

	public AuthRequest(String email, String password) {
		this.email = email;
		this.password = password;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
