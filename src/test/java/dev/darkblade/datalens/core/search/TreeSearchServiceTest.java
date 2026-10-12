package dev.darkblade.datalens.core.search;

import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeSearchServiceTest {

    private TreeSearchService searchService;
    private DataNode root;

    @BeforeEach
    void setUp() {
        searchService = new TreeSearchService();

        root = DataNode.ofCompound("root");
        root.addChild(DataNode.ofPrimitive("amount", DataType.INT, 1));

        DataNode components = DataNode.ofCompound("components");
        components.addChild(DataNode.ofPrimitive("minecraft:item_model", DataType.STRING, "craftlab:weapons/katana"));
        components.addChild(DataNode.ofPrimitive("minecraft:damage", DataType.INT, 15));

        DataNode customData = DataNode.ofCompound("minecraft:custom_data");
        customData.addChild(DataNode.ofPrimitive("craftlab:mana", DataType.INT, 100));
        customData.addChild(DataNode.ofPrimitive("craftlab:rarity", DataType.STRING, "MYTHIC"));
        components.addChild(customData);

        root.addChild(components);
    }

    @Test
    void search_findsByKeyCaseInsensitive() {
        List<TreeSearchService.SearchResult> results = searchService.search(root, "model");

        assertEquals(1, results.size());
        assertEquals("components.minecraft:item_model", results.get(0).path());
        assertEquals("craftlab:weapons/katana", results.get(0).node().getValue());
    }

    @Test
    void search_findsByValueCaseInsensitive() {
        List<TreeSearchService.SearchResult> results = searchService.search(root, "mythic");

        assertEquals(1, results.size());
        assertEquals("components.minecraft:custom_data.craftlab:rarity", results.get(0).path());
        assertEquals("MYTHIC", results.get(0).node().getValue());
    }

    @Test
    void search_findsMultipleMatchesAcrossDepths() {
        List<TreeSearchService.SearchResult> results = searchService.search(root, "craftlab");

        // matches:
        // 1. components.minecraft:item_model (value: craftlab:weapons/katana)
        // 2. components.minecraft:custom_data.craftlab:mana (key: craftlab:mana)
        // 3. components.minecraft:custom_data.craftlab:rarity (key: craftlab:rarity)
        assertEquals(3, results.size());
    }

    @Test
    void search_returnsEmpty_whenNoMatch() {
        List<TreeSearchService.SearchResult> results = searchService.search(root, "nonexistent");
        assertTrue(results.isEmpty());
    }

    @Test
    void search_returnsEmpty_whenQueryBlank() {
        assertTrue(searchService.search(root, "").isEmpty());
        assertTrue(searchService.search(root, "   ").isEmpty());
        assertTrue(searchService.search(root, null).isEmpty());
    }
}
