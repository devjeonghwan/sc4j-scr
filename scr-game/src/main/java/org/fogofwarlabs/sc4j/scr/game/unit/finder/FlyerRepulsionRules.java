package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies the facts the flyer repulsion ({@link FlyerRepulsion}) reads about a unit: whether it takes part, whether it may be displaced now,
 * whether it stands at its movement target, and where it is shown with its edges. Positions are map pixels as the unit stores them.
 */
interface FlyerRepulsionRules {
    /**
     * Checks whether a unit takes part in the repulsion: it counts in its cell and is pushed by crowding.
     *
     * @param unit the unit
     * @return {@code true} if the unit is a repulsion flyer, {@code false} otherwise
     */
    boolean isRepulsionFlyer(@NotNull Unit unit);

    /**
     * Checks whether a repulsion flyer must not be displaced now, for example while an animation or an order holds it in place.
     *
     * @param unit the repulsion flyer
     * @return {@code true} if crowding does not displace the unit now, {@code false} otherwise
     */
    boolean isRepulsionHeld(@NotNull Unit unit);

    /**
     * Checks whether a unit is shown at its movement destination.
     *
     * @param unit the unit
     * @return {@code true} if the unit stands at its movement target, {@code false} otherwise
     */
    boolean isAtMovementTarget(@NotNull Unit unit);

    /**
     * @param unit the unit
     * @return the x coordinate the unit is shown at, as the unit stores it
     */
    int getShownX(@NotNull Unit unit);

    /**
     * @param unit the unit
     * @return the y coordinate the unit is shown at, as the unit stores it
     */
    int getShownY(@NotNull Unit unit);

    /**
     * @param unit the unit
     * @return the left edge of the unit around the position it is shown at
     */
    int getLeftEdge(@NotNull Unit unit);

    /**
     * @param unit the unit
     * @return the right edge of the unit around the position it is shown at
     */
    int getRightEdge(@NotNull Unit unit);

    /**
     * @param unit the unit
     * @return the top edge of the unit around the position it is shown at
     */
    int getTopEdge(@NotNull Unit unit);

    /**
     * @param unit the unit
     * @return the bottom edge of the unit around the position it is shown at
     */
    int getBottomEdge(@NotNull Unit unit);
}
