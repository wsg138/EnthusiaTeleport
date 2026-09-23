package org.enthusia.teleport.back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.junit.jupiter.api.Test;

class BackManagerTest {

    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void historyIsBoundedLifoAndEvictsOldestEntries() {
        EnthusiaTeleportPlugin plugin = pluginWithBackMax(2);
        BackManager manager = new BackManager(plugin);
        World world = mock(World.class);
        Player player = player();

        manager.record(player, location(world, 1.0, 64.0, 1.0));
        manager.record(player, location(world, 2.0, 64.0, 2.0));
        manager.record(player, location(world, 3.0, 64.0, 3.0));

        assertEquals(3.0, manager.peek(player).getX());
        manager.pop(player);
        assertEquals(2.0, manager.peek(player).getX());
        manager.pop(player);
        assertNull(manager.peek(player));
    }

    @Test
    void duplicateRecordInSameBlockKeepsOriginalSnapshot() {
        BackManager manager = new BackManager(pluginWithBackMax(4));
        World world = mock(World.class);
        Player player = player();

        manager.record(player, location(world, 10.10, 64.10, -4.10));
        manager.record(player, location(world, 10.90, 64.90, -4.90));

        Location top = manager.peek(player);
        assertEquals(10.10, top.getX(), 0.0001);
        assertEquals(64.10, top.getY(), 0.0001);
        assertEquals(-4.10, top.getZ(), 0.0001);

        manager.pop(player);
        assertNull(manager.peek(player));
    }

    @Test
    void identicalBlockCoordinatesInDifferentWorldsRemainDistinctHistoryEntries() {
        BackManager manager = new BackManager(pluginWithBackMax(4));
        World firstWorld = mock(World.class);
        World secondWorld = mock(World.class);
        Player player = player();

        manager.record(player, location(firstWorld, 4.2, 70.0, 8.2));
        manager.record(player, location(secondWorld, 4.7, 70.4, 8.9));

        assertEquals(secondWorld, manager.peek(player).getWorld());
        manager.pop(player);
        assertEquals(firstWorld, manager.peek(player).getWorld());
    }

    @Test
    void recordedAndReturnedLocationsAreDefensiveCopies() {
        BackManager manager = new BackManager(pluginWithBackMax(4));
        World world = mock(World.class);
        Player player = player();
        Location source = location(world, 2.5, 80.0, 3.5);

        manager.record(player, source);
        source.setX(999.0);

        Location firstPeek = manager.peek(player);
        assertEquals(2.5, firstPeek.getX(), 0.0001);
        firstPeek.setX(777.0);

        Location secondPeek = manager.peek(player);
        assertNotSame(firstPeek, secondPeek);
        assertEquals(2.5, secondPeek.getX(), 0.0001);
    }

    @Test
    void disabledHistoryAndInvalidRecordsAreIgnored() {
        BackManager disabled = new BackManager(pluginWithBackMax(0));
        Player player = player();
        World world = mock(World.class);

        disabled.record(player, location(world, 1.0, 64.0, 1.0));
        disabled.record(null, location(world, 2.0, 64.0, 2.0));
        disabled.record(player, null);
        disabled.record(player, location(null, 3.0, 64.0, 3.0));

        assertNull(disabled.peek(player));
        assertNull(disabled.peek(null));
    }

    @Test
    void reloadAppliesNewMaximumToSubsequentRecords() {
        EnthusiaTeleportPlugin plugin = mock(EnthusiaTeleportPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getPluginConfigManager().current().teleport().backMax()).thenReturn(3, 1);
        BackManager manager = new BackManager(plugin);
        World world = mock(World.class);
        Player player = player();

        manager.record(player, location(world, 1.0, 64.0, 1.0));
        manager.record(player, location(world, 2.0, 64.0, 2.0));
        manager.record(player, location(world, 3.0, 64.0, 3.0));

        manager.reload();
        manager.record(player, location(world, 4.0, 64.0, 4.0));

        assertEquals(4.0, manager.peek(player).getX());
        manager.pop(player);
        assertNull(manager.peek(player));
    }

    @Test
    void removeClearsOnlyTheRequestedPlayersHistory() {
        BackManager manager = new BackManager(pluginWithBackMax(3));
        World world = mock(World.class);
        Player first = player(PLAYER_ID);
        Player second = player(UUID.fromString("20000000-0000-0000-0000-000000000002"));

        manager.record(first, location(world, 1.0, 64.0, 1.0));
        manager.record(second, location(world, 2.0, 64.0, 2.0));

        manager.remove(first.getUniqueId());
        manager.remove(null);

        assertNull(manager.peek(first));
        assertEquals(2.0, manager.peek(second).getX());
    }

    private static EnthusiaTeleportPlugin pluginWithBackMax(int max) {
        EnthusiaTeleportPlugin plugin = mock(EnthusiaTeleportPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getPluginConfigManager().current().teleport().backMax()).thenReturn(max);
        return plugin;
    }

    private static Player player() {
        return player(PLAYER_ID);
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private static Location location(World world, double x, double y, double z) {
        return new Location(world, x, y, z, 45.0F, 15.0F);
    }
}
