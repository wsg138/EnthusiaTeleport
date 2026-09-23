package org.enthusia.teleport.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

final class TeleportRequestManagerTest {
    @Test
    void mostRecentIncomingUsesLargestExpiryAndCollectionsAreDefensiveCopies() throws Exception {
        TeleportRequestManager manager = new TeleportRequestManager(null);
        UUID senderA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID senderB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Player target = player(targetId);
        Player sender = player(senderA);
        TeleportRequest older = request(senderA, targetId, TeleportRequestType.TPA, 100L);
        TeleportRequest newer = request(senderB, targetId, TeleportRequestType.TPA_HERE, 200L);

        incoming(manager).computeIfAbsent(targetId, unused -> new ConcurrentHashMap<>()).put(senderA, older);
        incoming(manager).get(targetId).put(senderB, newer);
        outgoing(manager).computeIfAbsent(senderA, unused -> new ConcurrentHashMap<>()).put(targetId, older);

        assertSame(newer, manager.getMostRecentIncoming(target));
        assertSame(older, manager.getIncoming(target, sender));

        var snapshot = new ArrayList<>(manager.getOutgoingRequests(sender));
        snapshot.clear();
        assertEquals(1, manager.getOutgoingRequests(sender).size(), "callers must not mutate manager state through returned collections");
    }

    @Test
    void removingRequestCleansBothIndexesAndCancelsExpiryTaskExactlyOnce() throws Exception {
        TeleportRequestManager manager = new TeleportRequestManager(null);
        UUID senderId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        BukkitTask task = mock(BukkitTask.class);
        TeleportRequest request = new TeleportRequest(senderId, targetId, TeleportRequestType.TPA, 500L, task);

        outgoing(manager).computeIfAbsent(senderId, unused -> new ConcurrentHashMap<>()).put(targetId, request);
        incoming(manager).computeIfAbsent(targetId, unused -> new ConcurrentHashMap<>()).put(senderId, request);

        manager.removeRequest(request);

        assertNull(outgoing(manager).get(senderId));
        assertNull(incoming(manager).get(targetId));
        verify(task).cancel();
    }

    @Test
    void removeByPlayerRemovesOutgoingAndIncomingEdgesWithoutTouchingUnrelatedRequests() throws Exception {
        TeleportRequestManager manager = new TeleportRequestManager(null);
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID sender = UUID.fromString("00000000-0000-0000-0000-000000000023");
        UUID unrelatedSender = UUID.fromString("00000000-0000-0000-0000-000000000024");
        UUID unrelatedTarget = UUID.fromString("00000000-0000-0000-0000-000000000025");

        BukkitTask outgoingTask = mock(BukkitTask.class);
        BukkitTask incomingTask = mock(BukkitTask.class);
        BukkitTask unrelatedTask = mock(BukkitTask.class);
        TeleportRequest outgoingRequest = new TeleportRequest(player, target, TeleportRequestType.TPA, 100L, outgoingTask);
        TeleportRequest incomingRequest = new TeleportRequest(sender, player, TeleportRequestType.TPA_HERE, 200L, incomingTask);
        TeleportRequest unrelated = new TeleportRequest(unrelatedSender, unrelatedTarget, TeleportRequestType.TPA, 300L, unrelatedTask);

        put(manager, outgoingRequest);
        put(manager, incomingRequest);
        put(manager, unrelated);

        manager.removeByPlayer(player, false);

        assertNull(outgoing(manager).get(player));
        assertNull(incoming(manager).get(player));
        assertEquals(unrelated, outgoing(manager).get(unrelatedSender).get(unrelatedTarget));
        assertEquals(unrelated, incoming(manager).get(unrelatedTarget).get(unrelatedSender));
        verify(outgoingTask).cancel();
        verify(incomingTask).cancel();
        verify(unrelatedTask, never()).cancel();
    }

    @Test
    void nullRequestRemovalIsSafeNoOp() {
        TeleportRequestManager manager = new TeleportRequestManager(null);
        manager.removeRequest(null);
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private static TeleportRequest request(UUID sender, UUID target, TeleportRequestType type, long expiryAt) {
        return new TeleportRequest(sender, target, type, expiryAt, mock(BukkitTask.class));
    }

    private static void put(TeleportRequestManager manager, TeleportRequest request) throws Exception {
        outgoing(manager).computeIfAbsent(request.getSender(), unused -> new ConcurrentHashMap<>())
                .put(request.getTarget(), request);
        incoming(manager).computeIfAbsent(request.getTarget(), unused -> new ConcurrentHashMap<>())
                .put(request.getSender(), request);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Map<UUID, TeleportRequest>> outgoing(TeleportRequestManager manager) throws Exception {
        Field field = TeleportRequestManager.class.getDeclaredField("outgoing");
        field.setAccessible(true);
        return (Map<UUID, Map<UUID, TeleportRequest>>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Map<UUID, TeleportRequest>> incoming(TeleportRequestManager manager) throws Exception {
        Field field = TeleportRequestManager.class.getDeclaredField("incoming");
        field.setAccessible(true);
        return (Map<UUID, Map<UUID, TeleportRequest>>) field.get(manager);
    }
}
