package org.enthusia.teleport.command;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.BedAccessPolicy;
import org.enthusia.teleport.home.BedHome;
import org.enthusia.teleport.home.BedHomeManager;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Teleports players to persistent bed homes, enforces the single-bed policy,
 * and provides migration cleanup for players who already have multiple beds.
 */
public class BedCommand implements CommandExecutor, Listener {

    private static final int BED_ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int CONFIRM_DELETE_SLOT = 11;
    private static final int CANCEL_DELETE_SLOT = 15;

    private final EnthusiaTeleportPlugin plugin;
    private final Map<UUID, BedBlockKey> recentBedInteractions = new HashMap<>();

    public BedCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        BedHomeManager beds = plugin.getBedHomeManager();
        if (args.length == 0) {
            if (isOverLimit(player)) {
                sendOverLimit(player);
                return true;
            }
            BedHome mostRecent = beds.getMostRecentBed(player.getUniqueId());
            if (mostRecent == null) {
                sendOrFallback(msg, player, "bed.not-set", "&cYou do not have a bed home set.");
                return true;
            }
            teleportToBed(player, mostRecent);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list" -> listBeds(player);
            case "manage" -> manageBeds(player);
            case "delete", "del", "remove" -> deleteBed(player, args);
            case "rename" -> renameBed(player, args);
            case "help" -> showUsage(player);
            default -> teleportNamedBed(player, args[0]);
        };
    }

    /**
     * Capture the exact bed block the player interacted with so PlayerSetSpawnEvent can distinguish
     * sleeping in the already-saved bed from attempting to save a different bed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        BedBlockKey key = canonicalBed(event.getBed());
        if (key == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        recentBedInteractions.put(playerId, key);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentBedInteractions.remove(playerId, key), 10L);
    }

    /**
     * Minecraft normally changes the player's bed spawn whenever a bed is used. EnthusiaTeleport
     * intentionally keeps one persistent /bed instead: the first bed is saved, the same bed may be
     * refreshed, and a different bed is ignored until the existing saved bed is explicitly deleted.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED || event.getLocation() == null) {
            return;
        }

        Player player = event.getPlayer();
        Collection<BedHome> homes = plugin.getBedHomeManager().getBeds(player.getUniqueId());
        if (BedAccessPolicy.canCreateNew(homes)) {
            return;
        }

        BedBlockKey interacted = recentBedInteractions.remove(player.getUniqueId());
        if (!BedAccessPolicy.isOverLimit(homes)
                && interacted != null
                && BedAccessPolicy.matchesOnlySavedBed(
                        homes, interacted.worldName(), interacted.x(), interacted.y(), interacted.z())) {
            return;
        }

        // Cancelling PlayerSetSpawnEvent only prevents the respawn-point change. It does not stop
        // the player from sleeping, which is exactly the behavior wanted for non-saved beds.
        event.setNotifyPlayer(false);
        event.setCancelled(true);

        if (BedAccessPolicy.isOverLimit(homes)) {
            sendOverLimit(player);
            return;
        }

        BedHome current = homes.iterator().next();
        sendBedNotChanged(player, current);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isOverLimit(player)) {
                sendOverLimit(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recentBedInteractions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BedManageHolder) && !(holder instanceof BedDeleteConfirmHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof BedManageHolder manageHolder) {
            handleManageClick(player, event, manageHolder);
        } else if (holder instanceof BedDeleteConfirmHolder confirmHolder) {
            handleDeleteConfirmClick(player, event, confirmHolder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BedManageHolder) && !(holder instanceof BedDeleteConfirmHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean teleportNamedBed(Player player, String name) {
        if (isOverLimit(player)) {
            sendOverLimit(player);
            return true;
        }

        BedHome home = plugin.getBedHomeManager().getBed(player.getUniqueId(), name);
        if (home == null) {
            player.sendMessage(plugin.getMessages().color("&cUnknown bed home: &e" + name + "&c."));
            return true;
        }
        teleportToBed(player, home);
        return true;
    }

    private void teleportToBed(Player player, BedHome home) {
        if (isOverLimit(player)) {
            sendOverLimit(player);
            return;
        }

        BedHomeManager beds = plugin.getBedHomeManager();
        Messages msg = plugin.getMessages();
        if (!beds.isBedPresent(home)) {
            beds.deleteBed(player.getUniqueId(), home.getName());
            beds.saveAll();
            player.sendMessage(msg.color("&cYour bed (&e" + home.getName() + "&c) is missing."));
            return;
        }

        Location initialLocation = home.toLocation();
        if (initialLocation == null || initialLocation.getWorld() == null) {
            sendOrFallback(msg, player, "bed.missing", "&cYour bed is missing or unavailable.");
            return;
        }

        TeleportManager tpMgr = plugin.getTeleportManager();
        if (msg.raw("bed.warmup-start") == null) {
            player.sendMessage(msg.color("&eTeleporting to your bed. Don't move."));
        }

        String bedKey = home.getKey();
        tpMgr.startTeleportDynamic(
                player,
                () -> beds.resolveTeleportLocation(player.getUniqueId(), bedKey),
                true,
                null,
                "bed.warmup-start"
        );
    }

    private boolean listBeds(Player player) {
        Collection<BedHome> homes = plugin.getBedHomeManager().getBeds(player.getUniqueId());
        if (homes.isEmpty()) {
            player.sendMessage(plugin.getMessages().color("&cYou do not have any bed homes set."));
            return true;
        }

        if (BedAccessPolicy.isOverLimit(homes)) {
            sendOverLimit(player);
        }

        BedHome mostRecent = plugin.getBedHomeManager().getMostRecentBed(player.getUniqueId());
        player.sendMessage(plugin.getMessages().color("&6Your bed homes:"));
        for (BedHome home : homes) {
            String lastUsed = mostRecent != null && mostRecent.getKey().equals(home.getKey()) ? " &a(last used)" : "";
            player.sendMessage(plugin.getMessages().color(
                    "&7- &e" + home.getName() + " &7(" + home.getWorldName() + " "
                            + home.getBedX() + " " + home.getBedY() + " " + home.getBedZ() + ")" + lastUsed
            ));
        }
        return true;
    }

    private boolean manageBeds(Player player) {
        openManageMenu(player, 0, true);
        return true;
    }

    private boolean deleteBed(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().color("&cUsage: &e/bed delete <name>"));
            return true;
        }

        int beforeCount = plugin.getBedHomeManager().getBeds(player.getUniqueId()).size();
        BedHome removed = plugin.getBedHomeManager().deleteBed(player.getUniqueId(), args[1]);
        if (removed == null) {
            player.sendMessage(plugin.getMessages().color("&cUnknown bed home: &e" + args[1] + "&c."));
            return true;
        }

        plugin.getBedHomeManager().saveAll();
        player.sendMessage(plugin.getMessages().color("&aDeleted bed home &e" + removed.getName() + "&a."));
        sendPostDeleteState(player, beforeCount);
        return true;
    }

    private boolean renameBed(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessages().color("&cUsage: &e/bed rename <old_name> <new_name>"));
            return true;
        }

        if ("manage".equalsIgnoreCase(args[2])) {
            player.sendMessage(plugin.getMessages().color(
                    "&cThe name &emanage &cis reserved for the &e/bed manage &ccommand."
            ));
            return true;
        }

        BedHomeManager.RenameResult result = plugin.getBedHomeManager()
                .renameBed(player.getUniqueId(), args[1], args[2]);
        switch (result) {
            case SUCCESS -> {
                plugin.getBedHomeManager().saveAll();
                player.sendMessage(plugin.getMessages().color(
                        "&aRenamed bed home &e" + args[1] + " &ato &e" + args[2] + "&a."
                ));
            }
            case NOT_FOUND -> player.sendMessage(plugin.getMessages().color(
                    "&cUnknown bed home: &e" + args[1] + "&c."
            ));
            case INVALID_NAME -> player.sendMessage(plugin.getMessages().color(
                    "&cBed names must be 1-32 letters, numbers, underscores, or hyphens and cannot be a /bed subcommand."
            ));
            case DUPLICATE -> player.sendMessage(plugin.getMessages().color(
                    "&cYou already have a bed home named &e" + args[2] + "&c."
            ));
        }
        return true;
    }

    private boolean showUsage(Player player) {
        player.sendMessage(plugin.getMessages().color("&e/bed &7- teleport to your saved bed"));
        player.sendMessage(plugin.getMessages().color("&e/bed <name> &7- teleport to a named legacy bed"));
        player.sendMessage(plugin.getMessages().color("&e/bed list &7- list saved beds and coordinates"));
        player.sendMessage(plugin.getMessages().color("&e/bed manage &7- open the bed management menu"));
        player.sendMessage(plugin.getMessages().color("&e/bed delete <name> &7- delete a saved bed"));
        player.sendMessage(plugin.getMessages().color("&e/bed rename <old> <new> &7- rename a saved bed"));
        player.sendMessage(plugin.getMessages().color(
                "&7Only one saved bed is allowed. Delete your current bed before sleeping in a new bed if you want to move /bed."
        ));
        return true;
    }

    private void openManageMenu(Player player, int requestedPage, boolean announceOverLimit) {
        List<BedHome> homes = new ArrayList<>(plugin.getBedHomeManager().getBeds(player.getUniqueId()));
        if (homes.isEmpty()) {
            player.closeInventory();
            player.sendMessage(plugin.getMessages().color(
                    "&eYou do not have a saved bed. &7The next bed you successfully sleep in will become your /bed."
            ));
            return;
        }

        if (announceOverLimit && BedAccessPolicy.isOverLimit(homes)) {
            sendOverLimit(player);
        }

        int totalPages = Math.max(1, (homes.size() + BED_ITEMS_PER_PAGE - 1) / BED_ITEMS_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int fromIndex = page * BED_ITEMS_PER_PAGE;
        int toIndex = Math.min(homes.size(), fromIndex + BED_ITEMS_PER_PAGE);
        List<BedHome> visibleHomes = homes.subList(fromIndex, toIndex);
        List<String> visibleKeys = visibleHomes.stream().map(BedHome::getKey).toList();

        BedManageHolder holder = new BedManageHolder(player.getUniqueId(), page, visibleKeys);
        String title = totalPages > 1
                ? plugin.getMessages().color("&8Bed Management &7(" + (page + 1) + "/" + totalPages + ")")
                : plugin.getMessages().color("&8Bed Management");
        Inventory inventory = Bukkit.createInventory(holder, 54, title);

        for (int slot = 0; slot < visibleHomes.size(); slot++) {
            inventory.setItem(slot, createBedItem(visibleHomes.get(slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createSimpleItem(Material.ARROW, "&ePrevious page", List.of()));
        }
        inventory.setItem(INFO_SLOT, createInfoItem(homes.size()));
        if (page + 1 < totalPages) {
            inventory.setItem(NEXT_PAGE_SLOT, createSimpleItem(Material.ARROW, "&eNext page", List.of()));
        }

        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String bedKey, int returnPage) {
        BedHome home = plugin.getBedHomeManager().getBed(player.getUniqueId(), bedKey);
        if (home == null) {
            openManageMenu(player, returnPage, false);
            return;
        }

        BedDeleteConfirmHolder holder = new BedDeleteConfirmHolder(player.getUniqueId(), bedKey, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getMessages().color("&8Delete saved bed?"));
        inventory.setItem(13, createBedItem(home));
        inventory.setItem(CONFIRM_DELETE_SLOT, createSimpleItem(
                Material.RED_CONCRETE,
                "&cDelete this bed",
                List.of("&7This removes the saved teleport location.", "&cThis cannot be undone through /bed.")
        ));
        inventory.setItem(CANCEL_DELETE_SLOT, createSimpleItem(
                Material.LIME_CONCRETE,
                "&aKeep this bed",
                List.of("&7Return without deleting it.")
        ));
        player.openInventory(inventory);
    }

    private void handleManageClick(Player player, InventoryClickEvent event, BedManageHolder holder) {
        if (!holder.owner().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (rawSlot == PREVIOUS_PAGE_SLOT && holder.page() > 0) {
            openManageMenu(player, holder.page() - 1, false);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT) {
            openManageMenu(player, holder.page() + 1, false);
            return;
        }

        String bedKey = holder.keyAt(rawSlot);
        if (bedKey != null) {
            openDeleteConfirm(player, bedKey, holder.page());
        }
    }

    private void handleDeleteConfirmClick(Player player, InventoryClickEvent event, BedDeleteConfirmHolder holder) {
        if (!holder.owner().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == CANCEL_DELETE_SLOT) {
            openManageMenu(player, holder.returnPage(), false);
            return;
        }
        if (rawSlot != CONFIRM_DELETE_SLOT) {
            return;
        }

        int beforeCount = plugin.getBedHomeManager().getBeds(player.getUniqueId()).size();
        BedHome removed = plugin.getBedHomeManager().deleteBed(player.getUniqueId(), holder.bedKey());
        if (removed == null) {
            player.sendMessage(plugin.getMessages().color("&cThat saved bed no longer exists."));
            openManageMenu(player, holder.returnPage(), false);
            return;
        }

        plugin.getBedHomeManager().saveAll();
        player.sendMessage(plugin.getMessages().color("&aDeleted bed home &e" + removed.getName() + "&a."));

        int afterCount = plugin.getBedHomeManager().getBeds(player.getUniqueId()).size();
        if (beforeCount > BedAccessPolicy.MAX_SAVED_BEDS && afterCount == BedAccessPolicy.MAX_SAVED_BEDS) {
            player.closeInventory();
            sendMigrationComplete(player);
            return;
        }
        if (afterCount == 0) {
            player.closeInventory();
            player.sendMessage(plugin.getMessages().color(
                    "&eYou no longer have a saved bed. &7The next bed you successfully sleep in will become your /bed."
            ));
            return;
        }

        openManageMenu(player, holder.returnPage(), false);
        if (afterCount > BedAccessPolicy.MAX_SAVED_BEDS) {
            player.sendMessage(plugin.getMessages().color(
                    "&eYou still have " + afterCount + " saved beds. Delete extras until only one remains."
            ));
        }
    }

    private ItemStack createBedItem(BedHome home) {
        return createSimpleItem(
                Material.RED_BED,
                "&e" + home.getName(),
                List.of(
                        "&7World: &f" + home.getWorldName(),
                        "&7Coordinates: &f" + home.getBedX() + ", " + home.getBedY() + ", " + home.getBedZ(),
                        "",
                        "&cClick to delete this saved bed."
                )
        );
    }

    private ItemStack createInfoItem(int bedCount) {
        if (bedCount > BedAccessPolicy.MAX_SAVED_BEDS) {
            return createSimpleItem(
                    Material.BARRIER,
                    "&cToo many saved beds",
                    List.of(
                            "&7You have &f" + bedCount + " &7saved beds.",
                            "&7Only &f1 &7is allowed.",
                            "&7Bed teleporting is disabled until", 
                            "&7you delete the extras."
                    )
            );
        }

        return createSimpleItem(
                Material.BOOK,
                "&eSaved bed management",
                List.of(
                        "&7You may keep one saved bed.",
                        "&7Delete it before sleeping in a", 
                        "&7different bed if you want to move /bed."
                )
        );
    }

    private ItemStack createSimpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(plugin.getMessages().color(name));
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(plugin.getMessages()::color).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void sendPostDeleteState(Player player, int beforeCount) {
        List<BedHome> remaining = new ArrayList<>(plugin.getBedHomeManager().getBeds(player.getUniqueId()));
        int afterCount = remaining.size();

        if (beforeCount > BedAccessPolicy.MAX_SAVED_BEDS && afterCount == BedAccessPolicy.MAX_SAVED_BEDS) {
            sendMigrationComplete(player);
            return;
        }
        if (afterCount > BedAccessPolicy.MAX_SAVED_BEDS) {
            player.sendMessage(plugin.getMessages().color(
                    "&eYou still have " + afterCount + " saved beds. Delete extras until only one remains."
            ));
            return;
        }
        if (afterCount == 0) {
            player.sendMessage(plugin.getMessages().color(
                    "&eYou can now sleep in a new bed to save it as your /bed."
            ));
        }
    }

    private void sendMigrationComplete(Player player) {
        BedHome remaining = plugin.getBedHomeManager().getMostRecentBed(player.getUniqueId());
        if (remaining == null) {
            player.sendMessage(plugin.getMessages().color(
                    "&aBed cleanup complete. &7You have no saved bed; sleep in one to set it."
            ));
            return;
        }

        player.sendMessage(plugin.getMessages().color(
                "&aBed cleanup complete. &7Your remaining saved bed is &e" + remaining.getName()
                        + " &7at &f" + remaining.getWorldName() + " " + remaining.getBedX() + " "
                        + remaining.getBedY() + " " + remaining.getBedZ() + "&7. Bed teleporting is unlocked."
        ));
    }

    private boolean isOverLimit(Player player) {
        return BedAccessPolicy.isOverLimit(plugin.getBedHomeManager().getBeds(player.getUniqueId()));
    }

    private void sendOverLimit(Player player) {
        int count = plugin.getBedHomeManager().getBeds(player.getUniqueId()).size();
        player.sendMessage(plugin.getMessages().color(
                "&cYou have &e" + count + " &csaved beds from the old system, but only &e1 &cis allowed. "
                        + "Bed teleporting is disabled until you remove the extras. Use &e/bed manage &cor "
                        + "&e/bed delete <name>&c."
        ));
    }

    private void sendBedNotChanged(Player player, BedHome current) {
        player.sendMessage(plugin.getMessages().color(
                "&eThis bed was not saved as your /bed. &cYou already have a saved bed (&e" + current.getName()
                        + "&c) at &e" + current.getWorldName() + " " + current.getBedX() + " "
                        + current.getBedY() + " " + current.getBedZ() + "&c. Delete your current bed with &e/bed delete "
                        + current.getName() + " &cor &e/bed manage &cbefore sleeping in a new bed."
        ));
    }

    private BedBlockKey canonicalBed(Block block) {
        if (!(block.getBlockData() instanceof Bed bedData)) {
            return null;
        }

        Block head = bedData.getPart() == Bed.Part.HEAD
                ? block
                : block.getRelative(bedData.getFacing());
        return new BedBlockKey(head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
    }

    private void sendOrFallback(Messages msg, Player player, String key, String fallback) {
        String raw = msg.raw(key);
        if (raw == null || raw.isEmpty()) {
            player.sendMessage(msg.color(fallback));
        } else {
            msg.send(player, key);
        }
    }

    private record BedBlockKey(String worldName, int x, int y, int z) {
    }

    private static final class BedManageHolder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private final List<String> visibleBedKeys;

        private BedManageHolder(UUID owner, int page, List<String> visibleBedKeys) {
            this.owner = owner;
            this.page = page;
            this.visibleBedKeys = List.copyOf(visibleBedKeys);
        }

        private UUID owner() {
            return owner;
        }

        private int page() {
            return page;
        }

        private String keyAt(int slot) {
            return slot >= 0 && slot < visibleBedKeys.size() ? visibleBedKeys.get(slot) : null;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class BedDeleteConfirmHolder implements InventoryHolder {
        private final UUID owner;
        private final String bedKey;
        private final int returnPage;

        private BedDeleteConfirmHolder(UUID owner, String bedKey, int returnPage) {
            this.owner = owner;
            this.bedKey = bedKey;
            this.returnPage = returnPage;
        }

        private UUID owner() {
            return owner;
        }

        private String bedKey() {
            return bedKey;
        }

        private int returnPage() {
            return returnPage;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
