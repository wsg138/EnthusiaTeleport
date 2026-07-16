package org.enthusia.teleport;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.enthusia.teleport.api.TeleportApi;
import org.enthusia.teleport.api.CancelReason;
import org.enthusia.teleport.back.BackManager;
import org.enthusia.teleport.combat.CombatTagManager;
import org.enthusia.teleport.command.AdminHomeCommand;
import org.enthusia.teleport.command.BackCommand;
import org.enthusia.teleport.command.BedCommand;
import org.enthusia.teleport.command.DelHomeCommand;
import org.enthusia.teleport.command.HomeCommand;
import org.enthusia.teleport.command.InventoryViewCommand;
import org.enthusia.teleport.command.RtpCommand;
import org.enthusia.teleport.command.SetHomeCommand;
import org.enthusia.teleport.command.SpawnCommand;
import org.enthusia.teleport.command.TeleportAdminCommand;
import org.enthusia.teleport.command.TeleportTabCompleter;
import org.enthusia.teleport.command.TopCommand;
import org.enthusia.teleport.command.TpAcceptCommand;
import org.enthusia.teleport.command.TpCancelCommand;
import org.enthusia.teleport.command.TpDenyCommand;
import org.enthusia.teleport.command.TpIgnoreCommand;
import org.enthusia.teleport.command.TpaCommand;
import org.enthusia.teleport.command.TpoCommand;
import org.enthusia.teleport.command.TpposCommand;
import org.enthusia.teleport.config.PluginConfigManager;
import org.enthusia.teleport.debug.PerformanceMonitor;
import org.enthusia.teleport.home.HomeGuiManager;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.ignore.IgnoreManager;
import org.enthusia.teleport.log.AdminLogManager;
import org.enthusia.teleport.player.LastLocationManager;
import org.enthusia.teleport.player.OfflineNameCache;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.rtp.RtpManager;
import org.enthusia.teleport.spawn.SpawnManager;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.task.TaskCoordinator;
import org.enthusia.teleport.util.Messages;

import java.io.File;

public class EnthusiaTeleportPlugin extends JavaPlugin {

    private static EnthusiaTeleportPlugin instance;

    private PluginConfigManager pluginConfigManager;
    private Messages messages;
    private AdminLogManager adminLogManager;
    private IgnoreManager ignoreManager;
    private HomeManager homeManager;
    private BackManager backManager;
    private TeleportManager teleportManager;
    private TeleportRequestManager requestManager;
    private RtpManager rtpManager;
    private CombatTagManager combatManager;
    private HomeGuiManager homeGuiManager;
    private InventoryViewCommand inventoryViewCommand;
    private SpawnManager spawnManager;
    private LastLocationManager lastLocationManager;
    private OfflineNameCache offlineNameCache;
    private PerformanceMonitor performanceMonitor;
    private TaskCoordinator taskCoordinator;

    public static EnthusiaTeleportPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        this.performanceMonitor = new PerformanceMonitor(this);
        this.pluginConfigManager = new PluginConfigManager(this);
        this.messages = new Messages(this);
        this.adminLogManager = new AdminLogManager(this);
        this.ignoreManager = new IgnoreManager(this);
        this.homeManager = new HomeManager(this);
        this.backManager = new BackManager(this);
        this.teleportManager = new TeleportManager(this);
        this.requestManager = new TeleportRequestManager(this);
        this.teleportManager.setRequestManager(requestManager);
        this.rtpManager = new RtpManager(this);
        this.combatManager = new CombatTagManager(this);
        this.homeGuiManager = new HomeGuiManager(this);
        this.inventoryViewCommand = new InventoryViewCommand(this);
        this.spawnManager = new SpawnManager(this);
        this.lastLocationManager = new LastLocationManager(this);
        this.offlineNameCache = new OfflineNameCache(this);
        this.taskCoordinator = new TaskCoordinator(this);

        Bukkit.getServicesManager().register(TeleportApi.class, teleportManager, this, ServicePriority.Normal);

