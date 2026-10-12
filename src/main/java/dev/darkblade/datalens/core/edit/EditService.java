package dev.darkblade.datalens.core.edit;

import dev.darkblade.datalens.adapter.common.Adapter;
import dev.darkblade.datalens.core.session.PlayerSession;
import dev.darkblade.datalens.core.validate.ValidationService;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.InspectableObject;
import dev.darkblade.datalens.model.validation.ValidationResult;
import dev.darkblade.datalens.repository.ChangeLogRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Handles safe editing of {@link DataNode} values within an {@link InspectableObject}
 * or staged within a {@link PlayerSession}.
 */
public final class EditService {

    private final Adapter adapter;
    private final ValidationService validator;
    private final ChangeLogRepository changeLog;

    public EditService(Adapter adapter, ValidationService validator, ChangeLogRepository changeLog) {
        this.adapter = Objects.requireNonNull(adapter);
        this.validator = Objects.requireNonNull(validator);
        this.changeLog = Objects.requireNonNull(changeLog);
    }

    // ── Staged Operations (Session-aware) ─────────────────────────────────────

    /**
     * Staged set: modifies the workingCopy of the player session without writing to the world.
     */
    public void setValueStaged(PlayerSession session, String path, String rawValue, String actor) {
        DataNode target = resolveOrThrow(session.getWorkingCopy(), path);
        setNodeValueStaged(session, target, path, rawValue, actor);
    }

    /**
     * Staged set directly on a DataNode belonging to session.workingCopy.
     */
    public void setNodeValueStaged(PlayerSession session, DataNode target, String path, String rawValue, String actor) {
        ValidationResult validation = validator.validate(target.getType(), rawValue);
        if (!validation.isValid()) {
            throw new EditException("Validation failed: " + validation.getError().orElse("unknown error"));
        }

        Object coerced = validator.coerce(target.getType(), rawValue);
        session.pushHistorySnapshot();
        target.setValue(coerced);
        changeLog.log(actor, session.getTarget().getId(), "STAGE_SET", path != null ? path : target.getKey(), String.valueOf(target.getValue()), rawValue);
    }

    /**
     * Staged remove: removes a path from session.workingCopy without writing to the world.
     */
    public void removeStaged(PlayerSession session, String path, String actor) {
        Optional<PathResolver.ParentContext> ctx = PathResolver.resolveParent(session.getWorkingCopy(), path);
        if (ctx.isEmpty()) {
            throw new EditException("Path not found: " + path);
        }

        PathResolver.ParentContext pc = ctx.get();
        session.pushHistorySnapshot();

        boolean removed;
        if (pc.lastSegment().isIndex()) {
            removed = pc.parent().removeElement(pc.lastSegment().getIndex());
        } else {
            removed = pc.parent().removeChild(pc.lastSegment().getKey());
        }
        if (!removed) throw new EditException("Node not found at path: " + path);
        changeLog.log(actor, session.getTarget().getId(), "STAGE_REMOVE", path, null, null);
    }

    /**
     * Applies all staged changes from the session's working copy to the live object in the world.
     */
    public void apply(PlayerSession session, String actor) {
        InspectableObject target = session.getTarget();
        DataNode workingCopy = session.getWorkingCopy();

        DataNode snapshot = target.getRoot().deepCopy();
        try {
            target.setRoot(workingCopy.deepCopy());
            adapter.writeData(target, target.getRoot());
            session.setDirty(false);
            changeLog.log(actor, target.getId(), "APPLY", "root", null, null);
        } catch (Exception ex) {
            target.setRoot(snapshot); // rollback target on failure
            throw new EditException("Persist to world failed — rolled back. Cause: " + ex.getMessage(), ex);
        }
    }

    // ── Direct / Legacy Operations ────────────────────────────────────────────

    public void setValue(InspectableObject object, String path, String rawValue, String actor) {
        DataNode target = resolveOrThrow(object.getRoot(), path);
        setNodeValue(object, target, path, rawValue, actor);
    }

    public void setNodeValue(InspectableObject object, DataNode target, String path, String rawValue, String actor) {
        ValidationResult validation = validator.validate(target.getType(), rawValue);
        if (!validation.isValid()) {
            throw new EditException("Validation failed: " + validation.getError().orElse("unknown error"));
        }

        Object coerced = validator.coerce(target.getType(), rawValue);
        DataNode snapshot = object.getRoot().deepCopy();
        Object oldValue = target.getValue();

        try {
            target.setValue(coerced);
            adapter.writeData(object, object.getRoot());
            changeLog.log(actor, object.getId(), "SET", path != null ? path : target.getKey(), String.valueOf(oldValue), rawValue);
        } catch (Exception ex) {
            object.setRoot(snapshot);
            throw new EditException("Persist failed — rolled back. Cause: " + ex.getMessage(), ex);
        }
    }

    public void remove(InspectableObject object, String path, String actor) {
        Optional<PathResolver.ParentContext> ctx = PathResolver.resolveParent(object.getRoot(), path);
        if (ctx.isEmpty()) {
            throw new EditException("Path not found: " + path);
        }

        DataNode snapshot = object.getRoot().deepCopy();
        PathResolver.ParentContext pc = ctx.get();

        try {
            boolean removed;
            if (pc.lastSegment().isIndex()) {
                removed = pc.parent().removeElement(pc.lastSegment().getIndex());
            } else {
                removed = pc.parent().removeChild(pc.lastSegment().getKey());
            }
            if (!removed) throw new EditException("Node not found at path: " + path);

            adapter.writeData(object, object.getRoot());
            changeLog.log(actor, object.getId(), "REMOVE", path, null, null);
        } catch (EditException e) {
            throw e;
        } catch (Exception ex) {
            object.setRoot(snapshot);
            throw new EditException("Persist failed — rolled back. Cause: " + ex.getMessage(), ex);
        }
    }

    private DataNode resolveOrThrow(DataNode root, String path) {
        return PathResolver.resolve(root, path)
                .orElseThrow(() -> new EditException("Path not found: " + path));
    }

    public static final class EditException extends RuntimeException {
        public EditException(String msg) { super(msg); }
        public EditException(String msg, Throwable cause) { super(msg, cause); }
    }
}
