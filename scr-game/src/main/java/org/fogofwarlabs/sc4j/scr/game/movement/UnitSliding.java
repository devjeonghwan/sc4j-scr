package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.UnitPath;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Applies collision sliding in fixed-point substeps without committing blocked motion: the slide states along a unit blocker and along the terrain.
 */
public final class UnitSliding {
    /**
     * The speed of one slide substep: one pixel per frame.
     */
    private static final int SLIDE_STEP_SPEED = 256;
    /**
     * A flingy-controlled unit prepares its slide with half its saved speed when the saved speed is at least this.
     */
    private static final int SLIDE_HALVING_SPEED = 512;
    /**
     * The divisor of the saved speed of a flingy-controlled unit that prepares its slide at {@link #SLIDE_HALVING_SPEED} or faster.
     */
    private static final int SLIDE_SPEED_DIVISOR = 2;
    /**
     * A slide that ends free within this many frames of the path creation continues on the path.
     */
    private static final int SLIDE_PATH_AGE = 150;
    /**
     * The slide direction of a terrain collision without a slide.
     */
    private static final int NO_SLIDE_DIRECTION = 0xFF;

    private final @NotNull Game game;

    /**
     * Creates a {@link UnitSliding} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitSliding(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Updates the slide states: SlidePrep sets the slide speed, SlideFree slides along the blocker in substeps until the unit is free, ForceMoveFree
     * gives up and repaths, FixTerrain and TerrainSlide slide along the terrain.
     *
     * @param context          the movement context
     * @param unit             the sliding unit
     * @param instructionLimit the instruction budget of the movement animations
     * @return {@code true} if the movement dispatcher continues with the next state in this frame, {@code false} otherwise
     * @throws UnsupportedOperationException if the path pool or the unit pool is not initialized
     * @throws IllegalStateException         if the unit has no sprite
     * @throws NullPointerException          if the world was built without game data
     */
    boolean update(@NotNull UnitMovement.Context context, @NotNull Unit unit, int instructionLimit) {
        if (unit.getMovementState() == MovementStates.FORCE_MOVE_FREE) {
            UnitMovement.increasePathRecalculationCountdown(unit);

            if (UnitTargetMovement.isAtMovementTarget(unit) || (unit.getMovementFlags() & MovementFlags.BRAKING) != 0) {
                unit.setMovementState(MovementStates.AT_MOVE_TARGET);

                return false;
            }

            unit.setNextSpeed(0);
            FlingyMotionUtils.setSpeed(unit, 0);

            if (unit.getPathIndex() >= 0) {
                this.game.getPathSystem().requirePathPool().release(unit.getPathIndex());
                unit.setPathIndex(-1);
            }

            unit.setMovementState(MovementStates.TURN_AND_START);

            return true;
        }

        if (unit.getMovementState() == MovementStates.FIX_TERRAIN || unit.getMovementState() == MovementStates.TERRAIN_SLIDE) {
            return this.updateTerrain(context, unit, instructionLimit);
        }

        boolean preparing = unit.getMovementState() == MovementStates.SLIDE_PREP;

        if (!preparing && unit.getPathRecalculationCountdown() < UnitMovement.MAXIMUM_PATH_RECALCULATION_COUNTDOWN) {
            unit.setPathRecalculationCountdown(unit.getPathRecalculationCountdown() + 1);
        }

        if (UnitPath.updateMovementGuard(this.game, unit, true)) {
            return true;
        }

        PathPool paths = this.game.getPathSystem().requirePathPool();
        int pathIndex = unit.getPathIndex();

        if (preparing) {
            int speed = paths.getSavedSpeed(pathIndex);

            if (unit.getMovementControl() == ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY) {
                if (speed >= SLIDE_HALVING_SPEED) {
                    speed /= SLIDE_SPEED_DIVISOR;
                } else if (speed > SLIDE_STEP_SPEED) {
                    speed = SLIDE_STEP_SPEED;
                }

                paths.setSavedSpeed(pathIndex, speed);
            }

            FlingyMotionUtils.setDirection(unit, unit.getTargetHeading());
            unit.setNextSpeed(speed);
            FlingyMotionUtils.setSpeed(unit, speed);
            unit.setMovementState(MovementStates.SLIDE_FREE);

            return true;
        }

        unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.ORDERED);

        int slideDirection = paths.getSlideDirection(pathIndex);

        FlingyMotionUtils.setDesiredDirection(unit, slideDirection);
        unit.setTargetHeading(unit.getMovementDirection());
        FlingyMotionUtils.updateFacing(unit, this.game.requireGameData().getFlingyProperties());
        IscriptExecutor.applySpriteDirection(this.game, unit.requireSprite(), unit.getHeading());

