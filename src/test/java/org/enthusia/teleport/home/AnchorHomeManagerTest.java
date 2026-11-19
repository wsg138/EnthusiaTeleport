package org.enthusia.teleport.home;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnchorHomeManagerTest {
    @Test
    void onlyAnchorBindingsUpdateTheIndependentSlotAndChargeIsCheckedOnEveryVisit() {
        var manager = new AnchorHomeManager(mock(EnthusiaTeleportPlugin.class));
        var player = mock(Player.class);
        var data = mock(PersistentDataContainer.class);
        var world = mock(World.class);
        var block = mock(Block.class);
        var anchor = mock(RespawnAnchor.class);
        var event = mock(PlayerSetSpawnEvent.class);
        UUID id = UUID.randomUUID();
        when(world.getUID()).thenReturn(id);
        when(world.isRespawnAnchorWorks()).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(11, 22, -33)).thenReturn(block);
        when(block.getBlockData()).thenReturn(anchor);
        when(anchor.getCharges()).thenReturn(2);
        when(event.getPlayer()).thenReturn(player);
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(event.getLocation()).thenReturn(new Location(world, 11, 22, -33));
        when(event.getCause()).thenReturn(PlayerSetSpawnEvent.Cause.BED);
        manager.onSpawnSet(event);
        verifyNoInteractions(data);
        when(event.getCause()).thenReturn(PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR);
        manager.onSpawnSet(event);
        var position = ArgumentCaptor.forClass(long[].class);
        verify(data).set(any(), eq(PersistentDataType.LONG_ARRAY), position.capture());
        when(data.get(any(), eq(PersistentDataType.LONG_ARRAY))).thenReturn(position.getValue());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(id)).thenReturn(world);
            assertEquals(event.getLocation(), manager.available(player).orElseThrow());
            when(anchor.getCharges()).thenReturn(0);
            assertTrue(manager.available(player).isEmpty());
            when(anchor.getCharges()).thenReturn(1);
            assertTrue(manager.available(player).isPresent());
        }
    }
}
