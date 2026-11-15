package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the game setup fields stored in the replay header.
 */
public final class ReplaySettings extends GameSettings {
    private final long subtypeValue;

    /**
     * The standard unit stats option byte, kept with the recorded game options although replay setup does not read it.
     */
    private final int useStandardUnitStats;
    /**
     * The fog of war option byte, kept with the recorded game options although replay setup does not read it.
     */
    private final int fogOfWarEnabled;
    /**
     * The fixed positions option byte, kept with the recorded game options although replay setup does not read it.
     */
    private final int useFixedPositions;
    private final int restrictionFlags;

    private final int alliesEnabled;
    private final int teamsEnabled;
    private final int cheatsEnabled;

    /**
     * The victory condition value DWORD, kept with the recorded game options although replay setup does not read it.
     */
    private final long victoryConditionValue;

    private final long startingGas;

    /**
     * Creates a {@link ReplaySettings} instance.
     *
     * @param subtypeValue              the subtype value DWORD at header {@code 0x85}
     * @param victoryCondition          the victory condition ID
     * @param resourceType              the resource setting ID
     * @param useStandardUnitStats      whether standard unit stats are enabled
     * @param fogOfWarEnabled           whether fog of war is enabled
     * @param createInitialUnits        the initial unit setting
     * @param useFixedPositions         whether fixed positions are enabled
     * @param restrictionFlags          the unit/tech/upgrade restriction flags
     * @param alliesEnabled             whether allied victory is enabled
     * @param teamsEnabled              whether teams are enabled
     * @param cheatsEnabled             whether cheats are enabled
     * @param tournamentMode            whether tournament mode is enabled
     * @param victoryConditionValue     the victory condition value
     * @param startingMinerals          the starting minerals
     * @param startingGas               the unverified field at header offset {@code 0x9c}
     * @param createMeleeUnitsForPlayer per-player melee starting unit flags (8 entries)
     */
    public ReplaySettings(long subtypeValue,
                          int victoryCondition,
                          int resourceType,
                          int useStandardUnitStats,
                          int fogOfWarEnabled,
                          int createInitialUnits,
                          int useFixedPositions,
                          int restrictionFlags,
                          int alliesEnabled, int teamsEnabled,
                          int cheatsEnabled,
                          int tournamentMode,
                          long victoryConditionValue,
                          long startingMinerals, long startingGas,
                          boolean @NotNull [] createMeleeUnitsForPlayer) {
        super(victoryCondition, resourceType, startingMinerals, createInitialUnits, createMeleeUnitsForPlayer, tournamentMode);

        this.subtypeValue = subtypeValue;

        this.useStandardUnitStats = useStandardUnitStats;
        this.fogOfWarEnabled      = fogOfWarEnabled;
        this.useFixedPositions    = useFixedPositions;
        this.restrictionFlags     = restrictionFlags;

        this.alliesEnabled = alliesEnabled;
        this.teamsEnabled  = teamsEnabled;
        this.cheatsEnabled = cheatsEnabled;

        this.victoryConditionValue = victoryConditionValue;

        this.startingGas = startingGas;
    }

    /**
     * @return the subtype value DWORD of the game options: the template's Greed resources, Slaughter minutes, team count or Top vs Bottom top slots
     */
    public long getSubtypeValue() {
        return this.subtypeValue;
    }

    /**
     * @return the game-option byte stored here as the restriction flags
     */
    public int getRestrictionFlags() {
        return this.restrictionFlags;
    }

    /**
     * @return the game-option byte enabling allies
     */
    public int getAlliesEnabled() {
        return this.alliesEnabled;
    }

    /**
     * @return the game-option byte enabling teams
     */
    public int getTeamsEnabled() {
        return this.teamsEnabled;
    }

    /**
     * @return the game-option byte controlling cheat commands
     */
    public int getCheatsEnabled() {
        return this.cheatsEnabled;
    }

    /**
     * Returns the field previously interpreted as starting gas.
     * <p>
     * Its meaning is unverified; this value is retained without interpreting it as a resource amount.
     *
     * @return the raw unsigned value at header offset {@code 0x9c}
     */
    public long getStartingGas() {
        return this.startingGas;
    }
}
