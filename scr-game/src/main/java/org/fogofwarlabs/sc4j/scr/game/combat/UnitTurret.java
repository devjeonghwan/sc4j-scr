package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maintains turret subunit death, the turret orders and the tank siege transitions. Turret creation and movement are in
 * {@link UnitStorageConstructor} and {@link UnitPhase}.
 */
public final class UnitTurret {
    /**
     * The order states of Sieging and Unsieging: started, waiting until the tank and its turret stop (Sieging only), turning to the {@code units.dat}
     * direction, and morphing until the animation signals its end.
     */
    private static final int TRANSITION_STATE_START = 0;
    private static final int TRANSITION_STATE_STOPPING = 1;
    private static final int TRANSITION_STATE_TURNING = 2;
    private static final int TRANSITION_STATE_MORPHING = 3;

    /**
     * The distance in pixels of the waypoint ahead in the {@code units.dat} direction that a sieging tank turns to.
     */
    private static final int FACING_WAYPOINT_DISTANCE = 16;

    /**
     * The entries of the inner bounding box array: left, upper, right and lower pixel bounds.
     */
    private static final int BOX_LEFT = 0;
    private static final int BOX_UPPER = 1;
    private static final int BOX_RIGHT = 2;
    private static final int BOX_LOWER = 3;

    /**
     * The mask that wraps a direction or a direction difference to one byte.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * The fraction bits of a fixed-point position or distance.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;

    /**
     * Checks whether the unit faces its next target waypoint.
     *
     * @param unit the turning unit
     * @return {@code true} if the unit stands on its waypoint or its heading points at it, {@code false} otherwise
     * @throws IllegalStateException if the unit is away from its waypoint and has no sprite
     */
    private static boolean isFacingDirectionTarget(@NotNull Unit unit) {
        if (unit.getPositionX() == unit.getDirectionTargetX() && unit.getPositionY() == unit.getDirectionTargetY()) {
            return true;
        }

        Sprite sprite = unit.requireSprite();
        int direction = FlingyMotionUtils.calculateDirection(unit.getDirectionTargetX() - sprite.getX(), unit.getDirectionTargetY() - sprite.getY());

        return ((unit.getHeading() - direction) & BYTE_MASK) == 0;
    }

