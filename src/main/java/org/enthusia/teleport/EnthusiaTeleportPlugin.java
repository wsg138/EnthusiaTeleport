package org.enthusia.teleport;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.enthusia.teleport.api.TeleportApi;
import org.enthusia.teleport.back.BackManager;
import org.enthusia.teleport.combat.CombatTagManager;
import org.enthusia.teleport.command.*;
import org.enthusia.teleport.home.HomeGuiManager;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.ignore.IgnoreManager;
import org.enthusia.teleport.log.AdminLogManager;
import org.enthusia.teleport.log.ChatLogListener;
import org.enthusia.teleport.log.MessageLogManager;
import org.enthusia.teleport.message.MessageManager;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.rtp.RtpManager;
import org.enthusia.teleport.spawn.SpawnManager;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

public class EnthusiaTeleportPlugin extends JavaPlugin {

    private static EnthusiaTeleportPlugin instance;

    private Messages messages;
    private TeleportManager teleportManager;
    private TeleportRequestManager requestManager;
    private HomeManager homeManager;
    private IgnoreManager ignoreManager;
    private RtpManager rtpManager;
    private CombatTagManager combatManager;
    private HomeGuiManager homeGuiManager;
    private InventoryViewCommand inventoryViewCommand;
    private SpawnManager spawnManager;
    private BackManager backManager;
    private MessageManager messageManager;
    private MessageLogManager messageLogManager;
    private AdminLogManager adminLogManager;

    public static EnthusiaTeleportPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        applyDefaultConfigValues();
        saveResource("messages.yml", false);

        this.messages = new Messages(this);
        this.messageLogManager = new MessageLogManager(this);
        this.adminLogManager = new AdminLogManager(this);
        this.messageManager = new MessageManager(this);
        this.ignoreManager = new IgnoreManager(this);
        this.homeManager = new HomeManager(this);
        this.backManager = new BackManager(this);
        this.teleportManager = new TeleportManager(this);
        this.requestManager = new TeleportRequestManager(this);
        this.rtpManager = new RtpManager(this);
        this.combatManager = new CombatTagManager(this);
        this.homeGuiManager = new HomeGuiManager(this);
        this.inventoryViewCommand = new InventoryViewCommand(this);
        this.spawnManager = new SpawnManager(this);

        Bukkit.getServicesManager().register(
                TeleportApi.class,
                teleportManager,
                this,
                ServicePriority.Normal
        );

        registerCommands();
        registerListeners();

        getLogger().info("EnthusiaTeleport enabled.");
    }

    @Override
    public void onDisable() {
        teleportManager.shutdown();
        requestManager.shutdown();
        homeManager.saveAll();
        ignoreManager.saveAll();
        rtpManager.saveAll();
        Bukkit.getServicesManager().unregister(TeleportApi.class, teleportManager);
    }

    private void registerCommands() {
        TeleportTabCompleter tab = new TeleportTabCompleter(this);

        // TPA system
        getCommand("tpa").setExecutor(new TpaCommand(this, false));
        getCommand("tpa").setTabCompleter(tab);

        getCommand("tpask").setExecutor(new TpaCommand(this, false));
        getCommand("tpask").setTabCompleter(tab);

        getCommand("tpahere").setExecutor(new TpaCommand(this, true));
        getCommand("tpahere").setTabCompleter(tab);

        getCommand("tpaccept").setExecutor(new TpAcceptCommand(this));
        getCommand("tpaccept").setTabCompleter(tab);

        getCommand("tpadeny").setExecutor(new TpDenyCommand(this));
        getCommand("tpadeny").setTabCompleter(tab);

        getCommand("tpacancel").setExecutor(new TpCancelCommand(this));
        getCommand("tpacancel").setTabCompleter(tab);

        getCommand("tpignore").setExecutor(new TpIgnoreCommand(this));
        getCommand("tpignore").setTabCompleter(tab);

        getCommand("back").setExecutor(new BackCommand(this));
        getCommand("back").setTabCompleter(tab);

        // Homes
        getCommand("sethome").setExecutor(new SetHomeCommand(this));
        getCommand("sethome").setTabCompleter(tab);

        getCommand("home").setExecutor(new HomeCommand(this));
        getCommand("home").setTabCompleter(tab);

        getCommand("delhome").setExecutor(new DelHomeCommand(this));
        getCommand("delhome").setTabCompleter(tab);

        getCommand("bed").setExecutor(new BedCommand(this));
        getCommand("bed").setTabCompleter(tab);

        // Spawn / tppos
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("spawn").setTabCompleter(tab);

        getCommand("tppos").setExecutor(new TpposCommand(this));
        getCommand("tppos").setTabCompleter(tab);

        getCommand("tpo").setExecutor(new TpoCommand(this));
        getCommand("tpo").setTabCompleter(tab);

        // Invsee / endersee (all share the same handler + same tab)
        getCommand("invsee").setExecutor(inventoryViewCommand);
        getCommand("invsee").setTabCompleter(tab);

        getCommand("inventorysee").setExecutor(inventoryViewCommand);
        getCommand("inventorysee").setTabCompleter(tab);

        getCommand("endersee").setExecutor(inventoryViewCommand);
        getCommand("endersee").setTabCompleter(tab);

        getCommand("enderview").setExecutor(inventoryViewCommand);
        getCommand("enderview").setTabCompleter(tab);

        // RTP / top
        getCommand("rtp").setExecutor(new RtpCommand(this));
        getCommand("rtp").setTabCompleter(tab);

        getCommand("top").setExecutor(new TopCommand(this));
        getCommand("top").setTabCompleter(tab);

        // Admin
        getCommand("eteleport").setExecutor(new TeleportAdminCommand(this));
        getCommand("eteleport").setTabCompleter(tab);
        getCommand("ahome").setExecutor(new AdminHomeCommand(this));
        getCommand("ahome").setTabCompleter(tab);

        // Messaging
        getCommand("msg").setExecutor(new MsgCommand(this));
        getCommand("msg").setTabCompleter(tab);

        getCommand("r").setExecutor(new ReplyCommand(this));
        getCommand("r").setTabCompleter(tab);

        getCommand("msglog").setExecutor(new MsgLogCommand(this));
        getCommand("msglog").setTabCompleter(tab);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(teleportManager, this);
        Bukkit.getPluginManager().registerEvents(combatManager, this);
        Bukkit.getPluginManager().registerEvents(homeGuiManager, this);
        Bukkit.getPluginManager().registerEvents(inventoryViewCommand, this);
        Bukkit.getPluginManager().registerEvents(spawnManager, this);
        Bukkit.getPluginManager().registerEvents(new ChatLogListener(messageLogManager), this);
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

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public MessageLogManager getMessageLogManager() {
        return messageLogManager;
    }

    public AdminLogManager getAdminLogManager() {
        return adminLogManager;
    }

    public void reloadPlugin() {
        applyDefaultConfigValues();
        messages.reload();
        ignoreManager.reload();
        homeManager.reload();
        teleportManager.reloadSettings();
        rtpManager.reload();
        combatManager.reload();
        backManager.reload();
    }

    private void applyDefaultConfigValues() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
    }
}
