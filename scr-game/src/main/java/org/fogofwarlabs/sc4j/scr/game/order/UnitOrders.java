package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitMovement;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatches the orders of a unit each frame through the game's order behaviors ({@link ScrOrderBehaviors}, the engine order registry), in the
 * stage order of {@link OrderStage}: the main order (unconditional, every frame, then on the order timer), the secondary order, and the hidden-unit
 * orders. The stage order, the disabled test and the order timer are fixed here; the steps of every order id come from its
 * {@link ScrOrderBehavior}, looked up by the unit's order byte. The steps of one dispatch share one {@link ScrOrderContext}.
 */
public final class UnitOrders {
    /**
     * The order timer value after the timer expires.
     */
    private static final int ORDER_TIMER = 8;

    /**
     * The mask of an unsigned BYTE: the target acquisition range wraps to it.
     */
    private static final int BYTE_MASK = 0xFF;


    private final @NotNull Game game;

    private final @NotNull ScrOrderContext context;

    /**
     * Creates a {@link UnitOrders} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitOrders(@NotNull Game game) {
        this.game = game;

        this.context = new ScrOrderContext(game);
    }

    /**
     * Returns the target acquisition range of a unit: none for a cloaked Ghost that does not hold its position, otherwise the {@code units.dat} range
     * plus the bonus of the unit's range upgrade (for the hero types, of the Brood War expansion; {@link Stat#TARGET_ACQUISITION_RANGE}).
     *
     * @param unit the unit
     * @return the target acquisition range in tiles, as an unsigned BYTE
     * @throws UnsupportedOperationException if a range upgrade applies and the player upgrade levels are missing
     * @throws NullPointerException          if the world was built without game data
     */
    public int getTargetAcquisitionRange(@NotNull Unit unit) {
        int unitType = unit.getUnitId();

        if ((unit.getStatus() & (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED)) != 0 &&
            unit.getPrimaryOrder() != ScrOrderIds.HOLD_POSITION &&
            this.game.requireGameData().getUnitTypes().isGhost(unitType)) {
            return 0;
        }

        int range = this.game.requireGameData().getUnitTypes().getTargetAcquisitionRange(unitType);

        return this.game.applyModifiers(Stat.TARGET_ACQUISITION_RANGE, unit.getOwnerIndex(), unitType, ContentType.NO_INDEX, range) & BYTE_MASK;
    }


    /**
     * Runs the per-frame update of one visible unit: the sprite flash timer with its solid circle, the unit timers, the main order
     * ({@link #updatePrimary}), the secondary order, the subunit with the same player type, and the sprite script, which releases the sprite when it
     * ends.
     *
     * @param unit             the unit
     * @param ownerPlayerType  the player type of the unit's owner for this frame, also used for its subunit
     * @param instructionLimit the instruction budget of each sprite script invocation
     * @throws UnsupportedOperationException if the unit has no sprite, or its subunit belongs to another player
     * @throws NullPointerException          if the world was built without game data
     */
    public void update(@NotNull Unit unit, int ownerPlayerType, int instructionLimit) {
        boolean subunitType = (this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.SUBUNIT) != 0;
        Sprite sprite = unit.getSpriteOrNull();

        if (!subunitType) {
            if (sprite == null) {
                throw Exceptions.newUnsupportedOperationException("Native unit order updates require the retained sprite.");
            }

            if ((sprite.getFlags() & Sprite.FLAG_HIDDEN) == 0) {
                this.game.getSelectionImages().updateFlash(sprite, unit.getOwnerIndex());
            }
        }

        this.game.getUnitTimers().update(unit, instructionLimit);
        this.updatePrimary(unit, ownerPlayerType, instructionLimit);

        ScrOrderBehaviors behaviors = this.game.getOrderBehaviors();
        ScrOrderContext dispatch = this.context;
        int secondaryOrder = unit.getSecondaryOrder();
        Order unconditionalSecondary = behaviors.findStep(OrderStage.SECONDARY_UNCONDITIONAL, secondaryOrder);

        dispatch.set(ownerPlayerType, instructionLimit);

        if (unconditionalSecondary != null) {
            unconditionalSecondary.update(dispatch, unit);
        } else if (!UnitOrderEligibility.isDisabled(unit)) {
            Order secondary = behaviors.getStep(OrderStage.SECONDARY, secondaryOrder);

            if (secondary != null) {
                secondary.update(dispatch, unit);
            }
        }

        Unit subunit = unit.getSubunit();

        if (subunit != null && (this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.SUBUNIT) == 0) {
            if (subunit.getOwnerIndex() != unit.getOwnerIndex()) {
                throw Exceptions.newUnsupportedOperationException("Native subunit order dispatch requires its distinct owner-player context.");
            }

            this.update(subunit, ownerPlayerType, instructionLimit);
        }

        sprite = unit.getSpriteOrNull();

        if (sprite != null && !this.game.getIscriptExecutor().executeSprite(sprite, unit, instructionLimit)) {
            unit.setSprite(null);
        }
    }

