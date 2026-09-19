package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * The "support" block reqres.in appends to most responses. Modelled so tests can assert the
 * envelope is present, demonstrating validation of nested objects, not just top-level fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Support {

	private String url;
	private String text;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
