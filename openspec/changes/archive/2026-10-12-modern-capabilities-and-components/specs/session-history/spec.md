# Spec Delta: session-history

## Purpose

Manages in-memory inspection sessions with staged draft mutations, multi-step undo and redo history stacks, and explicit persistence to the live Minecraft world.

## ADDED Requirements

### Requirement: Staged In-Memory Editing
The session system SHALL isolate user modifications within a mutable `workingCopy` DataNode tree, preventing unintended writes to the live world target until explicitly applied.

#### Scenario: Editing in draft mode
- **WHEN** a player executes `/data set <path> <value>` or performs an edit via the GUI
- **THEN** the mutation updates `workingCopy` in memory while the live target in the world remains unmodified until committed

### Requirement: Undo and Redo History Stacks
Each active player session SHALL maintain an in-memory history stack of previous DataNode states up to a configurable maximum depth.

#### Scenario: Undoing an edit
- **WHEN** a player executes `/data undo` after making one or more changes
- **THEN** the current state is pushed onto the redo stack, the previous state is restored to `workingCopy`, and the player receives confirmation of the undone step

#### Scenario: Undoing when no history exists
- **WHEN** a player executes `/data undo` on an unedited session
- **THEN** no state change occurs and the system notifies the player that no actions are available to undo

#### Scenario: Redoing a previously undone edit
- **WHEN** a player executes `/data redo` after an undo operation
- **THEN** the next state from the redo stack is restored to `workingCopy` and the state is pushed back onto the undo stack

### Requirement: Explicit World Persistence and Revert
The system SHALL provide explicit commands to commit staged changes to the live object or discard all staged changes.

#### Scenario: Applying staged changes to the world
- **WHEN** a player executes `/data apply` with uncommitted changes
- **THEN** the system validates the working copy, writes the tree to the live target in the world, records the commit in the changelog, and marks the session as clean

#### Scenario: Reverting uncommitted changes
- **WHEN** a player executes `/data reset`
- **THEN** all uncommitted changes in `workingCopy` are discarded, the tree is refreshed from the live target, and history stacks are cleared

### Requirement: GUI History Controls
The Inspector GUI SHALL display interactive Undo and Redo buttons reflecting the current availability of history steps.

#### Scenario: Clicking Undo button in GUI
- **WHEN** a player clicks the Undo button in the GUI and undo steps are available
- **THEN** the session undoes the last modification and re-renders the current GUI view with the restored data