        if (unit.getMovementControl() == ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT) {
            unit.setCurrentSpeed(unit.getTopSpeed());
        } else {
            FlingyMotionUtils.updateSpeed(unit, this.game.requireGameData().getFlingyProperties());
        }

        int originalSpeed = unit.getCurrentSpeed();
        int originalDirection = unit.getTargetHeading();
        Unit collision = this.game.getUnitSystem().requireUnitPool().resolveHandle(paths.getLastCollisionHandle(pathIndex));
        Unit nextCollision = null;
        boolean forced = false;

        for (int speed = originalSpeed; speed > 0; speed -= SLIDE_STEP_SPEED) {
            unit.setMovementFlags(unit.getMovementFlags() & ~MovementFlags.ORDERED);
            FlingyMotionUtils.setDirection(unit, slideDirection);
            unit.setNextSpeed(Math.min(speed, SLIDE_STEP_SPEED));
            FlingyMotionUtils.setSpeed(unit, unit.getNextSpeed());
            FlingyMotionUtils.updateValues(context, unit, unit.getMovementFlags());

            if (this.game.getMovementCollision().findAnyUnitCollision(unit, context.getPositionX(), context.getPositionY()) != null ||
                this.game.getMovementCollision().isBlockedByTerrain(unit, context.getPositionX(), context.getPositionY())) {
                forced = true;

                break;
            }

            this.game.getUnitMovement().commitMotion(context, unit, instructionLimit);
            unit.setTargetHeading(originalDirection);
            FlingyMotionUtils.updateDirection(unit);
            unit.setNextSpeed(originalSpeed);
            FlingyMotionUtils.setSpeed(unit, originalSpeed);
            FlingyMotionUtils.updateValues(context, unit, unit.getMovementFlags());

            nextCollision = this.game.getMovementCollision().findAnyUnitCollision(unit, context.getPositionX(), context.getPositionY());

            if (nextCollision != collision) {
                break;
            }
        }

        unit.setTargetHeading(originalDirection);
        FlingyMotionUtils.setDirection(unit, originalDirection);
        unit.setNextSpeed(originalSpeed);
        FlingyMotionUtils.setSpeed(unit, originalSpeed);

