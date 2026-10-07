package org.enthusia.teleport.ignore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IgnoreApiServiceTest {

    private final UUID receiver = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();

    @Test
    void readsAndWritesThroughTheIgnoreManager() {
        IgnoreManager manager = mock(IgnoreManager.class);
        when(manager.isIgnoring(receiver, sender)).thenReturn(true);
        IgnoreApiService api = new IgnoreApiService(manager, () -> true);

        assertTrue(api.isIgnoring(receiver, sender));
        api.setIgnoring(receiver, sender, false);
        verify(manager).setIgnoring(receiver, sender, false);
    }

    @Test
    void refusesSelfIgnoreAndOffThreadCalls() {
        IgnoreManager manager = mock(IgnoreManager.class);
        assertThrows(IllegalArgumentException.class, () -> new IgnoreApiService(manager, () -> true).setIgnoring(receiver, receiver, true));

        IgnoreApiService offThread = new IgnoreApiService(manager, () -> false);
        assertThrows(IllegalStateException.class, () -> offThread.setIgnoring(receiver, sender, true));
        assertThrows(IllegalStateException.class, () -> offThread.isIgnoring(receiver, sender));
        verify(manager, never()).setIgnoring(any(), any(), anyBoolean());
        assertFalse(manager.isIgnoring(receiver, sender));
    }
}
