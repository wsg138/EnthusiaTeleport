package org.enthusia.teleport.home;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.SafeLocationFinder;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.*;

public final class HomeGuiManager implements Listener {
    private static final String PLACEHOLDER_TARGET = "target";
    private static final String PLACEHOLDER_NAME = "name";
    private static final int INVENTORY_ROW_SIZE = 9;
    private static final int MIN_GUI_SIZE = 9;
    private static final int MAX_GUI_SIZE = 54;
    private static final int COORDINATE_DECIMAL_FACTOR = 10;

    private final EnthusiaTeleportPlugin plugin;

    public HomeGuiManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHomeGui(Player player) {
        Messages msg = plugin.getMessages();
        HomeManager hm = plugin.getHomeManager();

        if (hm.isOverLimit(player)) {
            openLimitGui(player);
            msg.send(player, "home.limit-select.required");
            return;
        }

        Collection<Home> homes = hm.getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            msg.send(player, "home.no-homes");
            return;
        }

        int size = inventorySizeFor(homes.size());

        String title = msg.color(msg.rawOr("home.gui.title", "&2Your Homes"));

        Inventory inv = Bukkit.createInventory(
                new HomeGuiHolder(player.getUniqueId()),
                size,
                title
        );

        for (Home home : homes) {
            ItemStack item = new ItemStack(Material.OAK_DOOR);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + home.getName());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + home.getWorldName());
                lore.add(ChatColor.GRAY + String.format("X: %s Y: %s Z: %s",
                        ChatColor.YELLOW + formatCoordinate(home.getX()),
                        ChatColor.YELLOW + formatCoordinate(home.getY()),
                        ChatColor.YELLOW + formatCoordinate(home.getZ())
                ));
                lore.add(ChatColor.DARK_GRAY + "Click to teleport.");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }

        player.openInventory(inv);
    }

    public void openAdminHomeGui(Player viewer, OfflinePlayer target, String targetNameFallback) {
        Messages msg = plugin.getMessages();
        HomeManager hm = plugin.getHomeManager();

        String targetName = target.getName() != null ? target.getName() : targetNameFallback;
        Collection<Home> homes = hm.getHomes(target.getUniqueId());
        if (homes.isEmpty()) {
            msg.send(viewer, "admin.homes.no-homes", Map.of("target", targetName));
            return;
        }

        int size = inventorySizeFor(homes.size());

        String rawTitle = msg.rawOr("admin.homes.gui.title", "&2Homes: &e{target}");
        String title = msg.color(rawTitle.replace("{target}", targetName));

        AdminHomeGuiHolder holder = new AdminHomeGuiHolder(viewer.getUniqueId(), target.getUniqueId(), targetName);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        boolean canDelete = viewer.hasPermission("enthusia.teleport.admin.homes.delete");
        for (Home home : homes) {
            inv.addItem(buildAdminHomeItem(home, canDelete));
        }

        viewer.openInventory(inv);
    }

    public void openLimitGui(Player player) {
        Messages msg = plugin.getMessages();
        HomeManager hm = plugin.getHomeManager();

        Collection<Home> homes = hm.getHomes(player.getUniqueId());
        int limit = hm.getHomeLimit(player);

        int size = inventorySizeFor(Math.max(1, homes.size()));

        String title = msg.color(msg.rawOr("home.limit-select.title", "&cChoose homes to keep"));

        HomeLimitGuiHolder holder = new HomeLimitGuiHolder(player.getUniqueId(), limit);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        int slot = 0;
        for (Home home : homes) {
            if (slot >= size - 1) break; // reserve last slot for confirm
            inv.setItem(slot++, buildHomeItem(home.getName(), false));
        }

        inv.setItem(size - 1, buildConfirmItem(holder.getSelected().size(), limit));
        if (size >= 2) {
            inv.setItem(size - 2, buildInfoItem(limit));
        }

        player.openInventory(inv);
    }

    private ItemStack buildHomeItem(String name, boolean selected) {
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String title = (selected ? ChatColor.GREEN : ChatColor.RED) + name;
            meta.setDisplayName(title);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + (selected ? "Selected to keep." : "Click to keep this home."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int inventorySizeFor(int itemCount) {
        int size = ((itemCount - 1) / INVENTORY_ROW_SIZE + 1) * INVENTORY_ROW_SIZE;
        return Math.min(MAX_GUI_SIZE, Math.max(MIN_GUI_SIZE, size));
    }

    private ItemStack buildAdminHomeItem(Home home, boolean canDelete) {
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + home.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + home.getWorldName());
            lore.add(ChatColor.GRAY + String.format("X: %s Y: %s Z: %s",
                    ChatColor.YELLOW + formatCoordinate(home.getX()),
                    ChatColor.YELLOW + formatCoordinate(home.getY()),
                    ChatColor.YELLOW + formatCoordinate(home.getZ())
            ));
            lore.add(ChatColor.DARK_GRAY + "Click to teleport.");
            if (canDelete) {
                lore.add(ChatColor.DARK_GRAY + "Shift+Right-click to delete.");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatCoordinate(double value) {
        return Double.toString(Math.round(value * COORDINATE_DECIMAL_FACTOR) / (double) COORDINATE_DECIMAL_FACTOR);
    }

    private ItemStack buildConfirmItem(int selected, int limit) {
        boolean ready = selected == limit;
        Material mat = ready ? Material.EMERALD_BLOCK : Material.BARRIER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String title = ready ? "&aConfirm selection" : "&cSelect homes to keep";
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));
            List<String> lore = new ArrayList<>();
            if (ready) {
                lore.add(ChatColor.GRAY + "Click to remove unselected homes.");
            } else {
                lore.add(ChatColor.GRAY + "Selected: " + ChatColor.YELLOW + selected);
                lore.add(ChatColor.GRAY + "Needed: " + ChatColor.YELLOW + limit);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildInfoItem(int limit) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Home limit: " + limit);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Choose exactly " + limit + " homes.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Teleport to a home by name, with safety + /home name force support.
     */
    public void teleportToHome(Player player, String name, boolean force) {
        Messages msg = plugin.getMessages();
        HomeManager hm = plugin.getHomeManager();

        if (hm.isOverLimit(player)) {
            openLimitGui(player);
            msg.send(player, "home.limit-select.required");
            return;
        }

        Home home = hm.getHome(player.getUniqueId(), name);

        if (home == null) {
            msg.send(player, "home.unknown", Map.of("name", name));
            return;
        }

        TeleportManager tp = plugin.getTeleportManager();
        SafeLocationFinder finder = tp.getSafeFinder();

        if (!force) {
            if (!finder.isSafeHomeLocation(home.toLocation())) {
                // Build clickable "[Teleport anyway]" text using messages.yml
                String raw = msg.rawOr(
                        "home.unsafe-teleport-warning",
                        "&cYour home &e{name}&c is unsafe. &e[Teleport anyway]"
                );
                raw = raw.replace("{name}", home.getName());

                String colored = ChatColor.translateAlternateColorCodes('&', raw);
                TextComponent comp = new TextComponent(colored);
                comp.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/home " + home.getName() + " force"
                ));
                player.spigot().sendMessage(comp);
                return;
            }
        }

        tp.startTeleport(
                player,
                home.toLocation(),
                false, // homes already checked for safety before (unless force)
                null,
                "teleport.warmup-start"
        );
    }

    public void teleportAdminToHome(Player player, OfflinePlayer target, Home home) {
        Messages msg = plugin.getMessages();
        if (!player.hasPermission("enthusia.teleport.admin.homes.teleport")) {
            msg.send(player, "generic.no-permission");
            return;
        }

        String targetName = target.getName() != null ? target.getName() : "unknown";
        org.bukkit.Location dest = home.toLocation();

        plugin.getTeleportManager().startTeleport(
                player,
                dest,
                false,
                null,
                "teleport.warmup-start",
                () -> {
                    msg.send(player, "admin.homes.teleported",
                            Map.of("target", targetName, "name", home.getName()));
                    plugin.getAdminLogManager().logHomeTeleport(player, target, home, dest);
                },
                TeleportManager.TeleportFlags.instant()
        );
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof HomeGuiHolder holder) {
            handleHomeGuiClick(event, player, holder);
            return;
        }

        if (event.getInventory().getHolder() instanceof AdminHomeGuiHolder holder) {
            handleAdminHomeGuiClick(event, player, holder);
            return;
        }

        if (event.getInventory().getHolder() instanceof HomeLimitGuiHolder holder) {
            handleLimitGuiClick(event, player, holder);
        }
    }

    private void handleHomeGuiClick(InventoryClickEvent event, Player player, HomeGuiHolder holder) {
        event.setCancelled(true);
        if (!holder.getOwner().equals(player.getUniqueId())) {
            return;
        }
        clickedHomeName(event).ifPresent(homeName -> teleportToHome(player, homeName, false));
    }

    private void handleAdminHomeGuiClick(InventoryClickEvent event, Player player, AdminHomeGuiHolder holder) {
        event.setCancelled(true);
        if (!holder.getViewer().equals(player.getUniqueId())) {
            return;
        }
        clickedHomeName(event).ifPresent(homeName -> handleAdminHomeSelection(event, player, holder, homeName));
    }

    private void handleAdminHomeSelection(InventoryClickEvent event, Player player, AdminHomeGuiHolder holder, String homeName) {
        HomeManager hm = plugin.getHomeManager();
        Home home = hm.getHome(holder.getTarget(), homeName);
        if (home == null) {
            plugin.getMessages().send(player, "home.unknown", Map.of(PLACEHOLDER_NAME, homeName));
            return;
        }

        if (event.isShiftClick() && event.isRightClick()) {
            deleteAdminHome(event, player, holder, hm, homeName, home);
            return;
        }

        teleportAdminToHome(player, Bukkit.getOfflinePlayer(holder.getTarget()), home);
    }

    private void deleteAdminHome(InventoryClickEvent event, Player player, AdminHomeGuiHolder holder, HomeManager hm, String homeName, Home home) {
        if (!player.hasPermission("enthusia.teleport.admin.homes.delete")) {
            plugin.getMessages().send(player, "generic.no-permission");
            return;
        }

        hm.deleteHome(holder.getTarget(), homeName);
        hm.saveAll();
        plugin.getMessages().send(player, "admin.homes.deleted",
                Map.of(PLACEHOLDER_TARGET, holder.getTargetName(), PLACEHOLDER_NAME, home.getName()));
        plugin.getAdminLogManager().logHomeDelete(player, Bukkit.getOfflinePlayer(holder.getTarget()), home);

        event.getInventory().setItem(event.getSlot(), null);
        if (isInventoryEmpty(event.getInventory())) {
            player.closeInventory();
            plugin.getMessages().send(player, "admin.homes.no-homes",
                    Map.of(PLACEHOLDER_TARGET, holder.getTargetName()));
        }
    }

    private void handleLimitGuiClick(InventoryClickEvent event, Player player, HomeLimitGuiHolder holder) {
        event.setCancelled(true);
        if (!holder.getOwner().equals(player.getUniqueId()) || isEmptyClick(event)) {
            return;
        }

        int size = event.getInventory().getSize();
        if (event.getSlot() == size - 1) {
            confirmLimitSelection(player, holder);
            return;
        }
        clickedHomeName(event).ifPresent(homeName -> toggleLimitSelection(event, holder, homeName, size));
    }

    private void confirmLimitSelection(Player player, HomeLimitGuiHolder holder) {
        if (holder.getSelected().size() != holder.getLimit()) {
            return;
        }

        HomeManager hm = plugin.getHomeManager();
        Set<String> selected = holder.getSelected();
        for (Home home : hm.getHomes(player.getUniqueId())) {
            if (!selected.contains(home.getName())) {
                hm.deleteHome(player.getUniqueId(), home.getName());
            }
        }
        hm.saveAll();

        player.closeInventory();
        plugin.getMessages().send(player, "home.limit-select.done");
    }

    private void toggleLimitSelection(InventoryClickEvent event, HomeLimitGuiHolder holder, String homeName, int size) {
        Set<String> selected = holder.getSelected();

        if (selected.contains(homeName)) {
            selected.remove(homeName);
            event.getInventory().setItem(event.getSlot(), buildHomeItem(homeName, false));
        } else {
            if (selected.size() >= holder.getLimit()) {
                return;
            }
            selected.add(homeName);
            event.getInventory().setItem(event.getSlot(), buildHomeItem(homeName, true));
        }

        event.getInventory().setItem(size - 1, buildConfirmItem(selected.size(), holder.getLimit()));
    }

    private Optional<String> clickedHomeName(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return Optional.empty();
        }
        String rawName = ChatColor.stripColor(meta.getDisplayName());
        if (rawName == null || rawName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rawName.toLowerCase(Locale.ROOT));
    }

    private boolean isEmptyClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        return clicked == null || clicked.getType().isAir();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof HomeLimitGuiHolder holder)) return;
        if (!holder.getOwner().equals(player.getUniqueId())) return;

        HomeManager hm = plugin.getHomeManager();
        if (hm.isOverLimit(player)) {
            plugin.getMessages().send(player, "home.limit-select.required");
        }
    }

    private boolean isInventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