        registerCommands();
        registerListeners();
        taskCoordinator.restart();
        getLogger().info("EnthusiaTeleport enabled.");
    }

    @Override
    public void onDisable() {
        if (taskCoordinator != null) {
            taskCoordinator.cancelAll();
        }
        saveAllDataBlocking();
        if (adminLogManager != null) {
            adminLogManager.flushBlocking();
        }
        if (teleportManager != null) {
            teleportManager.shutdown();
            Bukkit.getServicesManager().unregister(TeleportApi.class, teleportManager);
        }
        if (requestManager != null) {
            requestManager.shutdown();
        }
    }

    public void reloadPlugin() {
        saveAllDataBlocking();
        adminLogManager.flushBlocking();
        teleportManager.cancelAll(CancelReason.RELOAD);
        requestManager.cancelAllForReload();

        pluginConfigManager.reload();
        messages.reload();
        ignoreManager.reload();
        homeManager.reload();
        rtpManager.reload();
        combatManager.reload();
        backManager.reload();
        lastLocationManager.reload();
        teleportManager.reloadSettings();
        taskCoordinator.restart();
    }

    public void saveAllData() {
        homeManager.saveAll();
        ignoreManager.saveAll();
        rtpManager.saveAll();
        lastLocationManager.saveOnlinePlayers();
    }

    public void saveAllDataBlocking() {
        if (homeManager != null) {
            homeManager.flushBlocking();
        }
        if (ignoreManager != null) {
            ignoreManager.flushBlocking();
        }
        if (rtpManager != null) {
            rtpManager.flushBlocking();
        }
        if (lastLocationManager != null) {
            lastLocationManager.saveOnlinePlayersBlocking();
        }
    }

    private void registerCommands() {
        TeleportTabCompleter tabCompleter = new TeleportTabCompleter(this);

        getCommand("tpa").setExecutor(new TpaCommand(this, false));
        getCommand("tpa").setTabCompleter(tabCompleter);

        getCommand("tpask").setExecutor(new TpaCommand(this, false));
        getCommand("tpask").setTabCompleter(tabCompleter);

        getCommand("tpahere").setExecutor(new TpaCommand(this, true));
        getCommand("tpahere").setTabCompleter(tabCompleter);

        getCommand("tpaccept").setExecutor(new TpAcceptCommand(this));
        getCommand("tpaccept").setTabCompleter(tabCompleter);

        getCommand("tpyes").setExecutor(new TpAcceptCommand(this));
        getCommand("tpyes").setTabCompleter(tabCompleter);

        getCommand("tpadeny").setExecutor(new TpDenyCommand(this));
        getCommand("tpadeny").setTabCompleter(tabCompleter);

        getCommand("tpno").setExecutor(new TpDenyCommand(this));
        getCommand("tpno").setTabCompleter(tabCompleter);

        getCommand("tpacancel").setExecutor(new TpCancelCommand(this));
        getCommand("tpacancel").setTabCompleter(tabCompleter);

        getCommand("tpignore").setExecutor(new TpIgnoreCommand(this));
        getCommand("tpignore").setTabCompleter(tabCompleter);

        getCommand("back").setExecutor(new BackCommand(this));
        getCommand("back").setTabCompleter(tabCompleter);

        getCommand("sethome").setExecutor(new SetHomeCommand(this));
        getCommand("sethome").setTabCompleter(tabCompleter);

        getCommand("home").setExecutor(new HomeCommand(this));
        getCommand("home").setTabCompleter(tabCompleter);

        getCommand("homes").setExecutor(new HomeCommand(this));
        getCommand("homes").setTabCompleter(tabCompleter);

        getCommand("delhome").setExecutor(new DelHomeCommand(this));
        getCommand("delhome").setTabCompleter(tabCompleter);

        getCommand("bed").setExecutor(new BedCommand(this));
        getCommand("bed").setTabCompleter(tabCompleter);

        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("spawn").setTabCompleter(tabCompleter);

        getCommand("tppos").setExecutor(new TpposCommand(this));
        getCommand("tppos").setTabCompleter(tabCompleter);

        getCommand("tpo").setExecutor(new TpoCommand(this));
        getCommand("tpo").setTabCompleter(tabCompleter);

        getCommand("invsee").setExecutor(inventoryViewCommand);
        getCommand("invsee").setTabCompleter(tabCompleter);
        getCommand("inventorysee").setExecutor(inventoryViewCommand);
        getCommand("inventorysee").setTabCompleter(tabCompleter);
        getCommand("endersee").setExecutor(inventoryViewCommand);
        getCommand("endersee").setTabCompleter(tabCompleter);
        getCommand("enderview").setExecutor(inventoryViewCommand);
        getCommand("enderview").setTabCompleter(tabCompleter);

        getCommand("rtp").setExecutor(new RtpCommand(this));
        getCommand("rtp").setTabCompleter(tabCompleter);

        getCommand("top").setExecutor(new TopCommand(this));
        getCommand("top").setTabCompleter(tabCompleter);

        getCommand("eteleport").setExecutor(new TeleportAdminCommand(this));
        getCommand("eteleport").setTabCompleter(tabCompleter);

        getCommand("ahome").setExecutor(new AdminHomeCommand(this));
        getCommand("ahome").setTabCompleter(tabCompleter);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(teleportManager, this);
        Bukkit.getPluginManager().registerEvents(requestManager, this);
        Bukkit.getPluginManager().registerEvents(backManager, this);
        Bukkit.getPluginManager().registerEvents(combatManager, this);
        Bukkit.getPluginManager().registerEvents(homeGuiManager, this);
        Bukkit.getPluginManager().registerEvents(inventoryViewCommand, this);
        Bukkit.getPluginManager().registerEvents(spawnManager, this);
        Bukkit.getPluginManager().registerEvents(lastLocationManager, this);
        Bukkit.getPluginManager().registerEvents(offlineNameCache, this);
    }

    public PluginConfigManager getPluginConfigManager() {
        return pluginConfigManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TeleportRequestManager getRequestManager() {
        return requestManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public RtpManager getRtpManager() {
        return rtpManager;
    }

    public CombatTagManager getCombatManager() {
        return combatManager;
    }

    public HomeGuiManager getHomeGuiManager() {
        return homeGuiManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public BackManager getBackManager() {
        return backManager;
    }

    public AdminLogManager getAdminLogManager() {
        return adminLogManager;
    }

    public LastLocationManager getLastLocationManager() {
        return lastLocationManager;
    }

    public OfflineNameCache getOfflineNameCache() {
        return offlineNameCache;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public TaskCoordinator getTaskCoordinator() {
        return taskCoordinator;
    }
}
