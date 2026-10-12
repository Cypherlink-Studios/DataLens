package dev.darkblade.datalens;

import dev.darkblade.datalens.adapter.common.Adapter;
import dev.darkblade.datalens.api.DataLensAPI;
import dev.darkblade.datalens.command.DataCommand;
import dev.darkblade.datalens.command.InspectCommand;
import dev.darkblade.datalens.core.session.SessionService;
import dev.darkblade.datalens.model.schema.Schema;
import dev.darkblade.datalens.repository.ChangeLogRepository;
import dev.darkblade.datalens.service.DataLensServiceLocator;
import dev.darkblade.datalens.ui.gui.GuiListener;
import dev.darkblade.datalens.util.AdapterLoader;
import dev.darkblade.datalens.util.TextUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DataLens — Minecraft block/entity/item data inspector plugin.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #onEnable}: detect server version → load adapter → init services → register commands/listeners</li>
 *   <li>{@link #onDisable}: flush and clean up</li>
 * </ol>
 */
public final class DataLensPlugin extends JavaPlugin implements DataLensAPI {

    private static DataLensPlugin instance;

    private final Map<String, Schema> schemas = new HashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialise cross-platform Adventure support (must be first)
        TextUtil.init(this);

        // Config values
        long ttlSeconds  = getConfig().getLong("cache.session-ttl-seconds", 60);
        long maxSessions = getConfig().getLong("cache.max-sessions", 100);
        double maxRay    = getConfig().getDouble("inspect.max-ray-distance", 5.0);
        boolean logEnabled = getConfig().getBoolean("changelog.enabled", true);

        // Load version-specific adapter
        Adapter adapter;
        try {
            adapter = AdapterLoader.load();
            getLogger().info("Loaded adapter: " + adapter.getSupportedVersion());
        } catch (IllegalStateException ex) {
            getLogger().severe(ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Init services
        SessionService sessions = new SessionService(ttlSeconds, maxSessions);
        ChangeLogRepository changeLog = new ChangeLogRepository(getDataFolder(), getLogger(), logEnabled);
        DataLensServiceLocator.init(adapter, sessions, changeLog, getDataFolder());

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new GuiListener(sessions, DataLensServiceLocator.get().editor(),
                        DataLensServiceLocator.get().serializer()), this);

        // Register commands
        InspectCommand inspectCmd = new InspectCommand(maxRay);
        registerCommand("inspect", inspectCmd, inspectCmd);
        DataCommand dataCmd = new DataCommand();
        registerCommand("data", dataCmd, dataCmd);

        getLogger().info("DataLens enabled. Server: " + getServer().getBukkitVersion());
    }

    @Override
    public void onDisable() {
        TextUtil.close();
        getLogger().info("DataLens disabled.");
    }

    // ── Command registration helper ───────────────────────────────────────────

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        registerCommand(name, executor, null);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                  org.bukkit.command.TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not defined in plugin.yml!");
            return;
        }
        cmd.setExecutor(executor);
        if (completer != null) cmd.setTabCompleter(completer);
    }

    // ── DataLensAPI impl ──────────────────────────────────────────────────────

    @Override
    public void registerSchema(String namespace, Schema schema) {
        schemas.put(Objects.requireNonNull(namespace), Objects.requireNonNull(schema));
        getLogger().info("Schema registered for namespace: " + namespace);
    }

    @Override
    public dev.darkblade.datalens.core.inspect.InspectorService getInspectorService() {
        return DataLensServiceLocator.get().inspector();
    }

    @Override
    public SessionService getSessionService() {
        return DataLensServiceLocator.get().sessions();
    }

    // ── Static accessor ───────────────────────────────────────────────────────

    public static DataLensPlugin getInstance() {
        return Objects.requireNonNull(instance, "DataLensPlugin not yet initialised");
    }

    public static DataLensAPI getAPI() {
        return getInstance();
    }
}
