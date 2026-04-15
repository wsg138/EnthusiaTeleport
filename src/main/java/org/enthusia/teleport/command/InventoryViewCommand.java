package org.enthusia.teleport.command;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.invsee.InvseeHolder;
import org.enthusia.teleport.util.Messages;

import java.util.Locale;
import java.util.Map;

public class InventoryViewCommand implements CommandExecutor, Listener {

    private static final String VIEW_PERMISSION = "enthusia.teleport.invsee";
    private static final String EDIT_PERMISSION = "enthusia.teleport.invsee.edit";
    private static final String ENDER_VIEW_PERMISSION = "enthusia.teleport.endersee";
    private static final String ENDER_EDIT_PERMISSION = "enthusia.teleport.endersee.edit";

    private final EnthusiaTeleportPlugin plugin;

    public InventoryViewCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();
        if (!(sender instanceof Player viewer)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length < 1) {
            viewer.sendMessage(msg.color("&cUsage: /" + label.toLowerCase(Locale.ROOT) + " <player>"));
            return true;
        }

        String commandName = cmd.getName().toLowerCase(Locale.ROOT);
        boolean inventoryView = commandName.equals("invsee") || commandName.equals("inventorysee");
        boolean editable = checkPermission(viewer, inventoryView);
        if (!editable && !hasViewPermission(viewer, inventoryView)) {
            msg.send(viewer, "generic.no-permission");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            msg.send(viewer, "invsee.target-offline", Map.of("target", args[0]));
            return true;
        }

