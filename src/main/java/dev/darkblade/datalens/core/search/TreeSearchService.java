package dev.darkblade.datalens.core.search;

import dev.darkblade.datalens.model.DataNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recursively crawls a {@link DataNode} tree to find nodes matching a search query
 * against keys or stringified primitive values.
 */
public final class TreeSearchService {

    /**
     * Represents a single search match holding its dot-notation path and the matched node.
     */
    public record SearchResult(String path, DataNode node) {}

    /**
     * Searches recursively within the given tree root.
     *
     * @param root  the tree root to crawl
     * @param query case-insensitive search term
     * @return list of matching results with absolute dot-paths
     */
    public List<SearchResult> search(DataNode root, String query) {
        if (root == null || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String lowerQuery = query.trim().toLowerCase();
        List<SearchResult> results = new ArrayList<>();
        crawl(root, "", lowerQuery, results);
        return results;
    }

    private void crawl(DataNode current, String currentPath, String query, List<SearchResult> results) {
        for (DataNode child : current.getChildren()) {
            String childPath = currentPath.isEmpty() ? child.getKey() : currentPath + "." + child.getKey();

            boolean keyMatch = child.getKey().toLowerCase().contains(query);
            boolean valueMatch = child.getValue() != null &&
                    String.valueOf(child.getValue()).toLowerCase().contains(query);

            if (keyMatch || valueMatch) {
                results.add(new SearchResult(childPath, child));
            }

            if (child.getType().isContainer()) {
                crawl(child, childPath, query, results);
            }
        }
    }
}
