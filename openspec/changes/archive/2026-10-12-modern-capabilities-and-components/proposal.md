# Proposal: Modern Capabilities and Item Data Components

## Why

Minecraft 1.20.5+ and 1.21.2–1.21.4+ fundamentally restructured items from legacy arbitrary NBT tags into structured Data Components (such as `item_model`, `custom_data`, `damage`, and complex `custom_model_data`). DataLens currently relies on a legacy 1.20 adapter structure that lacks deep component extraction and editing.

Additionally, data inspection in complex objects suffers from critical workflow friction:
- Edits were committed immediately to the world with no voluntary undo/redo safety net or staging preview.
- Large entity/block/item trees with hundreds of tags lack search or filtering capabilities in both chat and the chest GUI.
- Exports are limited to JSON and YAML printed directly to chat, causing chat flooding and truncation while lacking Minecraft's canonical SNBT format and file system persistence.

Modernizing DataLens with these four capabilities equips server operators and developers with a robust, production-grade inspection, staging, search, and export pipeline.

## What Changes

- **Item Data Components Support (1.21.2–1.21.4+)**: Dynamically inspect and edit Item Data Components via a hybrid NBT-API / NBTContainer strategy under a first-class `components` node, decoupling DataLens from compile-time component enums while supporting modern mechanics like `item_model`.
- **Staging / Draft Mode with Undo & Redo**: Decouple live world persistence from session editing:
  - Edits mutate `workingCopy` in memory.
  - Maintain an in-memory Memento history stack (`undoStack` and `redoStack`) per player session.
  - Add `/data undo`, `/data redo`, `/data reset`, and `/data apply` (which commits staged changes to the live target in the world).
  - Add Undo/Redo controls directly into the Inspector GUI.
- **Deep Tree Search and Filtering**:
  - Add `/data search <query>` (or `/data filter <query>`) in chat to crawl the tree recursively and print exact dot-paths and values with interactive click-to-edit actions.
  - Add Search button in the Inspector GUI, prompting the player in chat for the search term, switching the GUI into a flattened "Filter Mode" with breadcrumbs/paths and a "Clear Filter" button.
- **SNBT Format & File System Export Engine**:
  - Implement full Stringified NBT (SNBT) serialization honoring Minecraft types (`b`, `s`, `L`, `f`, `d`, arrays `[B; ]`, `[I; ]`, `[L; ]`, escaped strings).
  - Add disk export destination writing to `plugins/DataLens/exports/{name}.{ext}` with strict path sanitization.
  - Implement smart chat truncation for exports exceeding 20 lines with guidance to export to disk.
  - Support direct file export via Shift+Click on the GUI export button and `/data export <format> file [name]`.

## Capabilities

### New Capabilities
- `item-data-components`: Dynamic extraction and modification of Minecraft 1.20.5 - 1.21.4+ Item Data Components using a generic container model.
- `session-history`: Staging/draft edit lifecycle with configurable undo/redo history stacks, rollback, and explicit world commit (`/data apply`).
- `tree-search`: Recursive deep searching and filtering across DataNode trees accessible via chat commands and a flattened GUI view.
- `export-pipeline`: Multi-format serialization (JSON, YAML, SNBT) supporting smart chat pagination and sanitized disk persistence under `exports/`.

### Modified Capabilities
*(None; this is the initial specification baseline for these capabilities).*

## Impact

- **Adapters**: `Paper121Adapter` enhanced to extract and serialize the `components` compound tree from/to `ItemStack` using NBT-API.
- **Session & Edit Core**: `PlayerSession` extended with undo/redo stacks and active search filter; `EditService` updated to support staging vs apply commit workflows.
- **UI (GUI & Chat)**: `InspectorGui` updated with Search, Undo, Redo, and Clear Filter slots; `GuiListener` extended to intercept search queries; `DataCommand` extended with `undo`, `redo`, `reset`, `apply`, `search`, and disk export arguments.
- **Serialization**: `SerializationService` augmented with `toSnbt(...)` and file system export utilities.
- **Configuration**: `config.yml` augmented with history limit (`session.max-history-states`), export directory settings, and chat truncation limits.
