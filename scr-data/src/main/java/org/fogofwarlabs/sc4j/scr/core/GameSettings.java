package org.fogofwarlabs.sc4j.scr.core;

import org.jetbrains.annotations.NotNull;

/**
 * Represents established game setup values used by resource and unit initialization.
 */
public class GameSettings {
    private final int victoryCondition;

    private final int resourceType;
    private final long startingMinerals;

    private final int createInitialUnits;
    private final boolean @NotNull [] createMeleeUnitsForPlayer;

    private final int tournamentMode;

    /**
     * Creates a {@link GameSettings} instance using the supplied per-player flags.
     * The array is retained without copying, matching the setup storage ownership contract.
     *
     * @param victoryCondition          the victory condition ID
     * @param resourceType              the resource setting ID
     * @param startingMinerals          the starting minerals
     * @param createInitialUnits        the initial unit setting
     * @param createMeleeUnitsForPlayer the per-player melee starting-unit flags
     * @param tournamentMode            the tournament mode value
     */
    public GameSettings(int victoryCondition,
                        int resourceType, long startingMinerals,
                        int createInitialUnits, boolean @NotNull [] createMeleeUnitsForPlayer,
                        int tournamentMode) {
        this.victoryCondition = victoryCondition;

        this.resourceType     = resourceType;
        this.startingMinerals = startingMinerals;

        this.createInitialUnits        = createInitialUnits;
        this.createMeleeUnitsForPlayer = createMeleeUnitsForPlayer;

        this.tournamentMode = tournamentMode;
    }

    /**
     * @return the victory condition ID
     */
    public final int getVictoryCondition() {
        return this.victoryCondition;
    }


    /**
     * @return the resource setting ID
     */
    public final int getResourceType() {
        return this.resourceType;
    }

    /**
     * @return the starting minerals
     */
    public final long getStartingMinerals() {
        return this.startingMinerals;
    }


    /**
     * @return the initial unit setting
     */
    public final int getCreateInitialUnits() {
        return this.createInitialUnits;
    }

    /**
     * Returns whether game setup requests melee starting units for the given player slot.
     *
     * @param playerSlot the player slot (0-7)
     * @return {@code true} if melee starting units are requested, {@code false} otherwise
     */
    public final boolean createsMeleeUnitsForPlayer(int playerSlot) {
        return playerSlot >= 0 && playerSlot < this.createMeleeUnitsForPlayer.length && this.createMeleeUnitsForPlayer[playerSlot];
    }


    /**
     * @return the tournament mode value; nonzero enables tournament mode
     */
    public final int getTournamentMode() {
        return this.tournamentMode;
    }


    /**
     * Returns whether the game setup uses the map settings: no victory condition, no tournament mode and no initial
     * unit setting.
     *
     * @return {@code true} if the game setup uses the map settings, {@code false} otherwise
     */
    public final boolean usesMapSettings() {
        return this.victoryCondition == 0 && this.tournamentMode == 0 && this.createInitialUnits == 0;
    }
}
