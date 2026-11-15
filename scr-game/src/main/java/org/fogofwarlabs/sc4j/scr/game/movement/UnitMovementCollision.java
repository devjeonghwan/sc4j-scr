package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.UnitPath;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves retained movement blockers and waits for moving units to release space.
 */
public final class UnitMovementCollision {
    /**
     * The collision decision that stops the unit at its position and keeps it movable.
     */
    private static final int DECISION_STOP = 1;
    /**
     * The collision decision that stops the unit and makes it immovable.
     */
    private static final int DECISION_STOP_IMMOVABLE = 2;
    /**
     * The collision decision that searches a new path around the blocker.
     */
    private static final int DECISION_REPATH = 3;
    /**
     * The collision decision that searches a new path around moving units.
     */
    private static final int DECISION_REPATH_MOVERS = 4;
    /**
     * The collision decision that stops and turns before a new path.
     */
    private static final int DECISION_TURN_AND_START = 5;
    /**
     * The collision decision that waits for the blocker to leave.
     */
    private static final int DECISION_WAIT = 6;
    /**
     * The collision decision that slides along the blocker.
     */
    private static final int DECISION_SLIDE = 7;

    /**
     * The blocker is above the unit.
     */
    private static final int SIDE_ABOVE = 0;
    /**
     * The blocker is right of the unit.
     */
    private static final int SIDE_RIGHT = 1;
    /**
     * The blocker is below the unit.
     */
    private static final int SIDE_BELOW = 2;
    /**
     * The blocker is left of the unit.
     */
    private static final int SIDE_LEFT = 3;

    /**
     * A blocker whose path recalculation countdown reached this is treated as standing still.
     */
    private static final int STALLED_BLOCKER_COUNTDOWN = 30;
    /**
     * A repath decision waits until the path is at least this many frames old; until then the unit only stops walking.
     */
    private static final int MINIMUM_REPATH_PATH_AGE = 7;
    /**
     * A waiting unit whose path recalculation countdown reached this gives up waiting.
     */
    private static final int WAIT_GIVE_UP_COUNTDOWN = 25;
    /**
     * A slide is possible only while the waypoint direction differs from the movement direction by less than this.
     */
    private static final int MAXIMUM_SLIDE_TURN = 80;
    /**
     * The number of slide directions tried: the preferred one and its opposite.
     */
    private static final int SLIDE_ATTEMPTS = 2;
    /**
     * The largest value of an unranged random number.
     */
    private static final int RANDOM_MAXIMUM = 0x7FFF;
    /**
     * A waiting unit that gives up forces its way free when its random number is below {@link #RANDOM_MAXIMUM} divided by this, and repaths around
     * moving units otherwise.
     */
    private static final int FORCE_MOVE_FREE_RANDOM_DIVISOR = 2;
    /**
     * The margin in pixels within which the main unit counts as touching its movement target.
     */
    private static final int TARGET_CONTACT_MARGIN = 1;

    private static boolean isIntersecting(@NotNull ScrUnitTypeView unitTypes,
                                          @NotNull Unit first, int firstX, int firstY,
                                          @NotNull Unit second, int secondX, int secondY,
                                          int margin) {
        int firstType = first.getUnitId();
        int secondType = second.getUnitId();

        return secondX - unitTypes.getDimensionLeft(secondType) <= firstX + unitTypes.getDimensionRight(firstType) + margin &&
               secondY - unitTypes.getDimensionUp(secondType) <= firstY + unitTypes.getDimensionDown(firstType) + margin &&
               firstX - unitTypes.getDimensionLeft(firstType) <= secondX + unitTypes.getDimensionRight(secondType) + margin &&
               firstY - unitTypes.getDimensionUp(firstType) <= secondY + unitTypes.getDimensionDown(secondType) + margin;
    }

