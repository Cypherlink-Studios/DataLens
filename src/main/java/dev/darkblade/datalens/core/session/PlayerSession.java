package dev.darkblade.datalens.core.session;

import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.InspectableObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Holds a single player's inspection state.
 * The {@code workingCopy} is a deep copy of the root that can be mutated
 * independently of the committed data in staging mode.
 */
public final class PlayerSession {

    public static final int DEFAULT_MAX_HISTORY = 20;

    private final InspectableObject target;
    private final int maxHistory;
    private DataNode workingCopy;

    /** Undo and Redo Memento history stacks. */
    private final Deque<DataNode> undoStack = new ArrayDeque<>();
    private final Deque<DataNode> redoStack = new ArrayDeque<>();

    /** Whether workingCopy has uncommitted changes compared to target. */
    private boolean dirty = false;

    /** Active search query filter, if any. */
    private String filterQuery = null;

    /** Current GUI page index. */
    private int page = 0;

    /** Path of nodes the player has navigated into (GUI breadcrumb). */
    private final Deque<DataNode> navigationStack = new ArrayDeque<>();

    public PlayerSession(InspectableObject target) {
        this(target, DEFAULT_MAX_HISTORY);
    }

    public PlayerSession(InspectableObject target, int maxHistory) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.maxHistory = Math.max(1, maxHistory);
        this.workingCopy = target.getRoot().deepCopy();
        this.navigationStack.push(this.workingCopy);
    }

    public InspectableObject getTarget() { return target; }
    public DataNode getWorkingCopy() { return workingCopy; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(0, page); }

    /** Navigates one level deeper into the given compound/list node. */
    public void push(DataNode node) { navigationStack.push(node); page = 0; }

    /** Navigates back up one level. Returns false if already at root. */
    public boolean pop() {
        if (navigationStack.size() <= 1) return false;
        navigationStack.pop();
        page = 0;
        return true;
    }

    /** The node currently being viewed in the GUI. */
    public DataNode current() {
        return navigationStack.isEmpty() ? workingCopy : navigationStack.peek();
    }

    // ── History & Staging ─────────────────────────────────────────────────────

    /**
     * Saves a snapshot of {@code workingCopy} onto the undo stack prior to a mutation.
     */
    public void pushHistorySnapshot() {
        undoStack.push(workingCopy.deepCopy());
        if (undoStack.size() > maxHistory) {
            undoStack.removeLast();
        }
        redoStack.clear();
        this.dirty = true;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public int getUndoCount() { return undoStack.size(); }
    public int getRedoCount() { return redoStack.size(); }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }

    /**
     * Undoes the last mutation, restoring the previous workingCopy snapshot.
     */
    public boolean undo() {
        if (!canUndo()) return false;
        redoStack.push(workingCopy.deepCopy());
        this.workingCopy = undoStack.pop();
        resetNavigation();
        return true;
    }

    /**
     * Redoes a previously undone mutation.
     */
    public boolean redo() {
        if (!canRedo()) return false;
        undoStack.push(workingCopy.deepCopy());
        this.workingCopy = redoStack.pop();
        resetNavigation();
        return true;
    }

    /**
     * Discards all uncommitted changes and re-clones from the target.
     */
    public void refresh() {
        this.workingCopy = target.getRoot().deepCopy();
        this.undoStack.clear();
        this.redoStack.clear();
        this.dirty = false;
        this.filterQuery = null;
        resetNavigation();
    }

    private void resetNavigation() {
        navigationStack.clear();
        navigationStack.push(workingCopy);
        page = 0;
    }

    // ── Search & Filter ───────────────────────────────────────────────────────

    public String getFilterQuery() { return filterQuery; }

    public void setFilterQuery(String filterQuery) {
        this.filterQuery = (filterQuery != null && !filterQuery.isBlank()) ? filterQuery.trim() : null;
        this.page = 0;
    }

    public void clearFilter() {
        this.filterQuery = null;
        this.page = 0;
    }

    public boolean isFilterActive() {
        return filterQuery != null;
    }
}
