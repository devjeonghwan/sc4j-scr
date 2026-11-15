package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.registry.TableKind;
import org.fogofwarlabs.sc4j.engine.content.table.PlayerProgressTable;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerModifiers;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains the death, kill and score counters of the game context that the trigger conditions Deaths, Kill and Score and the actions Set Deaths and
 * Set Score use.
 * <p>
 * The death and kill tables ({@code type * PLAYER_SLOTS + player}) are views over SC:R extension columns of the engine {@link PlayerProgressTable}
 * ({@link PlayerProgressColumns#DEATHS}, {@link PlayerProgressColumns#KILLS}); the storage contains a per-player DWORD array for each of units lost,
 * units killed, unit kill score, buildings lost, buildings razed, razing score, factories lost, factories razed and custom score. The counters
 * consume no random numbers and are not part of the state hash.
 * <p>
 * The kill table is followed by the death table in the game context, so a kill index past its table addresses the death table
 * ({@link PlayerUnitCounts}).
 */
public final class PlayerScores {
    /**
     * The game-context offset of the kill table ({@code type * PLAYER_SLOTS + player} DWORDs), followed by the death table.
     */
    public static final int KILL_TABLE_OFFSET = 0x87B4;
    /**
     * The game-context offset of the death table ({@code type * PLAYER_SLOTS + player} DWORDs).
     */
    public static final int DEATH_TABLE_OFFSET = 0xB274;

    /**
     * The game-context offset of the units-lost DWORDs, one per player.
     */
    private static final int UNITS_LOST_OFFSET = 0x2D84;
    /**
     * The game-context offset of the units-killed DWORDs, one per player.
     */
    private static final int UNITS_KILLED_OFFSET = 0x2DB4;
    /**
     * The game-context offset of the unit kill score DWORDs, one per player.
     */
    private static final int UNIT_KILL_SCORE_OFFSET = 0x2E14;
    /**
     * The game-context offset of the buildings-lost DWORDs, one per player.
     */
    private static final int BUILDINGS_LOST_OFFSET = 0x2ED4;
    /**
     * The game-context offset of the buildings-razed DWORDs, one per player.
     */
    private static final int BUILDINGS_RAZED_OFFSET = 0x2F04;
    /**
     * The game-context offset of the razing score DWORDs, one per player.
     */
    private static final int RAZING_SCORE_OFFSET = 0x2F64;
    /**
     * The game-context offset of the factories-lost DWORDs, one per player.
     */
    private static final int FACTORIES_LOST_OFFSET = 0x2FF4;
    /**
     * The game-context offset of the factories-razed DWORDs, one per player.
     */
    private static final int FACTORIES_RAZED_OFFSET = 0x3024;
    /**
     * The game-context offset of the custom score DWORDs, one per player.
     */
    private static final int CUSTOM_SCORE_OFFSET = 0x3204;

    /**
     * The game-context offset of the first per-player counter array (units lost, see {@link #getPlayerCounters}).
     */
    public static final int PLAYER_COUNTERS_OFFSET = UNITS_LOST_OFFSET;
    /**
     * The game-context offset after the per-player counter arrays (the end of the custom score DWORDs).
     */
    public static final int PLAYER_COUNTERS_END = CUSTOM_SCORE_OFFSET + PlayerTable.PLAYER_SLOTS * Integer.BYTES;

    /**
     * Applies a trigger numeric modifier (Set Deaths, Set Score, a transmission's duration) to a value: Set To replaces it, Add adds with Java
     * overflow and Subtract subtracts clamped at zero (unsigned comparison); any other modifier keeps the value.
     *
     * @param value    the current value
     * @param modifier the trigger modifier
     * @param amount   the trigger amount
     * @return the modified value
     */
    public static int modify(int value, int modifier, int amount) {
        return switch (modifier) {
            case TriggerModifiers.SET_TO -> amount;
            case TriggerModifiers.ADD -> value + amount;
            case TriggerModifiers.SUBTRACT -> Integer.compareUnsigned(value, amount) > 0 ? value - amount : 0;
            default -> value;
        };
    }


    private final @NotNull PlayerUnitCounts deaths;
    private final @NotNull PlayerUnitCounts kills;

    private final int @NotNull [] unitsLost;
    private final int @NotNull [] unitsKilled;
    private final int @NotNull [] unitKillScore;
    private final int @NotNull [] buildingsLost;
    private final int @NotNull [] buildingsRazed;
    private final int @NotNull [] razingScore;
    private final int @NotNull [] factoriesLost;
    private final int @NotNull [] factoriesRazed;
    private final int @NotNull [] customScore;

    /**
     * Creates a {@link PlayerScores} instance over a progress table with every counter zero.
     *
     * @param progress the progress table of the game, with the SC:R extension columns ({@link PlayerProgressColumns})
     */
    public PlayerScores(@NotNull PlayerProgressTable progress) {
        this.deaths = PlayerUnitCounts.ofColumn(progress.getExtensionColumns(TableKind.UNIT).get(PlayerProgressColumns.DEATHS));
        this.kills  = PlayerUnitCounts.ofColumn(progress.getExtensionColumns(TableKind.UNIT).get(PlayerProgressColumns.KILLS));

        this.unitsLost      = new int[PlayerTable.PLAYER_SLOTS];
        this.unitsKilled    = new int[PlayerTable.PLAYER_SLOTS];
        this.unitKillScore  = new int[PlayerTable.PLAYER_SLOTS];
        this.buildingsLost  = new int[PlayerTable.PLAYER_SLOTS];
        this.buildingsRazed = new int[PlayerTable.PLAYER_SLOTS];
        this.razingScore    = new int[PlayerTable.PLAYER_SLOTS];
        this.factoriesLost  = new int[PlayerTable.PLAYER_SLOTS];
        this.factoriesRazed = new int[PlayerTable.PLAYER_SLOTS];
        this.customScore    = new int[PlayerTable.PLAYER_SLOTS];

        this.deaths.clear();
        this.kills.clear();

        // The kill table and the death table lie back to back in the game context.
        this.kills.setSuccessor(this.deaths);
    }

    /**
     * @return the death table, ordered {@code type * PLAYER_SLOTS + player}
     */
    public @NotNull PlayerUnitCounts getDeathTable() {
        return this.deaths;
    }

    /**
     * @return the kill table, ordered {@code type * PLAYER_SLOTS + player}
     */
    public @NotNull PlayerUnitCounts getKillTable() {
        return this.kills;
    }

    /**
     * @param player the player slot
     * @return the number of units the player lost
     */
    public int getUnitsLost(int player) {
        return this.unitsLost[player];
    }

    /**
     * @param player the player slot
     * @return the number of opposing units the player killed
     */
    public int getUnitsKilled(int player) {
        return this.unitsKilled[player];
    }

    /**
     * @param player the player slot
     * @return the kill score of the player
     */
    public int getUnitKillScore(int player) {
        return this.unitKillScore[player];
    }

    /**
     * Sets the kill score of a player.
     *
     * @param player the player slot
     * @param value  the new kill score
     */
    public void setUnitKillScore(int player, int value) {
        this.unitKillScore[player] = value;
    }

    /**
     * @param player the player slot
     * @return the number of buildings the player lost
     */
    public int getBuildingsLost(int player) {
        return this.buildingsLost[player];
    }

    /**
     * @param player the player slot
     * @return the number of opposing buildings the player razed
     */
    public int getBuildingsRazed(int player) {
        return this.buildingsRazed[player];
    }

    /**
     * @param player the player slot
     * @return the razing score of the player
     */
    public int getRazingScore(int player) {
        return this.razingScore[player];
    }

    /**
     * Sets the razing score of a player.
     *
     * @param player the player slot
     * @param value  the new razing score
     */
    public void setRazingScore(int player, int value) {
        this.razingScore[player] = value;
    }

    /**
     * @param player the player slot
     * @return the custom score of the player
     */
    public int getCustomScore(int player) {
        return this.customScore[player];
    }

    /**
     * Sets the custom score of a player.
     *
     * @param player the player slot
     * @param value  the new custom score
     */
    public void setCustomScore(int player, int value) {
        this.customScore[player] = value;
    }


    /**
     * Records the death of a unit. Hallucinations and subunits are ignored. The owner's men or building loss counter, factory loss counter and death
     * count increase. When the killer is another playable player that is not out of the game ({@link Player#isFinished}), men and the larva and egg
     * types add a unit kill and the destroy score, other buildings a razing and the destroy score, factories a factory razing, and the kill count
     * increases.
     *
     * @param game   the game supplying player records
     * @param units  the unit type table
     * @param unit   the destroyed unit
     * @param killer the crediting player, or a value of {@link PlayerTable#PLAYABLE_PLAYER_SLOTS} and above (including {@code -1}) for none
     * @throws NullPointerException if the crediting player's slot is empty
     */
    public void recordDeath(@NotNull Game game, @NotNull ScrUnitTypeView units, @NotNull Unit unit, int killer) {
        int type = unit.getUnitId();

        if ((unit.getStatus() & UnitStatusFlags.HALLUCINATION) != 0 || (units.getFlags(type) & UnitTypeFlags.SUBUNIT) != 0) {
            return;
        }

        int owner = unit.getOwnerIndex();
        int groupFlags = units.getGroupFlags(type);

        if ((groupFlags & UnitGroupFlags.MEN) != 0) {
            this.unitsLost[owner]++;
        } else if ((groupFlags & UnitGroupFlags.BUILDING) != 0) {
            this.buildingsLost[owner]++;
        }

        if ((groupFlags & UnitGroupFlags.FACTORY) != 0) {
            this.factoriesLost[owner]++;
        }

        this.deaths.increment(type, owner);

        if (killer == owner || Integer.compareUnsigned(killer, PlayerTable.PLAYABLE_PLAYER_SLOTS) >= 0) {
            return;
        }

        Player killerPlayer = game.getPlayerTable().getPlayer(killer);

        if (killerPlayer == null) {
            throw Exceptions.newNullPointerException("The slot of crediting player {0} is empty.", killer);
        }

        if (killerPlayer.isFinished()) {
            return;
        }

        int destroyScore = units.getDestroyScore(type);

        if ((groupFlags & UnitGroupFlags.MEN) != 0 || units.getBaseType(type) == ScrUnitIds.ZERG_LARVA ||
            units.getBaseType(type) == ScrUnitIds.ZERG_EGG) {
            this.unitsKilled[killer]++;
            this.unitKillScore[killer] += destroyScore;
        } else if ((groupFlags & UnitGroupFlags.BUILDING) != 0) {
            this.buildingsRazed[killer]++;
            this.razingScore[killer] += destroyScore;
        }

        if ((groupFlags & UnitGroupFlags.FACTORY) != 0) {
            this.factoriesRazed[killer]++;
        }

        this.kills.increment(type, killer);
    }


    /**
     * Reads one player's value of the Deaths condition for any player slot: {@link TriggerUnitGroups#ANY_UNIT} men and buildings lost,
     * {@link TriggerUnitGroups#MEN} men, {@link TriggerUnitGroups#BUILDINGS} buildings, {@link TriggerUnitGroups#FACTORIES} factories, other types
     * the death count; {@link TriggerUnitGroups#NONE} reads the DWORD after the death table.
     *
     * @param player   the player slot
     * @param unitType the unit type or trigger unit group
     * @return the death value
     * @throws UnsupportedOperationException if the unit type is {@link TriggerUnitGroups#NONE} and the DWORDs after the death table are not linked
     */
    public int getDeaths(int player, int unitType) {
        return switch (unitType) {
            case TriggerUnitGroups.ANY_UNIT -> this.buildingsLost[player] + this.unitsLost[player];
            case TriggerUnitGroups.MEN -> this.unitsLost[player];
            case TriggerUnitGroups.BUILDINGS -> this.buildingsLost[player];
            case TriggerUnitGroups.FACTORIES -> this.factoriesLost[player];
            default -> this.deaths.get(unitType, player);
        };
    }

    /**
     * Reads one player's value of the Kill condition, as {@link #getDeaths} for the kill counters; {@link TriggerUnitGroups#NONE} reads the first row
     * of the death table, which follows the kill table.
     *
     * @param player   the player slot
     * @param unitType the unit type or trigger unit group
     * @return the kill value
     */
    public int getKills(int player, int unitType) {
        return switch (unitType) {
            case TriggerUnitGroups.ANY_UNIT -> this.buildingsRazed[player] + this.unitsKilled[player];
            case TriggerUnitGroups.MEN -> this.unitsKilled[player];
            case TriggerUnitGroups.BUILDINGS -> this.buildingsRazed[player];
            case TriggerUnitGroups.FACTORIES -> this.factoriesRazed[player];
            default -> this.kills.get(unitType, player);
        };
    }


    /**
     * Applies one player's modification of Set Deaths (set, add, or subtract clamped at zero) for a playable player: {@link TriggerUnitGroups#MEN},
     * {@link TriggerUnitGroups#BUILDINGS} and {@link TriggerUnitGroups#FACTORIES} modify the loss counters, {@link TriggerUnitGroups#ANY_UNIT} and
     * types from {@link TriggerUnitGroups#NONE} nothing, other types the death count.
     *
     * @param player   the player slot
     * @param unitType the unit type or trigger unit group
     * @param modifier the trigger modifier
     * @param amount   the trigger amount
     */
    public void modifyDeaths(int player, int unitType, int modifier, int amount) {
        int[] counters;

        switch (unitType) {
            case TriggerUnitGroups.MEN -> counters = this.unitsLost;
            case TriggerUnitGroups.BUILDINGS -> counters = this.buildingsLost;
            case TriggerUnitGroups.FACTORIES -> counters = this.factoriesLost;
            default -> {
                if (unitType >= ScrUnitIds.NONE) {
                    return;
                }

                this.deaths.set(unitType, player, modify(this.deaths.get(unitType, player), modifier, amount));

                return;
            }
        }

        counters[player] = modify(counters[player], modifier, amount);
    }


    /**
     * Returns the per-player counter array stored at a game-context offset, for the classic address translation of the EUD emulation.
     *
     * @param offset the game-context offset of the array
     * @return the array, or {@code null} if no per-player counter lives there
     */
    public int @Nullable [] getPlayerCounters(int offset) {
        return switch (offset) {
            case UNITS_LOST_OFFSET -> this.unitsLost;
            case UNITS_KILLED_OFFSET -> this.unitsKilled;
            case UNIT_KILL_SCORE_OFFSET -> this.unitKillScore;
            case BUILDINGS_LOST_OFFSET -> this.buildingsLost;
            case BUILDINGS_RAZED_OFFSET -> this.buildingsRazed;
            case RAZING_SCORE_OFFSET -> this.razingScore;
            case FACTORIES_LOST_OFFSET -> this.factoriesLost;
            case FACTORIES_RAZED_OFFSET -> this.factoriesRazed;
            case CUSTOM_SCORE_OFFSET -> this.customScore;
            default -> null;
        };
    }
}
