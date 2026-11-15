package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SoundIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.order.ScrOrderSystem;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Burrows and unburrows units: the Burrow and Unburrow commands of a selection, the Burrowing, Burrowed and Unburrowing orders, and the burrow of a
 * Spider Mine. A burrowed unit is passively cloaked; its detection is {@link UnitCloaking}.
 */
public final class UnitBurrowing {
    /**
     * The Burrowing order state that waits until the unit stops.
     */
    private static final int BURROWING_STATE_STOPPING = 1;
    /**
     * The Burrowing order state that turns the unit to its burrow direction.
     */
    private static final int BURROWING_STATE_FACING = 2;
    /**
     * The Burrowing order state that waits for the burrow animation.
     */
    private static final int BURROWING_STATE_ANIMATING = 3;
    /**
     * The Unburrowing order state that resets the detection after the unburrow animation started.
     */
    private static final int UNBURROWING_STATE_STARTED = 1;
    /**
     * The Unburrowing order state that waits for the unburrow animation.
     */
    private static final int UNBURROWING_STATE_ANIMATING = 2;
    /**
     * The Unburrowing order state that queues the idle order and finishes.
     */
    private static final int UNBURROWING_STATE_FINISHED = 3;
    /**
     * The burrow direction, in {@code 32} steps, of a Defiler or Unclean One.
     */
    private static final int DEFILER_BURROW_DIRECTION = 11;
    /**
     * The burrow direction, in {@code 32} steps, of every other burrowing unit.
     */
    private static final int DEFAULT_BURROW_DIRECTION = 12;
    /**
     * The burrow direction, in {@code 32} steps, of a Drone.
     */
    private static final int DRONE_BURROW_DIRECTION = 13;
    /**
     * The distance in pixels of the point a burrowing unit turns to.
     */
    private static final int BURROW_FACING_DISTANCE = 16;
    /**
     * The frames between two casts or target searches of a burrowed unit with a computer record.
     */
    private static final int BURROWED_COMPUTER_DELAY = 7;
    /**
     * The elevation level of a burrowed unit's sprite.
     */
    private static final int BURROWED_ELEVATION_LEVEL = 1;
    /**
     * The force argument of an animation request that respects the sprite's visibility flag.
     */
    private static final int NOT_FORCED = 0;
    /**
     * The fraction bits of a fixed-point position.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    /**
     * The shift of the vertical component in a packed direction vector.
     */
    private static final int PACKED_COORDINATE_SHIFT = Integer.SIZE;
    /**
     * The mask of an unsigned byte value.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Finds the first carried powerup or resource image of a sprite: a Flag, mineral chunk or gas container image first, then the Uraj or Khalis
     * crystal.
     *
     * @param sprite the sprite whose images are searched
     * @return the carried image, or {@code null} if the sprite has none
     */
    private static @Nullable SpriteImage findCarriedImage(@NotNull Sprite sprite) {
        for (SpriteImage image : sprite.getImages()) {
            int imageType = image.getImageTypeId();

            if (imageType >= ImageIds.FLAG && imageType <= ImageIds.TERRAN_GAS_TANK_TYPE2) {
                return image;
            }
        }

        for (SpriteImage image : sprite.getImages()) {
            int imageType = image.getImageTypeId();

            if (imageType >= ImageIds.URAJ && imageType <= ImageIds.KHALIS) {
                return image;
            }
        }

        return null;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitBurrowing} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitBurrowing(@NotNull Game game) {
        this.game = game;
    }


    /**
     * Issues Burrowing to every selected unit able to burrow.
     * <p>
     * The selection ends at its first empty entry. A unit destroyed while dying, a unit of another player or without Burrowing, and a unit that is
     * burrowed or already burrowing are skipped.
     *
     * @param owner            the command player
     * @param queue            whether to append the order
     * @param instructionLimit the order activation budget
     * @throws UnsupportedOperationException if the selection storage is not initialized
     */
    public void burrow(int owner, boolean queue, int instructionLimit) {
        UnitSelection selection = this.game.requireSelection();

        for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
            Unit unit = selection.getSelectedUnit(owner, position);

            if (unit == null) {
                break;
            }

            if (unit.isDestroyed()) {
                continue;
            }

            if (unit.getOwnerIndex() != owner || !this.game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.BURROWING)) {
                continue;
            }

