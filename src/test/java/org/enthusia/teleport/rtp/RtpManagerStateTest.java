package org.enthusia.teleport.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;
import org.enthusia.teleport.debug.PerformanceMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RtpManagerStateTest {

    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @TempDir
    Path tempDir;

    @Test
    void loadReadsValidUsageClampsNegativeCountsAndIgnoresInvalidUuids() throws IOException {
        Files.writeString(tempDir.resolve("rtp_uses.yml"), String.join("\n",
                PLAYER_ID + ": 3",
                SECOND_ID + ": -7",
                "not-a-uuid: 99",
                ""
        ));

        RtpManager manager = new RtpManager(plugin(tempDir, settings(5, Map.of())).plugin());

        assertEquals(3, manager.getUses(PLAYER_ID));
        assertEquals(0, manager.getUses(SECOND_ID));
        assertEquals(0, manager.getUses(UUID.fromString("30000000-0000-0000-0000-000000000003")));
    }

    @Test
    void incrementAndBlockingFlushSurviveManagerRecreation() {
        PluginHarness firstHarness = plugin(tempDir, settings(5, Map.of()));
        RtpManager first = new RtpManager(firstHarness.plugin());

        first.incrementUse(PLAYER_ID);
        first.incrementUse(PLAYER_ID);
        first.incrementUse(SECOND_ID);
        first.flushBlocking();

        assertEquals(2, first.getUses(PLAYER_ID));
        assertEquals(1, first.getUses(SECOND_ID));
        verify(firstHarness.monitor()).increment("yaml.rtp.flushed");

        RtpManager reloaded = new RtpManager(plugin(tempDir, settings(5, Map.of())).plugin());
        assertEquals(2, reloaded.getUses(PLAYER_ID));
        assertEquals(1, reloaded.getUses(SECOND_ID));
    }

    @Test
    void incrementMarksQueuedPersistenceWork() {
        PluginHarness harness = plugin(tempDir, settings(5, Map.of()));
        RtpManager manager = new RtpManager(harness.plugin());

        manager.incrementUse(PLAYER_ID);

        assertEquals(1, manager.getUses(PLAYER_ID));
        verify(harness.monitor()).increment("yaml.rtp.queued");
    }

    @Test
    void rankLimitOnlyRaisesTheDefaultMaximum() {
        PluginConfig.RtpSettings settings = settings(2, Map.of(
                "rank.vip", 5,
                "rank.low", 1,
                "rank.elite", 8
        ));
        RtpManager manager = new RtpManager(plugin(tempDir, settings).plugin());
        Player player = player(PLAYER_ID);

        when(player.hasPermission("rank.vip")).thenReturn(true);
        when(player.hasPermission("rank.low")).thenReturn(true);
        when(player.hasPermission("rank.elite")).thenReturn(false);

        assertEquals(5, manager.getLimit(player));
    }

    @Test
    void highestMatchingRankWinsRegardlessOfMapIterationOrder() {
        PluginConfig.RtpSettings settings = settings(2, Map.of(
                "rank.first", 4,
                "rank.second", 9,
                "rank.third", 6
        ));
        RtpManager manager = new RtpManager(plugin(tempDir, settings).plugin());
        Player player = player(PLAYER_ID);

        when(player.hasPermission("rank.first")).thenReturn(true);
        when(player.hasPermission("rank.second")).thenReturn(true);
        when(player.hasPermission("rank.third")).thenReturn(true);

        assertEquals(9, manager.getLimit(player));
    }

    @Test
    void finiteQuotaAllowsUsesStrictlyBelowLimitAndRejectsAtLimit() {
        RtpManager manager = new RtpManager(plugin(tempDir, settings(2, Map.of())).plugin());
        Player player = player(PLAYER_ID);

        assertTrue(manager.canUse(player));
        manager.incrementUse(PLAYER_ID);
        assertTrue(manager.canUse(player));
        manager.incrementUse(PLAYER_ID);
        assertFalse(manager.canUse(player));
        manager.incrementUse(PLAYER_ID);
        assertFalse(manager.canUse(player));
    }

    @Test
    void negativeLimitIsUnlimitedEvenAfterUsageAccumulates() {
        RtpManager manager = new RtpManager(plugin(tempDir, settings(-1, Map.of())).plugin());
        Player player = player(PLAYER_ID);

        for (int i = 0; i < 20; i++) {
            manager.incrementUse(PLAYER_ID);
        }

        assertEquals(20, manager.getUses(PLAYER_ID));
        assertTrue(manager.canUse(player));
    }

    private static PluginHarness plugin(Path dataFolder, PluginConfig.RtpSettings settings) {
        EnthusiaTeleportPlugin plugin = mock(EnthusiaTeleportPlugin.class, RETURNS_DEEP_STUBS);
        PerformanceMonitor monitor = mock(PerformanceMonitor.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getPerformanceMonitor()).thenReturn(monitor);
        when(plugin.getPluginConfigManager().current().rtp()).thenReturn(settings);
        return new PluginHarness(plugin, monitor);
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private static PluginConfig.RtpSettings settings(int maxUsesDefault, Map<String, Integer> rankLimits) {
        return new PluginConfig.RtpSettings(
                true,
                "world",
                -500,
                500,
                -500,
                500,
                maxUsesDefault,
                rankLimits,
                16,
                new PluginConfig.QueueSettings(true, 2, 4, 8, 30),
                new PluginConfig.SpacingSettings(0.0D, 0.0D, 0.0D, 10),
                new PluginConfig.SafetySettings(16, 4)
        );
    }

    private record PluginHarness(EnthusiaTeleportPlugin plugin, PerformanceMonitor monitor) {
    }
}
