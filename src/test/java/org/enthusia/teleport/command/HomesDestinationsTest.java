package org.enthusia.teleport.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.enthusia.teleport.home.HomeGuiManager;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.BedHome;
import org.enthusia.teleport.home.Home;
import org.junit.jupiter.api.Test;

class HomesDestinationsTest {
    private final EnthusiaTeleportPlugin plugin = mock(EnthusiaTeleportPlugin.class, RETURNS_DEEP_STUBS);
    private final Player player = mock(Player.class);
    private final Command command = mock(Command.class);
    private final TeleportTabCompleter tabs = new TeleportTabCompleter(plugin);

    HomesDestinationsTest() {
        UUID owner = UUID.randomUUID();
        when(command.getName()).thenReturn("homes");
        when(player.getUniqueId()).thenReturn(owner);
        when(player.hasPermission("enthusia.teleport.bed")).thenReturn(true);
        var home = new Home(owner, "base", "Base", "world", 1, 2, 3, 0, 0, 1);
        var bed = new BedHome(owner, "bunk", "Bunk", "world", 4, 5, 6, 4, 5, 6, 0, 0, 1, 1);
        when(plugin.getHomeManager().getHomes(owner)).thenReturn(List.of(home));
        when(plugin.getHomeManager().getHome(owner, "Base")).thenReturn(home);
        when(plugin.getHomeManager().getHome(owner, "Bunk")).thenReturn(null);
        when(plugin.getBedHomeManager().getBeds(owner)).thenReturn(List.of(bed));
        when(plugin.getBedHomeManager().getBed(owner, "Bunk")).thenReturn(bed);
    }

    @Test
    void playerCompletesPersonalHomesAndRenamedBedByPrefix() {
        assertEquals(List.of("Base", "Bunk"), tabs.onTabComplete(player, command, "homes", new String[]{"B"}));
        assertEquals(List.of("Bunk"), tabs.onTabComplete(player, command, "homes", new String[]{"bu"}));
        when(player.hasPermission("enthusia.teleport.bed")).thenReturn(false);
        assertEquals(List.of("Base"), tabs.onTabComplete(player, command, "homes", new String[]{""}));
    }

    @Test
    void anchorHasItsOwnSelectorEvenWhenANamedHomeIsCalledAnchor() {
        var anchor = mock(org.bukkit.Location.class);
        when(plugin.getAnchorHomes().available(player)).thenReturn(java.util.Optional.of(anchor));
        when(plugin.getHomeManager().getHome(player.getUniqueId(), "anchor")).thenReturn(null);
        when(plugin.getBedHomeManager().getBed(player.getUniqueId(), "anchor")).thenReturn(null);
        assertEquals(List.of("anchor"), tabs.onTabComplete(player, command, "homes", new String[]{"AN"}));
        var executor = new HomeCommand(plugin);
        executor.onCommand(player, command, "homes", new String[]{"anchor"});
        verify(plugin.getAnchorHomes()).teleport(player);

        when(plugin.getHomeManager().getHome(player.getUniqueId(), "anchor")).thenReturn(mock(Home.class));
        executor.onCommand(player, command, "homes", new String[]{"anchor"});
        verify(plugin.getHomeGuiManager()).teleportToHome(player, "anchor", false);
        assertEquals(List.of("respawn"), tabs.onTabComplete(player, command, "homes", new String[]{"anchor", "r"}));
        executor.onCommand(player, command, "homes", new String[]{"anchor", "respawn"});
        verify(plugin.getAnchorHomes(), times(2)).teleport(player);
    }

    @Test
    void personalHomeSuggestionIsExecutableByOrdinaryPlayer() {
        new HomeCommand(plugin).onCommand(player, command, "homes", new String[]{"Base"});
        verify(plugin.getHomeGuiManager()).teleportToHome(player, "Base", false);
    }

    @Test
    void renamedBedAndExplicitBedSelectorUseTheExistingBedTeleport() {
        var executor = new HomeCommand(plugin);
        executor.onCommand(player, command, "homes", new String[]{"Bunk"});
        executor.onCommand(player, command, "homes", new String[]{"bed", "Bunk"});
        verify(plugin.getBedCommand(), times(2)).teleportNamedBed(player, "Bunk");
        assertEquals(List.of("Bunk"), tabs.onTabComplete(player, command, "homes", new String[]{"bed", "bu"}));
    }

    @Test
    void bedOnlyMenuOpensAndClickRoutesToTheSavedBed() {
        checkMenu(false);
    }

    @Test
    void allThreeHomesHaveSeparateIconsAndAnchorClickUsesItsOwnTeleport() {
        checkMenu(true);
    }

    private void checkMenu(boolean includeHomeAndAnchor) {
        if (includeHomeAndAnchor) {
            var anchor = mock(org.bukkit.Location.class, RETURNS_DEEP_STUBS);
            when(anchor.getWorld().getName()).thenReturn("world_nether");
            when(plugin.getAnchorHomes().available(player)).thenReturn(java.util.Optional.of(anchor));
        } else {
            when(plugin.getHomeManager().getHomes(player.getUniqueId())).thenReturn(List.of());
        }
        var manager = new HomeGuiManager(plugin);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(9);
        var materials = new java.util.ArrayList<>();
        try (var bukkit = mockStatic(Bukkit.class);
             var items = mockConstruction(ItemStack.class, (item, context) -> materials.add(context.arguments().get(0)))) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(9), anyString()))
                    .thenAnswer(call -> {
                        InventoryHolder holder = call.getArgument(0);
                        when(inventory.getHolder()).thenReturn(holder);
                        return inventory;
                    });
            when(plugin.getMessages().color(any())).thenReturn("Your Homes");
            manager.openHomeGui(player);
            verify(player).openInventory(inventory);
            assertEquals(includeHomeAndAnchor
                    ? List.of(Material.OAK_DOOR, Material.RED_BED, Material.RESPAWN_ANCHOR)
                    : List.of(Material.RED_BED), materials);
            var click = mock(InventoryClickEvent.class);
            when(click.getWhoClicked()).thenReturn(player);
            when(click.getInventory()).thenReturn(inventory);
            when(click.getRawSlot()).thenReturn(includeHomeAndAnchor ? 1 : 0);
            manager.onInventoryClick(click);
            verify(plugin.getBedCommand()).teleportNamedBed(player, "Bunk");
            if (includeHomeAndAnchor) {
                when(click.getRawSlot()).thenReturn(2);
                manager.onInventoryClick(click);
                verify(plugin.getAnchorHomes()).teleport(player);
            }
        }
    }
}
