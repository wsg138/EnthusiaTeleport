package org.enthusia.teleport.api;

/** Reason a request or teleport warmup was cancelled. */
public enum CancelReason {
    EXTERNAL_STATE_CHANGE,
    DUEL_SPECTATE,
    MOVE,
    DAMAGE,
    DISCONNECT,
    RELOAD,
    DISABLE
}
