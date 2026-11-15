package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.collision.CollisionSystem;
import org.fogofwarlabs.sc4j.engine.math.MutablePoint;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitCollisionRules;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves the SC:R movement collision as the engine {@link CollisionSystem}: the blocking test ({@link UnitCollisionRules#isBlockingMovement}), the
 * placement search of a unit ({@link UnitPositioning#find} in its default rectangle) and the immovable state
 * ({@link UnitMovementCollision#setImmovable}). The view holds no simulation state and is no state hash contributor; it keeps one scratch array
 * for the search result.
 * <p>
 * The SC:R unstacking runs inside the movement states ({@link UnitMovement#update}).
 */
public final class ScrCollisionSystem implements CollisionSystem {
    private final int @NotNull [] foundPosition;

    private @Nullable Game game;

    /**
     * Creates a {@link ScrCollisionSystem} instance; it serves the SC:R game it finds when it is attached.
     */
    public ScrCollisionSystem() {
        this.foundPosition = new int[UnitPositioning.POSITION_COORDINATES];

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
     * Checks whether one unit blocks the movement of another.
     * <p>
     * The SC:R test is {@link UnitCollisionRules#isBlockingMovement} with the other unit as the candidate and the moving unit as the incoming one.
     *
     * @param unit      the moving unit
     * @param otherUnit the unit in the way
     * @return {@code true} if the other unit blocks the moving unit, {@code false} otherwise
     * @throws IllegalArgumentException      if a unit is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the other unit has a status flag the SC:R test does not represent
     */
    @Override
    public boolean isBlocking(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit otherUnit) {
        return this.requireGame().getHooks().getCollisionRules().isBlockingMovement(Unit.of(otherUnit), Unit.of(unit));
    }

    /**
     * Finds a free position for a unit near a position, within the search area of the implementation: the requested
     * position itself if the unit can stand there, else the nearest free one the search meets.
     * <p>
     * The SC:R search is {@link UnitPositioning#find} within the default rectangle of {@code 128} pixels around the position; the found WORD
     * coordinates are written unsigned. A failed search leaves the exit-blocked command error.
     *
     * @param unit     the unit to put down
     * @param x        the x coordinate to search around
     * @param y        the y coordinate to search around
     * @param position receives the found position
     * @return {@code true} if a free position was found and written, {@code false} otherwise
     * @throws IllegalArgumentException      if the unit is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if required placement storage is unavailable or the search repeats its steps
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public boolean findFreePosition(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int x, int y, @NotNull MutablePoint position) {
        int[] found = this.foundPosition;

        if (!this.requireGame().getUnitPositioning().find(Unit.of(unit), x, y, found)) {
            return false;
        }

        position.set(found[UnitPositioning.X_INDEX], found[UnitPositioning.Y_INDEX]);

        return true;
    }

    /**
     * Makes a unit immovable for other units: pushing does not move it until it moves again by itself.
     * <p>
     * The SC:R state is {@link UnitMovementCollision#setImmovable}; the unit's next movement clears it.
     *
     * @param unit the unit
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the system is not attached
     */
    @Override
    public void makeImmovable(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        this.requireGame().getUnitMovementCollision().setImmovable(Unit.of(unit));
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R collision system is not attached.");
        }

        return attached;
    }
}
