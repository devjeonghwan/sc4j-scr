package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.order.FollowTargetHook;
import org.fogofwarlabs.sc4j.engine.order.OrderSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a Follow of a Shield Battery the unit can recharge at into RechargeShieldsUnit: the SC:R implementation of the follow target point
 * ({@link OrderSystem#FOLLOW_TARGET}), asked after {@link NydusFollowHook}. The hook holds no state of its own; it finds the SC:R game when it is
 * attached.
 */
public final class ShieldBatteryFollowHook implements FollowTargetHook, GameSystem {
    /**
     * The fixed-point scale of shield points (eight fraction bits).
     */
    private static final int SHIELD_POINT_SCALE = 256;

    private @Nullable Game game;

    /**
     * Creates a {@link ShieldBatteryFollowHook} instance; it calls the SC:R game it finds when it is attached.
     */
    public ShieldBatteryFollowHook() {
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
     * Checks whether a following unit uses its target; a hook that answers {@code true} has given the unit the order that uses the target.
     * <p>
     * A completed Shield Battery of the unit's owner with energy that is not disabled, locked down, in stasis or in a Maelstrom gets a completed,
     * movable Protoss unit other than a grounded building, an Interceptor or a Scarab whose shields are below their maximum the RechargeShieldsUnit
     * order on it.
     *
     * @param unit             the following unit
     * @param target           the followed unit
     * @param instructionLimit the instruction limit of each animation program run the change of the unit's order starts
     * @return {@code true} if the unit uses its target and no longer follows it, {@code false} if this hook gives no answer
     * @throws IllegalArgumentException if a unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public boolean useTarget(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit,
                             org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit target,
                             int instructionLimit) {
        Unit follower = Unit.of(unit);
        Unit battery = Unit.of(target);
        Game attached = this.requireGame();

        if (!this.canUseShieldBattery(attached, follower, battery)) {
            return false;
        }

        attached.getOrders().issue(follower, ScrOrderIds.RECHARGE_SHIELDS_UNIT, battery, 0, 0, battery.getUnitId(), false, instructionLimit);

        return true;
    }

    private boolean canUseShieldBattery(@NotNull Game attached, @NotNull Unit unit, @NotNull Unit target) {
        if (target.getUnitId() != ScrUnitIds.PROTOSS_SHIELD_BATTERY) {
            return false;
        }

        ScrUnitTypeView unitTypes = attached.requireGameData().getUnitTypes();

        return (target.getStatus() & UnitStatusFlags.COMPLETED) != 0 && (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0 &&
               (unit.getStatus() & UnitStatusFlags.CAN_MOVE) != 0 &&
               !UnitQueries.isGroundedBuilding(unit) && unit.getOwnerIndex() == target.getOwnerIndex() &&
               (unitTypes.getGroupFlags(unit.getUnitId()) & UnitGroupFlags.PROTOSS) != 0 &&
               unitTypes.getShieldEnabled(unit.getUnitId()) != 0 &&
               unit.getShieldPoints() < unitTypes.getShieldAmount(unit.getUnitId()) * SHIELD_POINT_SCALE && target.getEnergy() != 0 &&
               (target.getStatus() & UnitStatusFlags.DISABLED) == 0 && target.getLockdownTimer() == 0 && target.getStasisTimer() == 0 &&
               target.getMaelstromTimer() == 0 && attached.getBaseType(unit) != ScrUnitIds.PROTOSS_INTERCEPTOR &&
               attached.getBaseType(unit) != ScrUnitIds.PROTOSS_SCARAB;
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R Shield Battery follow hook is not attached.");
        }

        return attached;
    }
}