        if (forced) {
            unit.setMovementState(MovementStates.FORCE_MOVE_FREE);
        } else if (nextCollision == null) {
            unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.ORDERED);
            unit.setMovementState(this.game.getCurrentFrame() - paths.getCreationFrame(pathIndex) < SLIDE_PATH_AGE
                                  ? MovementStates.FOLLOW_PATH
                                  : MovementStates.TURN_AND_START);
        }

        return false;
    }

    private boolean updateTerrain(@NotNull UnitMovement.Context context, @NotNull Unit unit, int instructionLimit) {
        UnitMovement.increasePathRecalculationCountdown(unit);

        if (UnitPath.updateMovementGuard(this.game, unit, true)) {
            return true;
        }

        PathPool paths = this.game.getPathSystem().requirePathPool();
        int pathIndex = unit.getPathIndex();

        if (unit.getMovementState() == MovementStates.FIX_TERRAIN) {
            unit.setNextSpeed(paths.getSavedSpeed(pathIndex));
            FlingyMotionUtils.setSpeed(unit, unit.getNextSpeed());
            FlingyMotionUtils.update(context, unit, this.game.requireGameData().getFlingyProperties());

            if (this.game.getMovementCollision().isBlockedByTerrain(unit, context.getPositionX(), context.getPositionY()) ||
                this.game.getMovementCollision().findAnyUnitCollision(unit, context.getPositionX(), context.getPositionY()) != null) {
                this.game.getUnitMovementCollision().stopWalkingAnimation(unit, instructionLimit);

                int direction = FlingyMotionUtils.calculateDirection((short) unit.getMovementWaypointX() - unit.getPositionX(),
                                                                     (short) unit.getMovementWaypointY() - unit.getPositionY());

                if (unit.getTargetHeading() == direction) {
                    int horizontal = context.getPositionX() - unit.getPositionX();
                    int vertical = context.getPositionY() - unit.getPositionY();
                    int quadrant = unit.getMovementDirection() / FlingyMotionUtils.DIRECTION_QUARTER_TURN;
                    int slideDirection = NO_SLIDE_DIRECTION;

                    if (horizontal != 0 &&
                        (vertical == 0 || this.game.getMovementCollision().isBlockedByTerrain(unit, context.getPositionX(), unit.getPositionY()))) {
                        if (horizontal >= 0) {
                            if (quadrant == FlingyMotionUtils.QUADRANT_UP_RIGHT) {
                                slideDirection = FlingyMotionUtils.DIRECTION_UP;
                            }

                            if (quadrant == FlingyMotionUtils.QUADRANT_DOWN_RIGHT) {
                                slideDirection = FlingyMotionUtils.DIRECTION_DOWN;
                            }
                        } else {
                            if (quadrant == FlingyMotionUtils.QUADRANT_UP_LEFT) {
                                slideDirection = FlingyMotionUtils.DIRECTION_UP;
                            }

                            if (quadrant == FlingyMotionUtils.QUADRANT_DOWN_LEFT) {
                                slideDirection = FlingyMotionUtils.DIRECTION_DOWN;
                            }
                        }
                    } else if (vertical < 0) {
                        if (quadrant == FlingyMotionUtils.QUADRANT_UP_RIGHT) {
                            slideDirection = FlingyMotionUtils.DIRECTION_RIGHT;
                        }

                        if (quadrant == FlingyMotionUtils.QUADRANT_UP_LEFT) {
                            slideDirection = FlingyMotionUtils.DIRECTION_LEFT;
                        }
                    } else {
                        if (quadrant == FlingyMotionUtils.QUADRANT_DOWN_RIGHT) {
                            slideDirection = FlingyMotionUtils.DIRECTION_RIGHT;
                        }

                        if (quadrant == FlingyMotionUtils.QUADRANT_DOWN_LEFT) {
                            slideDirection = FlingyMotionUtils.DIRECTION_LEFT;
                        }
                    }

                    paths.setSlideDirection(pathIndex, slideDirection);
                    unit.setMovementState(MovementStates.TERRAIN_SLIDE);
                } else {
                    unit.setTargetHeading(unit.getMovementDirection());
                    FlingyMotionUtils.updateFacing(unit, this.game.requireGameData().getFlingyProperties());
                    IscriptExecutor.applySpriteDirection(this.game, unit.requireSprite(), unit.getHeading());
                }
            } else {
                this.game.getUnitMovement().commitMotion(context, unit, instructionLimit);
                unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.ORDERED);
                unit.setMovementState(MovementStates.FOLLOW_PATH);
            }

            return false;
        }

        unit.setMovementFlags(unit.getMovementFlags() & ~MovementFlags.ORDERED);

        int originalDirection = unit.getTargetHeading();
        int slideDirection = paths.getSlideDirection(pathIndex);
        int flags = unit.getMovementFlags();

        FlingyMotionUtils.setDirection(unit, slideDirection);
        FlingyMotionUtils.updateSpeed(unit, this.game.requireGameData().getFlingyProperties());
        FlingyMotionUtils.updateValues(context, unit, flags);
        unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.ORDERED);
        unit.setTargetHeading(originalDirection);
        FlingyMotionUtils.setDesiredDirection(unit, slideDirection);

        if (this.game.getMovementCollision().findAnyUnitCollision(unit, context.getPositionX(), context.getPositionY()) != null ||
            this.game.getMovementCollision().isBlockedByTerrain(unit, context.getPositionX(), context.getPositionY())) {
            unit.setMovementState(MovementStates.FORCE_MOVE_FREE);
        } else {
            this.game.getUnitMovement().commitMotion(context, unit, instructionLimit);

            int nextSpeed = unit.getNextSpeed();

            unit.setNextSpeed(paths.getSavedSpeed(pathIndex));
            FlingyMotionUtils.setSpeed(unit, unit.getNextSpeed());

            int direction = FlingyMotionUtils.calculateDirection((short) unit.getMovementWaypointX() - unit.getPositionX(),
                                                                 (short) unit.getMovementWaypointY() - unit.getPositionY());
            int movementFlags = unit.getMovementFlags();

            FlingyMotionUtils.setDirection(unit, direction);
            FlingyMotionUtils.updateSpeed(unit, this.game.requireGameData().getFlingyProperties());
            FlingyMotionUtils.updateValues(context, unit, movementFlags);
            unit.setNextSpeed(nextSpeed);
            FlingyMotionUtils.setSpeed(unit, nextSpeed);

            if (!this.game.getMovementCollision().isBlockedByTerrain(unit, context.getPositionX(), context.getPositionY())) {
                unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.ORDERED);
                unit.setMovementState(MovementStates.FOLLOW_PATH);
            }
        }

        return false;
    }
}
