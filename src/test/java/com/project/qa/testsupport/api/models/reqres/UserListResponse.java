package com.project.qa.testsupport.api.models.reqres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/*
 * Envelope for GET /api/users?page=N. Carries the pagination metadata (page/per_page/total/
 * total_pages) alongside the page's "data" list — the canonical shape for asserting paginated APIs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserListResponse {

	private int page;

	@JsonProperty("per_page")
	private int perPage;

	private int total;

	@JsonProperty("total_pages")
	private int totalPages;

	private List<User> data;
	private Support support;

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getPerPage() {
		return perPage;
	}

	public void setPerPage(int perPage) {
		this.perPage = perPage;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	public List<User> getData() {
		return data;
	}

	public void setData(List<User> data) {
		this.data = data;
	}

	public Support getSupport() {
		return support;
	}

	public void setSupport(Support support) {
		this.support = support;
	}
}
