package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.order.ScrOrderSystem;
import org.fogofwarlabs.sc4j.scr.game.order.UnitOrderEligibility;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;

/**
 * Recharges Protoss shields from a Shield Battery.
 * <p>
 * Eligible units approach the battery and receive shields while its energy lasts. Recharge overlays and the battery working animation follow the unit
 * and secondary order states.
 */
public final class UnitShieldBattery {
    /**
     * The small recharge overlay image; the medium and large ones follow it.
     */
    private static final int RECHARGE_OVERLAY_SMALL = 368;
    /**
     * The largest overlay size offset.
     */
    private static final int LARGE_OVERLAY = 2;

    /**
     * The distance from the battery at which a recharging unit stops approaching.
     */
    private static final int RECHARGE_RANGE = 128;
    /**
     * The half size of the box around the target point whose units are sent to the battery.
     */
    private static final int BATTERY_CALL_RADIUS = 128;
    /**
     * The most shield points transferred per update.
     */
    private static final int MAXIMUM_RECHARGE_PER_UPDATE = 5;
    /**
     * The fraction bits of the fixed-point shield values.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    /**
     * The shield points transferred per energy point of the battery.
     */
    private static final int SHIELD_POINTS_PER_ENERGY = 2;

    /**
     * The order states of {@link ScrOrderIds#RECHARGE_SHIELDS_UNIT}: started, approaching, at the battery, recharging.
     */
    private static final int RECHARGE_STATE_START = 0;
    private static final int RECHARGE_STATE_APPROACHING = 1;
    private static final int RECHARGE_STATE_ARRIVED = 2;
    private static final int RECHARGE_STATE_RECHARGING = 3;
    /**
     * The secondary order states of {@link ScrOrderIds#SHIELD_BATTERY}: idle, working, finishing.
     */
    private static final int BATTERY_STATE_IDLE = 0;
    private static final int BATTERY_STATE_WORKING = 1;
    private static final int BATTERY_STATE_FINISHING = 2;

    private final @NotNull Game game;

