# item-data-components Specification

## Purpose

Provides dynamic extraction, inspection, and modification of modern Minecraft 1.20.5+ and 1.21.2–1.21.4+ Item Data Components without relying on hardcoded compile-time component enums.

## Requirements

### Requirement: Dynamic Component Tree Extraction
The inspection system SHALL extract all Data Components present on a Minecraft ItemStack into a first-class `components` compound node inside the item's root `DataNode`.

#### Scenario: Inspecting an item with data components
- **WHEN** a player inspects an item possessing components on a 1.21+ server
- **THEN** the resulting DataNode tree contains a `components` node populated with key-value entries representing all active components

#### Scenario: Item with custom data component
- **WHEN** an item contains a `minecraft:custom_data` component
- **THEN** the custom data is mapped as a child compound node under `components.minecraft:custom_data` preserving nested primitives and compounds

### Requirement: Component Mutation and Persistence
The system SHALL support setting and removing individual component values by dot-notation paths and applying them back to the live ItemStack.

#### Scenario: Modifying an item model component
- **WHEN** a player executes `/data set components.minecraft:item_model "craftlab:weapons/katana"`
- **THEN** the component value in the working tree is updated to the new string value and staged for commit

#### Scenario: Removing an optional component
- **WHEN** a player executes `/data remove components.minecraft:damage`
- **THEN** the `minecraft:damage` component is removed from the component tree

### Requirement: Modern 1.21.4 Component Compatibility
The component adapter SHALL dynamically process modern 1.21.2–1.21.4+ components including `minecraft:item_model`, `minecraft:equippable`, `minecraft:consumable`, and multi-typed `minecraft:custom_model_data`.

#### Scenario: Inspecting modern item model
- **WHEN** an item utilizes the 1.21.4 `minecraft:item_model` component
- **THEN** the inspector extracts the model identifier under `components.minecraft:item_model` without throwing reflection or class-cast exceptions