        if (inventoryView) {
            openInventoryView(viewer, target, editable);
        } else {
            openEnderView(viewer, target, editable);
        }
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvseeHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (!holder.getViewerId().equals(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!holder.isEditable()) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        if (holder.getViewType() == InvseeHolder.ViewType.INVENTORY) {
            if (isFillerSlot(event.getRawSlot())) {
                event.setCancelled(true);
                return;
            }
            if (!event.getClickedInventory().equals(top) && event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (holder.getViewType() == InvseeHolder.ViewType.INVENTORY && event.getRawSlot() >= 45 && event.getRawSlot() <= 48 && event.getClickedInventory().equals(top)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isValidArmorForSlot(event.getRawSlot(), cursor.getType())) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> syncHolder(top, holder));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvseeHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player viewer) || !holder.getViewerId().equals(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!holder.isEditable()) {
            event.setCancelled(true);
            return;
        }
        if (holder.getViewType() == InvseeHolder.ViewType.INVENTORY) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= event.getView().getTopInventory().getSize()) {
                    continue;
                }
                if (isFillerSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
                if (rawSlot >= 45 && rawSlot <= 48) {
                    ItemStack newItem = event.getOldCursor();
                    if (newItem != null && !newItem.getType().isAir() && !isValidArmorForSlot(rawSlot, newItem.getType())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> syncHolder(top, holder));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (top.getHolder() instanceof InvseeHolder holder && holder.isEditable()) {
            syncHolder(top, holder);
        }
    }

    private void openInventoryView(Player viewer, Player target, boolean editable) {
        Messages msg = plugin.getMessages();
        Inventory gui = Bukkit.createInventory(
                new InvseeHolder(viewer.getUniqueId(), target.getUniqueId(), InvseeHolder.ViewType.INVENTORY, editable),
                54,
                ChatColor.DARK_GREEN + "Inventory: " + target.getName()
        );

        PlayerInventory targetInventory = target.getInventory();
        for (int slot = 0; slot <= 8; slot++) {
            gui.setItem(slot, cloneOrNull(targetInventory.getItem(slot)));
        }
        for (int slot = 9; slot <= 35; slot++) {
            gui.setItem(slot, cloneOrNull(targetInventory.getItem(slot)));
        }

        ItemStack filler = isBedrock(viewer) ? null : createFiller();
        for (int slot = 36; slot <= 44; slot++) {
            gui.setItem(slot, filler);
        }
        gui.setItem(45, cloneOrNull(targetInventory.getBoots()));
        gui.setItem(46, cloneOrNull(targetInventory.getLeggings()));
        gui.setItem(47, cloneOrNull(targetInventory.getChestplate()));
        gui.setItem(48, cloneOrNull(targetInventory.getHelmet()));
        gui.setItem(49, filler);
        gui.setItem(50, filler);
        gui.setItem(51, filler);
        gui.setItem(52, cloneOrNull(targetInventory.getItemInOffHand()));
        gui.setItem(53, filler);

        viewer.openInventory(gui);
        sendSwapHint(viewer, msg.rawOr("invsee.opened-inv", "&aViewing &e{target}&a's inventory."), target.getName(), "/endersee " + target.getName());
    }

    private void openEnderView(Player viewer, Player target, boolean editable) {
        Messages msg = plugin.getMessages();
        Inventory gui = Bukkit.createInventory(
                new InvseeHolder(viewer.getUniqueId(), target.getUniqueId(), InvseeHolder.ViewType.ENDER_CHEST, editable),
                target.getEnderChest().getSize(),
                ChatColor.DARK_GREEN + "Ender Chest: " + target.getName()
        );
        for (int slot = 0; slot < target.getEnderChest().getSize(); slot++) {
            gui.setItem(slot, cloneOrNull(target.getEnderChest().getItem(slot)));
        }
        viewer.openInventory(gui);
        sendSwapHint(viewer, msg.rawOr("invsee.opened-ender", "&aViewing &e{target}&a's ender chest."), target.getName(), "/invsee " + target.getName());
    }

    private void syncHolder(Inventory inventory, InvseeHolder holder) {
        Player target = Bukkit.getPlayer(holder.getTargetId());
        if (target == null || !target.isOnline()) {
            return;
        }

        if (holder.getViewType() == InvseeHolder.ViewType.ENDER_CHEST) {
            for (int slot = 0; slot < target.getEnderChest().getSize(); slot++) {
                target.getEnderChest().setItem(slot, cloneOrNull(inventory.getItem(slot)));
            }
            return;
        }

        PlayerInventory targetInventory = target.getInventory();
        for (int slot = 0; slot <= 8; slot++) {
            targetInventory.setItem(slot, cloneOrNull(inventory.getItem(slot)));
        }
        for (int slot = 9; slot <= 35; slot++) {
            targetInventory.setItem(slot, cloneOrNull(inventory.getItem(slot)));
        }
        targetInventory.setBoots(cloneOrNull(inventory.getItem(45)));
        targetInventory.setLeggings(cloneOrNull(inventory.getItem(46)));
        targetInventory.setChestplate(cloneOrNull(inventory.getItem(47)));
        targetInventory.setHelmet(cloneOrNull(inventory.getItem(48)));
        targetInventory.setItemInOffHand(cloneOrNull(inventory.getItem(52)));
        target.updateInventory();
    }

    private boolean hasViewPermission(Player viewer, boolean inventoryView) {
        return viewer.hasPermission(inventoryView ? VIEW_PERMISSION : ENDER_VIEW_PERMISSION);
    }

    private boolean checkPermission(Player viewer, boolean inventoryView) {
        return viewer.hasPermission(inventoryView ? EDIT_PERMISSION : ENDER_EDIT_PERMISSION);
    }

    private void sendSwapHint(Player viewer, String rawMessage, String targetName, String command) {
        String colored = ChatColor.translateAlternateColorCodes('&', rawMessage.replace("{target}", targetName));
        TextComponent component = new TextComponent(colored);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        viewer.spigot().sendMessage(component);
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isBedrock(Player player) {
        return player.getName().startsWith("*");
    }

    private boolean isValidArmorForSlot(int slot, Material material) {
        if (material.isAir()) {
            return true;
        }
        String name = material.name();
        return switch (slot) {
            case 45 -> name.endsWith("_BOOTS");
            case 46 -> name.endsWith("_LEGGINGS");
            case 47 -> name.endsWith("_CHESTPLATE");
            case 48 -> name.endsWith("_HELMET");
            default -> true;
        };
    }

    private boolean isFillerSlot(int slot) {
        return (slot >= 36 && slot <= 44) || slot == 49 || slot == 50 || slot == 51 || slot == 53;
    }

    private ItemStack cloneOrNull(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }
}
