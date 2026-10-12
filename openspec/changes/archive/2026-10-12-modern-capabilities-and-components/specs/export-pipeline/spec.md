# Spec Delta: export-pipeline

## Purpose

Serializes inspected data structures into portable formats (JSON, YAML, and SNBT) with smart chat preview truncation and safe file system disk exports.

## ADDED Requirements

### Requirement: Stringified NBT (SNBT) Serialization
The export engine SHALL serialize DataNode trees into standard Minecraft Stringified NBT (SNBT) format adhering to typed scalar suffixes and array tags.

#### Scenario: Serializing compounds and primitives to SNBT
- **WHEN** an export in SNBT format is requested
- **THEN** integers render as plain numbers, bytes as `<val>b`, shorts as `<val>s`, longs as `<val>L`, floats as `<val>f`, doubles as `<val>d`, and string keys/values are escaped according to SNBT syntax

#### Scenario: Serializing array types to SNBT
- **WHEN** a node contains a byte array, int array, or long array
- **THEN** it serializes to `[B; ...]`, `[I; ...]`, or `[L; ...]` prefix format respectively

### Requirement: File System Disk Export Destination
The export engine SHALL allow saving serialized data trees directly into the server filesystem under `plugins/DataLens/exports/{filename}`.

#### Scenario: Exporting to disk with custom name
- **WHEN** a player executes `/data export json file custom_report`
- **THEN** the system writes the serialized file to `plugins/DataLens/exports/custom_report.json` and notifies the player with file path and byte size

#### Scenario: Exporting to disk with default generated name
- **WHEN** a player executes `/data export snbt file` without specifying a name
- **THEN** the system generates a timestamped name using target type and target identifier and saves the file to `plugins/DataLens/exports/`

### Requirement: Path Sanitization and Traversal Protection
The disk exporter SHALL sanitize user-supplied filenames and prohibit path traversal sequences.

#### Scenario: Preventing directory traversal attempts
- **WHEN** a player supplies a filename containing `../` or invalid path characters
- **THEN** the exporter strips or replaces traversal characters ensuring files are written strictly inside the `exports/` folder

### Requirement: Smart Chat Preview Truncation
When printing exports to in-game chat, the system SHALL truncate large payloads exceeding 20 lines to prevent chat flooding.

#### Scenario: Exporting large payload to chat
- **WHEN** an export rendered for chat exceeds 20 lines
- **THEN** the system prints a preview of the initial lines followed by a truncation warning and instructions for disk export

### Requirement: GUI Quick Disk Export
The Inspector GUI export button SHALL support secondary click actions to trigger instant disk exports.

#### Scenario: Shift-clicking export button in GUI
- **WHEN** a player Shift+Left-clicks the export button in the Inspector GUI
- **THEN** the system exports the current tree directly to disk without dumping lines to chat and sends a confirmation message with the generated file name