    /**
     * Creates a {@link UnitShieldBattery} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitShieldBattery(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Tests recharge eligibility. A unit with a computer record already recharging, waiting for a transport or with
     * {@link ScrOrderIds#RECHARGE_SHIELDS_UNIT} at its queue head is not taken over.
     *
     * @param unit    the unit to recharge
     * @param battery the candidate Shield Battery
     * @return {@code true} if the unit may recharge from the battery, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean canUse(@NotNull Unit unit, @NotNull Unit battery) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int type = unit.getUnitId();

        if (battery.getUnitId() != ScrUnitIds.PROTOSS_SHIELD_BATTERY || (battery.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            return false;
        }

        if (units.getShieldEnabled(type) == 0 || (unit.getStatus() & UnitStatusFlags.COMPLETED) == 0 ||
            (unit.getStatus() & UnitStatusFlags.CAN_MOVE) == 0) {
            return false;
        }

        if (UnitQueries.isGroundedBuilding(unit) || unit.getOwnerIndex() != battery.getOwnerIndex()) {
            return false;
        }

        int race = units.getGroupFlags(type);

        if ((race & UnitGroupFlags.ZERG) != 0 || (race & UnitGroupFlags.TERRAN) != 0 || (race & UnitGroupFlags.PROTOSS) == 0) {
            return false;
        }

        if (unit.getShieldPoints() >= units.getShieldAmount(type) << FIXED_POINT_FRACTION_BITS || battery.getEnergy() == 0) {
            return false;
        }

        int baseType = this.game.getBaseType(type);

        if (UnitOrderEligibility.isDisabled(battery) || baseType == ScrUnitIds.PROTOSS_INTERCEPTOR || baseType == ScrUnitIds.PROTOSS_SCARAB) {
            return false;
        }

        if (unit.getComputerRecord() != null) {
            int order = unit.getPrimaryOrder();

            if (order == ScrOrderIds.RECHARGE_SHIELDS_UNIT || order == ScrOrderIds.PICKUP4) {
                return false;
            }

            OrderPool.Order head = unit.getFirstOrder();

            if (head != null && head.getOrderId() == ScrOrderIds.RECHARGE_SHIELDS_UNIT) {
                return false;
            }
        }

        return true;
    }


    /**
     * Approaches the battery, shows the overlay, then transfers up to {@link #MAXIMUM_RECHARGE_PER_UPDATE} shield points per update at
     * {@link #SHIELD_POINTS_PER_ENERGY} shield points per energy point.
     * <p>
     * An absent, empty or disabled battery still finishes the order.
     *
     * @param unit             the recharging unit
     * @param instructionLimit the image and order budget
     * @throws IllegalStateException if the unit has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    public void updateRechargeShieldsUnit(@NotNull Unit unit, int instructionLimit) {
        Unit battery = unit.getOrderTargetUnit();

        if (battery == null || battery.getEnergy() == 0 || UnitOrderEligibility.isDisabled(battery)) {
            this.game.getOrders().finishOrder(unit, instructionLimit);

            return;
        }

        if (unit.getPrimaryOrderState() == RECHARGE_STATE_START) {
            if (!this.game.getUnitTargetMovement().moveToTargetReset(unit, battery)) {
                return;
            }

            unit.setPrimaryOrderState(RECHARGE_STATE_APPROACHING);
        } else if (unit.getPrimaryOrderState() == RECHARGE_STATE_APPROACHING) {
            if (this.game.getEngineGame().getMovement().isAtDestination(unit)) {
                if ((unit.getStatus() & UnitStatusFlags.IMMOVABLE) != 0) {
                    this.game.getOrders().finishOrder(unit, instructionLimit);

                    return;
                }

                this.game.getUnitPointMovement().stopAndFaceDestination(unit);
                unit.setPrimaryOrderState(RECHARGE_STATE_ARRIVED);
            } else if (this.game.getUnitTargeting().isInRange(unit, battery, RECHARGE_RANGE)) {
                this.game.getUnitPointMovement().stopAndFaceDestination(unit);
                unit.setPrimaryOrderState(RECHARGE_STATE_ARRIVED);
            }
        }

        if (unit.getPrimaryOrderState() == RECHARGE_STATE_ARRIVED) {
            ScrOrderSystem.setSecondaryOrder(battery, ScrOrderIds.SHIELD_BATTERY);

            Sprite sprite = unit.requireSprite();
            int size = this.game.getUnitSpells().getSpriteSize(unit);

            this.game.getIscriptExecutor().createImage(sprite,
                                                       sprite.getImages().isEmpty() ? null : sprite.getImages().getFirst(),
                                                       RECHARGE_OVERLAY_SMALL + size,
                                                       0, 0,
                                                       0,
                                                       unit,
                                                       instructionLimit);
            this.game.getOrders().queueFront(unit, ScrOrderIds.RECHARGE_SHIELDS_UNIT_REMOVE_OVERLAY, null, 0, 0, ScrUnitIds.NONE);
            unit.setPrimaryOrderState(RECHARGE_STATE_RECHARGING);
        }

        if (unit.getPrimaryOrderState() == RECHARGE_STATE_RECHARGING) {
            if (battery.getOrderTargetUnit() == null) {
                battery.setOrderTargetUnit(unit);
            }

            int maximum = this.game.requireGameData().getUnitTypes().getShieldAmount(unit.getUnitId()) << FIXED_POINT_FRACTION_BITS;
            int recharge = Math.min(maximum - unit.getShieldPoints(), MAXIMUM_RECHARGE_PER_UPDATE << FIXED_POINT_FRACTION_BITS);
            int energyCost = recharge / SHIELD_POINTS_PER_ENERGY;

            if (energyCost > battery.getEnergy()) {
                energyCost = battery.getEnergy();
                recharge   = energyCost * SHIELD_POINTS_PER_ENERGY;
            }

            unit.setShieldPoints(Math.min(unit.getShieldPoints() + recharge, maximum));
            this.game.getUnitEnergy().spend(battery, energyCost);

            if (unit.getShieldPoints() >= maximum || battery.getEnergy() == 0) {
                if (battery.getOrderTargetUnit() == unit) {
                    battery.setOrderTargetUnit(null);
                }

                this.game.getOrders().finishOrder(unit, instructionLimit);
            }
        }
    }

    /**
     * Removes recharge overlays and resumes queued orders, allocating an idle order if the queue is empty.
     *
     * @param unit             the unit that finished recharging
     * @param instructionLimit the order budget
     * @throws IllegalStateException if the unit or the subunit has no sprite
     */
    public void updateRemoveOverlay(@NotNull Unit unit, int instructionLimit) {
        this.removeOverlay(unit.requireSprite());

        Unit subunit = unit.getSubunit();

        if (subunit != null) {
            this.removeOverlay(subunit.requireSprite());
        }

        this.game.getOrders().completeOrderToIdle(unit, instructionLimit);
    }

