package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenDigestUtils;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenValues;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayCommandObserver;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Records the outcome of every recorded command through the {@link ReplayCommandObserver} hook: whether it executed without an exception and whether
 * it changed the observable state ({@link StateDigestCheck#digestObservableState}).
 * <p>
 * The observer only reads state. The acceptance digest chains frame, player, type, payload length and the two outcome flags of every command in
 * execution order.
 */
public final class CommandAcceptanceCheck implements ReplayCommandObserver {
    private static final int NOT_EXECUTED = 0;
    private static final int EXECUTED = 1;
    private static final int CHANGED_STATE = 2;

    private long digest;
    private int observed;
    private int executed;
    private int changedState;

    private @Nullable ReplayCommand pending;
    private long pendingStateDigest;

    /**
     * Creates a {@link CommandAcceptanceCheck} instance with no observed command.
     */
    public CommandAcceptanceCheck() {
        this.digest       = GoldenDigestUtils.OFFSET_BASIS;
        this.observed     = 0;
        this.executed     = 0;
        this.changedState = 0;

        this.pending            = null;
        this.pendingStateDigest = 0L;
    }

    /**
     * Called before the command executes.
     *
     * @param game    the simulation state
     * @param command the recorded command
     */
    @Override
    public void onBeforeCommand(@NotNull Game game, @NotNull ReplayCommand command) {
        this.finishPending(NOT_EXECUTED);

        this.pending            = command;
        this.pendingStateDigest = StateDigestCheck.digestObservableState(game);
    }

    /**
     * Called after the command executed without an exception.
     *
     * @param game    the simulation state
     * @param command the recorded command
     */
    @Override
    public void onAfterCommand(@NotNull Game game, @NotNull ReplayCommand command) {
        int outcome = EXECUTED;

        if (StateDigestCheck.digestObservableState(game) != this.pendingStateDigest) {
            outcome |= CHANGED_STATE;
        }

        this.finishPending(outcome);
    }

    /**
     * Puts the acceptance values; a command whose execution threw counts as observed but not executed.
     *
     * @param values the values to add to
     */
    public void putValues(@NotNull GoldenValues values) {
        this.finishPending(NOT_EXECUTED);

        values.put("commands.observed", this.observed);
        values.put("commands.observed.executed", this.executed);
        values.put("commands.observed.changedState", this.changedState);
        values.put("commands.acceptance.digest", GoldenDigestUtils.format(this.digest));
    }

    private void finishPending(int outcome) {
        ReplayCommand command = this.pending;

        if (command == null) {
            return;
        }

        this.pending = null;
        this.observed++;

        if ((outcome & EXECUTED) != 0) {
            this.executed++;
        }

        if ((outcome & CHANGED_STATE) != 0) {
            this.changedState++;
        }

        this.digest = GoldenDigestUtils.mix(this.digest, (int) command.getFrame());
        this.digest = GoldenDigestUtils.mix(this.digest, command.getPlayerId());
        this.digest = GoldenDigestUtils.mix(this.digest, command.getTypeId());
        this.digest = GoldenDigestUtils.mix(this.digest, command.getPayload().length);
        this.digest = GoldenDigestUtils.mix(this.digest, outcome);
    }
}
