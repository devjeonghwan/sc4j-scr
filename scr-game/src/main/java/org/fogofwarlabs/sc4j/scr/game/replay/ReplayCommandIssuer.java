package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.setup.PlayerSlots;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the player record of a recorded command issuer. A live session resolves it through its player slots
 * ({@link Game#getPlayerSlots}): the slot of the record the player holds now. Replay playback resolves it to the first
 * eligible represented record whose id matches, of any type, so an open record that keeps the id of the player who
 * released it comes before the player's own record.
 * <p>
 * An unmatched issuer is not dispatched; a missing represented player record remains an error.
 */
public final class ReplayCommandIssuer {
    /**
     * The largest recorded issuer byte.
     */
    static final int LAST_ISSUER = 0xFF;

    /**
     * Describes whether the issuer's record is established.
     */
    public enum Status {
        /**
         * The issuer record was found.
         */
        RESOLVED,
        /**
         * No matching player record exists.
         */
        UNRESOLVED_PLAYER_RECORD,
        /**
         * The matching record lies in the unrepresented extended records, or the live session's player holds no slot.
         */
        UNRESOLVED_EXTENDED_RECORDS
    }

    /**
     * Resolves an issuer against the current represented player records: through the live session's player slots, or in
     * replay playback through the first record of its id.
     *
     * @param game           the simulation state
     * @param recordedIssuer the unsigned packet issuer byte
     * @return the resolution result without changing simulation state
     * @throws IllegalArgumentException if the issuer is outside the byte range
     */
    public static @NotNull ReplayCommandIssuer resolve(@NotNull Game game, int recordedIssuer) {
        if (recordedIssuer < 0 || recordedIssuer > LAST_ISSUER) {
            throw Exceptions.newIllegalArgumentException("Recorded issuer is outside the unsigned byte range: {0}", recordedIssuer);
        }

        PlayerSlots playerSlots = game.getPlayerSlots();

        return playerSlots == null ? resolveFirstRecord(game, recordedIssuer) : resolvePlayerSlot(game, playerSlots, recordedIssuer);
    }

    /**
     * Resolves a live command's issuer to the slot the player holds; a player without a slot, or with a slot beyond the
     * eight game players, acts in the unrepresented extended records.
     */
    private static @NotNull ReplayCommandIssuer resolvePlayerSlot(@NotNull Game game, @NotNull PlayerSlots playerSlots, int recordedIssuer) {
        int recordIndex = playerSlots.getSlot(recordedIssuer);

        if (recordIndex < 0 || recordIndex >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            return new ReplayCommandIssuer(Status.UNRESOLVED_EXTENDED_RECORDS, -1, 0);
        }

        Player player = game.getPlayerTable().getPlayer(recordIndex);

        if (player == null) {
            return new ReplayCommandIssuer(Status.UNRESOLVED_PLAYER_RECORD, -1, 0);
        }

        return new ReplayCommandIssuer(Status.RESOLVED, recordIndex, player.getCommandPlayerIndex());
    }

    /**
     * Resolves a replay command's issuer to the first eligible represented record whose id matches, of any type.
     */
    private static @NotNull ReplayCommandIssuer resolveFirstRecord(@NotNull Game game, int recordedIssuer) {
        for (int recordIndex = 0; recordIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; recordIndex++) {
            Player player = game.getPlayerTable().getPlayer(recordIndex);

            if (player == null) {
                return new ReplayCommandIssuer(Status.UNRESOLVED_PLAYER_RECORD, -1, 0);
            }

            if (player.getId() == recordedIssuer) {
                return new ReplayCommandIssuer(Status.RESOLVED, recordIndex, player.getCommandPlayerIndex());
            }
        }

        return new ReplayCommandIssuer(Status.UNRESOLVED_EXTENDED_RECORDS, -1, 0);
    }


    private final @NotNull Status status;
    private final int recordIndex;
    private final int commandPlayerIndex;

    private ReplayCommandIssuer(@NotNull Status status, int recordIndex, int commandPlayerIndex) {
        this.status             = status;
        this.recordIndex        = recordIndex;
        this.commandPlayerIndex = commandPlayerIndex;
    }

    /**
     * @return the resolution status
     */
    public @NotNull Status getStatus() {
        return this.status;
    }

    /**
     * @return the matching player record's zero-based position
     * @throws IllegalStateException if the issuer is unresolved
     */
    public int getRecordIndex() {
        if (this.status != Status.RESOLVED) {
            throw Exceptions.newIllegalStateException("The recorded command issuer is unresolved.");
        }

        return this.recordIndex;
    }

    /**
     * @return the matching record's complete command player DWORD
     * @throws IllegalStateException if the issuer is unresolved
     */
    public int getCommandPlayerIndex() {
        if (this.status != Status.RESOLVED) {
            throw Exceptions.newIllegalStateException("The recorded command issuer is unresolved.");
        }

        return this.commandPlayerIndex;
    }
}
