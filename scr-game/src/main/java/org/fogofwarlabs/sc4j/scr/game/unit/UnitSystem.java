package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.engine.spatial.SearchCounter;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the unit storage of one game: the unit pool, the unit finder (spatial index) with the host's search counter, the
 * unit registry and the creation inputs of the current phase.
 * <p>
 * The pool and the finder are created by world initialization with the established capacity and stay {@code null}
 * until then.
 */
public final class UnitSystem {
    private @Nullable UnitPool unitPool;
    private @Nullable UnitSpatialIndex unitSpatialIndex;
    /**
     * The unit finder search counter of the host process ({@link SearchCounter}): the one of the installed data the
     * game was copied from (loaded once per process), or a fresh process's without game data.
     */
    private @NotNull SearchCounter unitFinderSearchCounter;

    private @Nullable UnitRegistry unitRegistry;
    private @Nullable UnitCreationContext unitCreationContext;
    /**
     * The players of the game by slot index ({@link #setPlayers}), which the pool resolves the engine owners from.
     */
    private @NotNull Player @NotNull [] players;

    /**
     * Creates a {@link UnitSystem} instance without storage.
     *
     * @param unitFinderSearchCounter the unit finder search counter of the host process
     */
    public UnitSystem(@NotNull SearchCounter unitFinderSearchCounter) {
        this.unitPool                = null;
        this.unitSpatialIndex        = null;
        this.unitFinderSearchCounter = unitFinderSearchCounter;

        this.unitRegistry        = null;
        this.unitCreationContext = null;
        this.players             = new Player[0];
    }

    /**
     * @return the explicitly initialized unit pool, or {@code null} before initialization
     */
    public @Nullable UnitPool getUnitPool() {
        return this.unitPool;
    }

    /**
     * Returns the unit pool of an initialized world.
     *
     * @return the unit pool
     * @throws UnsupportedOperationException if the unit pool is not initialized
     */
    public @NotNull UnitPool requireUnitPool() {
        UnitPool unitPool = this.unitPool;

        if (unitPool == null) {
            throw Exceptions.newUnsupportedOperationException("The unit pool is not initialized.");
        }

        return unitPool;
    }

    /**
     * @return the initialized spatial index, or {@code null} before initialization
     */
    public @Nullable UnitSpatialIndex getUnitSpatialIndex() {
        return this.unitSpatialIndex;
    }

    /**
     * Returns the unit spatial index of an initialized world.
     *
     * @return the unit spatial index
     * @throws UnsupportedOperationException if the unit spatial index is not initialized
     */
    public @NotNull UnitSpatialIndex requireUnitSpatialIndex() {
        UnitSpatialIndex unitSpatialIndex = this.unitSpatialIndex;

        if (unitSpatialIndex == null) {
            throw Exceptions.newUnsupportedOperationException("The unit spatial index is not initialized.");
        }

        return unitSpatialIndex;
    }

    /**
     * @return the unit finder search counter of the host process this game uses
     */
    public @NotNull SearchCounter getUnitFinderSearchCounter() {
        return this.unitFinderSearchCounter;
    }

    /**
     * Sets the game's finder counter and updates an initialized spatial index to use the same counter.
     *
     * @param unitFinderSearchCounter the process's counter
     */
    public void setUnitFinderSearchCounter(@NotNull SearchCounter unitFinderSearchCounter) {
        this.unitFinderSearchCounter = unitFinderSearchCounter;

        if (this.unitSpatialIndex != null) {
            this.unitSpatialIndex.setSearchCounter(unitFinderSearchCounter);
        }
    }

    /**
     * @return the established registry context, or {@code null} before its initialization
     */
    public @Nullable UnitRegistry getUnitRegistry() {
        return this.unitRegistry;
    }

    /**
     * Returns the unit registry of an initialized world.
     *
     * @return the unit registry
     * @throws UnsupportedOperationException if the unit registry is not initialized
     */
    public @NotNull UnitRegistry requireUnitRegistry() {
        UnitRegistry unitRegistry = this.unitRegistry;

        if (unitRegistry == null) {
            throw Exceptions.newUnsupportedOperationException("The unit registry is not initialized.");
        }

        return unitRegistry;
    }

    /**
     * Sets the registry before owner population.
     *
     * @param registry the established registry context
     * @throws IllegalStateException if a registry context is already installed
     */
    public void setUnitRegistry(@NotNull UnitRegistry registry) {
        if (this.unitRegistry != null) {
            throw Exceptions.newIllegalStateException("Native registry storage is already initialized.");
        }

        this.unitRegistry = registry;
    }

    /**
     * @return the established unit-creation inputs for the current phase, or {@code null} if unknown
     */
    public @Nullable UnitCreationContext getUnitCreationContext() {
        return this.unitCreationContext;
    }

    /**
     * Returns the unit-creation inputs of the current phase of an initialized world.
     *
     * @return the unit-creation inputs of the current phase
     * @throws UnsupportedOperationException if the unit creation context is not established
     */
    public @NotNull UnitCreationContext requireUnitCreationContext() {
        UnitCreationContext unitCreationContext = this.unitCreationContext;

        if (unitCreationContext == null) {
            throw Exceptions.newUnsupportedOperationException("The unit creation context is not established.");
        }

        return unitCreationContext;
    }

    /**
     * Sets the unit-creation inputs of the current phase.
     *
     * @param context the explicitly established creation inputs for the current phase, or {@code null} if unknown
     */
    public void setUnitCreationContext(@Nullable UnitCreationContext context) {
        this.unitCreationContext = context;
    }


    /**
     * Sets the players of the game, the engine owners of the native owner BYTEs, on this system and its unit pool.
     *
     * @param players the players of the game by slot index, kept
     */
    public void setPlayers(@NotNull Player @NotNull [] players) {
        this.players = players;

        if (this.unitPool != null) {
            this.unitPool.setPlayers(players);
        }
    }

    /**
     * Initializes persistent native unit storage with an established capacity and the players of the game.
     *
     * @param capacity the established runtime unit capacity
     * @throws IllegalStateException if unit storage is already initialized
     */
    public void initializeUnitPool(int capacity) {
        if (this.unitPool != null) {
            throw Exceptions.newIllegalStateException("Native unit storage is already initialized.");
        }

        this.unitPool = new UnitPool(capacity);

        this.unitPool.setPlayers(this.players);
    }

    /**
     * Resets native edge storage after establishing unit capacity.
     *
     * @param units the unit type table (query bounds)
     * @throws IllegalStateException if unit storage is absent or the index is already initialized
     */
    public void initializeUnitSpatialIndex(@NotNull ScrUnitTypeView units) {
        if (this.unitPool == null || this.unitSpatialIndex != null) {
            throw Exceptions.newIllegalStateException("Native spatial initialization requires unit storage and an uninitialized index.");
        }

        this.unitSpatialIndex = new UnitSpatialIndex(this.unitPool, this.unitFinderSearchCounter);

        this.unitSpatialIndex.initializeQueryBounds(units);
    }
}
