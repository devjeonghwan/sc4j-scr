package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the per-player tables of one game: the player slots, the start locations, relations, upgrades, technology, unit availability, production
 * statistics, unit accounting and the player color rows.
 * <p>
 * The tables other than the slots and start locations are installed by world initialization and stay {@code null} until then. The class also owns the
 * player slot numbers every game package uses.
 */
public final class PlayerTable {
    /**
     * The number of player slots, including the unused and the neutral slots.
     */
    public static final int PLAYER_SLOTS = 12;
    /**
     * The neutral player slot, the last one.
     */
    public static final int NEUTRAL_PLAYER_ID = 11;
    /**
     * The number of playable player slots ({@code 0}-{@code 7}); slots {@code 8}-{@code 10} are unused and {@link #NEUTRAL_PLAYER_ID} is neutral.
     */
    public static final int PLAYABLE_PLAYER_SLOTS = 8;
    /**
     * The number of forces (teams) of a map.
     */
    public static final int FORCE_COUNT = 4;
    /**
     * The number of the first force; force numbers run from this number to {@link #FORCE_COUNT}.
     */
    public static final int FIRST_FORCE = 1;
    /**
     * The first local slot of an observer, which is also its network player identifier; the observer slots follow it.
     */
    public static final int FIRST_OBSERVER_SLOT = 0x80;
    /**
     * The number of observer slots.
     */
    public static final int OBSERVER_SLOTS = 4;

    /**
     * The shift from a bit index to its byte in the per-player bit sets (researching and upgrading bits).
     */
    static final int BIT_SET_BYTE_SHIFT = 3;
    /**
     * The mask of a bit index within its byte in the per-player bit sets.
     */
    static final int BIT_SET_BIT_MASK = Byte.SIZE - 1;

    /*
     * Rows of a map's per-player settings section (UPGR and PUPx, PTEC and PTEx), in units of the entry count: the first
     * values of every player, the second values of every player, the default first value, the default second value, then
     * the use-default flags of every player.
     */
    /**
     * The row of the players' second values.
     */
    static final int SETTINGS_PLAYER_SECOND_ROW = PLAYER_SLOTS;
    /**
     * The row of the default first values.
     */
    static final int SETTINGS_DEFAULT_FIRST_ROW = SETTINGS_PLAYER_SECOND_ROW + PLAYER_SLOTS;
    /**
     * The row of the default second values.
     */
    static final int SETTINGS_DEFAULT_SECOND_ROW = SETTINGS_DEFAULT_FIRST_ROW + 1;
    /**
     * The row of the players' use-default flags.
     */
    public static final int SETTINGS_USE_DEFAULT_ROW = SETTINGS_DEFAULT_SECOND_ROW + 1;
    /**
     * The number of rows of the section.
     */
    public static final int SETTINGS_ROWS = SETTINGS_USE_DEFAULT_ROW + PLAYER_SLOTS;


    /**
     * Tests whether a local slot is an observer's.
     *
     * @param slot the local slot
     * @return {@code true} if the slot is one of the {@link #OBSERVER_SLOTS} slots from {@link #FIRST_OBSERVER_SLOT}, {@code false} otherwise
     */
    public static boolean isObserverSlot(int slot) {
        return slot >= FIRST_OBSERVER_SLOT && slot < FIRST_OBSERVER_SLOT + OBSERVER_SLOTS;
    }

    /**
     * Tests whether the local view of a game is a viewer's: the game plays a replay, or the local slot is an observer's
     * ({@link #isObserverSlot}). A viewer's start draws per human, starts on the first human's start location and has the
     * Terran console.
     *
     * @param replayPlayback whether the game plays a replay
     * @param localSlot      the local slot
     * @return {@code true} if the game plays a replay or the local slot is an observer's, {@code false} otherwise
     */
    public static boolean isReplayOrObserver(boolean replayPlayback, int localSlot) {
        return replayPlayback || isObserverSlot(localSlot);
    }


    private final @Nullable GameData gameData;

    private final @Nullable Player @NotNull [] players;
    private final @NotNull List<StartLocation> startLocations;

    private @Nullable PlayerRelations playerRelations;
    private @Nullable PlayerUpgrades playerUpgrades;
    private @Nullable PlayerTechnology playerTechnology;
    private @Nullable PlayerUnitAvailability playerUnitAvailability;
    private @Nullable ProductionStatistics productionStatistics;
    private @Nullable UnitAccounting unitAccounting;

    /**
     * The player color rows of the game context, filled on first use ({@link #getPlayerColorRows()}).
     */
    private @Nullable PlayerColorRows playerColorRows;
    /**
     * Whether the first use filled {@link #playerColorRows}, which stays {@code null} if a player's color lies outside the {@code tunit.pcx} rows.
     */
    private boolean playerColorRowsCreated;

