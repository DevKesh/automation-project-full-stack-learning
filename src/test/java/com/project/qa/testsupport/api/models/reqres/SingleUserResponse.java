package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * Envelope for GET /api/users/{id}: the user sits under "data", with a sibling "support" block.
 * Deserializing the whole envelope (rather than reaching in with JsonPath) keeps the nesting typed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SingleUserResponse {

	private User data;
	private Support support;

	public User getData() {
		return data;
	}

	public void setData(User data) {
		this.data = data;
	}

	public Support getSupport() {
		return support;
	}

	public void setSupport(Support support) {
		this.support = support;
	}
}
