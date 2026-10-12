package dev.darkblade.datalens.core.export;

import dev.darkblade.datalens.core.serialize.SerializationService;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiskExportServiceTest {

    @TempDir
    Path tempDir;

    private DiskExportService exportService;
    private SerializationService serializer;

    @BeforeEach
    void setUp() {
        serializer = new SerializationService();
        exportService = new DiskExportService(tempDir.toFile(), serializer);
    }

    @Test
    void sanitizeFileName_stripsTraversalAndIllegalChars() {
        assertEquals("____etc_passwd", exportService.sanitizeFileName("../../etc/passwd"));
        assertEquals("valid_file_name", exportService.sanitizeFileName("valid:file*name"));
        assertEquals("item_123.test", exportService.sanitizeFileName("item:123.test"));
        assertEquals("export", exportService.sanitizeFileName(""));
        assertEquals("export", exportService.sanitizeFileName(null));
    }

    @Test
    void exportToFile_createsFileInExportsDirectory() throws IOException {
        DataNode root = DataNode.ofCompound("root");
        root.addChild(DataNode.ofPrimitive("score", DataType.INT, 100));

        File exportedJson = exportService.exportToFile(root, "json", "my_export");
        assertTrue(exportedJson.exists());
        assertEquals("my_export.json", exportedJson.getName());

        String jsonContent = Files.readString(exportedJson.toPath());
        assertTrue(jsonContent.contains("100"));

        File exportedSnbt = exportService.exportToFile(root, "snbt", "my_snbt");
        assertTrue(exportedSnbt.exists());
        assertEquals("my_snbt.snbt", exportedSnbt.getName());

        String snbtContent = Files.readString(exportedSnbt.toPath());
        assertTrue(snbtContent.contains("score:100"));
    }

    @Test
    void generateDefaultName_createsNonEmptyString() {
        String name = exportService.generateDefaultName("ITEM", "diamond_sword");
        assertNotNull(name);
        assertTrue(name.startsWith("ITEM_diamond_sword_"));
    }
}