    /**
     * Creates a {@link PlayerTable} instance with empty slots.
     *
     * @param gameData the player color tables, or {@code null} for a world built without game data
     */
    public PlayerTable(@Nullable GameData gameData) {
        this.gameData = gameData;

        this.players        = new Player[PLAYER_SLOTS];
        this.startLocations = new ArrayList<>();

        this.playerRelations        = null;
        this.playerUpgrades         = null;
        this.playerTechnology       = null;
        this.playerUnitAvailability = null;
        this.productionStatistics   = null;
        this.unitAccounting         = null;

        this.playerColorRows        = null;
        this.playerColorRowsCreated = false;
    }

    /**
     * @param slotId the slot ID
     * @return the player, or {@code null} if the slot is empty
     */
    public @Nullable Player getPlayer(int slotId) {
        return this.players[slotId];
    }

    /**
     * Returns the player of a slot that must be occupied: every slot of a game created from a setup is.
     *
     * @param slotId the slot ID
     * @return the player
     * @throws IllegalStateException if the slot is empty
     */
    public @NotNull Player requirePlayer(int slotId) {
        Player player = this.players[slotId];

        if (player == null) {
            throw Exceptions.newIllegalStateException("The player slot {0} is empty.", slotId);
        }

        return player;
    }

    /**
     * Sets the player of the given slot.
     *
     * @param slotId the slot ID
     * @param player the player
     */
    public void setPlayer(int slotId, @NotNull Player player) {
        this.players[slotId] = player;
    }

    /**
     * @return the players of the occupied slots in slot order, as an unmodifiable list
     */
    public @NotNull List<Player> getOccupiedPlayers() {
        List<Player> occupiedPlayers = new ArrayList<>();

        for (Player player : this.players) {
            if (player != null) {
                occupiedPlayers.add(player);
            }
        }

        return Collections.unmodifiableList(occupiedPlayers);
    }

    /**
     * @return the start locations in CHK order
     */
    public @NotNull List<StartLocation> getStartLocations() {
        return this.startLocations;
    }

    /**
     * @return the player relations, or {@code null} before initialization
     */
    public @Nullable PlayerRelations getPlayerRelations() {
        return this.playerRelations;
    }

    /**
     * Returns the player relations of an initialized world.
     *
     * @return the player relations
     * @throws UnsupportedOperationException if the player relations are not initialized
     */
    public @NotNull PlayerRelations requirePlayerRelations() {
        PlayerRelations playerRelations = this.playerRelations;

        if (playerRelations == null) {
            throw Exceptions.newUnsupportedOperationException("The player relations are not initialized.");
        }

        return playerRelations;
    }

    /**
     * Sets the player relations produced by world initialization.
     *
     * @param relations the player relations produced by world initialization
     */
    public void setPlayerRelations(@NotNull PlayerRelations relations) {
        this.playerRelations = relations;
    }

    /**
     * @return the player upgrade storage, or {@code null} before initialization
     */
    public @Nullable PlayerUpgrades getPlayerUpgrades() {
        return this.playerUpgrades;
    }

    /**
     * Returns the player upgrade storage of an initialized world.
     *
     * @return the player upgrade storage
     * @throws UnsupportedOperationException if the player upgrade storage is not initialized
     */
    public @NotNull PlayerUpgrades requirePlayerUpgrades() {
        PlayerUpgrades playerUpgrades = this.playerUpgrades;

        if (playerUpgrades == null) {
            throw Exceptions.newUnsupportedOperationException("The player upgrade storage is not initialized.");
        }

        return playerUpgrades;
    }

    /**
     * Sets the upgrade storage whose fresh context and game mode have been established.
     *
     * @param upgrades the initialized storage
     * @throws IllegalStateException if upgrade storage is already installed
     */
    public void setPlayerUpgrades(@NotNull PlayerUpgrades upgrades) {
        if (this.playerUpgrades != null) {
            throw Exceptions.newIllegalStateException("Player upgrade storage is already initialized.");
        }

        this.playerUpgrades = upgrades;
    }

    /**
     * @return the player technology storage, or {@code null} before initialization
     */
    public @Nullable PlayerTechnology getPlayerTechnology() {
        return this.playerTechnology;
    }

    /**
     * Returns the player technology storage of an initialized world.
     *
     * @return the player technology storage
     * @throws UnsupportedOperationException if the player technology storage is not initialized
     */
    public @NotNull PlayerTechnology requirePlayerTechnology() {
        PlayerTechnology playerTechnology = this.playerTechnology;

        if (playerTechnology == null) {
            throw Exceptions.newUnsupportedOperationException("The player technology storage is not initialized.");
        }

        return playerTechnology;
    }

