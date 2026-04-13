package org.enthusia.teleport.command;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.invsee.InvseeHolder;
import org.enthusia.teleport.util.Messages;

import java.util.Locale;
import java.util.Map;

public class InventoryViewCommand implements CommandExecutor, Listener {

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
            viewer.sendMessage("§cUsage: /" + label.toLowerCase(Locale.ROOT) + " <player>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(viewer, "invsee.target-offline",
                    Map.of("target", targetName));
            return true;
        }

        String cmdName = cmd.getName().toLowerCase(Locale.ROOT);

        if (cmdName.equals("invsee") || cmdName.equals("inventorysee")) {
            openInventoryView(viewer, target);
        } else {
            // endersee / enderview
            openEnderView(viewer, target);
        }

        return true;
    }

    // ------------------------------------------------------------------------
    // /invsee → custom double chest with armor + offhand
    // ------------------------------------------------------------------------

    private void openInventoryView(Player viewer, Player target) {
        Messages msg = plugin.getMessages();

        String title = ChatColor.DARK_GREEN + "Inventory: " + target.getName();
        Inventory gui = Bukkit.createInventory(
                new InvseeHolder(target.getUniqueId()),
                54,
                title
        );

        PlayerInventory targetInv = target.getInventory();
        boolean bedrock = isBedrock(viewer);

        // Row 1: hotbar slots 0–8
        for (int i = 0; i <= 8; i++) {
            gui.setItem(i, safeClone(targetInv.getItem(i)));
        }

        // Rows 2–4: main inventory slots 9–35
        for (int i = 9; i <= 35; i++) {
            gui.setItem(i, safeClone(targetInv.getItem(i)));
        }

        // Fill row 5 (36–44) with filler
        ItemStack filler = bedrock ? null : createFiller();
        for (int i = 36; i <= 44; i++) {
            gui.setItem(i, filler);
        }

        // Row 6 (45–53):
        // 45 boots, 46 leggings, 47 chest, 48 helmet, 49–51 filler, 52 offhand, 53 filler
        gui.setItem(45, safeClone(targetInv.getBoots()));
        gui.setItem(46, safeClone(targetInv.getLeggings()));
        gui.setItem(47, safeClone(targetInv.getChestplate()));
        gui.setItem(48, safeClone(targetInv.getHelmet()));

        gui.setItem(49, filler);
        gui.setItem(50, filler);
        gui.setItem(51, filler);

        gui.setItem(52, safeClone(targetInv.getItemInOffHand()));
        gui.setItem(53, filler);

        viewer.openInventory(gui);

        // Clickable chat to jump to ender chest
        String raw = msg.rawOr(
                "invsee.opened-inv",
                "&aViewing &e{target}&a's inventory. &7[&eClick to view ender chest&7]"
        );
        raw = raw.replace("{target}", target.getName());

        String colored = ChatColor.translateAlternateColorCodes('&', raw);
        TextComponent comp = new TextComponent(colored);
        comp.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/endersee " + target.getName()
        ));
        viewer.spigot().sendMessage(comp);
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

    private ItemStack safeClone(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private boolean isBedrock(Player player) {
        // Floodgate/Geyser bedrock players use the "*" prefix by default
        return player.getName().startsWith("*");
    }

    // ------------------------------------------------------------------------
    // /endersee → just open real ender chest
    // ------------------------------------------------------------------------

    private void openEnderView(Player viewer, Player target) {
        Messages msg = plugin.getMessages();

        viewer.openInventory(target.getEnderChest());

        String raw = msg.rawOr(
                "invsee.opened-ender",
                "&aViewing &e{target}&a's ender chest. &7[&eClick to view inventory&7]"
        );
        raw = raw.replace("{target}", target.getName());

        String colored = ChatColor.translateAlternateColorCodes('&', raw);
        TextComponent comp = new TextComponent(colored);
        comp.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/invsee " + target.getName()
        ));
        viewer.spigot().sendMessage(comp);
    }

    // ------------------------------------------------------------------------
    // Listener: handle clicks in our custom /invsee GUI
    // ------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvseeHolder holder)) return;

        // We only care about this /invsee GUI
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        int slot = event.getRawSlot(); // top inventory slots = 0–53

        // Armor restriction on direct clicks into armor slots
        if (slot >= 45 && slot <= 48 && clickedInv.equals(top)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!isValidArmorForSlot(slot, cursor.getType())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Let Bukkit handle the click, then sync GUI -> target
        Bukkit.getScheduler().runTask(plugin, () -> syncToTarget(holder, top));

        // We do NOT cancel the event (except for bad armor), so editing works.
    }

    private boolean isValidArmorForSlot(int slot, Material mat) {
        if (mat.isAir()) return true; // allow clearing slot

        String name = mat.name();
        return switch (slot) {
            case 45 -> name.endsWith("_BOOTS");
            case 46 -> name.endsWith("_LEGGINGS");
            case 47 -> name.endsWith("_CHESTPLATE");
            case 48 -> name.endsWith("_HELMET");
            default -> true;
        };
    }

    private void syncToTarget(InvseeHolder holder, Inventory gui) {
        Player target = Bukkit.getPlayer(holder.getTargetId());
        if (target == null || !target.isOnline()) {
            return;
        }

        PlayerInventory inv = target.getInventory();

        // Hotbar 0–8
        for (int i = 0; i <= 8; i++) {
            inv.setItem(i, gui.getItem(i));
        }

        // Main inventory 9–35
        for (int i = 9; i <= 35; i++) {
            inv.setItem(i, gui.getItem(i));
        }

        // Armor & offhand
        inv.setBoots(gui.getItem(45));
        inv.setLeggings(gui.getItem(46));
        inv.setChestplate(gui.getItem(47));
        inv.setHelmet(gui.getItem(48));

        inv.setItemInOffHand(gui.getItem(52));

        target.updateInventory();
    }
}
