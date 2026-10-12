package dev.darkblade.datalens.command;

import dev.darkblade.datalens.core.diff.DiffService;
import dev.darkblade.datalens.core.edit.EditService;
import dev.darkblade.datalens.core.export.DiskExportService;
import dev.darkblade.datalens.core.search.TreeSearchService;
import dev.darkblade.datalens.core.serialize.SerializationService;
import dev.darkblade.datalens.core.session.PlayerSession;
import dev.darkblade.datalens.core.session.SessionService;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.diff.DataDiff;
import dev.darkblade.datalens.security.PermissionGuard;
import dev.darkblade.datalens.service.DataLensServiceLocator;
import dev.darkblade.datalens.ui.chat.ChatRenderer;
import dev.darkblade.datalens.util.PathCompleter;
import dev.darkblade.datalens.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@code /data <subcommand> [args...]} — manage the currently inspected object.
 */
public final class DataCommand implements CommandExecutor, TabCompleter {

    private final ChatRenderer chatRenderer = new ChatRenderer();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player;
        try {
            PermissionGuard.require(sender, PermissionGuard.INSPECT);
            player = PermissionGuard.requirePlayer(sender);
        } catch (PermissionGuard.PermissionException ex) {
            TextUtil.audience(sender).sendMessage(Component.text(ex.getMessage()).color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { sendUsage(sender); return true; }

        DataLensServiceLocator svc = DataLensServiceLocator.get();
        SessionService sessions = svc.sessions();

        Optional<PlayerSession> sessionOpt = sessions.get(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            TextUtil.audience(sender).sendMessage(Component.text("No active inspection. Use /inspect first.")
                    .color(NamedTextColor.YELLOW));
            return true;
        }
        PlayerSession session = sessionOpt.get();

        switch (args[0].toLowerCase()) {
            case "set"    -> handleSet(player, session, svc.editor(), args);
            case "remove" -> handleRemove(player, session, svc.editor(), args);
            case "apply"  -> handleApply(player, session, svc.editor());
            case "undo"   -> handleUndo(player, session);
            case "redo"   -> handleRedo(player, session);
            case "reset"  -> handleReset(player, session);
            case "diff"   -> handleDiff(player, session, svc.differ());
            case "search" -> handleSearch(player, session, svc.searcher(), args);
            case "export" -> handleExport(player, session, svc.serializer(), svc.exporter(), args);
            default       -> sendUsage(sender);
        }
        return true;
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private void handleSet(Player player, PlayerSession session, EditService editor, String[] args) {
        try { PermissionGuard.require(player, PermissionGuard.EDIT); }
        catch (PermissionGuard.PermissionException ex) {
            TextUtil.audience(player).sendMessage(Component.text(ex.getMessage()).color(NamedTextColor.RED)); return;
        }
        if (args.length < 3) {
            TextUtil.audience(player).sendMessage(Component.text("Usage: /data set <path> <value>").color(NamedTextColor.RED));
            return;
        }
        String path = args[1];
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        try {
            editor.setValueStaged(session, path, value, player.getName());
            TextUtil.audience(player).sendMessage(Component.text("✔ Staged set ").color(NamedTextColor.GREEN)
                    .append(Component.text(path).color(NamedTextColor.AQUA))
                    .append(Component.text(" = " + value).color(NamedTextColor.WHITE))
                    .append(Component.text(" (Use /data apply to commit, /data undo to revert)").color(NamedTextColor.GRAY)));
        } catch (EditService.EditException ex) {
            TextUtil.audience(player).sendMessage(Component.text("✘ " + ex.getMessage()).color(NamedTextColor.RED));
        }
    }

    private void handleRemove(Player player, PlayerSession session, EditService editor, String[] args) {
        try { PermissionGuard.require(player, PermissionGuard.EDIT); }
        catch (PermissionGuard.PermissionException ex) {
            TextUtil.audience(player).sendMessage(Component.text(ex.getMessage()).color(NamedTextColor.RED)); return;
        }
        if (args.length < 2) {
            TextUtil.audience(player).sendMessage(Component.text("Usage: /data remove <path>").color(NamedTextColor.RED));
            return;
        }
        try {
            editor.removeStaged(session, args[1], player.getName());
            TextUtil.audience(player).sendMessage(Component.text("✔ Staged remove ").color(NamedTextColor.GREEN)
                    .append(Component.text(args[1]).color(NamedTextColor.AQUA))
                    .append(Component.text(" (Use /data apply to commit, /data undo to revert)").color(NamedTextColor.GRAY)));
        } catch (EditService.EditException ex) {
            TextUtil.audience(player).sendMessage(Component.text("✘ " + ex.getMessage()).color(NamedTextColor.RED));
        }
    }

    private void handleApply(Player player, PlayerSession session, EditService editor) {
        try { PermissionGuard.require(player, PermissionGuard.EDIT); }
        catch (PermissionGuard.PermissionException ex) {
            TextUtil.audience(player).sendMessage(Component.text(ex.getMessage()).color(NamedTextColor.RED)); return;
        }
        if (!session.isDirty()) {
            TextUtil.audience(player).sendMessage(Component.text("No pending staged changes to apply.").color(NamedTextColor.YELLOW));
            return;
        }
        try {
            editor.apply(session, player.getName());
            TextUtil.audience(player).sendMessage(Component.text("✔ Successfully applied all staged changes to the world!")
                    .color(NamedTextColor.GREEN));
        } catch (Exception ex) {
            TextUtil.audience(player).sendMessage(Component.text("✘ Apply failed: " + ex.getMessage()).color(NamedTextColor.RED));
        }
    }

    private void handleUndo(Player player, PlayerSession session) {
        if (session.undo()) {
            TextUtil.audience(player).sendMessage(Component.text("✔ Undid last change. ").color(NamedTextColor.GREEN)
                    .append(Component.text("(Steps remaining: " + session.getUndoCount() + ")").color(NamedTextColor.GRAY)));
        } else {
            TextUtil.audience(player).sendMessage(Component.text("Nothing to undo.").color(NamedTextColor.YELLOW));
        }
    }

    private void handleRedo(Player player, PlayerSession session) {
        if (session.redo()) {
            TextUtil.audience(player).sendMessage(Component.text("✔ Redid change. ").color(NamedTextColor.GREEN)
                    .append(Component.text("(Steps remaining: " + session.getRedoCount() + ")").color(NamedTextColor.GRAY)));
        } else {
            TextUtil.audience(player).sendMessage(Component.text("Nothing to redo.").color(NamedTextColor.YELLOW));
        }
    }

    private void handleReset(Player player, PlayerSession session) {
        session.refresh();
        TextUtil.audience(player).sendMessage(Component.text("✔ Discarded all staged changes and reset session from live target.")
                .color(NamedTextColor.GREEN));
    }

    private void handleSearch(Player player, PlayerSession session, TreeSearchService searcher, String[] args) {
        if (args.length < 2) {
            TextUtil.audience(player).sendMessage(Component.text("Usage: /data search <query>").color(NamedTextColor.RED));
            return;
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        List<TreeSearchService.SearchResult> results = searcher.search(session.getWorkingCopy(), query);

        if (results.isEmpty()) {
            TextUtil.audience(player).sendMessage(Component.text("No results matching '").color(NamedTextColor.YELLOW)
                    .append(Component.text(query).color(NamedTextColor.WHITE))
                    .append(Component.text("'.").color(NamedTextColor.YELLOW)));
            return;
        }

        TextUtil.audience(player).sendMessage(Component.text("── Search Results for '" + query + "' (" + results.size() + ") ──")
                .color(NamedTextColor.DARK_AQUA));
        for (TreeSearchService.SearchResult res : results) {
            String valStr = res.node().getValue() != null ? " = " + res.node().getValue() : "";
            TextUtil.audience(player).sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
                    .append(Component.text(res.path()).color(NamedTextColor.AQUA))
                    .append(Component.text(valStr).color(NamedTextColor.WHITE)));
        }
    }

    private void handleExport(Player player, PlayerSession session,
                               SerializationService serializer, DiskExportService exporter, String[] args) {
        String format = args.length >= 2 ? args[1].toLowerCase() : "json";
        if (!format.equals("yaml") && !format.equals("snbt")) {
            format = "json";
        }

        // Check if disk export was requested: /data export <format> file [name]
        boolean toDisk = args.length >= 3 && args[2].equalsIgnoreCase("file");
        if (toDisk) {
            String customName = args.length >= 4 ? args[3] : exporter.generateDefaultName(session.getTarget().getType().name(), session.getTarget().getId());
            try {
                File file = exporter.exportToFile(session.getWorkingCopy(), format, customName);
                TextUtil.audience(player).sendMessage(Component.text("✔ Exported to ").color(NamedTextColor.GREEN)
                        .append(Component.text(file.getPath()).color(NamedTextColor.GOLD))
                        .append(Component.text(" (" + file.length() + " bytes)").color(NamedTextColor.GRAY)));
            } catch (Exception ex) {
                TextUtil.audience(player).sendMessage(Component.text("✘ Disk export failed: " + ex.getMessage()).color(NamedTextColor.RED));
            }
            return;
        }

        // Chat export with truncation check
        String output = switch (format) {
            case "yaml" -> serializer.toYaml(session.getWorkingCopy());
            case "snbt" -> serializer.toSnbt(session.getWorkingCopy());
            default     -> serializer.toJson(session.getWorkingCopy());
        };

        TextUtil.audience(player).sendMessage(Component.text("── DataLens Export (" + format.toUpperCase() + ") ──")
                .color(NamedTextColor.DARK_AQUA));
        String[] lines = output.split("\n");
        if (lines.length > 20) {
            for (int i = 0; i < 15; i++) {
                TextUtil.audience(player).sendMessage(Component.text(lines[i]).color(NamedTextColor.WHITE));
            }
            TextUtil.audience(player).sendMessage(Component.text("⚠ ... [" + (lines.length - 15) + " lines truncated]").color(NamedTextColor.YELLOW)
                    .append(Component.text(" Use: /data export " + format + " file [name] to save full output to disk.").color(NamedTextColor.GOLD)));
        } else {
            for (String line : lines) {
                TextUtil.audience(player).sendMessage(Component.text(line).color(NamedTextColor.WHITE));
            }
        }
    }

    private void handleDiff(Player player, PlayerSession session, DiffService differ) {
        List<DataDiff> diffs = differ.diff(session.getTarget().getRoot(), session.getWorkingCopy());
        chatRenderer.renderDiff(player, diffs);
    }

    // ── TabComplete ───────────────────────────────────────────────────────────

    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "apply", "undo", "redo", "reset", "diff", "search", "export");
    private static final List<String> EXPORT_FORMATS = List.of("json", "yaml", "snbt");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        // arg[0] — subcommand
        if (args.length == 1) {
            return PathCompleter.filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        // arg[1] — path (for set / remove / search) or format (for export)
        if (args.length == 2) {
            return switch (sub) {
                case "set", "remove" -> pathsForPlayer(player, args[1]);
                case "export"        -> PathCompleter.filter(EXPORT_FORMATS, args[1]);
                default              -> List.of();
            };
        }

        // arg[2] — value (for set <path> <value>) or 'file' (for export <format> file)
        if (args.length == 3) {
            if (sub.equals("set")) return valuesForPlayer(player, args[1], args[2]);
            if (sub.equals("export")) return PathCompleter.filter(List.of("file"), args[2]);
        }

        return List.of();
    }

    private List<String> pathsForPlayer(Player player, String prefix) {
        return DataLensServiceLocator.get()
                .sessions()
                .get(player.getUniqueId())
                .map(session -> PathCompleter.paths(session.getWorkingCopy(), prefix))
                .orElse(List.of());
    }

    private List<String> valuesForPlayer(Player player, String path, String prefix) {
        return DataLensServiceLocator.get()
                .sessions()
                .get(player.getUniqueId())
                .map(session -> {
                    DataNode root = session.getWorkingCopy();
                    return PathCompleter.values(root, path, prefix);
                })
                .orElse(List.of());
    }

    private void sendUsage(CommandSender sender) {
        TextUtil.audience(sender).sendMessage(Component.text("DataLens commands:").color(NamedTextColor.DARK_AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data set <path> <value>").color(NamedTextColor.AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data remove <path>").color(NamedTextColor.AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data apply").color(NamedTextColor.AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data undo | redo | reset").color(NamedTextColor.AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data diff").color(NamedTextColor.AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data search <query>").color(NamedTextColor.AQUA));
        TextUtil.audience(sender).sendMessage(Component.text("  /data export [json|yaml|snbt] [file [name]]").color(NamedTextColor.AQUA));
    }
}