    /**
     * Sets the player technology storage for the established game mode; with the accounting installed, the death table is linked to the technology
     * bytes that follow it in the game context.
     *
     * @param technology the initialized storage
     * @throws IllegalStateException if technology storage is already installed
     */
    public void setPlayerTechnology(@NotNull PlayerTechnology technology) {
        if (this.playerTechnology != null) {
            throw Exceptions.newIllegalStateException("Player technology storage is already initialized.");
        }

        this.playerTechnology = technology;

        this.linkDeathTable();
    }

    /**
     * @return the per-player unit availability of a map-settings game, or {@code null} when every unit is allowed
     */
    public @Nullable PlayerUnitAvailability getPlayerUnitAvailability() {
        return this.playerUnitAvailability;
    }

    /**
     * Sets the unit availability loaded from a map-settings game.
     *
     * @param availability the loaded availability
     * @throws IllegalStateException if availability is already installed
     */
    public void setPlayerUnitAvailability(@NotNull PlayerUnitAvailability availability) {
        if (this.playerUnitAvailability != null) {
            throw Exceptions.newIllegalStateException("Player unit availability is already initialized.");
        }

        this.playerUnitAvailability = availability;
    }

    /**
     * @return the production statistics, or {@code null} before initialization
     */
    public @Nullable ProductionStatistics getProductionStatistics() {
        return this.productionStatistics;
    }

    /**
     * Returns the production statistics of an initialized world.
     *
     * @return the production statistics
     * @throws UnsupportedOperationException if the production statistics are not initialized
     */
    public @NotNull ProductionStatistics requireProductionStatistics() {
        ProductionStatistics productionStatistics = this.productionStatistics;

        if (productionStatistics == null) {
            throw Exceptions.newUnsupportedOperationException("The production statistics are not initialized.");
        }

        return productionStatistics;
    }

    /**
     * Sets the production statistics established by world initialization.
     *
     * @param statistics the sample and accumulator storage
     * @throws IllegalStateException if statistics storage is already installed
     */
    public void setProductionStatistics(@NotNull ProductionStatistics statistics) {
        if (this.productionStatistics != null) {
            throw Exceptions.newIllegalStateException("Production statistics are already initialized.");
        }

        this.productionStatistics = statistics;
    }

    /**
     * @return the accounting storage, or {@code null} before context initialization
     */
    public @Nullable UnitAccounting getUnitAccounting() {
        return this.unitAccounting;
    }

    /**
     * Returns the accounting storage of an initialized world.
     *
     * @return the accounting storage
     * @throws UnsupportedOperationException if the unit accounting is not initialized
     */
    public @NotNull UnitAccounting requireUnitAccounting() {
        UnitAccounting unitAccounting = this.unitAccounting;

        if (unitAccounting == null) {
            throw Exceptions.newUnsupportedOperationException("The unit accounting is not initialized.");
        }

        return unitAccounting;
    }

    /**
     * Sets the accounting storage after its game-context initialization is established; with the technology storage installed, the death table is
     * linked to the technology bytes that follow it in the game context.
     *
     * @param accounting the initialized counters
     * @throws IllegalStateException if accounting storage is already installed
     */
    public void setUnitAccounting(@NotNull UnitAccounting accounting) {
        if (this.unitAccounting != null) {
            throw Exceptions.newIllegalStateException("Unit accounting is already initialized.");
        }

        this.unitAccounting = accounting;

        this.linkDeathTable();
    }

    /**
     * Returns the player color rows of the game context, filled from the players' colors on first use; EUD writes change them and every draw reads
     * them.
     *
     * @return the rows, or {@code null} if a player's color lies outside the {@code tunit.pcx} rows
     * @throws NullPointerException if the rows are not created yet and the table was built without game data
     */
    public synchronized @Nullable PlayerColorRows getPlayerColorRows() {
        if (!this.playerColorRowsCreated) {
            GameData gameData = this.gameData;

            if (gameData == null) {
                throw Exceptions.newNullPointerException("The player table was built without game data.");
            }

            this.playerColorRows        = PlayerColorRows.create(gameData, this);
            this.playerColorRowsCreated = true;
        }

        return this.playerColorRows;
    }

    /**
     * Links the death table to the classic technology availability bytes that follow it in the game context, once both the accounting and the
     * technology storage are installed.
     */
    private void linkDeathTable() {
        UnitAccounting accounting = this.unitAccounting;
        PlayerTechnology technology = this.playerTechnology;

        if (accounting != null && technology != null) {
            accounting.getScores().getDeathTable().setSuccessor(technology);
        }
    }
}
