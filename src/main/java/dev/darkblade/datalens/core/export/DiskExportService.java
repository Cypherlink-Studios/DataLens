package dev.darkblade.datalens.core.export;

import dev.darkblade.datalens.core.serialize.SerializationService;
import dev.darkblade.datalens.model.DataNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Manages exporting serialized DataNode trees to the disk under {@code plugins/DataLens/exports/}.
 * Guarantees filename sanitization and traversal prevention.
 */
public final class DiskExportService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final File exportsDir;
    private final SerializationService serializer;

    public DiskExportService(File dataFolder, SerializationService serializer) {
        Objects.requireNonNull(dataFolder, "dataFolder must not be null");
        this.exportsDir = new File(dataFolder, "exports");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    public File getExportsDir() {
        return exportsDir;
    }

    /**
     * Sanitizes a requested filename base, stripping path traversal tokens and invalid characters.
     */
    public String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) {
            return "export";
        }
        // Remove slashes and illegal filesystem characters
        String cleaned = input.replaceAll("[/\\\\:?*\"<>|]", "_")
                .replaceAll("\\.{2,}", "_")
                .trim();
        // Keep only alphanumeric, underscores, hyphens, dots
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isEmpty() ? "export" : cleaned;
    }

    /**
     * Generates a safe default filename using target type, target id, and current timestamp.
     */
    public String generateDefaultName(String targetType, String targetId) {
        String safeType = sanitizeFileName(targetType);
        String safeId = sanitizeFileName(targetId);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return safeType + "_" + safeId + "_" + timestamp;
    }

    /**
     * Exports a DataNode tree to a file in the exports directory.
     *
     * @param node     the data root to serialize
     * @param format   "json", "yaml", or "snbt"
     * @param baseName user requested filename or null for default
     * @return the written file
     * @throws IOException if disk write fails
     */
    public File exportToFile(DataNode node, String format, String baseName) throws IOException {
        String ext = format != null ? format.toLowerCase().trim() : "json";
        if (!ext.equals("json") && !ext.equals("yaml") && !ext.equals("snbt")) {
            ext = "json";
        }

        String safeBase = sanitizeFileName(baseName);
        if (safeBase.toLowerCase().endsWith("." + ext)) {
            safeBase = safeBase.substring(0, safeBase.length() - (ext.length() + 1));
        }
        String fileName = safeBase + "." + ext;

        if (!exportsDir.exists()) {
            exportsDir.mkdirs();
        }

        File targetFile = new File(exportsDir, fileName);

        // Security check: verify canonical path containment
        Path exportsCanonical = exportsDir.toPath().toAbsolutePath().normalize();
        Path targetCanonical = targetFile.toPath().toAbsolutePath().normalize();
        if (!targetCanonical.startsWith(exportsCanonical)) {
            throw new SecurityException("Directory traversal detected: file must be located inside exports directory.");
        }

        String content = switch (ext) {
            case "yaml" -> serializer.toYaml(node);
            case "snbt" -> serializer.toSnbt(node);
            default     -> serializer.toJson(node);
        };

        Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
        return targetFile;
    }
}
