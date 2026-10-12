package dev.darkblade.datalens.core.session;

import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import dev.darkblade.datalens.model.InspectableObject;
import dev.darkblade.datalens.model.InspectableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionTest {

    private InspectableObject target;
    private PlayerSession session;

    @BeforeEach
    void setUp() {
        DataNode root = DataNode.ofCompound("root");
        root.addChild(DataNode.ofPrimitive("count", DataType.INT, 10));
        target = new InspectableObject(InspectableType.ITEM, "test-item", root, new Object());
        session = new PlayerSession(target, 3); // maxHistory = 3 for testing
    }

    @Test
    void initialSession_cleanAndEmptyHistory() {
        assertFalse(session.canUndo());
        assertFalse(session.canRedo());
        assertEquals(0, session.getUndoCount());
        assertEquals(0, session.getRedoCount());
        assertFalse(session.isDirty());
        assertFalse(session.isFilterActive());
    }

    @Test
    void undoAndRedo_flowCorrectly() {
        // Mutation 1
        session.pushHistorySnapshot();
        session.getWorkingCopy().getChild("count").ifPresent(n -> n.setValue(20));
        assertTrue(session.isDirty());
        assertTrue(session.canUndo());
        assertEquals(1, session.getUndoCount());

        // Mutation 2
        session.pushHistorySnapshot();
        session.getWorkingCopy().getChild("count").ifPresent(n -> n.setValue(30));
        assertEquals(2, session.getUndoCount());

        // Undo to 20
        assertTrue(session.undo());
        assertEquals(20, session.getWorkingCopy().getChild("count").map(DataNode::getValue).orElse(null));
        assertTrue(session.canRedo());

        // Undo to 10
        assertTrue(session.undo());
        assertEquals(10, session.getWorkingCopy().getChild("count").map(DataNode::getValue).orElse(null));
        assertFalse(session.canUndo());

        // Redo to 20
        assertTrue(session.redo());
        assertEquals(20, session.getWorkingCopy().getChild("count").map(DataNode::getValue).orElse(null));

        // Redo to 30
        assertTrue(session.redo());
        assertEquals(30, session.getWorkingCopy().getChild("count").map(DataNode::getValue).orElse(null));
        assertFalse(session.canRedo());
    }

    @Test
    void undoStack_truncatesAtMaxHistory() {
        for (int i = 1; i <= 5; i++) {
            session.pushHistorySnapshot();
            int val = i;
            session.getWorkingCopy().getChild("count").ifPresent(n -> n.setValue(val));
        }

        // maxHistory is 3
        assertEquals(3, session.getUndoCount());
    }

    @Test
    void refresh_clearsHistoryAndRestoresOriginal() {
        session.pushHistorySnapshot();
        session.getWorkingCopy().getChild("count").ifPresent(n -> n.setValue(999));
        session.setFilterQuery("search");

        session.refresh();

        assertEquals(10, session.getWorkingCopy().getChild("count").map(DataNode::getValue).orElse(null));
        assertFalse(session.canUndo());
        assertFalse(session.canRedo());
        assertFalse(session.isDirty());
        assertFalse(session.isFilterActive());
    }

    @Test
    void filterQuery_stateOperations() {
        assertNull(session.getFilterQuery());
        session.setFilterQuery("damage");
        assertTrue(session.isFilterActive());
        assertEquals("damage", session.getFilterQuery());

        session.clearFilter();
        assertFalse(session.isFilterActive());
        assertNull(session.getFilterQuery());
    }
}
