# Design: Modern Capabilities and Item Data Components

## Context

DataLens uses a pluggable adapter architecture (`Adapter`), a per-player session manager (`PlayerSession`) backed by Caffeine, a tree-based data model (`DataNode`), a path resolution engine (`PathResolver`), an inventory GUI (`InspectorGui`), and serializers (`SerializationService`).

See `proposal.md` for the motivation and high-level scope. This document specifies the technical design for the four modernization pillars.

```text
+-------------------------------------------------------------------------------+
|                             DATALENS ARCHITECTURE                             |
+-------------------------------------------------------------------------------+
|  UI & CLI                                                                     |
|    * DataCommand (/data apply, undo, redo, reset, search, export)             |
|    * InspectorGui (Navbar: Undo, Redo, Search, Clear Filter, Export)          |
|    * GuiListener (Awaiting search & edit chat inputs)                         |
+-------------------------------------------------------------------------------+
                                        │
                                        ▼
+-------------------------------------------------------------------------------+
|  Session & Core Logic                                                         |
|    * PlayerSession: target (live), workingCopy (staged), undoStack, redoStack  |
|    * EditService: mutates workingCopy; applies to live target on /data apply  |
|    * TreeSearch: recursive crawler -> List<SearchResultNode(path, node)>     |
+-------------------------------------------------------------------------------+
                                        │
                                        ▼
+-------------------------------------------------------------------------------+
|  Adapters & Serialization                                                     |
|    * Paper121Adapter: hybrid NBT-API item components extraction & write-back |
|    * SerializationService: JSON, YAML, and custom recursive SNBT serializer   |
|    * DiskExporter: plugins/DataLens/exports/{filename} with path sanitization |
+-------------------------------------------------------------------------------+
```

## Goals / Non-Goals

**Goals:**
- Dynamically extract and mutate 1.20.5–1.21.4+ Item Data Components using NBT-API without hardcoded enum dependencies.
- Isolate edits to `workingCopy` (draft mode) with bounded Memento undo/redo stacks, committing to the live world only on `/data apply`.
- Provide recursive deep searching across both chat and the chest GUI with a flattened result view.
- Support Minecraft SNBT formatting and safe file exports in `plugins/DataLens/exports/` with smart chat truncation (>20 lines).

**Non-Goals:**
- Overhauling block or entity inspection pipelines (they continue utilizing PDC and block states).
- Implementing an Anvil GUI packet library (native Bukkit chat prompts are used to avoid protocol hack dependencies).
- Implementing web endpoints or remote sync mechanisms.

## Decisions

