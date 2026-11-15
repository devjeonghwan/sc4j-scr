package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Decides whether a selected unit may receive an explicitly requested order, using its requirement script ({@link ScrRequirementSystem}) and the
 * self-targeting rules.
 */
public final class UnitOrderEligibility {
    /**
     * @param unit the unit to inspect
     * @return {@code true} if the disabled flag or a disabling status timer is active, {@code false} otherwise
     */
    public static boolean isDisabled(@NotNull Unit unit) {
        return (unit.getStatus() & UnitStatusFlags.DISABLED) != 0 ||
               unit.getLockdownTimer() != 0 || unit.getStasisTimer() != 0 || unit.getMaelstromTimer() != 0;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitOrderEligibility} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitOrderEligibility(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies the order test with its requirement script ({@link ScrRequirementSystem#checkOrder}): the unit may receive the order when the script
     * reports no failure.
     *
     * @param unit    the selected unit
     * @param orderId the requested order
     * @param owner   the command player
     * @return {@code true} if the unit may receive the order, {@code false} otherwise
     */
    public boolean canReceiveOrder(@NotNull Unit unit, int orderId, int owner) {
        return this.game.getRequirements().checkOrder(unit, orderId, owner) == 0;
    }

    /**
     * Checks the orders that may target the ordered unit itself: a rally point of a grounded building, Dark Swarm of a Defiler and the unloading move
     * of a transport.
     *
     * @param unit    the ordered unit and target
     * @param orderId the requested order
     * @return {@code true} if the order may target its own unit, {@code false} otherwise
     */
    public boolean canTargetSelf(@NotNull Unit unit, int orderId) {
        if (UnitQueries.isGroundedBuilding(unit) &&
            (orderId == ScrOrderIds.RALLY_POINT_UNIT || orderId == ScrOrderIds.RALLY_POINT_TILE)) {
            return true;
        }

        if ((this.game.getBaseType(unit) == ScrUnitIds.ZERG_DEFILER || this.game.getBaseType(unit) == ScrUnitIds.HERO_UNCLEAN_ONE) &&
            orderId == ScrOrderIds.CAST_DARK_SWARM) {
            return true;
        }

        return this.game.getUnitQueries().isTransport(unit) && orderId == ScrOrderIds.MOVE_UNLOAD;
    }
}
