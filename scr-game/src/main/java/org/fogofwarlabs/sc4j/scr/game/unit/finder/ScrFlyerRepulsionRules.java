package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitTargetMovement;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the SC:R facts of the flyer repulsion: a moving unit that is not a building and not exempt ({@link UnitTraitFlags#NO_AIR_REPULSION},
 * the Interceptor) takes part; the order-not-interruptible and animation no-break statuses hold it in place; positions and edges are the sprite
 * position and the {@code units.dat} dimensions, read from the type table the finder last received.
 */
final class ScrFlyerRepulsionRules implements FlyerRepulsionRules {
    private static @NotNull Sprite requireShownSprite(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("The flyer repulsion requires the unit sprite.");
        }

        return sprite;
    }


    private @Nullable ScrUnitTypeView units;

    /**
     * Creates a {@link ScrFlyerRepulsionRules} instance without a type table.
     */
    ScrFlyerRepulsionRules() {
        this.units = null;
    }

    /**
     * Sets the type table the participation and the dimensions are read from.
     *
     * @param units the unit type table
     */
    void setUnitTypes(@NotNull ScrUnitTypeView units) {
        this.units = units;
    }

    /**
     * Checks whether a unit takes part in the repulsion: it counts in its cell and is pushed by crowding.
     *
     * @param unit the unit
     * @return {@code true} if the unit is a repulsion flyer, {@code false} otherwise
     * @throws IllegalStateException if no type table is set
     */
    @Override
    public boolean isRepulsionFlyer(@NotNull Unit unit) {
        ScrUnitTypeView unitTypes = this.requireUnitTypes();

        return (unit.getStatus() & UnitStatusFlags.CAN_MOVE) != 0 && !UnitQueries.isBuilding(unitTypes, unit.getUnitId()) &&
               !unitTypes.isAirRepulsionExempt(unit.getUnitId());
    }

    /**
     * Checks whether a repulsion flyer must not be displaced now, for example while an animation or an order holds it in place.
     *
     * @param unit the repulsion flyer
     * @return {@code true} if crowding does not displace the unit now, {@code false} otherwise
     */
    @Override
    public boolean isRepulsionHeld(@NotNull Unit unit) {
        return (unit.getStatus() & (UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE | UnitStatusFlags.ISCRIPT_NO_BREAK)) != 0;
    }

    /**
     * Checks whether a unit is shown at its movement destination.
     *
     * @param unit the unit
     * @return {@code true} if the unit stands at its movement target, {@code false} otherwise
     */
    @Override
    public boolean isAtMovementTarget(@NotNull Unit unit) {
        return UnitTargetMovement.isAtMovementTarget(unit);
    }

    /**
     * @param unit the unit
     * @return the x coordinate the unit is shown at, as the unit stores it
     * @throws UnsupportedOperationException if the unit has no sprite
     */
    @Override
    public int getShownX(@NotNull Unit unit) {
        return requireShownSprite(unit).getX();
    }

    /**
     * @param unit the unit
     * @return the y coordinate the unit is shown at, as the unit stores it
     * @throws UnsupportedOperationException if the unit has no sprite
     */
    @Override
    public int getShownY(@NotNull Unit unit) {
        return requireShownSprite(unit).getY();
    }

    /**
     * @param unit the unit
     * @return the left edge of the unit around the position it is shown at
     * @throws IllegalStateException if the unit has no sprite or no type table is set
     */
    @Override
    public int getLeftEdge(@NotNull Unit unit) {
        return unit.requireSprite().getX() - this.requireUnitTypes().getDimensionLeft(unit.getUnitId());
    }

    /**
     * @param unit the unit
     * @return the right edge of the unit around the position it is shown at
     * @throws IllegalStateException if the unit has no sprite or no type table is set
     */
    @Override
    public int getRightEdge(@NotNull Unit unit) {
        return unit.requireSprite().getX() + this.requireUnitTypes().getDimensionRight(unit.getUnitId());
    }

    /**
     * @param unit the unit
     * @return the top edge of the unit around the position it is shown at
     * @throws IllegalStateException if the unit has no sprite or no type table is set
     */
    @Override
    public int getTopEdge(@NotNull Unit unit) {
        return unit.requireSprite().getY() - this.requireUnitTypes().getDimensionUp(unit.getUnitId());
    }

    /**
     * @param unit the unit
     * @return the bottom edge of the unit around the position it is shown at
     * @throws IllegalStateException if the unit has no sprite or no type table is set
     */
    @Override
    public int getBottomEdge(@NotNull Unit unit) {
        return unit.requireSprite().getY() + this.requireUnitTypes().getDimensionDown(unit.getUnitId());
    }

    private @NotNull ScrUnitTypeView requireUnitTypes() {
        ScrUnitTypeView unitTypes = this.units;

        if (unitTypes == null) {
            throw Exceptions.newIllegalStateException("The flyer repulsion rules require the unit type table.");
        }

        return unitTypes;
    }
}