### 1. Component Adapter Strategy: Option C (Hybrid NBT-API Container)
- **Decision**: In `Paper121Adapter`, items are inspected via `NBTItem`. The `components` compound (or equivalent 1.20.5+ container) is traversed recursively to construct a first-class `DataNode` under `item.components`. On write-back (`writeData`), if `components` has been modified, the component values are written back to `NBTItem` and synced to the `ItemStack`.
- **Rationale**: Decouples the plugin from compile-time Minecraft/Paper version enums. New components introduced in minor Minecraft releases (e.g., 1.21.4's complex `custom_model_data`, `item_model`) are automatically discovered and editable.
- **Alternatives Considered**:
  - *Paper API direct compilation*: Would require updating the build toolchain to 1.21.4 Paper API and breaks backwards compilation with Spigot 1.20.4.
  - *Pure reflection on NMS DataComponentMap*: Extremely fragile across Paper build updates.

### 2. Staged In-Memory Editing (Draft Mode) and Memento Stacks
- **Decision**:
  - In `PlayerSession`, `workingCopy` is the single source of truth for GUI display, path resolution, and tab completion.
  - Edits via `/data set`, `/data remove`, or GUI modify `workingCopy` in memory. Before mutation, `workingCopy.deepCopy()` is pushed onto `undoStack`, and `redoStack.clear()`.
  - `undoStack` and `redoStack` are bounded (default 20 entries, configurable via `session.max-history-states`).
  - `/data undo` pops from `undoStack`, pushes current state to `redoStack`, and replaces `workingCopy`.
  - `/data redo` pops from `redoStack`, pushes current state to `undoStack`, and replaces `workingCopy`.
  - `/data reset` discards `workingCopy` and re-clones from `target.getRoot()`.
  - `/data apply` validates `workingCopy`, executes `adapter.writeData(target, workingCopy)`, updates the target snapshot, and logs the commit to `ChangeLogRepository`.
- **Rationale**: Prevents accidental live world corruption while allowing server admins to make multiple tweaks and inspect differences via `/data diff` prior to committing.
- **Alternatives Considered**:
  - *Immediate commit on edit*: Current behavior, but makes undo impossible if a previous state caused an irreversible entity modification.

### 3. Deep Tree Traversal for Search and Flattened Filter View
- **Decision**:
  - The crawler performs a depth-first search on `workingCopy`. A match occurs if a node's key contains the query (case-insensitive) or if a primitive node's value string representation contains the query.
  - In Chat: `/data search <query>` returns matching dot-paths with clickable suggestions (`/data set <path> `).
  - In GUI: Clicking the Search icon prompts the player in chat (`awaitingSearch` map in `GuiListener`). Upon receiving the query, `PlayerSession.setFilterQuery(query)` is set and the GUI reopens.
  - In Filter Mode: Items in slots 9–44 display matching nodes directly. Lore displays the absolute path (`Path: components.minecraft:item_model`). A "Clear Filter" button in the navigation bar resets the view to the tree hierarchy.
- **Rationale**: Deep trees (e.g. nested PDC or components) are impossible to navigate across 54-slot inventories without an indexing search tool.

### 4. SNBT Serializer and Safe Disk Exporter
- **Decision**:
  - `SerializationService.toSnbt(DataNode)` recursively serializes the tree into valid Stringified NBT:
    - Primitives: `Byte` $\to$ `<val>b`, `Short` $\to$ `<val>s`, `Long` $\to$ `<val>L`, `Float` $\to$ `<val>f`, `Double` $\to$ `<val>d`, `Boolean` $\to$ `true/false`, `String` $\to$ escaped quoted string.
    - Arrays: `[B; ...]`, `[I; ...]`, `[L; ...]`.
    - Compounds: `{k1:v1, k2:v2}`, Lists: `[v1, v2]`.
  - Disk storage: Written to `<dataFolder>/exports/<filename>`.
  - Filename resolution: User-provided name or timestamped default (`{type}_{id}_{yyyyMMdd_HHmmss}.{ext}`). Sanitization replaces any character outside `[a-zA-Z0-9._-]` with `_` and strips `..` to guarantee zero directory traversal.
  - Chat truncation: If output exceeds 20 lines, the first 15 lines are displayed with a notice: `"[DataLens] Truncated (total N lines). Use /data export <format> file [name] to save to disk."`
  - In GUI: Left-click on export sends to chat; Shift+Left-click exports directly to disk.

## Risks / Trade-offs

- **[Target Invalidated During Draft Session]** $\to$ *Mitigation*: If an entity dies or a block is broken while a player has uncommitted edits, `/data apply` checks `target.isValid()` and gracefully aborts with an error instead of crashing.
- **[Memory Consumption from Deep-Copy Stacks]** $\to$ *Mitigation*: Bounded deques (`max-history-states: 20`). Caffeine cache automatically evicts inactive sessions and their stacks on TTL expiration.
- **[Path Traversal Vulnerability in Export Filenames]** $\to$ *Mitigation*: Strict regex sanitization stripping slashes and path-relative tokens, resolving canonical paths to guarantee they reside within the `exports/` subfolder.
- **[Large Output Chat Spam]** $\to$ *Mitigation*: Hard threshold of 20 lines for chat outputs, routing complete payloads to disk exports.
