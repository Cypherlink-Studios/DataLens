package dev.darkblade.datalens.core.serialize;

import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SerializationServiceSnbtTest {

    private SerializationService serializer;

    @BeforeEach
    void setUp() {
        serializer = new SerializationService();
    }

    @Test
    void toSnbt_primitivesWithCorrectSuffixes() {
        DataNode root = DataNode.ofCompound("root");
        root.addChild(DataNode.ofPrimitive("b", DataType.BYTE, (byte) 12));
        root.addChild(DataNode.ofPrimitive("s", DataType.SHORT, (short) 300));
        root.addChild(DataNode.ofPrimitive("i", DataType.INT, 42));
        root.addChild(DataNode.ofPrimitive("l", DataType.LONG, 1000L));
        root.addChild(DataNode.ofPrimitive("f", DataType.FLOAT, 1.5f));
        root.addChild(DataNode.ofPrimitive("d", DataType.DOUBLE, 2.75d));
        root.addChild(DataNode.ofPrimitive("boolTrue", DataType.BOOLEAN, true));
        root.addChild(DataNode.ofPrimitive("boolFalse", DataType.BOOLEAN, false));

        String snbt = serializer.toSnbt(root);

        assertTrue(snbt.contains("b:12b"));
        assertTrue(snbt.contains("s:300s"));
        assertTrue(snbt.contains("i:42"));
        assertTrue(snbt.contains("l:1000L"));
        assertTrue(snbt.contains("f:1.5f"));
        assertTrue(snbt.contains("d:2.75d"));
        assertTrue(snbt.contains("boolTrue:1b"));
        assertTrue(snbt.contains("boolFalse:0b"));
    }

    @Test
    void toSnbt_arraysWithPrefixes() {
        DataNode root = DataNode.ofCompound("root");
        root.addChild(DataNode.ofPrimitive("byteArray", DataType.BYTE_ARRAY, new byte[]{1, 2}));
        root.addChild(DataNode.ofPrimitive("intArray", DataType.INT_ARRAY, new int[]{10, 20}));
        root.addChild(DataNode.ofPrimitive("longArray", DataType.LONG_ARRAY, new long[]{100L, 200L}));

        String snbt = serializer.toSnbt(root);

        assertTrue(snbt.contains("byteArray:[B;1b,2b]"));
        assertTrue(snbt.contains("intArray:[I;10,20]"));
        assertTrue(snbt.contains("longArray:[L;100L,200L]"));
    }

    @Test
    void toSnbt_quotesKeysWithColonAndEscapesStrings() {
        DataNode root = DataNode.ofCompound("root");
        DataNode components = DataNode.ofCompound("components");
        components.addChild(DataNode.ofPrimitive("minecraft:item_model", DataType.STRING, "craftlab:\"sword\""));
        root.addChild(components);

        String snbt = serializer.toSnbt(root);

        assertTrue(snbt.contains("\"minecraft:item_model\":\"craftlab:\\\"sword\\\"\""));
    }

    @Test
    void toSnbt_listFormatting() {
        DataNode root = DataNode.ofCompound("root");
        DataNode list = DataNode.ofList("tags");
        list.addChild(DataNode.ofPrimitive("[0]", DataType.STRING, "tag1"));
        list.addChild(DataNode.ofPrimitive("[1]", DataType.STRING, "tag2"));
        root.addChild(list);

        String snbt = serializer.toSnbt(root);
        assertTrue(snbt.contains("tags:[\"tag1\",\"tag2\"]"));
    }
}
