package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayGameVersion;
import org.jetbrains.annotations.NotNull;

/**
 * Stores the build, kind and flags used to select replay compatibility behavior.
 * <p>
 * New games use the current build; replay state comes from its version extension. Legacy replays skip the queued-order
 * capacity test and defer the NukeLaunch arrival signal test until the next frame. Other replays select producer-mismatch
 * and queued-order exceptions from their recorded build, kind and flags.
 */
public final class GameRulesVersion {
    /**
     * The build number used for new games.
     */
    public static final int CURRENT_BUILD = 13515;
    /**
     * The highlighted queued orders at which a unit's order queue is at capacity.
     */
    public static final int HIGHLIGHTED_ORDER_CAPACITY = 8;

    /**
     * The first replay build whose requirement interpreter runs the producer-mismatch branch.
     */
    private static final int PRODUCER_MISMATCH_BUILD = 0x18c6;
    /**
     * The version kind byte of the replays whose old builds relax the capacity exception.
     */
    private static final int RELAXED_CAPACITY_KIND = 5;
    /**
     * The flag bit that keeps the capacity exception of a {@link #RELAXED_CAPACITY_KIND} replay unrelaxed.
     */
    private static final int STRICT_CAPACITY_FLAG = 0x4;
    /**
     * The last relaxed build: older builds append every order at capacity, this build only after Die.
     */
    private static final int RELAXED_CAPACITY_LAST_BUILD = 0x1fd9;
    /**
     * The state shared by every game started by the running executable.
     */
    private static final @NotNull GameRulesVersion NEW_GAME = new GameRulesVersion(false, false, CURRENT_BUILD, 0, 0L);

    /**
     * Returns the state of a game started by the running executable.
     *
     * @return the shared new-game state
     */
    public static @NotNull GameRulesVersion newGame() {
        return NEW_GAME;
    }

    /**
     * Creates a {@link GameRulesVersion} instance from the replay version values.
     *
     * @param version the replay extension values, zero when absent
     * @param legacy  whether the replay is a legacy ({@code reRS}) file
     * @return the created {@link GameRulesVersion} instance
     */
    public static @NotNull GameRulesVersion forReplay(@NotNull ReplayGameVersion version, boolean legacy) {
        return new GameRulesVersion(true, legacy, version.getBuild(), version.getKind(), version.getFlags());
    }

    private final boolean replay;
    private final boolean legacyReplay;

    private final int build;
    private final int kind;
    private final long flags;

    /**
     * Creates a {@link GameRulesVersion} instance with the recorded compatibility selectors.
     *
     * @param replay       whether the game is a replay
     * @param legacyReplay whether the replay uses the legacy format
     * @param build        the build number
     * @param kind         the version kind byte
     * @param flags        the compatibility flags
     */
    private GameRulesVersion(boolean replay, boolean legacyReplay, int build, int kind, long flags) {
        this.replay       = replay;
        this.legacyReplay = legacyReplay;

        this.build = build;
        this.kind  = kind;
        this.flags = flags;
    }

    /**
     * @return {@code true} if the game is a replay, {@code false} otherwise
     */
    public boolean isReplay() {
        return this.replay;
    }

    /**
     * @return {@code true} if the game is a legacy ({@code reRS}) replay, {@code false} otherwise
     */
    public boolean isLegacyReplay() {
        return this.legacyReplay;
    }

    /**
     * @return the recorded (or running) build number
     */
    public int getBuild() {
        return this.build;
    }

    /**
     * @return the kind byte of the version block
     */
    public int getKind() {
        return this.kind;
    }

    /**
     * @return the BFIX flags QWORD
     */
    public long getFlags() {
        return this.flags;
    }


    /**
     * Applies the queued-order capacity test after the trailing orders are removed: while the order pool is at least 90%
     * full or the unit holds eight highlighted queued orders, the order is appended only when
     * {@link #allowsOrderAtCapacity} accepts the unit's current order. Legacy replays skip the test.
     *
     * @param nearlyFull        whether the order pool is at least 90% full
     * @param highlightedOrders the unit's highlighted queued-order count
     * @param currentOrder      the unit's current primary order
     * @return {@code true} if the order is appended, {@code false} otherwise
     */
    public boolean appendsOrder(boolean nearlyFull, int highlightedOrders, int currentOrder) {
        if (this.legacyReplay || !nearlyFull && highlightedOrders < HIGHLIGHTED_ORDER_CAPACITY) {
            return true;
        }

        return this.allowsOrderAtCapacity(currentOrder);
    }

    /**
     * Tells whether the NukeLaunch arrival tests the detonation signal in the arrival frame. SC:R sets the next state and
     * falls through into the signal test in the same frame, so the Nuclear Missile dies on the frame its arrival script
     * signals. Legacy replays test the signal only on the next frame.
     *
     * @return {@code true} if the arrival frame already tests the detonation signal, {@code false} otherwise
     */
    public boolean detonatesOnArrival() {
        return !this.legacyReplay;
    }

    /**
     * Tells whether the requirement interpreter's producer-mismatch branch runs: outside replays, and in replays from
     * build {@code 0x18c6} on.
     *
     * @return {@code true} if the branch runs, {@code false} otherwise
     */
    public boolean appliesProducerMismatch() {
        return !this.replay || Integer.compareUnsigned(this.build, PRODUCER_MISMATCH_BUILD) >= 0;
    }

    /**
     * Tells whether the queued-order path still appends an order while the order pool is at least 90% full or the unit
     * holds eight highlighted queued orders. Normally only a unit whose current order is Die or ResetHarvestCollision may;
     * an old-build replay of kind 5 relaxes this.
     *
     * @param currentOrder the unit's current primary order
     * @return {@code true} if the order is still appended, {@code false} otherwise
     */
    public boolean allowsOrderAtCapacity(int currentOrder) {
        if (this.replay && (this.flags & STRICT_CAPACITY_FLAG) == 0 && this.kind == RELAXED_CAPACITY_KIND) {
            if (Integer.compareUnsigned(this.build, RELAXED_CAPACITY_LAST_BUILD) < 0) {
                return true;
            }

            if (this.build == RELAXED_CAPACITY_LAST_BUILD) {
                return currentOrder == ScrOrderIds.DIE;
            }
        }

        return currentOrder == ScrOrderIds.DIE || currentOrder == ScrOrderIds.RESET_HARVEST_COLLISION;
    }
}
