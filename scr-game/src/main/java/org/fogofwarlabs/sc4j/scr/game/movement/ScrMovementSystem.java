package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.movement.MovementSystem;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves the SC:R flingy movement as the engine {@link MovementSystem}: destinations ({@link UnitPositioning#setMovementDestination}), target
 * following ({@link UnitTargetMovement#moveToTarget}), stopping ({@link UnitPointMovement#stop}), the target heading and the arrival test
 * ({@link UnitTargetMovement#isAtMovementTarget}) and the speed recalculation ({@link UnitSpeed#recalculate}). The view holds no state and is
 * no state hash contributor.
 * <p>
 * The motion step of a frame is {@link UnitMovement#update}, which the SC:R unit phase runs with its movement context.
 */
public final class ScrMovementSystem implements MovementSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link ScrMovementSystem} instance; it serves the SC:R game it finds when it is attached.
     */
    public ScrMovementSystem() {
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
     * Sets the point a unit moves to; the motion starts in its next update.
     * <p>
     * The SC:R destination is {@link UnitPositioning#setMovementDestination}: clamped to the map, the path invalidated, unless the unit already
     * heads there or the computer controller picks it up by transport.
     *
     * @param unit the unit
     * @param x    the x coordinate of the destination
     * @param y    the y coordinate of the destination
     * @throws IllegalArgumentException      if the unit is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the path storage is unavailable
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public void setDestination(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int x, int y) {
        this.requireGame().getUnitPositioning().setMovementDestination(Unit.of(unit), x, y);
    }

    /**
     * Makes a unit follow another unit: the destination follows the target's position in each update.
     * <p>
     * The SC:R follow is {@link UnitTargetMovement#moveToTarget}.
     *
     * @param unit       the unit
     * @param targetUnit the unit to follow
     * @throws IllegalArgumentException      if a unit is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached or the target has no sprite
     * @throws UnsupportedOperationException if a retained path has no path pool
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public void setTargetUnit(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit targetUnit) {
        this.requireGame().getUnitTargetMovement().moveToTarget(Unit.of(unit), Unit.of(targetUnit));
    }

    /**
     * Stops a unit where it is: the destination becomes its position and its speed becomes {@code 0}.
     * <p>
     * The SC:R stop is {@link UnitPointMovement#stop}: the unit brakes at its fixed-point braking destination, clamped to the map.
     *
     * @param unit the unit
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the system is not attached
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public void stop(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        this.requireGame().getUnitPointMovement().stop(Unit.of(unit));
    }

    /**
     * @return the number of direction steps of a full turn of the directions and headings of this system's units, clockwise from up
     */
    @Override
    public int getDirectionCount() {
        return FlingyMotionUtils.DIRECTION_FULL_TURN;
    }

    /**
     * Turns a unit towards a direction without moving it.
     * <p>
     * The direction becomes the target heading of the SC:R unit, truncated to a BYTE; its movement turns it at its turn rate.
     *
     * @param unit      the unit
     * @param direction the direction, {@code 0} up to the direction count less one ({@link #getDirectionCount})
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     */
    @Override
    public void turnTo(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int direction) {
        Unit.of(unit).setTargetHeading(direction);
    }

    /**
     * Checks whether a unit stands at its destination.
     * <p>
     * The SC:R test is {@link UnitTargetMovement#isAtMovementTarget}: the sprite stands exactly at the movement destination.
     *
     * @param unit the unit
     * @return {@code true} if the unit reached its destination and does not move, {@code false} otherwise
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     */
    @Override
    public boolean isAtDestination(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        return UnitTargetMovement.isAtMovementTarget(Unit.of(unit));
    }

    /**
     * Recalculates the speed, acceleration and turn rate of a unit after its type or its stat modifiers changed. A
     * movement whose speed comes from the unit's walking animation runs that program to measure it.
     * <p>
     * The SC:R recalculation is {@link UnitSpeed#recalculate}: an iscript-controlled unit runs its Walking script, any other unit takes the
     * {@code flingy.dat} values with its speed modifiers.
     *
     * @param unit             the unit
     * @param instructionLimit the instruction limit of each animation program run the recalculation starts
     * @throws IllegalArgumentException      if the unit is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if a flingy-controlled unit has no initialized execution scope
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public void recalculateSpeed(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int instructionLimit) {
        this.requireGame().getUnitSpeed().recalculate(Unit.of(unit), instructionLimit);
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R movement system is not attached.");
        }

        return attached;
    }
}
