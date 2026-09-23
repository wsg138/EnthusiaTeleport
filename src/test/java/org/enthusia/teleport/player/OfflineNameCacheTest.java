package org.enthusia.teleport.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

final class OfflineNameCacheTest {
    @Test
    void joinsPopulateCaseInsensitiveSortedSuggestionsWithoutDuplicates() {
        OfflineNameCache cache = new OfflineNameCache(null);

        cache.onJoin(join("Zed"));
        cache.onJoin(join("alice"));
        cache.onJoin(join("Bob"));
        cache.onJoin(join("ALICE"));

        assertEquals(List.of("alice", "Bob", "Zed"), cache.suggest("", false));
        assertEquals(List.of("alice"), cache.suggest("a", false));
        assertEquals(List.of("Bob"), cache.suggest("BO", false));
        assertEquals(List.of(), cache.suggest("missing", false));
    }

    @Test
    void nullPrefixMatchesAllKnownNames() {
        OfflineNameCache cache = new OfflineNameCache(null);
        cache.onJoin(join("Charlie"));
        cache.onJoin(join("Alice"));

        assertEquals(List.of("Alice", "Charlie"), cache.suggest(null, false));
    }

    private static PlayerJoinEvent join(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