            if ((unit.getStatus() & UnitStatusFlags.BURROWED) != 0 || unit.getPrimaryOrder() == ScrOrderIds.BURROWING) {
                continue;
            }

            this.game.getOrders().issue(unit, ScrOrderIds.BURROWING, null, 0, 0, ScrUnitIds.NONE, queue, instructionLimit);
        }
    }

    /**
     * Unburrows every selected unit of the command player that can use Burrowing and whose type can burrow ({@link #unburrowUnit}). The selection
     * ends at its first empty entry, and a unit destroyed while dying is skipped.
     *
     * @param owner            the command player
     * @param instructionLimit the order activation budget
     * @throws UnsupportedOperationException if the selection storage is not initialized
     * @throws NullPointerException          if the world was built without game data
     */
    public void unburrow(int owner, int instructionLimit) {
        UnitSelection selection = this.game.requireSelection();
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();

        for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
            Unit unit = selection.getSelectedUnit(owner, position);

            if (unit == null) {
                break;
            }

            if (unit.isDestroyed()) {
                continue;
            }

            if (unit.getOwnerIndex() != owner || !this.game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.BURROWING)) {
                continue;
            }

            if ((units.getFlags(unit.getUnitId()) & UnitTypeFlags.BURROWABLE) == 0) {
                continue;
            }

            this.unburrowUnit(unit, instructionLimit);
        }
    }

    /**
     * Issues Unburrowing to a burrowing or burrowed unit.
     * <p>
     * A Lurker is always issued the order and stays uninterruptible while it is issued; any other unit that is neither burrowing nor burrowed is left
     * unchanged.
     *
     * @param unit             the burrowing or burrowed unit
     * @param instructionLimit the order activation budget
     */
    public void unburrowUnit(@NotNull Unit unit, int instructionLimit) {
        int order = unit.getPrimaryOrder();
        boolean lurker = this.game.getBaseType(unit) == ScrUnitIds.ZERG_LURKER;

        if (order != ScrOrderIds.BURROWING && order != ScrOrderIds.BURROWED && !lurker) {
            return;
        }

        if (lurker) {
            unit.setStatus(unit.getStatus() | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);
        }

        this.game.getOrders().issueUntargeted(unit, ScrOrderIds.UNBURROWING, instructionLimit);
        this.game.getOrders().completeOrder(unit, instructionLimit);
    }

    /**
     * Advances stopping, facing and animation before completing the burrow.
     * <p>
     * Completing the burrow removes the status-effect overlays but keeps the effects: the Defensive Matrix overlay is removed by damage of its own
     * health, after which the matrix gets its health and timer back.
     *
     * @param unit             the burrowing unit
     * @param instructionLimit the order and script budget
     * @throws NullPointerException  if the world was built without game data
     * @throws IllegalStateException if the unit has no sprite once it has stopped moving
     */
    public void updateBurrowing(@NotNull Unit unit, int instructionLimit) {
        if (unit.getPrimaryOrderState() == 0) {
            this.game.getUnitPointMovement().stop(unit);
            unit.setPrimaryOrderState(BURROWING_STATE_STOPPING);
        }

        if (unit.getPrimaryOrderState() == BURROWING_STATE_STOPPING) {
            if ((unit.getMovementFlags() & MovementFlags.ACCELERATING) != 0) {
                if (unit.getNextSpeed() == 0) {
                    this.game.getUnitMovementCollision().setImmovable(unit);
                }
            } else {
                Sprite sprite = unit.requireSprite();

                this.game.getIscriptExecutor().runSpriteAnimation(sprite,
                                                                  unit,
                                                                  IscriptExecutor.ANIMATION_WALKING_TO_IDLE,
                                                                  NOT_FORCED,
                                                                  instructionLimit);

                int baseType = this.game.getBaseType(unit);
                int direction = baseType == ScrUnitIds.ZERG_DEFILER || baseType == ScrUnitIds.HERO_UNCLEAN_ONE
                                ? DEFILER_BURROW_DIRECTION
                                : baseType == ScrUnitIds.ZERG_DRONE ? DRONE_BURROW_DIRECTION : DEFAULT_BURROW_DIRECTION;
                long vector = FlingyMotionUtils.calculateDirectionVector(FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN * direction & BYTE_MASK,
                                                                         BURROW_FACING_DISTANCE << FIXED_POINT_FRACTION_BITS);

                unit.setDirectionTargetX((unit.getFixedPositionX() + (int) vector) >> FIXED_POINT_FRACTION_BITS);
                unit.setDirectionTargetY((unit.getFixedPositionY() + (int) (vector >>> PACKED_COORDINATE_SHIFT)) >> FIXED_POINT_FRACTION_BITS);
                unit.setPrimaryOrderState(BURROWING_STATE_FACING);
            }
        }

        if (unit.getPrimaryOrderState() == BURROWING_STATE_FACING) {
            Sprite sprite = unit.requireSprite();
            boolean facing = unit.getPositionX() == unit.getDirectionTargetX() && unit.getPositionY() == unit.getDirectionTargetY();

            if (!facing) {
                int direction = FlingyMotionUtils.calculateDirection(unit.getDirectionTargetX() - sprite.getX(),
                                                                     unit.getDirectionTargetY() - sprite.getY());

                facing = (byte) (unit.getHeading() - direction) == 0;
            }

            if (facing) {
                this.game.getIscriptExecutor().runSpriteAnimation(sprite, unit, IscriptExecutor.ANIMATION_BURROW, NOT_FORCED, instructionLimit);
                unit.setPrimaryOrderState(BURROWING_STATE_ANIMATING);
                this.game.getUnitSounds().play(SoundIds.BURROW_DOWN, unit);
            }
        }

        if (unit.getPrimaryOrderState() == BURROWING_STATE_ANIMATING && (unit.getOrderSignal() & Unit.ORDER_SIGNAL_ANIMATION_DONE) != 0) {
            unit.setOrderSignal(unit.getOrderSignal() & ~Unit.ORDER_SIGNAL_ANIMATION_DONE);

            Sprite sprite = unit.requireSprite();
            ScrImageTypeView images = this.game.requireGameData().getImageTypes();

            for (SpriteImage image : sprite.getImages()) {
                if (images.getDrawIfCloaked(image.getImageTypeId()) == 0) {
                    image.setFlags(image.getFlags() | SpriteImage.FLAG_HIDDEN);
                }
            }

            SpriteImage carriedImage = findCarriedImage(sprite);

            if (carriedImage != null) {
                carriedImage.setFlags(carriedImage.getFlags() | SpriteImage.FLAG_HIDDEN);
            }

            unit.setStatus(unit.getStatus() | UnitStatusFlags.NO_COLLIDE);
            this.setBurrowed(unit);
            ScrOrderSystem.setSecondaryOrder(unit, ScrOrderIds.CLOAK);
            unit.setDetectionFlags(Unit.DETECTION_FLAGS_UNDETECTED);

            // Remove the status-effect overlays while retaining the effects.
            if (unit.getDefensiveMatrixHealth() != 0) {
                int health = unit.getDefensiveMatrixHealth();
                int timer = unit.getDefensiveMatrixTimer();

                this.game.getUnitSpells().dealDefensiveMatrixDamage(unit, health, instructionLimit);
                unit.setDefensiveMatrixHealth(health);
                unit.setDefensiveMatrixTimer(timer);
            }

            if (unit.getIrradiateTimer() != 0) {
                this.game.getUnitSpells().destroyOverlay(unit, ImageIds.IRRADIATE_SMALL, ImageIds.IRRADIATE_LARGE);
            }

            if (unit.getSpeedDecreaseByte() != 0) {
                this.game.getUnitSpells().destroyOverlay(unit, ImageIds.ENSNARE_OVERLAY_SMALL, ImageIds.ENSNARE_OVERLAY_LARGE);
            }

            if (unit.getPlagueTimer() != 0) {
                this.game.getUnitSpells().destroyOverlay(unit, ImageIds.PLAGUE_OVERLAY_SMALL, ImageIds.PLAGUE_OVERLAY_LARGE);
            }

            this.game.getOrders().issueUntargeted(unit, ScrOrderIds.BURROWED, instructionLimit);
            this.game.getOrders().completeOrder(unit, instructionLimit);
        }
    }

    /**
     * Finishes the order of a Lurker, which activates its next queued order or returns it to idle, and unburrows a Hydralisk before a queued Lurker
     * morph; any other burrowed unit with a computer record lets its computer player cast a spell or pick a target every {@code 7} frames.
     *
     * @param unit             the burrowed unit
     * @param instructionLimit the order activation budget
     */
    public void updateBurrowed(@NotNull Unit unit, int instructionLimit) {
        int baseType = this.game.getBaseType(unit);

        if (baseType == ScrUnitIds.ZERG_LURKER) {
            this.game.getOrders().completeOrder(unit, instructionLimit);
        } else if (baseType == ScrUnitIds.ZERG_HYDRALISK && unit.getFirstOrder() != null &&
                   unit.getFirstOrder().getOrderId() == ScrOrderIds.ZERG_UNIT_MORPH) {
            // Unburrow before the queued Lurker morph.
            this.game.getOrders().issueUntargeted(unit, ScrOrderIds.UNBURROWING, instructionLimit);

            this.game.getOrders().queueAfter(unit, ScrOrderIds.ZERG_UNIT_MORPH, null, 0, 0, ScrUnitIds.NONE, unit.getFirstOrder());

            this.game.getOrders().completeOrder(unit, instructionLimit);
        } else if (unit.getComputerRecord() != null && unit.getOrderTimer() == 0) {
            // A burrowed unit with a computer record casts a spell or picks a target every seven frames.
            unit.setOrderTimer(BURROWED_COMPUTER_DELAY);
            this.game.getHooks().notifyBurrowedTimer(unit, instructionLimit);
        }
    }

    /**
     * Advances the unburrow animation and restores movement, images and the idle order.
     * <p>
     * The first state restores the elevation and collision, clears the burrowed sprite flag and the secondary order, starts the unburrow animation
     * and plays its sound; the next marks the unit undetected and zeroes its detection countdown. When the animation is done, the unit gets its
     * movement state, its own sprite type, its status-effect overlays, its carried image and its direction back; the last state queues the idle order
     * when no order is queued and finishes the order.
     *
     * @param unit             the unburrowing unit
     * @param instructionLimit the order and script budget
     * @throws NullPointerException          if the world was built without game data
     * @throws IllegalStateException         if the unit has no sprite
     * @throws UnsupportedOperationException if the game has no unit finder when the unburrowing starts
     */
    public void updateUnburrowing(@NotNull Unit unit, int instructionLimit) {
        Sprite sprite = unit.requireSprite();
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();

        if (unit.getPrimaryOrderState() == 0) {
            sprite.setElevationLevel(units.getElevationLevel(unit.getUnitId()));
            unit.setStatus(unit.getStatus() & ~UnitStatusFlags.NO_COLLIDE);

            UnitSpatialIndex spatialIndex = this.game.getUnitSystem().requireUnitSpatialIndex();

            spatialIndex.notifyCollision(unit, units);
            sprite.setFlags(sprite.getFlags() & ~Sprite.FLAG_BURROWED);
            ScrOrderSystem.setSecondaryOrder(unit, ScrOrderIds.NOTHING);
            this.game.getIscriptExecutor().runSpriteAnimation(sprite, unit, IscriptExecutor.ANIMATION_UN_BURROW, NOT_FORCED, instructionLimit);
            this.game.getUnitSounds().play(SoundIds.BURROW_UP, unit);
            unit.setPrimaryOrderState(UNBURROWING_STATE_STARTED);
        } else if (unit.getPrimaryOrderState() == UNBURROWING_STATE_STARTED) {
            unit.setDetectionFlags(Unit.DETECTION_FLAGS_UNDETECTED);
            unit.setVisibilityUpdateCountdown(0);
            unit.setPrimaryOrderState(UNBURROWING_STATE_ANIMATING);
        }

        if (unit.getPrimaryOrderState() == UNBURROWING_STATE_ANIMATING) {
            if ((unit.getOrderSignal() & Unit.ORDER_SIGNAL_ANIMATION_DONE) != 0) {
                unit.setOrderSignal(unit.getOrderSignal() & ~Unit.ORDER_SIGNAL_ANIMATION_DONE);
                this.game.getUnitInitializer().resetMovementState(unit);
                unit.setStatus(unit.getStatus() & ~UnitStatusFlags.PASSIVELY_CLOAKED & ~UnitStatusFlags.BURROWED);

                int spriteType = this.game.requireGameData().getFlingyTypes().getSprite(units.getFlingyType(unit.getUnitId()));

                this.game.getSelectionImages().changeSpriteType(sprite, spriteType);
                this.game.getUnitStatusEffects().apply(unit, instructionLimit);

                SpriteImage carriedImage = findCarriedImage(sprite);

                if (carriedImage != null && (carriedImage.getFlags() & SpriteImage.FLAG_HIDDEN) != 0) {
                    carriedImage.setFlags(carriedImage.getFlags() & ~SpriteImage.FLAG_HIDDEN | SpriteImage.FLAG_REDRAW);
                }

                IscriptExecutor.applySpriteDirection(this.game, sprite, unit.getHeading());
                unit.setPrimaryOrderState(UNBURROWING_STATE_FINISHED);
            }
        } else if (unit.getPrimaryOrderState() == UNBURROWING_STATE_FINISHED) {
            if (unit.getFirstOrder() == null) {
                this.game.getOrders().queueReplacingSameOrders(unit, units.getReturnToIdleOrder(unit.getUnitId()), null, 0, 0, ScrUnitIds.NONE);
            }

            this.game.getOrders().completeOrder(unit, instructionLimit);
        }
    }

    /**
     * Burrows and cloaks the Spider Mine, marking it undetected until the next detection update.
     *
     * @param unit the Spider Mine
     * @throws IllegalStateException if the unit has no sprite
     */
    public void burrowSpiderMine(@NotNull Unit unit) {
        this.setBurrowed(unit);
        this.game.getUnitCloaking().setCloaked(unit);
        this.game.getUnitCloaking().setSpriteCloakModifier(unit.requireSprite(), true, true, true, 0);
        unit.setStatus(unit.getStatus() | (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED));
        unit.setDetectionFlags(Unit.DETECTION_FLAGS_UNDETECTED);
        unit.setVisibilityUpdateCountdown(0);
        ScrOrderSystem.setSecondaryOrder(unit, ScrOrderIds.CLOAK);
    }

    /**
     * Resets movement, clears the burrow flags and restores the unit's own sprite type.
     *
     * @param unit the Spider Mine
     * @throws NullPointerException  if the world was built without game data
     * @throws IllegalStateException if the unit has no sprite
     */
    public void leaveBurrow(@NotNull Unit unit) {
        this.game.getUnitInitializer().resetMovementState(unit);
        unit.setStatus(unit.getStatus() & ~UnitStatusFlags.PASSIVELY_CLOAKED & ~UnitStatusFlags.BURROWED);

        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int spriteType = this.game.requireGameData().getFlingyTypes().getSprite(units.getFlingyType(unit.getUnitId()));

        this.game.getSelectionImages().changeSpriteType(unit.requireSprite(), spriteType);
    }

    /**
     * Sets a unit burrowed: resets its movement, marks it burrowed and passively cloaked, and lowers its sprite to the burrowed elevation with the
     * burrowed selection sprite (the Lurker's own).
     *
     * @param unit the unit that burrows
     * @throws IllegalStateException if the unit has no sprite
     */
    public void setBurrowed(@NotNull Unit unit) {
        this.game.getUnitInitializer().resetMovementState(unit);
        unit.setStatus(unit.getStatus() | UnitStatusFlags.BURROWED | UnitStatusFlags.PASSIVELY_CLOAKED);

        Sprite sprite = unit.requireSprite();
        int spriteType = this.game.getBaseType(unit) == ScrUnitIds.ZERG_LURKER ? SpriteIds.WHITE_CIRCLE2 : SpriteIds.WHITE_CIRCLE_INVISIBLE;

        sprite.setFlags(sprite.getFlags() | Sprite.FLAG_BURROWED);
        sprite.setElevationLevel(BURROWED_ELEVATION_LEVEL);
        this.game.getSelectionImages().changeSpriteType(sprite, spriteType);
    }
}