    /**
     * Updates a hidden unit: its subunit, movement, timers, registered hidden main and secondary orders, then the sprite script.
     *
     * @param movementContext  the shared movement controls of the frame
     * @param unit             the hidden unit
     * @param ownerPlayerType  the player type of the unit's owner for this frame
     * @param instructionLimit the instruction budget of each sprite script invocation
     * @throws NullPointerException if the world was built without game data
     */
    public void updateHidden(@NotNull UnitMovement.Context movementContext, @NotNull Unit unit, int ownerPlayerType, int instructionLimit) {
        Unit subunit = unit.getSubunit();

        if (subunit != null && (this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.SUBUNIT) == 0) {
            this.updateHidden(movementContext, subunit, ownerPlayerType, instructionLimit);
        }

        // Run movement before the unit timers, including the turn of a bunkered unit toward its target.
        this.game.getUnitMovement().update(movementContext, unit, instructionLimit, instructionLimit);
        this.game.getUnitTimers().update(unit, instructionLimit);

        ScrOrderBehaviors behaviors = this.game.getOrderBehaviors();
        ScrOrderContext dispatch = this.context;
        Order hiddenMain = behaviors.getStep(OrderStage.HIDDEN_MAIN, unit.getPrimaryOrder());

        dispatch.set(ownerPlayerType, instructionLimit);

        if (hiddenMain != null) {
            hiddenMain.update(dispatch, unit);
        }

        Order hiddenSecondary = behaviors.getStep(OrderStage.HIDDEN_SECONDARY, unit.getSecondaryOrder());

        if (hiddenSecondary != null) {
            hiddenSecondary.update(dispatch, unit);
        }

        Sprite sprite = unit.getSpriteOrNull();

        if (sprite != null && !this.game.getIscriptExecutor().executeSprite(sprite, unit, instructionLimit)) {
            unit.setSprite(null);
        }
    }

    /**
     * Runs the main order of a unit: its unconditional order, or, for an enabled unit, its every-frame order and the order timer, which reloads after
     * it expires and then runs the timer order of the current main order.
     *
     * @param unit             the unit
     * @param ownerPlayerType  the player type of the unit's owner for this frame
     * @param instructionLimit the instruction budget of order cleanup animations
     * @throws UnsupportedOperationException if the main order is not represented in the registry
     */
    public void updatePrimary(@NotNull Unit unit, int ownerPlayerType, int instructionLimit) {
        ScrOrderBehaviors behaviors = this.game.getOrderBehaviors();
        ScrOrderContext dispatch = this.context;
        int orderId = unit.getPrimaryOrder();
        ScrOrderBehavior behavior = behaviors.get(orderId);
        Order unconditional = behavior.getStep(OrderStage.MAIN_UNCONDITIONAL);

        dispatch.set(ownerPlayerType, instructionLimit);

        if (unconditional != null) {
            unconditional.update(dispatch, unit);

            return;
        }

        if (!behavior.isRepresented()) {
            throw Exceptions.newUnsupportedOperationException("The primary order has no represented handler: {0}", orderId);
        }

        int status = unit.getStatus();

        // Notify disabled or immobile unarmed units before the remaining main-order dispatch.
        boolean disabled = UnitOrderEligibility.isDisabled(unit);

        if (disabled || (status & UnitStatusFlags.CAN_MOVE) == 0 && (status & UnitStatusFlags.CANNOT_ATTACK) != 0) {
            // The main timer restarts and the unit is exposed to computer units.
            this.game.getHooks().notifyDisabledOrder(unit);

            if (disabled) {
                return;
            }
        }

        Order everyFrame = behavior.getStep(OrderStage.MAIN_EVERY_FRAME);

        if (everyFrame != null) {
            everyFrame.update(dispatch, unit);
        }

        int previousCountdown = unit.getPrimaryOrderCountdown();

        unit.setPrimaryOrderCountdown(previousCountdown - 1);

        if (previousCountdown != 0) {
            return;
        }

        unit.setPrimaryOrderCountdown(ORDER_TIMER);

        // Acid-spored units and the first Interceptor of the frame expose themselves to computer units, and the computer
        // spell target flags are cleared before the timer order runs.
        this.game.getHooks().notifyOrderTimer(unit);
        unit.setSpellTargetFlags(0);

        // The every-frame order may have changed the main order; the timer runs the current one.
        Order timer = behaviors.getStep(OrderStage.MAIN_TIMER, unit.getPrimaryOrder());

        if (timer != null) {
            timer.update(dispatch, unit);
        }
    }
}
