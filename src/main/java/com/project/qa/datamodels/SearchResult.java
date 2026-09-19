package com.project.qa.datamodels;

/**
 * Immutable snapshot of a single product card from a Myntra search results grid.
 *
 * A record (not a mutable POJO) because a captured result is read-only test evidence: once the page
 * layer reads a card, the values must not drift. Kept in the data package alongside {@link SearchData}
 * so the test layer consumes one cohesive result-object vocabulary.
 */
public record SearchResult(String brand, String name, String price) {
}
