package dev.darkblade.datalens.core.edit;

import dev.darkblade.datalens.adapter.common.Adapter;
import dev.darkblade.datalens.core.session.PlayerSession;
import dev.darkblade.datalens.core.validate.ValidationService;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import dev.darkblade.datalens.model.InspectableObject;
import dev.darkblade.datalens.model.InspectableType;
import dev.darkblade.datalens.repository.ChangeLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditServiceTest {

    private Adapter adapter;
    private ValidationService validator;
    private ChangeLogRepository changeLog;
    private EditService editService;

    private InspectableObject target;
    private PlayerSession session;

    @BeforeEach
    void setUp() {
        adapter = mock(Adapter.class);
        validator = new ValidationService();
        changeLog = mock(ChangeLogRepository.class);
        editService = new EditService(adapter, validator, changeLog);

        DataNode root = DataNode.ofCompound("root");
        root.addChild(DataNode.ofPrimitive("level", DataType.INT, 1));
        root.addChild(DataNode.ofPrimitive("name", DataType.STRING, "Initial"));

        target = new InspectableObject(InspectableType.ITEM, "item-123", root, new Object());
        session = new PlayerSession(target);
    }

    @Test
    void stagedSet_leavesLiveTargetUnmodified_untilApply() {
        editService.setValueStaged(session, "level", "50", "Admin");

        // workingCopy must be updated
        assertEquals(50, session.getWorkingCopy().getChild("level").map(DataNode::getValue).orElse(null));
        assertTrue(session.isDirty());
        assertTrue(session.canUndo());

        // Live target root MUST NOT be modified yet
        assertEquals(1, target.getRoot().getChild("level").map(DataNode::getValue).orElse(null));
        verify(adapter, never()).writeData(any(), any());

        // Now call apply
        editService.apply(session, "Admin");

        // Now live target root MUST be updated and adapter.writeData called
        assertEquals(50, target.getRoot().getChild("level").map(DataNode::getValue).orElse(null));
        assertFalse(session.isDirty());
        verify(adapter, times(1)).writeData(eq(target), any());
    }

    @Test
    void stagedRemove_leavesLiveTargetUnmodified_untilApply() {
        editService.removeStaged(session, "name", "Admin");

        // workingCopy has child removed
        assertTrue(session.getWorkingCopy().getChild("name").isEmpty());
        assertTrue(session.isDirty());

        // Target still has child
        assertTrue(target.getRoot().getChild("name").isPresent());
        verify(adapter, never()).writeData(any(), any());

        // Apply persists removal
        editService.apply(session, "Admin");
        assertTrue(target.getRoot().getChild("name").isEmpty());
        verify(adapter, times(1)).writeData(eq(target), any());
    }

    @Test
    void apply_rollsBackTargetOnAdapterFailure() {
        doThrow(new RuntimeException("Database offline")).when(adapter).writeData(any(), any());

        editService.setValueStaged(session, "level", "99", "Admin");

        assertThrows(EditService.EditException.class, () -> editService.apply(session, "Admin"));

        // Target must remain rolled back to original level 1
        assertEquals(1, target.getRoot().getChild("level").map(DataNode::getValue).orElse(null));
    }
}
