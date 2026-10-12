package dev.darkblade.datalens.ui.gui;

import dev.darkblade.datalens.core.search.TreeSearchService;
import dev.darkblade.datalens.core.session.PlayerSession;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.service.DataLensServiceLocator;
import dev.darkblade.datalens.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The main 54-slot inspector inventory GUI.
 *
 * <pre>
 * Layout (rows 0-5):
 *  Row 0: [Back] [filler] [Undo] [Redo] [filler] [Search] [ClearFilter] [filler] [Close]
 *  Rows 1-4: up to 36 DataNode items (paginated)
 *  Row 5: [Prev] [filler] [Apply] [filler] [Page info] [filler] [Export] [filler] [Next]
 * </pre>
 */
public final class InspectorGui implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int NODE_START_SLOT = 9;   // row 1
    private static final int NODE_END_SLOT   = 44;  // row 4 (inclusive)
    private static final int NODES_PER_PAGE  = 36;

    // Navbar slots: Row 0
    private static final int SLOT_BACK         = 0;
    private static final int SLOT_UNDO         = 2;
    private static final int SLOT_REDO         = 3;
    private static final int SLOT_SEARCH       = 5;
    private static final int SLOT_CLEAR_FILTER = 6;
    private static final int SLOT_CLOSE        = 8;

    // Navbar slots: Row 5
    private static final int SLOT_PREV      = 45;
    private static final int SLOT_APPLY     = 47;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_EXPORT    = 51;
    private static final int SLOT_NEXT      = 53;

    private final Inventory inventory;
    private final PlayerSession session;
    private List<TreeSearchService.SearchResult> currentSearchResults = new ArrayList<>();

    public InspectorGui(PlayerSession session) {
        this.session = session;
        String title = "DataLens — " + session.getTarget().getId();
        this.inventory = Bukkit.createInventory(this, SIZE,
                TextUtil.legacy(Component.text(title).color(NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.BOLD, true)));
        render();
    }

    // ── Inventory holder ──────────────────────────────────────────────────────

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Public helpers ────────────────────────────────────────────────────────

    public PlayerSession getSession() { return session; }

    /** Opens this GUI for the given player. */
    public void open(Player player) { player.openInventory(inventory); }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Clears and redraws the entire GUI based on current session state. */
    public void render() {
        inventory.clear();

        // Row 0 — top nav
        inventory.setItem(SLOT_BACK, NodeRenderer.backButton());
        inventory.setItem(SLOT_UNDO, NodeRenderer.undoButton(session.getUndoCount()));
        inventory.setItem(SLOT_REDO, NodeRenderer.redoButton(session.getRedoCount()));
        inventory.setItem(SLOT_SEARCH, NodeRenderer.searchButton(session.isFilterActive(), session.getFilterQuery()));
        if (session.isFilterActive()) {
            inventory.setItem(SLOT_CLEAR_FILTER, NodeRenderer.clearFilterButton());
        }
        inventory.setItem(SLOT_CLOSE, NodeRenderer.closeButton());
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, NodeRenderer.filler());
        }

        // Node area
        int page = session.getPage();
        int totalPages;

        if (session.isFilterActive()) {
            currentSearchResults = DataLensServiceLocator.get().searcher()
                    .search(session.getWorkingCopy(), session.getFilterQuery());
            totalPages = Math.max(1, (int) Math.ceil((double) currentSearchResults.size() / NODES_PER_PAGE));
            if (page >= totalPages) { session.setPage(0); page = 0; }

            int from = page * NODES_PER_PAGE;
            int to   = Math.min(from + NODES_PER_PAGE, currentSearchResults.size());

            for (int i = from; i < to; i++) {
                int slot = NODE_START_SLOT + (i - from);
                if (slot > NODE_END_SLOT) break;
                inventory.setItem(slot, NodeRenderer.renderSearchResult(currentSearchResults.get(i)));
            }
        } else {
            currentSearchResults = new ArrayList<>();
            DataNode current = session.current();
            List<DataNode> children = current.getChildren();
            totalPages = Math.max(1, (int) Math.ceil((double) children.size() / NODES_PER_PAGE));
            if (page >= totalPages) { session.setPage(0); page = 0; }

            int from = page * NODES_PER_PAGE;
            int to   = Math.min(from + NODES_PER_PAGE, children.size());

            for (int i = from; i < to; i++) {
                int slot = NODE_START_SLOT + (i - from);
                if (slot > NODE_END_SLOT) break;
                inventory.setItem(slot, NodeRenderer.render(children.get(i)));
            }
        }

        // Row 5 — bottom nav
        for (int i = 45; i < 54; i++) inventory.setItem(i, NodeRenderer.filler());
        if (page > 0) inventory.setItem(SLOT_PREV, NodeRenderer.prevPage());
        inventory.setItem(SLOT_APPLY, NodeRenderer.applyButton(session.isDirty()));
        inventory.setItem(SLOT_PAGE_INFO, pageInfoItem(page + 1, totalPages));
        inventory.setItem(SLOT_EXPORT, NodeRenderer.exportButton());
        if (page < totalPages - 1) inventory.setItem(SLOT_NEXT, NodeRenderer.nextPage());
    }

    private ItemStack pageInfoItem(int current, int total) {
        return NodeRenderer.namedPane(
                Component.text("Page " + current + " / " + total)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
    }

    // ── Slot queries ──────────────────────────────────────────────────────────

    public boolean isNodeSlot(int slot) { return slot >= NODE_START_SLOT && slot <= NODE_END_SLOT; }
    public boolean isBackSlot(int slot) { return slot == SLOT_BACK; }
    public boolean isCloseSlot(int slot) { return slot == SLOT_CLOSE; }
    public boolean isUndoSlot(int slot) { return slot == SLOT_UNDO; }
    public boolean isRedoSlot(int slot) { return slot == SLOT_REDO; }
    public boolean isSearchSlot(int slot) { return slot == SLOT_SEARCH; }
    public boolean isClearFilterSlot(int slot) { return slot == SLOT_CLEAR_FILTER; }
    public boolean isPrevSlot(int slot) { return slot == SLOT_PREV; }
    public boolean isApplySlot(int slot) { return slot == SLOT_APPLY; }
    public boolean isNextSlot(int slot) { return slot == SLOT_NEXT; }
    public boolean isExportSlot(int slot) { return slot == SLOT_EXPORT; }

    /** Returns the DataNode at the clicked slot, or null. */
    public DataNode nodeAt(int slot) {
        if (!isNodeSlot(slot)) return null;
        if (session.isFilterActive()) {
            int index = session.getPage() * NODES_PER_PAGE + (slot - NODE_START_SLOT);
            if (index < 0 || index >= currentSearchResults.size()) return null;
            return currentSearchResults.get(index).node();
        } else {
            List<DataNode> children = session.current().getChildren();
            int index = session.getPage() * NODES_PER_PAGE + (slot - NODE_START_SLOT);
            if (index < 0 || index >= children.size()) return null;
            return children.get(index);
        }
    }

    /** Returns the SearchResult at the clicked slot if in filter mode, or null. */
    public TreeSearchService.SearchResult searchResultAt(int slot) {
        if (!isNodeSlot(slot) || !session.isFilterActive()) return null;
        int index = session.getPage() * NODES_PER_PAGE + (slot - NODE_START_SLOT);
        if (index < 0 || index >= currentSearchResults.size()) return null;
        return currentSearchResults.get(index);
    }
}
