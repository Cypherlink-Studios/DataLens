package dev.darkblade.datalens.model;

import org.bukkit.Location;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Represents a Minecraft object that has been captured for inspection.
 * Holds its type, a human-readable identifier, optional world location,
 * and the root {@link DataNode} representing all inspected data.
 */
public final class InspectableObject {

    private final InspectableType type;
    private final String id;
    @Nullable private final Location location;
    @Nullable private final Object liveReference;
    private DataNode root;

    public InspectableObject(InspectableType type, String id, @Nullable Location location, DataNode root, @Nullable Object liveReference) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
        this.location = location;
        this.root = Objects.requireNonNull(root, "root");
        this.liveReference = liveReference;
    }

    public InspectableObject(InspectableType type, String id, DataNode root, @Nullable Object liveReference) {
        this(type, id, null, root, liveReference);
    }

    public InspectableType getType() { return type; }
    public String getId() { return id; }

    /** World location where this object was found. Null for ItemStack inspections. */
    @Nullable
    public Location getLocation() { return location; }

    /** Live reference to the Minecraft object (Block, Entity, or ItemStack) to allow write-backs. */
    @Nullable
    public Object getLiveReference() { return liveReference; }

    public DataNode getRoot() { return root; }

    /** Replaces the root data node (e.g. after an edit/reload). */
    public void setRoot(DataNode root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Override
    public String toString() {
        return "InspectableObject{type=" + type + ", id='" + id + "'}";
    }
}
