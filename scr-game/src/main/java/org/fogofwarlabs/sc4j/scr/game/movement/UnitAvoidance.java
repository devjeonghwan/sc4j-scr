package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.UnitPath;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptMotionPrediction;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Predicts the positions of the nearby moving units for the scout-path movement state and retains the prediction scratch storage.
 */
final class UnitAvoidance {
    /**
     * The pixels the nearby-unit search expands the unit's bounds on both axes.
     */
    private static final int NEARBY_RANGE = 32;
    /**
     * The largest number of nearby moving units collected.
     */
    private static final int NEARBY_LIMIT = 32;
    /**
     * The frames ahead each prediction looks.
     */
    private static final int PREDICTION_FRAMES = 30;
    /**
     * The number of positions of one prediction: the single position {@link #PREDICTION_FRAMES} frames ahead.
     */
    private static final int PREDICTED_POSITION_COUNT = 1;
    /**
     * The mask of the unsigned BYTE turn rate.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The mask of an unsigned WORD: the acceleration or the horizontal WORD of a packed position.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The shift of the vertical position in a packed position.
     */
    private static final int POSITION_VERTICAL_SHIFT = Short.SIZE;
    /**
     * The fraction bits of the fixed-point positions.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;

    /**
     * Predicts the positions of a flingy-controlled unit that turns and accelerates toward its waypoint, one position every {@code frameCount}
     * frames.
     *
     * @param unit        the moving unit
     * @param unitTypes   the unit type table
     * @param flingyTypes the flingy type table
     * @param frameCount  the number of frames between two predicted positions
     * @param positions   receives the predicted pixel positions, X in the low word and Y in the high word
     * @throws IllegalArgumentException if the unit is iscript-controlled
     * @throws IllegalStateException    if the unit has no sprite
     */
    static void predict(@NotNull Unit unit,
                        @NotNull ScrUnitTypeView unitTypes, @NotNull ScrFlingyTypeView flingyTypes,
                        int frameCount, int @NotNull [] positions) {
        if (unit.getMovementControl() == ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT) {
            throw Exceptions.newIllegalArgumentException("Iscript-controlled prediction requires retained interpreter state.");
        }

        int target = unit.getMovementWaypointX() | unit.getMovementWaypointY() << POSITION_VERTICAL_SHIFT;
        int direction = FlingyMotionUtils.calculateDirection((short) target - (short) unit.requireSprite().getX(),
                                                             (short) (target >>> POSITION_VERTICAL_SHIFT) - (short) unit.requireSprite().getY());
        int heading = unit.getTargetHeading();
        int modifier = unit.getSpeedModifier();
        int flingyType = unitTypes.getFlingyType(unit.getUnitId());
        int turn = UnitSpeed.applySpeedModifier(flingyTypes.getTurnRate(flingyType), modifier) & BYTE_MASK;
        int acceleration = UnitSpeed.applySpeedModifier(flingyTypes.getAcceleration(flingyType), modifier) & WORD_MASK;
        int speed = unit.getNextSpeed();
        int maximumSpeed = unit.getTopSpeed();
        int horizontal = unit.getFixedPositionX();
        int vertical = unit.getFixedPositionY();
        long velocity = 0;
        boolean refreshVelocity = true;

        for (int positionIndex = 0; positionIndex < positions.length; positionIndex++) {
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                if (heading != (direction & FlingyMotionUtils.DIRECTION_MASK)) {
                    int difference = (byte) (direction - heading);
                    int maximumTurn = turn >>> FlingyMotionUtils.MOTION_PROFILE_TURN_RATE_SHIFT;

                    heading         = (heading + Math.max(-maximumTurn, Math.min(maximumTurn, difference))) & FlingyMotionUtils.DIRECTION_MASK;
                    direction       = FlingyMotionUtils.calculateDirection((short) target - (short) unit.requireSprite().getX(),
                                                                           (short) (target >>> POSITION_VERTICAL_SHIFT) -
                                                                           (short) unit.requireSprite().getY()) & FlingyMotionUtils.DIRECTION_MASK;
                    refreshVelocity = true;
                }

                if (speed < maximumSpeed) {
                    speed += acceleration;

                    if (speed > maximumSpeed) {
                        speed = maximumSpeed;
                    }

                    refreshVelocity = true;
                }

                if (refreshVelocity) {
                    velocity        = FlingyMotionUtils.calculateDirectionVector(heading, speed);
                    refreshVelocity = false;
                }

                horizontal += (int) velocity;
                vertical += (int) (velocity >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT);
            }

            positions[positionIndex] = ((horizontal >> FIXED_POINT_FRACTION_BITS) & WORD_MASK) |
                                       (vertical >> FIXED_POINT_FRACTION_BITS) << POSITION_VERTICAL_SHIFT;
        }
    }


    private final int @NotNull [] nearbyUnits;
    private final int @NotNull [] nearbyPositions;
    private final int @NotNull [] predictedPosition;
    final @NotNull IscriptMotionPrediction prediction;

    /**
     * Creates a {@link UnitAvoidance} instance with empty prediction storage.
     */
    UnitAvoidance() {
        this.nearbyUnits       = new int[NEARBY_LIMIT];
        this.nearbyPositions   = new int[NEARBY_LIMIT];
        this.predictedPosition = new int[PREDICTED_POSITION_COUNT];
        this.prediction        = new IscriptMotionPrediction();
    }

    /**
     * Predicts the unit and its nearby moving units {@link #PREDICTION_FRAMES} frames ahead and selects the scout-free state when a predicted
     * position intersects the unit's predicted position, the follow-path state otherwise.
     *
     * @param game             the game
     * @param unit             the moving unit
     * @param instructionLimit the instruction budget of an iscript-controlled prediction
     * @throws UnsupportedOperationException if the unit spatial index or the unit pool is not initialized
     * @throws NullPointerException          if the world was built without game data
     */
    void update(@NotNull Game game, @NotNull Unit unit, int instructionLimit) {
        if (UnitPath.updateMovementGuard(game, unit, true)) {
            return;
        }

        int count = game.getUnitSystem().requireUnitSpatialIndex().collectNearbyMovingUnits(game.getHooks().getCollisionRules(),
                                                                                            unit, NEARBY_RANGE, NEARBY_LIMIT, this.nearbyUnits);
        int intersections = 0;

        if (count != 0) {
            if (unit.getMovementControl() == ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT) {
                this.prediction.predict(game, unit, PREDICTION_FRAMES, this.predictedPosition, instructionLimit);
            } else {
                predict(unit,
                        game.requireGameData().getUnitTypes(), game.requireGameData().getFlingyTypes(),
                        PREDICTION_FRAMES, this.predictedPosition);
            }

            int horizontal = (short) this.predictedPosition[0];
            int vertical = (short) (this.predictedPosition[0] >>> POSITION_VERTICAL_SHIFT);

            for (int index = 0; index < count; index++) {
                Unit candidate = game.getUnitSystem().requireUnitPool().getUnit(this.nearbyUnits[index]);

                if (candidate.getMovementControl() == ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT) {
                    this.prediction.predict(game, candidate, PREDICTION_FRAMES, this.predictedPosition, instructionLimit);
                } else {
                    predict(candidate,
                            game.requireGameData().getUnitTypes(), game.requireGameData().getFlingyTypes(),
                            PREDICTION_FRAMES, this.predictedPosition);
                }

                this.nearbyPositions[index] = this.predictedPosition[0];
            }

            ScrUnitTypeView unitTypes = game.requireGameData().getUnitTypes();
            int unitType = unit.getUnitId();

            for (int index = 0; index < count; index++) {
                Unit candidate = game.getUnitSystem().requireUnitPool().getUnit(this.nearbyUnits[index]);
                int candidateType = candidate.getUnitId();
                int candidateHorizontal = (short) this.nearbyPositions[index];
                int candidateVertical = (short) (this.nearbyPositions[index] >>> POSITION_VERTICAL_SHIFT);
                int left = (short) (candidateHorizontal - unitTypes.getDimensionLeft(candidateType) - unitTypes.getDimensionRight(unitType) - 1);
                int right = (short) (candidateHorizontal + unitTypes.getDimensionRight(candidateType) + unitTypes.getDimensionLeft(unitType) + 1);
                int upper = (short) (candidateVertical - unitTypes.getDimensionUp(candidateType) - unitTypes.getDimensionDown(unitType) - 1);
                int lower = (short) (candidateVertical + unitTypes.getDimensionDown(candidateType) + unitTypes.getDimensionUp(unitType) + 1);

                if (horizontal >= left && horizontal <= right && vertical >= upper && vertical <= lower) {
                    intersections++;
                }
            }
        }

        unit.setMovementState(intersections > 0 ? MovementStates.SCOUT_FREE : MovementStates.FOLLOW_PATH);
    }
}
