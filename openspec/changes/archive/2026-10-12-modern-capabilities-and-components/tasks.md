# Tasks

## 1. Item Data Components Adapter (1.20.5–1.21.4+)

- [x] 1.1 Implement component container reader in `Paper121Adapter` extracting NBT-API components compound into a first-class `components` DataNode tree; verify with unit tests checking component node mapping and nested `custom_data`.
- [x] 1.2 Implement component write-back in `Paper121Adapter.writeData` mapping modified `components` nodes back to `NBTItem` and applying to `ItemStack`; verify with unit tests asserting component mutations persist back to item metadata.

## 2. Staged In-Memory Editing and Session Undo/Redo Engine

- [x] 2.1 Enhance `PlayerSession` with `undoStack`, `redoStack`, dirty state, and configurable stack capacity limit; verify with unit tests checking push, pop, undo, redo, and capacity truncation.
- [x] 2.2 Refactor `EditService` to isolate mutations to `workingCopy`, pushing pre-mutation snapshots to `undoStack`, and implement `apply(...)` for explicit persistence to live target; verify with unit tests asserting live target remains unmodified until apply.
- [x] 2.3 Implement `/data undo`, `/data redo`, `/data reset`, and `/data apply` subcommands and tab-completions in `DataCommand`; verify execution flow and error handling.
- [x] 2.4 Add Undo and Redo navigation buttons to `InspectorGui` and wire click handlers in `GuiListener`; verify items render in the navigation bar with available step counts in lore.

## 3. Deep Tree Search and Flattened Filter View

- [x] 3.1 Implement recursive crawler in `TreeSearchService` searching keys and primitive values with full dot-paths; verify with unit tests covering deep compound searches and case-insensitive matching.
- [x] 3.2 Add `/data search <query>` command outputting clickable matching paths and values to chat; verify command formatting and zero-result message.
- [x] 3.3 Add Search and Clear Filter icons to `InspectorGui` and integrate chat query interceptor in `GuiListener`; verify session enters filter view upon query submission.
- [x] 3.4 Implement flattened paginated rendering in `InspectorGui` when filter query is active; verify matching items render with path lore in slots 9–44.

## 4. Multi-Format Export Engine (SNBT + Disk File System)

- [x] 4.1 Implement `toSnbt(DataNode)` in `SerializationService` producing valid Minecraft Stringified NBT with scalar suffixes (`b`, `s`, `L`, `f`, `d`) and typed arrays (`[B;]`, `[I;]`, `[L;]`); verify with comprehensive unit tests for all DataType values.
- [x] 4.2 Implement disk export manager writing to `plugins/DataLens/exports/{filename}` with filename sanitization and traversal prevention; verify with unit tests testing path sanitization and disk writing.
- [x] 4.3 Add smart chat truncation (>20 lines) to `/data export` and support `/data export <format> file [name]`; verify with tests checking line counting and disk routing.
- [x] 4.4 Add Shift+Left-click handler on the GUI export button in `GuiListener` to trigger instant disk export; verify notification message and file creation.

## 5. End-to-End Verification and Integration

- [x] 5.1 Run full Gradle test suite (`./gradlew test`) and shadow build (`./gradlew shadowJar`) verifying compilation and all test assertions pass.