    private static int findBlockerSide(@NotNull ScrUnitTypeView unitTypes, @NotNull Unit unit, @NotNull Unit blocker) {
        int blockerType = blocker.getUnitId();
        int unitType = unit.getUnitId();
        int left = blocker.getPositionX() + unitTypes.getDimensionRight(blockerType) - unit.getPositionX() + unitTypes.getDimensionLeft(unitType);
        int right = blocker.getPositionX() - unitTypes.getDimensionLeft(blockerType) - unit.getPositionX() - unitTypes.getDimensionRight(unitType);
        int up = blocker.getPositionY() + unitTypes.getDimensionDown(blockerType) - unit.getPositionY() + unitTypes.getDimensionUp(unitType);
        int down = blocker.getPositionY() - unitTypes.getDimensionUp(blockerType) - unit.getPositionY() - unitTypes.getDimensionDown(unitType);

        if (left < 0) {
            return SIDE_LEFT;
        }

        if (right > 0) {
            return SIDE_RIGHT;
        }

        if (up < 0) {
            return SIDE_ABOVE;
        }

        if (down > 0) {
            return SIDE_BELOW;
        }

        int side = SIDE_RIGHT;
        int distance = Math.abs(right);

        if (Math.abs(left) < distance) {
            side     = SIDE_LEFT;
            distance = Math.abs(left);
        }

        if (Math.abs(down) < distance) {
            side     = SIDE_BELOW;
            distance = Math.abs(down);
        }

        if (Math.abs(up) < distance) {
            side = SIDE_ABOVE;
        }

        return side;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitMovementCollision} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitMovementCollision(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Sets the unit immovable: stops it at its current position and clears its commanded user action flag.
     *
     * @param unit the unit to make immovable
     */
    public void setImmovable(@NotNull Unit unit) {
        unit.setNextSpeed(0);
        FlingyMotionUtils.setSpeed(unit, 0);
        this.game.getUnitPointMovement().stop(unit);
        this.game.getUnitPositioning().setMovementDestination(unit, unit.getPositionX(), unit.getPositionY());
        unit.setStatus(unit.getStatus() | UnitStatusFlags.IMMOVABLE);
        unit.setUserActionFlags(unit.getUserActionFlags() & ~Unit.USER_ACTION_COMMANDED);
    }


    /**
     * Updates the FixCollision state: decides from the retained blocker whether the unit stops, repaths, waits or slides, and enters the matching
     * state.
     *
     * @param unit             the colliding unit
     * @param instructionLimit the instruction budget of the walking animation
     * @return {@code true} if the movement dispatcher continues with the next state in this frame, {@code false} otherwise
     * @throws IllegalStateException         if a turret has no main unit or the decision is invalid, or the blocker has no sprite
     * @throws UnsupportedOperationException if the path pool or the unit pool is not initialized
     * @throws NullPointerException          if the world was built without game data
     */
    boolean fix(@NotNull Unit unit, int instructionLimit) {
        if (UnitPath.updateMovementGuard(this.game, unit, false)) {
            return true;
        }

        UnitMovement.increasePathRecalculationCountdown(unit);

        PathPool paths = this.game.getPathSystem().requirePathPool();
        Unit blocker = this.game.getUnitSystem().requireUnitPool().resolveHandle(paths.getLastCollisionHandle(unit.getPathIndex()));

        if (blocker == null || (blocker.requireSprite().getFlags() & Sprite.FLAG_HIDDEN) != 0 ||
            !this.game.getHooks().getCollisionRules().isBlockingMovement(blocker, unit)) {
            unit.setMovementState(MovementStates.FOLLOW_PATH);

            return false;
        }

        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        Unit target = unit.getMovementTargetUnit();
        Unit mainUnit = (unitTypes.getFlags(unit.getUnitId()) & UnitTypeFlags.SUBUNIT) != 0 ? unit.getSubunit() : unit;

        if (mainUnit == null) {
            throw Exceptions.newIllegalStateException("Turret collision requires its retained main unit.");
        }

        int decision = DECISION_STOP;
        int slideDirection = -1;

        if (target == null ||
            !isIntersecting(unitTypes, mainUnit, mainUnit.getPositionX(), mainUnit.getPositionY(),
                            target, target.getPositionX(), target.getPositionY(), TARGET_CONTACT_MARGIN)) {
            if (isIntersecting(unitTypes,
                               blocker, blocker.getPositionX(), blocker.getPositionY(),
                               unit, unit.getMovementDestinationX(), unit.getMovementDestinationY(),
                               0)) {
                if (blocker.getPathRecalculationCountdown() >= STALLED_BLOCKER_COUNTDOWN ||
                    isIntersecting(unitTypes,
                                   blocker, blocker.getMovementDestinationX(), blocker.getMovementDestinationY(),
                                   unit, unit.getMovementDestinationX(), unit.getMovementDestinationY(),
                                   0)) {
                    decision = target != null ? DECISION_REPATH : DECISION_STOP_IMMOVABLE;
                }
            } else {
                if (!isIntersecting(unitTypes,
                                    blocker, blocker.getPositionX(), blocker.getPositionY(),
                                    unit, unit.getMovementWaypointX(), unit.getMovementWaypointY(),
                                    0)) {
                    if ((blocker.getMovementFlags() & MovementFlags.ACCELERATING) != 0) {
                        int direction = blocker.getTargetHeading();
                        boolean departing = switch (findBlockerSide(unitTypes, unit, blocker)) {
                            case SIDE_ABOVE -> direction <= FlingyMotionUtils.DIRECTION_RIGHT || direction >= FlingyMotionUtils.DIRECTION_LEFT;
                            case SIDE_RIGHT -> direction <= FlingyMotionUtils.DIRECTION_DOWN;
                            case SIDE_BELOW -> direction >= FlingyMotionUtils.DIRECTION_RIGHT && direction <= FlingyMotionUtils.DIRECTION_LEFT;
                            default -> direction == FlingyMotionUtils.DIRECTION_UP || direction >= FlingyMotionUtils.DIRECTION_DOWN;
                        };

                        if (departing) {
                            decision = DECISION_WAIT;
                        } else if (blocker.getPathRecalculationCountdown() <= UnitMovement.MOVING_PATH_COUNTDOWN &&
                                   !UnitTargetMovement.isAtMovementTarget(blocker)) {
                            if (blocker.getMovementState() == MovementStates.FOLLOW_PATH || blocker.getMovementState() == MovementStates.SCOUT_PATH) {
                                decision = DECISION_WAIT;
                            } else if (blocker.getMovementState() != MovementStates.WAIT_FREE) {
                                decision = DECISION_REPATH_MOVERS;
                            }
                        }
                    }

                    if (decision == DECISION_STOP) {
                        slideDirection = this.findSlideDirection(unit, blocker);
                        decision       = slideDirection >= 0 ? DECISION_SLIDE : DECISION_TURN_AND_START;
                    }
                }

                if (decision == DECISION_STOP &&
                    (blocker.getPathRecalculationCountdown() >= STALLED_BLOCKER_COUNTDOWN ||
                     isIntersecting(unitTypes,
                                    blocker, blocker.getMovementDestinationX(), blocker.getMovementDestinationY(),
                                    unit, unit.getMovementWaypointX(), unit.getMovementWaypointY(),
                                    0))) {
                    decision = DECISION_REPATH;
                }
            }

            if (decision == DECISION_STOP) {
                int collisionDirection = FlingyMotionUtils.calculateDirection(blocker.getPositionX() - unit.getPositionX(),
                                                                              blocker.getPositionY() - unit.getPositionY());
                int direction = blocker.getTargetHeading();
                int comparedDirection = direction < FlingyMotionUtils.DIRECTION_UP + FlingyMotionUtils.DIRECTION_EIGHTH_TURN
                                        ? FlingyMotionUtils.DIRECTION_UP
                                        : direction < FlingyMotionUtils.DIRECTION_RIGHT + FlingyMotionUtils.DIRECTION_EIGHTH_TURN
                                          ? FlingyMotionUtils.DIRECTION_RIGHT
                                          : direction < FlingyMotionUtils.DIRECTION_DOWN + FlingyMotionUtils.DIRECTION_EIGHTH_TURN
                                            ? FlingyMotionUtils.DIRECTION_DOWN
                                            : direction < FlingyMotionUtils.DIRECTION_LEFT + FlingyMotionUtils.DIRECTION_EIGHTH_TURN
                                              ? FlingyMotionUtils.DIRECTION_LEFT
                                              : FlingyMotionUtils.DIRECTION_UP;

                decision = Math.abs((byte) (comparedDirection - collisionDirection)) > FlingyMotionUtils.DIRECTION_QUARTER_TURN
                           ? DECISION_REPATH_MOVERS
                           : DECISION_WAIT;
            }
        }

        if (this.game.getCurrentFrame() - paths.getCreationFrame(unit.getPathIndex()) >= MINIMUM_REPATH_PATH_AGE ||
            decision < DECISION_REPATH || decision > DECISION_TURN_AND_START) {
            switch (decision) {
                case DECISION_STOP, DECISION_STOP_IMMOVABLE -> {
                    this.setImmovable(unit);

                    if (decision == DECISION_STOP) {
                        unit.setStatus(unit.getStatus() & ~UnitStatusFlags.IMMOVABLE);
                    }

                    unit.setMovementState(MovementStates.AT_MOVE_TARGET);
                }
                case DECISION_REPATH -> unit.setMovementState(MovementStates.REPATH);
                case DECISION_REPATH_MOVERS -> unit.setMovementState(MovementStates.REPATH_MOVERS);
                case DECISION_TURN_AND_START -> {
                    unit.setMovementState(MovementStates.TURN_AND_START);
                    unit.setNextSpeed(0);
                    FlingyMotionUtils.setSpeed(unit, 0);
                }
                case DECISION_WAIT -> unit.setMovementState(MovementStates.WAIT_FREE);
                case DECISION_SLIDE -> {
                    paths.setSlideDirection(unit.getPathIndex(), slideDirection);
                    unit.setMovementState(MovementStates.SLIDE_PREP);
                }
                default -> throw Exceptions.newIllegalStateException("Invalid movement collision decision.");
            }
        } else {
            this.stopWalkingAnimation(unit, instructionLimit);
        }

        return false;
    }

    /**
     * Updates the WaitFree state: retries the step at the saved speed, moves on when no unit blocks it, repaths when the terrain blocks it, and gives
     * up waiting after {@link #WAIT_GIVE_UP_COUNTDOWN} blocked frames.
     *
     * @param context          the movement context
     * @param unit             the waiting unit
     * @param instructionLimit the instruction budget of the movement animations
     * @return {@code true} if the movement dispatcher continues with the next state in this frame, {@code false} otherwise
     * @throws UnsupportedOperationException if the path pool is not initialized
     * @throws NullPointerException          if the world was built without game data
     * @throws IllegalStateException         if the unit has no sprite
     */
    boolean waitFree(@NotNull UnitMovement.Context context, @NotNull Unit unit, int instructionLimit) {
        UnitMovement.increasePathRecalculationCountdown(unit);

        if (UnitPath.updateMovementGuard(this.game, unit, true)) {
            return true;
        }

        unit.setNextSpeed(this.game.getPathSystem().requirePathPool().getSavedSpeed(unit.getPathIndex()));
        FlingyMotionUtils.setSpeed(unit, unit.getNextSpeed());
        FlingyMotionUtils.update(context, unit, this.game.requireGameData().getFlingyProperties());

        if (this.game.getMovementCollision().findAnyUnitCollision(unit, context.getPositionX(), context.getPositionY()) != null) {
            this.stopWalkingAnimation(unit, instructionLimit);
            unit.setNextSpeed(0);
            FlingyMotionUtils.setSpeed(unit, 0);

            if (unit.getTargetHeading() != FlingyMotionUtils.calculateDirection(unit.getMovementWaypointX() - unit.getPositionX(),
                                                                                unit.getMovementWaypointY() - unit.getPositionY())) {
                unit.setTargetHeading(unit.getMovementDirection());
                FlingyMotionUtils.updateFacing(unit, this.game.requireGameData().getFlingyProperties());
                IscriptExecutor.applySpriteDirection(this.game, unit.requireSprite(), unit.getHeading());
            }

            if (unit.getPathRecalculationCountdown() >= WAIT_GIVE_UP_COUNTDOWN) {
                unit.setMovementState(this.game.getRandom().next(UnitMovement.PATH_RANDOM_SOURCE) < RANDOM_MAXIMUM / FORCE_MOVE_FREE_RANDOM_DIVISOR
                                      ? MovementStates.FORCE_MOVE_FREE
                                      : MovementStates.REPATH_MOVERS);
            }
        } else if (this.game.getMovementCollision().isBlockedByTerrain(unit, context.getPositionX(), context.getPositionY())) {
            unit.setMovementState(MovementStates.REPATH);
        } else {
            this.game.getUnitMovement().commitMotion(context, unit, instructionLimit);
            unit.setMovementState(MovementStates.FOLLOW_PATH);
        }

        return false;
    }

    /**
     * Ends the walking animation of a unit that was accelerating, unless it has recently moved or its order or script cannot be interrupted.
     *
     * @param unit             the stopping unit
     * @param instructionLimit the instruction budget of the walking-to-idle animation
     * @throws IllegalStateException if the unit has no sprite
     */
    void stopWalkingAnimation(@NotNull Unit unit, int instructionLimit) {
        if ((unit.getMovementFlags() & MovementFlags.ACCELERATING) != 0 &&
            unit.getPathRecalculationCountdown() > UnitMovement.MOVING_PATH_COUNTDOWN &&
            (unit.getStatus() & (UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE | UnitStatusFlags.ISCRIPT_NO_BREAK)) == 0) {
            unit.setMovementFlags(unit.getMovementFlags() & ~MovementFlags.ACCELERATING);
            this.game.getIscriptExecutor().runSpriteAnimation(unit.requireSprite(),
                                                              unit,
                                                              IscriptExecutor.ANIMATION_WALKING_TO_IDLE,
                                                              0,
                                                              instructionLimit);
        }
    }

    private int findSlideDirection(@NotNull Unit unit, @NotNull Unit blocker) {
        int direction = FlingyMotionUtils.calculateDirection(unit.getMovementWaypointX() - unit.getPositionX(),
                                                             unit.getMovementWaypointY() - unit.getPositionY());

        if (Math.abs((byte) (direction - unit.getMovementDirection())) >= MAXIMUM_SLIDE_TURN) {
            return -1;
        }

        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        UnitSpatialIndex spatialIndex = this.game.getUnitSystem().requireUnitSpatialIndex();
        int unitType = unit.getUnitId();
        int left = spatialIndex.getCoordinate(true, blocker.getMinimumHorizontalEdgeIndex()) - unitTypes.getDimensionRight(unitType) - 1;
        int right = spatialIndex.getCoordinate(true, blocker.getMaximumHorizontalEdgeIndex()) + unitTypes.getDimensionLeft(unitType) + 1;
        int top = spatialIndex.getCoordinate(false, blocker.getMinimumVerticalEdgeIndex()) - unitTypes.getDimensionDown(unitType) - 1;
        int bottom = spatialIndex.getCoordinate(false, blocker.getMaximumVerticalEdgeIndex()) + unitTypes.getDimensionUp(unitType) + 1;
        int horizontal = 0;
        int vertical = 0;
        int quadrant = direction / FlingyMotionUtils.DIRECTION_QUARTER_TURN;
        int slide;

        switch (findBlockerSide(unitTypes, unit, blocker)) {
            case SIDE_ABOVE -> {
                vertical = bottom;
                slide    = quadrant == FlingyMotionUtils.QUADRANT_UP_LEFT
                           ? FlingyMotionUtils.DIRECTION_LEFT
                           : quadrant == FlingyMotionUtils.QUADRANT_UP_RIGHT ? FlingyMotionUtils.DIRECTION_RIGHT : -1;
            }
            case SIDE_RIGHT -> {
                horizontal = left;
                slide      = quadrant == FlingyMotionUtils.QUADRANT_UP_RIGHT
                             ? FlingyMotionUtils.DIRECTION_UP
                             : quadrant == FlingyMotionUtils.QUADRANT_DOWN_RIGHT ? FlingyMotionUtils.DIRECTION_DOWN : -1;
            }
            case SIDE_BELOW -> {
                vertical = top;
                slide    = quadrant == FlingyMotionUtils.QUADRANT_DOWN_RIGHT
                           ? FlingyMotionUtils.DIRECTION_RIGHT
                           : quadrant == FlingyMotionUtils.QUADRANT_DOWN_LEFT ? FlingyMotionUtils.DIRECTION_LEFT : -1;
            }
            case SIDE_LEFT -> {
                horizontal = right;
                slide      = quadrant == FlingyMotionUtils.QUADRANT_DOWN_LEFT
                             ? FlingyMotionUtils.DIRECTION_DOWN
                             : quadrant == FlingyMotionUtils.QUADRANT_UP_LEFT ? FlingyMotionUtils.DIRECTION_UP : -1;
            }
            default -> throw Exceptions.newIllegalStateException("Invalid collision side.");
        }

        for (int attempt = 0; attempt < SLIDE_ATTEMPTS && slide >= 0; attempt++) {
            switch (slide) {
                case FlingyMotionUtils.DIRECTION_UP -> vertical = top;
                case FlingyMotionUtils.DIRECTION_DOWN -> vertical = bottom;
                case FlingyMotionUtils.DIRECTION_RIGHT -> horizontal = right;
                case FlingyMotionUtils.DIRECTION_LEFT -> horizontal = left;
                default -> throw Exceptions.newIllegalStateException("Invalid slide direction.");
            }

            if (spatialIndex.findMovementBlocker(this.game.getHooks().getCollisionRules(), unit, unitTypes, horizontal, vertical) == null &&
                this.game.getUnitPositioning().isTerrainPlacementWalkable(unitType, horizontal, vertical)) {
                return slide;
            }

            slide = (slide + FlingyMotionUtils.DIRECTION_HALF_TURN) & FlingyMotionUtils.DIRECTION_MASK;
        }

        return -1;
    }
}
