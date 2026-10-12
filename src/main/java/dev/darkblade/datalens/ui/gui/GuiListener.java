package dev.darkblade.datalens.ui.gui;

import dev.darkblade.datalens.core.edit.EditService;
import dev.darkblade.datalens.core.export.DiskExportService;
import dev.darkblade.datalens.core.search.TreeSearchService;
import dev.darkblade.datalens.core.serialize.SerializationService;
import dev.darkblade.datalens.core.session.PlayerSession;
import dev.darkblade.datalens.core.session.SessionService;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.security.PermissionGuard;
import dev.darkblade.datalens.service.DataLensServiceLocator;
import dev.darkblade.datalens.util.TextUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all click events inside {@link InspectorGui} inventories.
 */
public final class GuiListener implements Listener {

    private static class EditContext {
        final PlayerSession session;
        final DataNode node;
        final String path;
        final InspectorGui gui;

        EditContext(PlayerSession session, DataNode node, String path, InspectorGui gui) {
            this.session = session;
            this.node = node;
            this.path = path;
            this.gui = gui;
        }
    }

    /** Players currently waiting for a chat-based edit input. */
    private final Map<UUID, EditContext> awaitingEdit = new ConcurrentHashMap<>();

    /** Players currently waiting for a chat-based search query. */
    private final Map<UUID, InspectorGui> awaitingSearch = new ConcurrentHashMap<>();

    private final SessionService sessions;
    private final EditService editService;
    private final SerializationService serializer;

    public GuiListener(SessionService sessions, EditService editService, SerializationService serializer) {
        this.sessions = sessions;
        this.editService = editService;
        this.serializer = serializer;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InspectorGui gui)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getSlot();
        PlayerSession session = gui.getSession();

        // ── Navigation bar ────────────────────────────────────────────────────
        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        if (gui.isBackSlot(slot)) {
            session.pop();
            gui.render();
            return;
        }

        if (gui.isPrevSlot(slot)) {
            session.setPage(session.getPage() - 1);
            gui.render();
            return;
        }

        if (gui.isNextSlot(slot)) {
            session.setPage(session.getPage() + 1);
            gui.render();
            return;
        }

        // Undo
        if (gui.isUndoSlot(slot)) {
            if (session.undo()) {
                TextUtil.audience(player).sendMessage(Component.text("✔ Undid last change (Steps remaining: " + session.getUndoCount() + ")")
                        .color(NamedTextColor.GREEN));
                gui.render();
            } else {
                TextUtil.audience(player).sendMessage(Component.text("Nothing to undo.").color(NamedTextColor.YELLOW));
            }
            return;
        }

        // Redo
        if (gui.isRedoSlot(slot)) {
            if (session.redo()) {
                TextUtil.audience(player).sendMessage(Component.text("✔ Redid change (Steps remaining: " + session.getRedoCount() + ")")
                        .color(NamedTextColor.GREEN));
                gui.render();
            } else {
                TextUtil.audience(player).sendMessage(Component.text("Nothing to redo.").color(NamedTextColor.YELLOW));
            }
            return;
        }

        // Search prompt
        if (gui.isSearchSlot(slot)) {
            player.closeInventory();
            awaitingSearch.put(player.getUniqueId(), gui);
            Audience audience = TextUtil.audience(player);
            audience.sendMessage(Component.text("┌── DataLens Search ──────────────────────────").color(NamedTextColor.DARK_AQUA));
            audience.sendMessage(Component.text("└── Type search term in chat (or 'cancel'):").color(NamedTextColor.AQUA));
            return;
        }

        // Clear filter
        if (gui.isClearFilterSlot(slot)) {
            session.clearFilter();
            gui.render();
            TextUtil.audience(player).sendMessage(Component.text("Filter cleared.").color(NamedTextColor.YELLOW));
            return;
        }

        // Apply changes
        if (gui.isApplySlot(slot)) {
            if (!session.isDirty()) {
                TextUtil.audience(player).sendMessage(Component.text("No pending changes to apply.").color(NamedTextColor.YELLOW));
                return;
            }
            try {
                editService.apply(session, player.getName());
                TextUtil.audience(player).sendMessage(Component.text("✔ Staged changes successfully committed to the world!")
                        .color(NamedTextColor.GREEN));
                gui.render();
            } catch (Exception ex) {
                TextUtil.audience(player).sendMessage(Component.text("✘ Apply failed: " + ex.getMessage()).color(NamedTextColor.RED));
            }
            return;
        }

        // Export
        if (gui.isExportSlot(slot)) {
            if (event.isShiftClick()) {
                // Instant disk export
                try {
                    DiskExportService exporter = DataLensServiceLocator.get().exporter();
                    String defaultName = exporter.generateDefaultName(session.getTarget().getType().name(), session.getTarget().getId());
                    File exported = exporter.exportToFile(session.getWorkingCopy(), "json", defaultName);
                    player.closeInventory();
                    TextUtil.audience(player).sendMessage(Component.text("✔ Exported to disk: ").color(NamedTextColor.GREEN)
                            .append(Component.text(exported.getPath()).color(NamedTextColor.GOLD)));
                } catch (Exception e) {
                    TextUtil.audience(player).sendMessage(Component.text("✘ Disk export failed: " + e.getMessage()).color(NamedTextColor.RED));
                }
            } else {
                // Print to chat with truncation check
                player.closeInventory();
                String json = serializer.toJson(session.getWorkingCopy());
                Audience audience = TextUtil.audience(player);
                audience.sendMessage(Component.text("── DataLens Export (JSON) ──").color(NamedTextColor.DARK_AQUA));
                String[] lines = json.split("\n");
                if (lines.length > 20) {
                    for (int i = 0; i < 15; i++) {
                        audience.sendMessage(Component.text(lines[i]).color(NamedTextColor.WHITE));
                    }
                    audience.sendMessage(Component.text("⚠ ... [" + (lines.length - 15) + " lines truncated]").color(NamedTextColor.YELLOW)
                            .append(Component.text(" Shift+Click export in GUI or run /data export json file [name] to save to disk.").color(NamedTextColor.GOLD)));
                } else {
                    for (String line : lines) {
                        audience.sendMessage(Component.text(line).color(NamedTextColor.WHITE));
                    }
                }
            }
            return;
        }

