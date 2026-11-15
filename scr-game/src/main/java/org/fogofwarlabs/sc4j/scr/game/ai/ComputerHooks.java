package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.lifecycle.BuildingDestroyedHook;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitAdoptedHook;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitRemovedHook;
import org.fogofwarlabs.sc4j.engine.movement.TransportPickupHook;
import org.fogofwarlabs.sc4j.engine.order.DisabledOrderHook;
import org.fogofwarlabs.sc4j.engine.order.OrderTimerHook;
import org.fogofwarlabs.sc4j.engine.production.QueueSlotClearedHook;
import org.fogofwarlabs.sc4j.engine.production.QueueSlotMovedHook;
import org.fogofwarlabs.sc4j.scr.game.ability.TransportLoadedHook;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.ScrHooks;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.visibility.BurrowedTimerHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hears the generic places the SC:R computer players react to and forwards each to the game's computer AI systems: the
 * SC:R implementation of the hook points {@link ScrHooks} registers. The hook holds no state of its own; it finds the SC:R
 * game when it is attached, after the systems, so the AI owners that world initialization installs later are found.
 * <p>
 * The computer players' frame work is run by the computer player system ({@link ScrComputerPlayerSystem}), not by these hooks.
 */
public final class ComputerHooks implements UnitRemovedHook, UnitAdoptedHook, BuildingDestroyedHook, QueueSlotMovedHook, QueueSlotClearedHook,
                                            DisabledOrderHook, OrderTimerHook, TransportPickupHook, TransportLoadedHook, BurrowedTimerHook,
                                            GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link ComputerHooks} instance; it calls the SC:R game it finds when it is attached.
     */
    public ComputerHooks() {
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
     * Called when a unit leaves its owner.
     * <p>
     * The computer players drop the unit from their records ({@link ComputerUnits#notifyRemoved}).
     *
     * @param unit  the unit
     * @param death {@code true} if the unit dies, {@code false} if it changes owner
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public void onUnitRemoved(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, boolean death) {
        this.requireGame().getComputerUnits().notifyRemoved(Unit.of(unit), death);
    }

    /**
     * Called when a unit has its new owner.
     * <p>
     * A computer owner takes the unit into its records ({@link ComputerUnits#notifyAdopted}).
     *
     * @param unit             the unit with its new owner
     * @param instructionLimit the instruction limit of each animation program run the hook starts
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public void onUnitAdopted(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int instructionLimit) {
        this.requireGame().getComputerUnits().notifyAdopted(Unit.of(unit), instructionLimit);
    }

    /**
     * Called when a building dies.
     * <p>
     * The computer players refresh their unit status, which clears every claimed-target mark.
     *
     * @param unit the destroyed building
     * @throws IllegalStateException if the hook is not attached
     */
    @Override
    public void onBuildingDestroyed(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        ComputerPlayerPhase computer = this.requireGame().getComputerPlayerPhase();

        if (computer != null) {
            computer.requestUnitStatusRefresh();
        }
    }

    /**
     * Called when a queue entry moves.
     * <p>
     * The building record of a computer producer moves the request kind and town of the slot with the entry.
     *
     * @param producer   the producing unit
     * @param slot       the slot that receives the entry
     * @param sourceSlot the slot the entry leaves
     * @throws IllegalArgumentException if the producer is not an SC:R unit
     */
    @Override
    public void onQueueSlotMoved(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, int slot, int sourceSlot) {
        ComputerUnitRecord record = Unit.of(producer).getComputerRecord();

        if (record != null && record.getKind() == ComputerUnitRecord.KIND_BUILDING) {
            record.queueKinds[slot]   = record.queueKinds[sourceSlot];
            record.queueTargets[slot] = record.queueTargets[sourceSlot];
        }
    }

    /**
     * Called when a queue slot becomes empty.
     * <p>
     * The building record of a computer producer clears the request kind and town of the slot
     * ({@link ComputerPlayerPhase#notifyQueueSlotCleared}).
     *
     * @param producer the producing unit
     * @param slot     the emptied slot
     * @throws IllegalArgumentException if the producer is not an SC:R unit
     */
    @Override
    public void onQueueSlotCleared(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit producer, int slot) {
        ComputerPlayerPhase.notifyQueueSlotCleared(Unit.of(producer), slot);
    }

    /**
     * Called when a disabled or immobile unarmed unit runs its main order.
     * <p>
     * Computer units react to the exposed unit ({@link ComputerDefense#alertDisabled}).
     *
     * @param unit the unit
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public void onDisabledOrder(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        this.requireGame().getComputerDefense().alertDisabled(Unit.of(unit));
    }

    /**
     * Called before the timed order of a unit runs.
     * <p>
     * Acid-spored units and the first Interceptor of the frame expose themselves to computer units
     * ({@link ComputerDefense#alertOnOrderTimer}).
     *
     * @param unit the unit
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public void onOrderTimer(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        this.requireGame().getComputerDefense().alertOnOrderTimer(Unit.of(unit));
    }

    /**
     * Checks whether a transport picks a unit up for a destination; a hook that answers {@code true} has arranged the pickup.
     * <p>
     * The computer transport test is {@link ComputerCombat#requiresPickup}.
     *
     * @param unit the unit about to move
     * @param x    the x coordinate of the destination
     * @param y    the y coordinate of the destination
     * @return {@code true} if a transport picks the unit up and the unit does not move by itself, {@code false} if this hook gives no answer
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public boolean requiresPickup(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int x, int y) {
        return this.requireGame().getComputerCombat().requiresPickup(Unit.of(unit), x, y);
    }

    /**
     * Called when a unit is loaded into a transport.
     * <p>
     * A loaded SCV stops being the reserved worker of its computer town ({@link ComputerPlayerPhase#notifyLoaded}).
     *
     * @param unit the loaded unit
     */
    @Override
    public void onTransportLoaded(@NotNull Unit unit) {
        ComputerPlayerPhase.notifyLoaded(unit, this.requireGame().requireGameData().getBaseType(unit));
    }

    /**
     * Called when the order timer of a burrowed unit elapses.
     * <p>
     * A burrowed unit with a computer record casts a spell, or else picks a target.
     *
     * @param unit             the burrowed unit
     * @param instructionLimit the instruction limit of each animation program run the hook starts
     * @throws IllegalStateException         if the hook is not attached
     * @throws UnsupportedOperationException if the computer-player phase is absent
     */
    @Override
    public void onBurrowedTimer(@NotNull Unit unit, int instructionLimit) {
        Game attached = this.requireGame();
        ComputerPlayerPhase phase = attached.getComputerPlayerPhase();

        if (phase == null) {
            throw Exceptions.newUnsupportedOperationException("Burrowed computer units require the computer-player phase.");
        }

        if (!ComputerSpells.cast(phase, attached, unit, false)) {
            attached.getComputerCombat().acquireTarget(unit, 0, true, false, instructionLimit);
        }
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R computer hooks are not attached.");
        }

        return attached;
    }
}