    /**
     * Sends the target, or every eligible unit near the point, to the battery.
     *
     * @param battery          the Shield Battery
     * @param instructionLimit the order budget
     * @throws UnsupportedOperationException if the game has no unit finder
     */
    public void updateRechargeShieldsBattery(@NotNull Unit battery, int instructionLimit) {
        ScrOrderSystem.setSecondaryOrder(battery, ScrOrderIds.SHIELD_BATTERY);

        Unit target = battery.getOrderTargetUnit();

        if (target != null && this.canUse(target, battery)) {
            this.game.getOrders().issue(target, ScrOrderIds.FOLLOW, battery, 0, 0, ScrUnitIds.NONE, false, instructionLimit);
        } else {
            UnitSpatialIndex spatialIndex = this.game.getUnitSystem().requireUnitSpatialIndex();
            int positionX = (short) battery.getOrderTargetX();
            int positionY = (short) battery.getOrderTargetY();
            int count = spatialIndex.collectPlacementCandidates(positionX - BATTERY_CALL_RADIUS, positionY - BATTERY_CALL_RADIUS,
                                                                positionX + BATTERY_CALL_RADIUS, positionY + BATTERY_CALL_RADIUS);
            Unit[] candidates = new Unit[count];

            for (int index = 0; index < count; index++) {
                candidates[index] = spatialIndex.getPlacementCandidate(index);
            }

            for (Unit candidate : candidates) {
                if (this.canUse(candidate, battery)) {
                    this.game.getOrders().issue(candidate, ScrOrderIds.FOLLOW, battery, 0, 0, ScrUnitIds.NONE, false, instructionLimit);
                }
            }
        }

        this.game.getOrders().finishOrder(battery, instructionLimit);
    }

    /**
     * Advances the battery working animation according to its recharging target.
     * <p>
     * Working-state updates finish without dispatching an animation. Animation paths store the final secondary state only after dispatch returns.
     *
     * @param battery          the Shield Battery
     * @param instructionLimit the animation budget
     * @throws IllegalStateException if the battery has no sprite
     */
    public void updateSecondary(@NotNull Unit battery, int instructionLimit) {
        Unit target = battery.getOrderTargetUnit();
        int secondaryState = battery.getSecondaryOrderState();

        if (secondaryState == BATTERY_STATE_IDLE) {
            if (target != null) {
                this.game.getIscriptExecutor().runSpriteAnimation(battery.requireSprite(),
                                                                  battery,
                                                                  IscriptExecutor.ANIMATION_IS_WORKING,
                                                                  0,
                                                                  instructionLimit);
                battery.setSecondaryOrderState(BATTERY_STATE_WORKING);
            }
        } else if (secondaryState == BATTERY_STATE_WORKING) {
            if (target == null) {
                battery.setSecondaryOrderState(BATTERY_STATE_FINISHING);
            } else if (target.getPrimaryOrder() != ScrOrderIds.RECHARGE_SHIELDS_UNIT) {
                battery.setOrderTargetUnit(null);
                battery.setSecondaryOrderState(BATTERY_STATE_FINISHING);
            }
        } else if (secondaryState == BATTERY_STATE_FINISHING) {
            if (target != null) {
                battery.setSecondaryOrderState(BATTERY_STATE_WORKING);
            } else {
                this.game.getIscriptExecutor().runSpriteAnimation(battery.requireSprite(),
                                                                  battery,
                                                                  IscriptExecutor.ANIMATION_WORKING_TO_IDLE,
                                                                  0,
                                                                  instructionLimit);
                battery.setSecondaryOrderState(BATTERY_STATE_IDLE);
            }
        }
    }

    /**
     * Removes the first recharge overlay image of a sprite.
     *
     * @param sprite the sprite
     * @throws UnsupportedOperationException if the image pool is not initialized
     */
    private void removeOverlay(@NotNull Sprite sprite) {
        for (SpriteImage image : sprite.getImages()) {
            int type = image.getImageTypeId();

            if (type >= RECHARGE_OVERLAY_SMALL && type <= RECHARGE_OVERLAY_SMALL + LARGE_OVERLAY) {
                sprite.removeImage(image);
                this.game.getSpriteSystem().requireImagePool().recycle(image);

                return;
            }
        }
    }
}
