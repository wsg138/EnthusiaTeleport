package org.enthusia.teleport.ignore;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.enthusia.teleport.api.TeleportIgnoreApi;

/** Exposes {@link IgnoreManager} to other plugins with the same rules as /tpignore. */
public final class IgnoreApiService implements TeleportIgnoreApi {

    private final IgnoreManager ignores;
    private final BooleanSupplier mainThread;

    public IgnoreApiService(IgnoreManager ignores, BooleanSupplier mainThread) {
        this.ignores = Objects.requireNonNull(ignores);
        this.mainThread = Objects.requireNonNull(mainThread);
    }

    @Override
    public boolean isIgnoring(UUID receiver, UUID sender) {
        requireMainThread();
        return ignores.isIgnoring(Objects.requireNonNull(receiver), Objects.requireNonNull(sender));
    }

    @Override
    public void setIgnoring(UUID receiver, UUID sender, boolean ignore) {
        requireMainThread();
        if (Objects.requireNonNull(receiver).equals(Objects.requireNonNull(sender))) {
            throw new IllegalArgumentException("A player cannot ignore their own teleport requests");
        }
        ignores.setIgnoring(receiver, sender, ignore);
    }

    private void requireMainThread() {
        // IgnoreManager is not thread-safe; /tpignore also runs on the main thread.
        if (!mainThread.getAsBoolean()) {
            throw new IllegalStateException("TeleportIgnoreApi must be called on the main thread");
        }
    }
}
