package org.enthusia.teleport.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;
import org.enthusia.teleport.debug.PerformanceMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomeManagerTest {

    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @TempDir
    Path tempDir;

    @Test
    void setHomeNormalizesLookupAndPreservesOriginalIdentityWhenOverwritten() {
        PluginHarness harness = plugin(tempDir, new PluginConfig.HomeSettings(3, Map.of()));
        HomeManager manager = new HomeManager(harness.plugin());
        World world = world("world");
        Player player = player(world, new Location(world, 10.5, 70.0, -4.5, 30.0F, 15.0F));

        manager.setHome(player, "Base One");
        Home original = manager.getHome(PLAYER_ID, "  BASE ONE  ");
        assertNotNull(original);
        assertEquals("base one", original.getKey());
        assertEquals("Base One", original.getName());
        long createdAt = original.getCreatedAt();

        when(player.getLocation()).thenReturn(new Location(world, 99.5, 80.0, 42.5, 60.0F, 25.0F));
        manager.setHome(player, "BASE ONE");

        Home overwritten = manager.getHome(PLAYER_ID, "base one");
        assertEquals(1, manager.getHomeCount(PLAYER_ID));
        assertEquals("Base One", overwritten.getName());
        assertEquals(createdAt, overwritten.getCreatedAt());
        assertEquals(99.5, overwritten.getX(), 0.0001);
        assertEquals(80.0, overwritten.getY(), 0.0001);
        assertEquals(42.5, overwritten.getZ(), 0.0001);
        assertEquals(60.0F, overwritten.getYaw(), 0.0001F);
        assertEquals(25.0F, overwritten.getPitch(), 0.0001F);
    }

    @Test
    void deleteAndClearAreCaseInsensitiveAndOwnerScoped() {
        HomeManager manager = new HomeManager(plugin(tempDir, new PluginConfig.HomeSettings(5, Map.of())).plugin());
        World world = world("world");
        Player first = player(PLAYER_ID, world, new Location(world, 1.0, 64.0, 1.0));
        Player second = player(UUID.fromString("20000000-0000-0000-0000-000000000002"), world,
                new Location(world, 2.0, 64.0, 2.0));

        manager.setHome(first, "Mine");
        manager.setHome(first, "Shop");
        manager.setHome(second, "Mine");

        manager.deleteHome(PLAYER_ID, "  MINE ");
        manager.deleteHome(PLAYER_ID, null);
        assertNull(manager.getHome(PLAYER_ID, "mine"));
        assertEquals(1, manager.getHomeCount(PLAYER_ID));
        assertEquals(1, manager.getHomeCount(second.getUniqueId()));

        manager.clearHomes(PLAYER_ID);
        assertEquals(0, manager.getHomeCount(PLAYER_ID));
        assertEquals(1, manager.getHomeCount(second.getUniqueId()));
    }

    @Test
    void highestMatchingRankRaisesHomeLimitButLowerRanksCannotReduceIt() {
        PluginConfig.HomeSettings settings = new PluginConfig.HomeSettings(2, Map.of(
                "homes.low", 1,
                "homes.vip", 5,
                "homes.elite", 8
        ));
        HomeManager manager = new HomeManager(plugin(tempDir, settings).plugin());
        World world = world("world");
        Player player = player(world, new Location(world, 0.0, 64.0, 0.0));

        when(player.hasPermission("homes.low")).thenReturn(true);
        when(player.hasPermission("homes.vip")).thenReturn(true);
        when(player.hasPermission("homes.elite")).thenReturn(false);

        assertEquals(5, manager.getHomeLimit(player));
    }

    @Test
    void overLimitBecomesTrueOnlyAfterCountExceedsTheLimit() {
        HomeManager manager = new HomeManager(plugin(tempDir, new PluginConfig.HomeSettings(2, Map.of())).plugin());
        World world = world("world");
        Player player = player(world, new Location(world, 0.0, 64.0, 0.0));

        manager.setHome(player, "one");
        manager.setHome(player, "two");
        assertFalse(manager.isOverLimit(player));

        manager.setHome(player, "three");
        assertTrue(manager.isOverLimit(player));
    }

    @Test
    void blockingFlushRoundTripsHomeDataThroughYaml() {
        PluginConfig.HomeSettings settings = new PluginConfig.HomeSettings(3, Map.of());
        PluginHarness firstHarness = plugin(tempDir, settings);
        HomeManager first = new HomeManager(firstHarness.plugin());
        World world = world("survival");
        Player player = player(world, new Location(world, -12.25, 75.0, 48.75, 120.0F, -10.0F));

        first.setHome(player, "Castle");
        long createdAt = first.getHome(PLAYER_ID, "castle").getCreatedAt();
        first.flushBlocking();
        verify(firstHarness.monitor()).increment("yaml.homes.flushed");

        HomeManager reloaded = new HomeManager(plugin(tempDir, settings).plugin());
        Home home = reloaded.getHome(PLAYER_ID, "CASTLE");
        assertNotNull(home);
        assertEquals("castle", home.getKey());
        assertEquals("Castle", home.getName());
        assertEquals("survival", home.getWorldName());
        assertEquals(-12.25, home.getX(), 0.0001);
        assertEquals(75.0, home.getY(), 0.0001);
        assertEquals(48.75, home.getZ(), 0.0001);
        assertEquals(120.0F, home.getYaw(), 0.0001F);
        assertEquals(-10.0F, home.getPitch(), 0.0001F);
        assertEquals(createdAt, home.getCreatedAt());
    }

    @Test
    void missingHomeAndNullNameReturnNullWithoutCreatingData() {
        HomeManager manager = new HomeManager(plugin(tempDir, new PluginConfig.HomeSettings(3, Map.of())).plugin());

        assertNull(manager.getHome(PLAYER_ID, null));
        assertNull(manager.getHome(PLAYER_ID, "missing"));
        assertEquals(0, manager.getHomeCount(PLAYER_ID));
    }

    private static PluginHarness plugin(Path dataFolder, PluginConfig.HomeSettings settings) {
        EnthusiaTeleportPlugin plugin = mock(EnthusiaTeleportPlugin.class, RETURNS_DEEP_STUBS);
        PerformanceMonitor monitor = mock(PerformanceMonitor.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getPerformanceMonitor()).thenReturn(monitor);
        when(plugin.getPluginConfigManager().current().homes()).thenReturn(settings);
        return new PluginHarness(plugin, monitor);
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private static Player player(World world, Location location) {
        return player(PLAYER_ID, world, location);
    }

    private static Player player(UUID id, World world, Location location) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    private record PluginHarness(EnthusiaTeleportPlugin plugin, PerformanceMonitor monitor) {
    }
}
