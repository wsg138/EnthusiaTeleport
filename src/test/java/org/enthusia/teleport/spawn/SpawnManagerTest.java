package org.enthusia.teleport.spawn;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.teleport.command.SpawnCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SpawnManagerTest {
    private final EnthusiaTeleportPlugin plugin = mock(EnthusiaTeleportPlugin.class, RETURNS_DEEP_STUBS);
    private final SpawnManager manager = spy(new SpawnManager(plugin));
    private final PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
    private final Player player = mock(Player.class);
    private final PersistentDataContainer data = mock(PersistentDataContainer.class);
    private final Location spawn = mock(Location.class);

    SpawnManagerTest() {
        when(plugin.getPluginConfigManager().current().spawn().respawnOverrideEnabled()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRespawnReason()).thenReturn(PlayerRespawnEvent.RespawnReason.DEATH);
        when(player.getPersistentDataContainer()).thenReturn(data);
        doReturn(spawn).when(manager).getSpawnLocation();
    }

    @Test
    void deathAtAValidBedKeepsTheServerValidatedLocationByDefault() {
        when(event.isBedSpawn()).thenReturn(true);
        manager.onRespawn(event);
        verify(event, never()).setRespawnLocation(any());
    }

    @Test
    void deathAtChargedAnchorKeepsVanillaDestination() {
        when(event.isAnchorSpawn()).thenReturn(true);
        manager.onRespawn(event);
        verify(event, never()).setRespawnLocation(any());
    }

    @Test
    void deathWithoutAUsableBedUsesConfiguredSpawn() {
        manager.onRespawn(event);
        verify(event).setRespawnLocation(spawn);
    }

    @Test
    void spawnCommandOptsOutAndBedCommandArgumentRestoresDefault() {
        var key = new NamespacedKey("enthusiateleport", "respawn_at_spawn");
        when(plugin.getSpawnManager()).thenReturn(manager);
        when(event.isBedSpawn()).thenReturn(true);
        var command = new SpawnCommand(plugin);

        command.onCommand(player, null, "spawn", new String[]{"respawn", "spawn"});
        verify(data).set(key, PersistentDataType.BYTE, (byte) 1);
        when(data.has(key, PersistentDataType.BYTE)).thenReturn(true);
        manager.onRespawn(event);
        verify(event).setRespawnLocation(spawn);

        command.onCommand(player, null, "spawn", new String[]{"respawn", "bed"});
        verify(data).remove(key);
        verify(plugin, never()).getBedHomeManager();
        verify(plugin, never()).getHomeManager();
        verify(plugin, never()).getTeleportManager();
    }

    @Test
    void missingRespawnBlockFallsBackEvenIfEventFlagsABed() {
        when(event.isBedSpawn()).thenReturn(true);
        when(event.isMissingRespawnBlock()).thenReturn(true);
        manager.onRespawn(event);
        verify(event).setRespawnLocation(spawn);
    }

    @Test
    void disabledOverrideLeavesRespawnToTheServer() {
        when(plugin.getPluginConfigManager().current().spawn().respawnOverrideEnabled()).thenReturn(false);
        manager.onRespawn(event);
        verify(event, never()).setRespawnLocation(any());
    }
}
