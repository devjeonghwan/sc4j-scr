package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the Move order initialization and exact destination completion branches, and stops units at their braking destination.
 */
public final class UnitPointMovement {
    /**
     * The Move order state that sets the movement destination.
     */
    private static final int MOVE_STATE_START = 0;
    /**
     * The Move order state that waits for the unit to reach its move target.
     */
    private static final int MOVE_STATE_MOVING = 1;
    /**
     * The fraction bits of the fixed-point braking destination.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;

    private final @NotNull Game game;

    /**
     * Creates a {@link UnitPointMovement} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitPointMovement(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Updates the Move order without advancing the movement phase.
     * <p>
     * A pickup keeps its own changes and facing but prevents the ordinary state store.
     *
     * @param unit             the unit executing Move
     * @param instructionLimit the script budget for successor order activation
     */
    public void update(@NotNull Unit unit, int instructionLimit) {
        if (unit.getPrimaryOrderState() == MOVE_STATE_START) {
            Unit target = unit.getOrderTargetUnit();
            boolean moved;

            if (target != null) {
                moved = this.game.getUnitTargetMovement().moveToTargetReset(unit, target);
            } else {
                int positionX = (short) unit.getOrderTargetX();
                int positionY = (short) unit.getOrderTargetY();

                moved = this.game.getUnitPositioning().setMovementDestination(unit, positionX, positionY);
                unit.setDirectionTargetX(positionX);
                unit.setDirectionTargetY(positionY);
            }

            if (!moved) {
                return;
            }

            unit.setPrimaryOrderState(MOVE_STATE_MOVING);
        } else if (UnitTargetMovement.isAtMovementTarget(unit)) {
            this.game.getOrders().finishOrder(unit, instructionLimit);

            // A unit with a computer record forgets its automatic target when the move ends.
            if (unit.getComputerRecord() != null) {
                unit.setAutomaticTargetUnit(null);
            }
        }
    }

    /**
     * Stops movement at its fixed-point braking destination and clamps the footprint to map bounds.
     *
     * @param unit the stopping unit
     * @throws NullPointerException if the world was built without game data
     */
    public void stop(@NotNull Unit unit) {
        if ((unit.getPathCollisionFlags() & UnitInitializer.ILLEGAL_POSITION_PATH_FLAG) != 0) {
            unit.setPathCollisionFlags(unit.getPathCollisionFlags() | UnitInitializer.STOPPED_MOVING_TO_LEGAL_PATH_FLAG);
        }

        if ((unit.getMovementFlags() & MovementFlags.BRAKING) == 0) {
            unit.setMovementFlags(unit.getMovementFlags() & ~(MovementFlags.MOVING | MovementFlags.PASSING_THROUGH | MovementFlags.BRAKE_ON_PATH_STEP));
            unit.setMovementDestinationX(unit.getPositionX());
            unit.setMovementDestinationY(unit.getPositionY());
            unit.setMovementTargetUnit(null);
            unit.setMovementWaypointX(unit.getPositionX());
            unit.setMovementWaypointY(unit.getPositionY());

            if ((unit.getMovementFlags() & MovementFlags.ACCELERATING) != 0 &&
                unit.getMovementControl() == ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY) {
                int distance = this.game.getUnitTargeting().getHaltDistance(unit);
                long vector = FlingyMotionUtils.calculateDirectionVector(unit.getTargetHeading(), distance);
                int positionX = (unit.getFixedPositionX() + (int) vector) >> FIXED_POINT_FRACTION_BITS;
                int positionY = (unit.getFixedPositionY() + (int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT)) >>
                                FIXED_POINT_FRACTION_BITS;

                unit.setMovementDestinationX(positionX);
                unit.setMovementDestinationY(positionY);
                unit.setMovementWaypointX(positionX);
                unit.setMovementWaypointY(positionY);

                if (!UnitTargetMovement.isAtMovementTarget(unit)) {
                    unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.BRAKING);
                }
            }
        }

        int unitType = unit.getUnitId();
        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        int horizontal = UnitPositioning.clampCoordinate(unit.getMovementDestinationX(),
                                                         unitTypes.getDimensionLeft(unitType), unitTypes.getDimensionRight(unitType),
                                                         this.game.getMap().getWidth() * TerrainTileset.TILE_SIZE,
                                                         0);
        int vertical = UnitPositioning.clampCoordinate(unit.getMovementDestinationY(),
                                                       unitTypes.getDimensionUp(unitType), unitTypes.getDimensionDown(unitType),
                                                       this.game.getMap().getHeight() * TerrainTileset.TILE_SIZE,
                                                       UnitPositioning.MAP_BOTTOM_MARGIN);

        if (horizontal != unit.getMovementDestinationX() || vertical != unit.getMovementDestinationY()) {
            unit.setMovementDestinationX(horizontal);
            unit.setMovementDestinationY(vertical);
            unit.setMovementWaypointX(horizontal);
            unit.setMovementWaypointY(vertical);
        }

        unit.setMovementDestinationCountdown(UnitPositioning.DESTINATION_COUNTDOWN);
    }

    /**
     * Stops the unit ({@link #stop}), then points its direction target at its movement destination.
     *
     * @param unit the stopping unit
     */
    public void stopAndFaceDestination(@NotNull Unit unit) {
        this.stop(unit);
        unit.setDirectionTargetX(unit.getMovementDestinationX());
        unit.setDirectionTargetY(unit.getMovementDestinationY());
    }
}