    private static @NotNull Unit requireMainUnit(@NotNull Unit turret) {
        Unit unit = turret.getSubunit();

        if (unit == null) {
            throw Exceptions.newIllegalStateException("A turret has no main unit.");
        }

        return unit;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitTurret} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitTurret(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Returns the unit's subunit when it is a turret.
     *
     * @param unit the unit
     * @return the unit's turret, or {@code null}
     */
    public @Nullable Unit getTurret(@NotNull Unit unit) {
        Unit subunit = unit.getSubunit();

        return subunit != null && this.game.getUnitTargeting().isTurret(subunit) ? subunit : null;
    }


    /**
     * Moves the turret's main image to the head of the main unit's sprite, above every image and selection image, without directional frames, then
     * destroys the turret.
     *
     * @param unit the dying main unit
     * @throws IllegalStateException if a turret exists without a sprite, or a transferred main image requires an absent main-unit sprite
     */
    public void detachForDeath(@NotNull Unit unit) {
        Unit turret = unit.getSubunit();

        if (turret == null) {
            return;
        }

        Sprite turretSprite = turret.requireSprite();
        SpriteImage mainImage = turretSprite.getMainImage();

        if (mainImage != null) {
            turretSprite.removeImage(mainImage);
            turretSprite.setMainImage(null);

            Sprite sprite = unit.requireSprite();

            sprite.insertHeadImage(mainImage);
            mainImage.setFlags(mainImage.getFlags() & ~SpriteImage.FLAG_HAS_DIRECTIONAL_FRAMES);
        }

        this.destroy(turret);
        unit.setSubunit(null);
    }

    /**
     * Removes a turret's target references and owner membership, transfers it to the dead list, sets Die and destroys its sprite.
     *
     * @param turret the turret
     * @throws IllegalStateException         if the turret has no sprite or its sprite cannot be unlinked from its sprite row
     * @throws UnsupportedOperationException if the unit pool, the image pool or the sprite pool is not initialized
     */
    void destroy(@NotNull Unit turret) {
        this.game.getUnitLifecycle().removeAllTargetReferences(turret, true);
        turret.setSubunit(null);
        this.game.getUnitSystem().requireUnitPool().transferDetachedUnitToDead(turret);
        turret.setPrimaryOrder(ScrOrderIds.DIE);
        turret.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

        Sprite sprite = turret.requireSprite();

        for (SpriteImage image : sprite.getImages()) {
            this.game.getSpriteSystem().requireImagePool().recycle(image);
        }

        sprite.clearImages();
        sprite.setMainImage(null);

        if (!this.game.getSpriteSystem().requireSpritePool().recycleEmptySprite(sprite, this.game.getMap().getHeight())) {
            throw Exceptions.newIllegalStateException("Turret destruction could not unlink its sprite row.");
        }

        turret.setSprite(null);
    }

    /**
     * Faces where the main unit faces, then guards.
     * <p>
     * Retaliation or an active timer can return early.
     *
     * @param turret           the turret
     * @param instructionLimit the order budget
     * @throws IllegalStateException if the turret has no main unit
     */
    public void updateGuard(@NotNull Unit turret, int instructionLimit) {
        Unit mainUnit = requireMainUnit(turret);

        turret.setDirectionTargetX(mainUnit.getDirectionTargetX());
        turret.setDirectionTargetY(mainUnit.getDirectionTargetY());
        this.game.getUnitAttack().updatePlayerGuard(turret, instructionLimit);
    }

    /**
     * Aims at and fires on a visible target in range, falling back to TurretGuard when the main unit is not attacking the target.
     * <p>
     * The Siege Tank turret of a main unit with a computer record sieges first against a visible non-critter target that only the siege mode reaches.
     *
     * @param turret           the turret
     * @param instructionLimit the order and script budget
     * @throws IllegalStateException if the turret has no main unit, or the target has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    public void updateAttack(@NotNull Unit turret, int instructionLimit) {
        this.game.getUnitAttack().reacquireTarget(turret);

        Unit target = turret.getOrderTargetUnit();

        if (target != null) {
            Sprite targetSprite = target.requireSprite();

            turret.setOrderTargetX(targetSprite.getX());
            turret.setOrderTargetY(targetSprite.getY());
        }

        if (!this.game.getUnitTargeting().canAttackTarget(turret, target) || !this.game.getUnitTargeting().canSeeTarget(turret, target)) {
            this.game.getOrders().issueUntargeted(turret, ScrOrderIds.TURRET_GUARD, instructionLimit);

            return;
        }

        Unit mainUnit = requireMainUnit(turret);

        if (turret.getUnitId() == ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE_TURRET && mainUnit.getComputerRecord() != null &&
            this.shouldSiege(turret, mainUnit, target)) {
            ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();

            this.game.getOrders().issueUntargeted(turret, ScrOrderIds.NOTHING, instructionLimit);
            this.game.getOrders().queueReplacingSameOrders(
                    turret,
                    unitTypes.getAttackUnitOrder(ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE_TURRET),
                    null,
                    0, 0,
                    ScrUnitIds.NONE
            );
            this.game.getOrders().issueUntargeted(mainUnit, ScrOrderIds.SIEGING, instructionLimit);

            Sprite targetSprite = target.requireSprite();

            this.game.getOrders().queueReplacingSameOrders(
                    mainUnit,
                    unitTypes.getAttackUnitOrder(ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE),
                    target,
                    targetSprite.getX(), targetSprite.getY(),
                    ScrUnitIds.NONE
            );

            return;
        }

        boolean attack = this.game.getUnitTargeting().isInWeaponMovementRange(turret, target);
        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();

        if (!attack &&
            (mainUnit.getPrimaryOrder() != unitTypes.getAttackUnitOrder(mainUnit.getUnitId()) ||
             this.game.getUnitQueries().getRightClickClass(mainUnit) == ScrUnitTypeView.RIGHT_CLICK_ATTACK_NO_MOVE)) {
            this.game.getOrders().issueUntargeted(turret, ScrOrderIds.TURRET_GUARD, instructionLimit);
        } else {
            attack = true;
        }

        if (attack) {
            turret.setDirectionTargetX(turret.getOrderTargetX());
            turret.setDirectionTargetY(turret.getOrderTargetY());

            if ((mainUnit.getMovementFlags() & MovementFlags.ACCELERATING) == 0) {
                this.game.getUnitAttack().fireWeapon(turret, instructionLimit);
            }
        }
    }

    /**
     * Checks whether a computer Siege Tank sieges against its turret's target: the main unit can use Tank Siege Mode, the turret sees the target, the
     * target is within the siege-mode weapon range but beyond the tank-mode weapon range (both widened by the movement range), and the target is not
     * a critter.
     *
     * @param turret   the tank-mode turret
     * @param mainUnit the main unit, a Siege Tank with a computer record
     * @param target   the turret's order target
     * @return {@code true} if the tank sieges, {@code false} otherwise
     * @throws UnsupportedOperationException if the computer-player phase is missing
     * @throws NullPointerException          if the world was built without game data
     */
    private boolean shouldSiege(@NotNull Unit turret, @NotNull Unit mainUnit, @NotNull Unit target) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase == null) {
            throw Exceptions.newUnsupportedOperationException("Computer Siege Tanks require the computer-player phase.");
        }

