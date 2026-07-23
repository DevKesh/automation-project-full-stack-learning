package com.project.qa.data;

public class SearchData {

	// 1. Private field
	private String keyword;

	// 2. Default constructor (Jackson requires this to build the object)
	public SearchData() {

	}
	// 3. Getters and Setters
	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}


}
