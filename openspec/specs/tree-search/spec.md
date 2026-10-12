# tree-search Specification

## Purpose

Enables real-time deep recursive searching and filtering of inspected data trees across chat and GUI interfaces.

## Requirements

### Requirement: Recursive Data Tree Search via Chat
The system SHALL provide a `/data search <query>` command that crawls the active session's DataNode tree recursively to match keys and primitive string representations.

#### Scenario: Searching for a key across nested compounds
- **WHEN** a player executes `/data search model` on an inspected item
- **THEN** the system returns a list of all matching nodes with their complete dot-notation paths and current values

#### Scenario: No search matches found
- **WHEN** a player executes `/data search nonexistent_key`
- **THEN** the system responds with a message indicating zero matches were found for the query

### Requirement: Interactive Search in Inspector GUI
The Inspector GUI SHALL provide a search action button that prompts the player via chat to supply a search term.

#### Scenario: Initiating GUI search
- **WHEN** a player clicks the Search button in the Inspector GUI
- **THEN** the GUI closes or enters prompt mode, sending a chat prompt asking the player to type their search query

#### Scenario: Submitting search query via chat
- **WHEN** a player types a search query in response to the search prompt
- **THEN** the system reopens the GUI in Filter View populated with matching results

### Requirement: Flattened Filter Mode View
When a search filter is active in the GUI, the system SHALL render all matching nodes as a flattened paginated list displaying each node's full hierarchical path.

#### Scenario: Viewing filtered search results in GUI
- **WHEN** the GUI renders under an active search filter
- **THEN** matching nodes from any tree depth appear in slots 9–44 with their full path displayed in the item lore

### Requirement: Filter Clearing and Restoration
The GUI and command interface SHALL provide a mechanism to clear active search filters and return to the normal hierarchical tree navigation.

#### Scenario: Clearing GUI search filter
- **WHEN** a player clicks the Clear Filter button in the GUI
- **THEN** the search filter is cleared from the session and the GUI re-renders at the current hierarchical level