        // ── Node slots ────────────────────────────────────────────────────────
        if (!gui.isNodeSlot(slot)) return;
        DataNode node = gui.nodeAt(slot);
        if (node == null) return;

        boolean isShift = event.isShiftClick();
        boolean isRight = event.isRightClick();

        // In Filter Mode: Left click on container jumps into that container
        if (session.isFilterActive() && node.getType().isContainer() && !isShift && !isRight) {
            session.clearFilter();
            session.push(node);
            gui.render();
            return;
        }

        // In Normal Mode: Container → expand
        if (node.getType().isContainer() && !isShift && !isRight) {
            session.push(node);
            gui.render();
            return;
        }

        // Shift+Left → edit primitive
        if (isShift && !isRight && node.getType().isPrimitive()) {
            if (!PermissionGuard.has(player, PermissionGuard.EDIT)) {
                TextUtil.audience(player).sendMessage(Component.text("You lack datalens.edit permission.")
                        .color(NamedTextColor.RED));
                return;
            }
            player.closeInventory();
            TreeSearchService.SearchResult sr = gui.searchResultAt(slot);
            String path = sr != null ? sr.path() : node.getKey();
            startChatEdit(player, session, node, path, gui);
            return;
        }

        // Right-click → delete
        if (isRight && !isShift) {
            if (!PermissionGuard.has(player, PermissionGuard.EDIT)) {
                TextUtil.audience(player).sendMessage(Component.text("You lack datalens.edit permission.")
                        .color(NamedTextColor.RED));
                return;
            }
            player.closeInventory();
            confirmDelete(player, session, node, gui);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Retain session on close
    }

    // ── Chat prompt handlers ──────────────────────────────────────────────────

    private void startChatEdit(Player player, PlayerSession session, DataNode node, String path, InspectorGui gui) {
        Audience audience = TextUtil.audience(player);
        audience.sendMessage(Component.text("┌── DataLens Edit ──────────────────────────").color(NamedTextColor.DARK_AQUA));
        audience.sendMessage(Component.text("│ Path: ").color(NamedTextColor.GRAY)
                .append(Component.text(path).color(NamedTextColor.AQUA)));
        audience.sendMessage(Component.text("│ Type: ").color(NamedTextColor.GRAY)
                .append(Component.text(node.getType().label()).color(NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("│ Current: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(node.getValue())).color(NamedTextColor.YELLOW)));
        audience.sendMessage(Component.text("└── Type new value in chat (or 'cancel'):").color(NamedTextColor.DARK_AQUA));
        awaitingEdit.put(player.getUniqueId(), new EditContext(session, node, path, gui));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 1. Check awaiting search
        InspectorGui searchGui = awaitingSearch.remove(player.getUniqueId());
        if (searchGui != null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
                if (message.equalsIgnoreCase("cancel")) {
                    TextUtil.audience(player).sendMessage(Component.text("Search cancelled.").color(NamedTextColor.YELLOW));
                    searchGui.render();
                    player.openInventory(searchGui.getInventory());
                    return;
                }
                searchGui.getSession().setFilterQuery(message);
                searchGui.render();
                player.openInventory(searchGui.getInventory());
                TextUtil.audience(player).sendMessage(Component.text("Filter applied: " + message).color(NamedTextColor.AQUA));
            });
            return;
        }

        // 2. Check awaiting edit
        EditContext ctx = awaitingEdit.remove(player.getUniqueId());
        if (ctx == null) return;

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
            if (message.equalsIgnoreCase("cancel")) {
                TextUtil.audience(player).sendMessage(Component.text("Edit cancelled.").color(NamedTextColor.YELLOW));
                ctx.gui.render();
                player.openInventory(ctx.gui.getInventory());
                return;
            }

            try {
                editService.setNodeValueStaged(ctx.session, ctx.node, ctx.path, message, player.getName());
                TextUtil.audience(player).sendMessage(Component.text("✔ Value staged! (Use Apply in GUI or /data apply to commit)")
                        .color(NamedTextColor.GREEN));
                ctx.gui.render();
                player.openInventory(ctx.gui.getInventory());
            } catch (Exception e) {
                TextUtil.audience(player).sendMessage(Component.text("Edit failed: " + e.getMessage()).color(NamedTextColor.RED));
                ctx.gui.render();
                player.openInventory(ctx.gui.getInventory());
            }
        });
    }

    private void confirmDelete(Player player, PlayerSession session, DataNode node, InspectorGui gui) {
        TextUtil.audience(player).sendMessage(Component.text("To delete ").color(NamedTextColor.RED)
                .append(Component.text(node.getKey()).color(NamedTextColor.YELLOW))
                .append(Component.text(", use: /data remove " + node.getKey()).color(NamedTextColor.RED)));
    }
}
