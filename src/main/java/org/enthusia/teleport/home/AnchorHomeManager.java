package org.enthusia.teleport.home;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

public final class AnchorHomeManager implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("enthusiateleport", "anchor_home");
    private final EnthusiaTeleportPlugin plugin;

    public AnchorHomeManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getCause() == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR && charged(event.getLocation())) {
            remember(event.getPlayer(), event.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Import an already-bound vanilla anchor without changing the player's active spawn.
        Location active = player.getRespawnLocation(false);
        if (charged(active)) remember(player, active);
    }

    private void remember(Player player, Location block) {
        UUID world = block.getWorld().getUID();
        player.getPersistentDataContainer().set(KEY, PersistentDataType.LONG_ARRAY,
                new long[]{world.getMostSignificantBits(), world.getLeastSignificantBits(),
                        block.getBlockX(), block.getBlockY(), block.getBlockZ()});
    }

    public Optional<Location> available(Player player) {
        long[] saved = player.getPersistentDataContainer().get(KEY, PersistentDataType.LONG_ARRAY);
        if (saved == null || saved.length != 5) return Optional.empty();
        World world = Bukkit.getWorld(new UUID(saved[0], saved[1]));
        if (world == null) return Optional.empty();
        Location block = new Location(world, saved[2], saved[3], saved[4]);
        return charged(block) ? Optional.of(block) : Optional.empty();
    }

    private boolean charged(Location loc) {
        return loc != null && loc.getWorld() != null && loc.getWorld().isRespawnAnchorWorks()
                && loc.getBlock().getBlockData() instanceof RespawnAnchor anchor && anchor.getCharges() > 0;
    }

    public void teleport(Player player) {
        Location block = available(player).orElse(null);
        var tp = plugin.getTeleportManager();
        if (block != null) {
            // Standing on the anchor itself is safe when there is headroom.
            for (int[] offset : LANDINGS) {
                Location target = block.clone().add(offset[0] + 0.5, offset[1], offset[2] + 0.5);
                if (!tp.getSafeFinder().isSafeHomeLocation(target)) continue;
                tp.startTeleportGuarded(player, target, false, null, "teleport.warmup-start", null,
                        landing -> available(player).filter(block::equals).isPresent()
                                && tp.getSafeFinder().isSafeHomeLocation(landing));
                return;
            }
        }
        plugin.getMessages().send(player, "anchor.unavailable");
    }

    private static final int[][] LANDINGS = {
            {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}
    };
}
