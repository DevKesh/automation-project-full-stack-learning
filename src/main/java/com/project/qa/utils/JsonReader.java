package com.project.qa.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.qa.data.SearchData;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JsonReader {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String FILE_PATH = "src/test/resources/searchData.json";

	// Method 1: Using POJO (Type-Safe)
	public static List<SearchData> getSearchDataPojo() throws IOException {
		return mapper.readValue(
				new File(FILE_PATH), new TypeReference<>() {}
		);
	}

	// Method 2: Using Map (Untyped / Dynamic)
	public static List<Map<String, Object>> getSearchDataMap() throws IOException {
		return mapper.readValue(
				new File(FILE_PATH), new TypeReference<>() {}
		);
	}
}