        if (!phase.canUseTechnology(mainUnit, ScrTechIds.TANK_SIEGE_MODE) || !this.game.getUnitTargeting().canSeeTarget(turret, target)) {
            return false;
        }

        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        int siegeWeapon = unitTypes.getGroundWeapon(ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE_TURRET);
        int tankWeapon = unitTypes.getGroundWeapon(ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE_TURRET);
        int movement = this.game.getUnitTargeting().getTargetMovementRange(mainUnit, target);
        int siegeRange = this.game.getUnitTargeting().getWeaponMaximumRange(turret, siegeWeapon) + movement;

        if (!this.game.getUnitTargeting().isInRange(mainUnit, target, siegeRange)) {
            return false;
        }

        int tankRange = this.game.getUnitTargeting().getWeaponMaximumRange(turret, tankWeapon) + movement;

        if (this.game.getUnitTargeting().isInRange(mainUnit, target, tankRange)) {
            return false;
        }

        int type = this.game.getBaseType(target);

        return type != ScrUnitIds.CRITTER_RHYNADON && type != ScrUnitIds.CRITTER_BENGALAAS &&
               (type < ScrUnitIds.CRITTER_SCANTID || type > ScrUnitIds.CRITTER_URSADON);
    }

    /**
     * Stops and turns the tank and turret, then morphs the tank to siege mode.
     *
     * @param unit             the tank
     * @param instructionLimit the order and script budget
     * @throws IllegalStateException if the tank has no turret or its turret has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    public void updateSieging(@NotNull Unit unit, int instructionLimit) {
        int type = unit.getUnitId();

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_START) {
            if (type != ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE && type != ScrUnitIds.HERO_EDMUND_DUKE_TANK_MODE) {
                this.game.getOrders().finishOrder(unit, instructionLimit);

                return;
            }

            if ((unit.getMovementFlags() & MovementFlags.ACCELERATING) != 0) {
                this.game.getUnitPointMovement().stopAndFaceDestination(unit);
            }

            unit.setPrimaryOrderState(TRANSITION_STATE_STOPPING);
        }

        Unit turret = this.requireTurret(unit);

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_STOPPING) {
            if ((unit.getMovementFlags() & MovementFlags.ACCELERATING) == 0 && (turret.getMovementFlags() & MovementFlags.ACCELERATING) == 0 &&
                (turret.getStatus() & UnitStatusFlags.ISCRIPT_NO_BREAK) == 0) {
                this.faceUnitDirection(unit);
                this.faceUnitDirection(turret);
                this.game.getOrders().issue(turret,
                                            ScrOrderIds.NOTHING_WAIT,
                                            unit,
                                            0, 0,
                                            ScrUnitIds.NONE,
                                            false,
                                            instructionLimit);
                this.game.getIscriptExecutor().runSpriteAnimation(turret.requireSprite(),
                                                                  turret,
                                                                  IscriptExecutor.ANIMATION_SPECIAL_STATE_1,
                                                                  0,
                                                                  instructionLimit);
                unit.setPrimaryOrderState(TRANSITION_STATE_TURNING);

                int[] box = this.getInnerBoundingBox(unit);
                Unit[] blockers = this.game.getUnitTargeting().findUnits(box[BOX_LEFT], box[BOX_UPPER], box[BOX_RIGHT], box[BOX_LOWER]);

                for (Unit blocker : blockers) {
                    if (blocker == unit || !UnitQueries.isGroundedBuilding(blocker) || (blocker.getStatus() & UnitStatusFlags.NO_COLLIDE) != 0) {
                        continue;
                    }

                    this.game.getUnitLifecycle().kill(unit, instructionLimit);

                    return;
                }
            }
        }

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_TURNING) {
            if (isFacingDirectionTarget(unit) && isFacingDirectionTarget(turret)) {
                if (type == ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE) {
                    this.game.getUnitMorphing().morph(unit, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, instructionLimit);
                } else if (type == ScrUnitIds.HERO_EDMUND_DUKE_TANK_MODE) {
                    this.game.getUnitMorphing().morph(unit, ScrUnitIds.HERO_EDMUND_DUKE_SIEGE_MODE, instructionLimit);
                }

                unit.setPrimaryOrderState(TRANSITION_STATE_MORPHING);
            }
        }

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_MORPHING && (unit.getOrderSignal() & Unit.ORDER_SIGNAL_ACTION_DONE) != 0) {
            unit.setOrderSignal(unit.getOrderSignal() & ~Unit.ORDER_SIGNAL_ACTION_DONE);

            OrderPool.Order next = unit.getFirstOrder();

            if (next != null && next.getOrderId() != ScrOrderIds.WATCH_TARGET) {
                int idleOrder = this.game.requireGameData().getUnitTypes().getReturnToIdleOrder(unit.getUnitId());

                this.game.getOrders().issueUntargeted(unit, idleOrder, instructionLimit);
            }

            this.finishSiegeTransition(unit, this.requireTurret(unit), instructionLimit);
        }
    }

    /**
     * Turns the turret and waits for its animation before morphing the tank to tank mode.
     *
     * @param unit             the sieged tank
     * @param instructionLimit the order and script budget
     * @throws IllegalStateException if the tank has no turret, or the tank or its turret has no sprite
     */
    public void updateUnsieging(@NotNull Unit unit, int instructionLimit) {
        Unit turret = this.requireTurret(unit);
        int type = unit.getUnitId();

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_START) {
            if (type != ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE && type != ScrUnitIds.HERO_EDMUND_DUKE_SIEGE_MODE) {
                this.game.getOrders().finishOrder(unit, instructionLimit);

                return;
            }

            if ((turret.getStatus() & UnitStatusFlags.ISCRIPT_NO_BREAK) == 0) {
                this.game.getOrders().issue(turret,
                                            ScrOrderIds.NOTHING_WAIT,
                                            unit,
                                            0, 0,
                                            ScrUnitIds.NONE,
                                            false,
                                            instructionLimit);
                this.faceUnitDirection(turret);
                this.game.getIscriptExecutor().runSpriteAnimation(turret.requireSprite(),
                                                                  turret,
                                                                  IscriptExecutor.ANIMATION_SPECIAL_STATE_1,
                                                                  0,
                                                                  instructionLimit);
                unit.setPrimaryOrderState(TRANSITION_STATE_TURNING);
            }
        }

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_TURNING && isFacingDirectionTarget(turret)) {
            this.game.getIscriptExecutor().runSpriteAnimation(unit.requireSprite(),
                                                              unit,
                                                              IscriptExecutor.ANIMATION_SPECIAL_STATE_2,
                                                              0,
                                                              instructionLimit);
            this.game.getIscriptExecutor().runSpriteAnimation(turret.requireSprite(),
                                                              turret,
                                                              IscriptExecutor.ANIMATION_SPECIAL_STATE_2,
                                                              0,
                                                              instructionLimit);
            unit.setOrderSignal(unit.getOrderSignal() & ~Unit.ORDER_SIGNAL_ACTION_DONE);
            unit.setPrimaryOrderState(TRANSITION_STATE_MORPHING);
        }

        if (unit.getPrimaryOrderState() == TRANSITION_STATE_MORPHING && (unit.getOrderSignal() & Unit.ORDER_SIGNAL_ACTION_DONE) != 0) {
            unit.setOrderSignal(unit.getOrderSignal() & ~Unit.ORDER_SIGNAL_ACTION_DONE);

            if (type == ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE) {
                this.game.getUnitMorphing().morph(unit, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, instructionLimit);
            } else if (type == ScrUnitIds.HERO_EDMUND_DUKE_SIEGE_MODE) {
                this.game.getUnitMorphing().morph(unit, ScrUnitIds.HERO_EDMUND_DUKE_TANK_MODE, instructionLimit);
            }

            this.finishSiegeTransition(unit, this.requireTurret(unit), instructionLimit);
        }
    }

    /**
     * Runs the sieged tank's attack order while its turret fires: stop watching a target that cannot be attacked, or one out of sight or range,
     * returning to idle at the target position.
     *
     * @param unit             the sieged tank
     * @param instructionLimit the order budget
     */
    public void updateWatchTarget(@NotNull Unit unit, int instructionLimit) {
        Unit target = unit.getOrderTargetUnit();

        if (!this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            this.finishOrderAtTarget(unit, instructionLimit);

            return;
        }

        if (!this.game.getUnitTargeting().isInWeaponMovementRange(unit, target) &&
            this.game.getUnitQueries().getRightClickClass(unit) == ScrUnitTypeView.RIGHT_CLICK_ATTACK_NO_MOVE) {
            this.finishOrderAtTarget(unit, instructionLimit);
        }
    }

    /**
     * Continues the queued order or returns to idle at the current target position.
     *
     * @param unit             the sieged tank
     * @param instructionLimit the order budget
     * @throws NullPointerException if the world was built without game data
     */
    private void finishOrderAtTarget(@NotNull Unit unit, int instructionLimit) {
        if (unit.getFirstOrder() != null) {
            unit.setUserActionFlags(unit.getUserActionFlags() | Unit.USER_ACTION_ORDER_DONE);
            this.game.getOrders().activateNext(unit, instructionLimit);
        } else {
            this.game.getOrders().issue(unit,
                                        this.game.requireGameData().getUnitTypes().getReturnToIdleOrder(unit.getUnitId()),
                                        null,
                                        unit.getOrderTargetX(), unit.getOrderTargetY(),
                                        ScrUnitIds.NONE,
                                        false,
                                        instructionLimit);
        }
    }

    private void finishSiegeTransition(@NotNull Unit unit, @NotNull Unit turret, int instructionLimit) {
        this.game.getOrders().completeOrder(unit, instructionLimit);
        this.game.getOrders().completeOrder(turret, instructionLimit);
    }

    /**
     * Sets the next target waypoint {@code 16} pixels ahead in the {@code units.dat} unit direction.
     *
     * @param unit the turning unit
     * @throws NullPointerException if the world was built without game data
     */
    private void faceUnitDirection(@NotNull Unit unit) {
        int direction = this.game.requireGameData().getUnitTypes().getInitialDirection(unit.getUnitId());
        long vector = FlingyMotionUtils.calculateDirectionVector(FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN * direction & BYTE_MASK,
                                                                 FACING_WAYPOINT_DISTANCE << FIXED_POINT_FRACTION_BITS);

        unit.setDirectionTargetX((unit.getFixedPositionX() + (int) vector) >> FIXED_POINT_FRACTION_BITS);
        unit.setDirectionTargetY((unit.getFixedPositionY() + (int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT)) >>
                                 FIXED_POINT_FRACTION_BITS);
    }

    /**
     * Returns the unit's inner sprite bounding box.
     *
     * @param unit the unit
     * @return the left, upper, right and lower pixel bounds, indexed by {@link #BOX_LEFT} through {@link #BOX_LOWER}
     * @throws IllegalStateException if the unit has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    private int @NotNull [] getInnerBoundingBox(@NotNull Unit unit) {
        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        Sprite sprite = unit.requireSprite();
        int type = unit.getUnitId();

        return new int[]{sprite.getX() - unitTypes.getDimensionLeft(type), sprite.getY() - unitTypes.getDimensionUp(type),
                         sprite.getX() + unitTypes.getDimensionRight(type), sprite.getY() + unitTypes.getDimensionDown(type)};
    }

    private @NotNull Unit requireTurret(@NotNull Unit unit) {
        Unit turret = unit.getSubunit();

        if (turret == null || !this.game.getUnitTargeting().isTurret(turret)) {
            throw Exceptions.newIllegalStateException("A tank siege order has no turret.");
        }

        return turret;
    }
}
