package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.game.ability.DoodadOrders;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.VictoryCondition;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates Capture the Flag beacons before the victory-condition triggers are loaded and tracks unrepresented Flag notices.
 * <p>
 * Players or teams are visited in descending order. Each owner uses its first worker to find a site near its start
 * location, then constructs and completes the race's beacon. Missing workers or sites leave that owner without a beacon;
 * the beacon's {@link DoodadOrders} initialization creates the flag.
 */
public final class CaptureTheFlag {
    /**
     * The beacon type of race {@code 0}; the race is added.
     */
    private static final int FIRST_FLAG_BEACON = ScrUnitIds.SPECIAL_ZERG_FLAG_BEACON;
    /**
     * The placement search radius in tiles.
     */
    private static final int PLACEMENT_RADIUS = 0x40;
    /**
     * The packed placement result's coordinate mask.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The shift of the packed placement result's vertical half.
     */
    private static final int VERTICAL_SHIFT = 16;
    /**
     * The shift of the owner in the second byte of the creation tag.
     */
    private static final int CREATION_TAG_SHIFT = 8;
    /**
     * The lobby record type of a computer that the team owner test also counts.
     */
    private static final int LOBBY_COMPUTER = 5;

    private static final @NotNull String SCORED_FLAG_MESSAGE_KEY = "strCTF_CAPTURED_s";
    private static final @NotNull String TAKEN_FLAG_MESSAGE_KEY = "strCTF_TAKEN_s";

    private final @NotNull Game game;

    /**
     * Creates a {@link CaptureTheFlag} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public CaptureTheFlag(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Creates the beacons of every flag owner.
     * <p>
     * Missing workers or sites and a {@code null} creation result skip that owner. Construction can consume random values, and beacons already
     * created for earlier owners are not rolled back if a later owner fails.
     *
     * @param teamLeaders      a team game's leader slot of forces 1..4, {@code -1} for none, or {@code null} outside a team game
     * @param teamCount        the team count of a team game
     * @param instructionLimit the order and script budget
     */
    public void createFlags(int @Nullable [] teamLeaders, int teamCount, int instructionLimit) {
        if (teamLeaders == null) {
            for (int slot = PlayerTable.PLAYABLE_PLAYER_SLOTS - 1; slot >= 0; slot--) {
                int type = this.game.getPlayerTable().requirePlayer(slot).getType();

                if (type == PlayerTypes.HUMAN || type == PlayerTypes.COMPUTER) {
                    this.createFlag(slot, instructionLimit);
                }
            }

            return;
        }

        for (int team = teamCount; team >= 1; team--) {
            boolean hasOwner = false;

            for (int slot = PlayerTable.PLAYABLE_PLAYER_SLOTS - 1; slot >= 0; slot--) {
                Player player = this.game.getPlayerTable().requirePlayer(slot);
                int type = player.getType();

                if (player.getTeam() == team && (type == PlayerTypes.HUMAN || type == PlayerTypes.COMPUTER || type == LOBBY_COMPUTER)) {
                    hasOwner = true;

                    break;
                }
            }

            if (hasOwner && team <= teamLeaders.length && teamLeaders[team - 1] >= 0) {
                this.createFlag(teamLeaders[team - 1], instructionLimit);
            }
        }
    }

    /**
     * Marks a Flag score notice as unrepresented under the Capture the Flag victory condition.
     *
     * @param flagOwner the owner of the scored Flag
     * @throws UnsupportedOperationException if the game settings are not established
     */
    public void markFlagScoredUnrepresented(int flagOwner) {
        this.markFlagNoticeUnrepresented(flagOwner, SCORED_FLAG_MESSAGE_KEY);
    }

    /**
     * Marks a taken Flag notice as unrepresented under the Capture the Flag victory condition.
     *
     * @param flagOwner the owner of the taken Flag
     * @throws UnsupportedOperationException if the game settings are not established
     */
    public void markFlagTakenUnrepresented(int flagOwner) {
        this.markFlagNoticeUnrepresented(flagOwner, TAKEN_FLAG_MESSAGE_KEY);
    }

    private void markFlagNoticeUnrepresented(int flagOwner, @NotNull String messageKey) {
        GameSettings settings = this.game.getGameSettings();

        if (settings == null) {
            throw Exceptions.newUnsupportedOperationException("The flag message requires the established game options.");
        }

        if (settings.getVictoryCondition() != VictoryCondition.CAPTURE_THE_FLAG.getValue()) {
            return;
        }

        String writer = "the localized Flag notice " + messageKey + " for Flag owner " + flagOwner;

        this.game.getInterfaceLink().getMessageLines().markUnrepresentedWrite(writer);
    }

    /**
     * Creates the beacon of one owner next to its start location, built and completed by the owner's first worker.
     *
     * @param owner            the beacon owner
     * @param instructionLimit the order and script budget
     * @throws IllegalStateException         if the owner's slot is empty
     * @throws UnsupportedOperationException if the creation inputs are not established or the unit pool is not initialized
     */
    private void createFlag(int owner, int instructionLimit) {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();
        Unit worker = null;

        for (int index = pool.getFirstOwnerIndex(owner); index != 0 && worker == null; index = pool.getNextOwnerIndex(index)) {
            if (this.game.getComputerUnits().isWorker(pool.getUnit(index))) {
                worker = pool.getUnit(index);
            }
        }

        if (worker == null) {
            return;
        }

        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();
        UnitAccounting accounting = this.game.getPlayerTable().getUnitAccounting();
        UnitCreationContext creation = this.game.getUnitSystem().getUnitCreationContext();

        if (phase == null || accounting == null || creation == null) {
            throw Exceptions.newUnsupportedOperationException("Flag beacons require the computer placement, accounting and the creation context.");
        }

        Player player = this.game.getPlayerTable().requirePlayer(owner);
        StartLocation start = this.game.getPlayerTable().getStartLocations().get(owner);
        int beaconType = FIRST_FLAG_BEACON + player.getRace();
        int position = phase.getPlacement().findUnblocked(worker, beaconType, start.getX(), start.getY(), PLACEMENT_RADIUS);

        if (position == -1) {
            return;
        }

        Unit beacon = this.game.getUnitStorageConstructor().create(creation,
                                                                   accounting,
                                                                   beaconType,
                                                                   (short) (position & WORD_MASK), (short) (position >>> VERTICAL_SHIFT),
                                                                   owner,
                                                                   owner | owner << CREATION_TAG_SHIFT,
                                                                   instructionLimit);

        if (beacon == null) {
            return;
        }

        this.game.getUnitCompletion().initializeBeforePlacement(creation, beacon, instructionLimit);
        this.game.getUnitCompletion().completeAfterPlacement(creation, beacon, player.getType(), instructionLimit);
    }
}
