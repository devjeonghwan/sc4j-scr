package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.TechType;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.engine.content.type.UpgradeType;
import org.fogofwarlabs.sc4j.engine.production.ProductionSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrNativeIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitLifecycle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves the SC:R production as the engine {@link ProductionSystem}: the five unit type slots of a producer's build queue, read from its current
 * queue index ({@link Unit#getBuildQueueIndex()}, wrapping at {@link Unit#BUILD_QUEUE_SLOTS}), where {@link ScrUnits#NATIVE_NONE} marks an empty
 * slot; the queue push of the computer players and abilities ({@link UnitProduction#pushBuildQueue}), the research and upgrade start shared by the
 * commands and the computer players ({@link UnitResearch#startResearch}, {@link UnitResearch#startUpgrade}) and the entry cancellation
 * ({@link UnitLifecycle#cancelBuildQueueEntry}). The view holds no state and is no state hash contributor.
 * <p>
 * The SC:R build, train and research commands test the requirements of the commanding player first and then use these operations.
 */
public final class ScrProductionSystem implements ProductionSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link ScrProductionSystem} instance; it serves the SC:R game it finds when it is attached.
     */
    public ScrProductionSystem() {
        this.game = null;
    }

    /**
     * Resolves what this system uses from the game. Called once, after every system exists.
     * <p>
     * Binds the SC:R game system of the game.
     *
     * @param game the game
     */
    @Override
    public void attach(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
        this.game = game.getModSystem(Game.SYSTEM_KEY);
    }

    /**
     * Adds a unit to train, build or morph at the end of a producer's queue after testing its cost; the cost is spent
     * now or when the entry starts, as the implementation documents.
     * <p>
     * The SC:R push is {@link UnitProduction#pushBuildQueue}: supply is tested only when the entry is the current one, and the cost is charged at
     * once unless the type is a building.
     *
     * @param producer the producing unit
     * @param type     the unit type to make
     * @return the queue slot of the new entry, or {@code -1} if the queue is full or the cost cannot be paid
     * @throws IllegalArgumentException      if the producer is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the queue index is outside the queue
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public int enqueueUnit(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, @NotNull UnitType type) {
        return this.requireGame().getUnitProduction().pushBuildQueue(Unit.of(producer), ScrNativeIds.toNative(type));
    }

    /**
     * Starts researching a tech at a producer after testing and spending its cost.
     * <p>
     * The SC:R start is {@link UnitResearch#startResearch}: the cost test with its error display, the research state, the spend and the working
     * animation.
     *
     * @param producer         the producing unit
     * @param type             the tech
     * @param instructionLimit the instruction limit of each animation program run the start starts
     * @return {@code true} if the research started, {@code false} otherwise
     * @throws IllegalArgumentException      if the producer is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached or the producer has no sprite
     * @throws UnsupportedOperationException if player technology storage is absent
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public boolean startResearch(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, @NotNull TechType type, int instructionLimit) {
        return this.requireGame().getUnitResearch().startResearch(Unit.of(producer), ScrNativeIds.toNative(type), instructionLimit);
    }

    /**
     * Starts the next level of an upgrade at a producer after testing and spending its cost.
     * <p>
     * The SC:R start is {@link UnitResearch#startUpgrade}: the cost test with its error display, the upgrade state and target level, the spend and
     * the working animation.
     *
     * @param producer         the producing unit
     * @param type             the upgrade
     * @param instructionLimit the instruction limit of each animation program run the start starts
     * @return {@code true} if the upgrade started, {@code false} otherwise
     * @throws IllegalArgumentException      if the producer is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached or the producer has no sprite
     * @throws UnsupportedOperationException if player upgrade storage is absent
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public boolean startUpgrade(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, @NotNull UpgradeType type, int instructionLimit) {
        return this.requireGame().getUnitResearch().startUpgrade(Unit.of(producer), ScrNativeIds.toNative(type), instructionLimit);
    }

    /**
     * Cancels one entry of a producer's queue; the later entries move up.
     * <p>
     * The SC:R cancellation is {@link UnitLifecycle#cancelBuildQueueEntry}: the front entry with a unit in progress cancels that unit, other
     * entries are refunded unless they are buildings.
     *
     * @param producer         the producing unit
     * @param slot             the queue slot
     * @param instructionLimit the instruction limit of each animation program run the cancellation starts
     * @return {@code true} if an entry was cancelled, {@code false} if the slot is empty
     * @throws IllegalArgumentException if the producer is not an SC:R unit
     * @throws IllegalStateException    if the system is not attached
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public boolean cancel(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, int slot, int instructionLimit) {
        Unit unit = Unit.of(producer);

        if (slot < 0 || slot >= Unit.BUILD_QUEUE_SLOTS ||
            unit.getBuildQueueUnitType((unit.getBuildQueueIndex() + slot) % Unit.BUILD_QUEUE_SLOTS) == ScrUnits.NATIVE_NONE) {
            return false;
        }

        this.requireGame().getUnitLifecycle().cancelBuildQueueEntry(unit, slot, instructionLimit);

        return true;
    }

    /**
     * Returns the length of a producer's queue.
     * <p>
     * The length is the number of occupied slots from the current queue index on, up to the first empty one.
     *
     * @param producer the producing unit
     * @return the number of entries in the producer's queue
     * @throws IllegalArgumentException if the producer is not an SC:R unit
     */
    @Override
    public int getQueueLength(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer) {
        Unit unit = Unit.of(producer);
        int first = unit.getBuildQueueIndex();
        int length = 0;

        if (first >= Unit.BUILD_QUEUE_SLOTS) {
            return 0;
        }

        while (length < Unit.BUILD_QUEUE_SLOTS && unit.getBuildQueueUnitType((first + length) % Unit.BUILD_QUEUE_SLOTS) != ScrUnits.NATIVE_NONE) {
            length++;
        }

        return length;
    }

    /**
     * Returns the unit type of one queue entry.
     *
     * @param producer the producing unit
     * @param slot     the queue slot, below the queue length
     * @return the unit type
     * @throws IllegalArgumentException  if the producer is not an SC:R unit or the slot holds a value outside units.dat
     * @throws IndexOutOfBoundsException if the slot is not below the queue length
     */
    @Override
    public @NotNull UnitType getQueuedType(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, int slot) {
        int length = this.getQueueLength(producer);

        if (slot < 0 || slot >= length) {
            throw Exceptions.newIndexOutOfBoundsException("No build queue entry {0}; the queue holds {1}.", slot, length);
        }

        Unit unit = Unit.of(producer);

        return ScrUnits.get(unit.getBuildQueueUnitType((unit.getBuildQueueIndex() + slot) % Unit.BUILD_QUEUE_SLOTS));
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R production system is not attached.");
        }

        return attached;
    }
}
