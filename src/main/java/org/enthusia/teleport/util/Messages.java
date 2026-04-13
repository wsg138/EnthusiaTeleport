package org.enthusia.teleport.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Messages {

    private final Plugin plugin;
    private File file;
    private FileConfiguration config;

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("messages.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);

        InputStream defaultsStream = plugin.getResource("messages.yml");
        if (defaultsStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save messages.yml defaults: " + e.getMessage());
            }
        }
    }

    public String raw(String key) {
        if (config == null) return null;
        return config.getString(key);
    }

    public String rawOr(String key, String def) {
        String v = raw(key);
        return v != null ? v : def;
    }

    public String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders) {
        if (message == null) return null;
        if (placeholders == null || placeholders.isEmpty()) return message;

        String out = message;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String token = "{" + e.getKey() + "}";
            out = out.replace(token, e.getValue());
        }
        return out;
    }

    public void send(CommandSender sender, String key) {
        String msg = raw(key);
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(color(msg));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = raw(key);
        if (msg == null || msg.isEmpty()) return;
        msg = applyPlaceholders(msg, placeholders);
        sender.sendMessage(color(msg));
    }
}
