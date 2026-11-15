package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrWeaponTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponBehaviors;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerCombat;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerOrders;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.order.UnitOrderEligibility;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.RegionPathSearch;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Executes attack orders, target acquisition, weapon cooldowns and script weapon launches.
 * <p>
 * The main order timer occupies the low cooldown byte; ground and air cooldowns occupy the next two bytes. Units with computer records delegate
 * target choice to {@link ComputerCombat}.
 */
public final class UnitAttack {
    /**
     * The main order timer reload between two periodic target searches.
     */
    private static final int TARGET_SEARCH_DELAY = 15;

    /**
     * The order state of an order in its first update, before its one-time setup.
     */
    private static final int ORDER_STATE_START = 0;
    /**
     * The order state of an order after its one-time setup (moving, holding or in range).
     */
    private static final int ORDER_STATE_RUNNING = 1;
    /**
     * The order state of a suicide order whose detonation animation runs.
     */
    private static final int SUICIDE_STATE_DETONATING = 2;
    /**
     * The range in pixels within which an Infested Terran stops at its target.
     */
    private static final int SUICIDE_CONTACT_RANGE = 4;
    /**
     * The range in pixels within which an Infested Terran moves ahead of a moving target.
     */
    private static final int SUICIDE_AHEAD_RANGE = 256;

    /**
     * The {@code attackwith} operand of the ground weapon.
     */
    private static final int GROUND_WEAPON_NUMBER = 1;
    /**
     * The {@code attackwith} operand of the air weapon.
     */
    private static final int AIR_WEAPON_NUMBER = 2;
    /**
     * The {@code weapons.dat} damage factor of a weapon that launches two bullets per attack.
     */
    private static final int TWO_BULLET_DAMAGE_FACTOR = 2;
    /**
     * The forward offset operand that selects the weapon's own forward offset.
     */
    private static final int WEAPON_FORWARD_OFFSET = -1;

    /**
     * The bullet appearance box is the target box shrunk on each side by its size divided by this.
     */
    private static final int APPEARANCE_MARGIN_DIVISOR = 4;
    /**
     * The outcode of a point left of the bullet appearance box.
     */
    private static final int CLIP_LEFT = 1;
    /**
     * The outcode of a point right of the bullet appearance box.
     */
    private static final int CLIP_RIGHT = 2;
    /**
     * The outcode of a point below the bullet appearance box.
     */
    private static final int CLIP_BELOW = 4;
    /**
     * The outcode of a point above the bullet appearance box.
     */
    private static final int CLIP_ABOVE = 8;

    /**
     * The random call site of the cooldown variation of a weapon attack.
     */
    private static final int FIRE_RANDOM_SOURCE = 12;
    /**
     * The random call site of the cooldown variation of an attack without movement.
     */
    private static final int ATTACK_SOMETHING_RANDOM_SOURCE = 13;
    /**
     * The mask of the random cooldown variation, which then is lowered by one (from {@code -1} through {@code 2}).
     */
    private static final int COOLDOWN_VARIATION_MASK = 3;
    /**
     * The cooldown timers without the ground and air weapon cooldown bytes.
     */
    private static final int WEAPON_COOLDOWNS_CLEARED = ~(Unit.GROUND_COOLDOWN_MASK | Unit.AIR_COOLDOWN_MASK);

    /**
     * Each acid spore adds the cooldown divided by this, but at least {@link #MINIMUM_ACID_SPORE_COOLDOWN}.
     */
    private static final int ACID_SPORE_COOLDOWN_DIVISOR = 8;
    /**
     * The least cooldown each acid spore adds.
     */
    private static final int MINIMUM_ACID_SPORE_COOLDOWN = 3;
    /**
     * A unit with a net speed increase divides the cooldown by this.
     */
    private static final int INCREASED_SPEED_COOLDOWN_DIVISOR = 2;
    /**
     * A slowed unit adds the cooldown divided by this.
     */
    private static final int SLOWED_COOLDOWN_DIVISOR = 4;
    /**
     * The lower bound of a modified weapon cooldown.
     */
    private static final int MINIMUM_COOLDOWN = 5;
    /**
     * The upper bound of a modified weapon cooldown.
     */
    private static final int MAXIMUM_COOLDOWN = 250;

    /**
     * The range in pixels within which a unit follows a target that it does not see.
     */
    private static final int FOLLOW_UNSEEN_RANGE = 448;
    /**
     * A unit following ahead of a moving target aims this many of its halt distances ahead of it.
     */
    private static final int FOLLOW_AHEAD_HALT_DISTANCES = 3;
    /**
     * The {@code flingy.dat} entry {@code 0} (Scourge): a ground weapon whose bullet uses it makes its attacker follow ahead.
     */
    private static final int SCOURGE_FLINGY = 0;

    /**
     * The attack priority of the most preferred targets ({@link UnitTargeting#getAttackPriority}).
     */
    private static final int TOP_PRIORITY = 0;
    /**
     * The attack priority a {@link #TOP_PRIORITY} target takes while it is out of weapon movement range.
     */
    private static final int OUT_OF_RANGE_TOP_PRIORITY = 1;

    /**
     * The fraction bits of a fixed-point position or distance.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    /**
     * The shift of the vertical coordinate in a packed position or direction vector.
     */
    private static final int PACKED_COORDINATE_SHIFT = Integer.SIZE;
    /**
     * The mask of an unsigned byte value.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The mask of the horizontal coordinate DWORD in a packed position.
     */
    private static final long DWORD_MASK = 0xFFFFFFFFL;
    /**
     * The divisor of a box size that gives its center offset.
     */
    private static final int CENTER_POINT_DIVISOR = 2;

    private static int getClipFlags(int positionX, int positionY, int left, int upper, int right, int lower) {
        int flags = 0;

        if (positionY < upper) {
            flags |= CLIP_ABOVE;
        }

        if (positionY > lower) {
            flags |= CLIP_BELOW;
        }

        if (positionX > right) {
            flags |= CLIP_RIGHT;
        }

        if (positionX < left) {
            flags |= CLIP_LEFT;
        }

        return flags;
    }

    /**
     * Sets the order target position to the position of the target's sprite.
     *
     * @param unit   the unit whose order target position is set
     * @param target the order target
     * @throws IllegalStateException if the target has no sprite
     */
    private static void setOrderTargetPosition(@NotNull Unit unit, @NotNull Unit target) {
        Sprite targetSprite = target.requireSprite();

        unit.setOrderTargetX(targetSprite.getX());
        unit.setOrderTargetY(targetSprite.getY());
    }

    /**
     * Lowers the primary order countdown to at most one frame less than the remaining weapon cooldown.
     *
     * @param unit     the attacking unit
     * @param cooldown the remaining nonzero weapon cooldown
     */
    private static void waitForWeaponCooldown(@NotNull Unit unit, int cooldown) {
        if (unit.getPrimaryOrderCountdown() > cooldown - 1) {
            unit.setPrimaryOrderCountdown(cooldown - 1);
        }
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitAttack} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitAttack(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Checks the script target range: the distance from the unit's exact position to the bullet appearance point.
     * <p>
     * A missing order target returns before the appearance-position data and sprite reads.
     *
     * @param unit  the script unit
     * @param range the inclusive range in pixels
     * @return {@code true} if the unit has an order target within the range, {@code false} otherwise
     */
    public boolean isTargetWithinScriptRange(@NotNull Unit unit, int range) {
        Unit target = unit.getOrderTargetUnit();

        if (target == null) {
            return false;
        }

        long position = this.getTargetAppearancePosition(unit, target);
        int horizontal = ((int) position << FIXED_POINT_FRACTION_BITS) - unit.getFixedPositionX();
        int vertical = ((int) (position >>> PACKED_COORDINATE_SHIFT) << FIXED_POINT_FRACTION_BITS) - unit.getFixedPositionY();

        return RegionPathSearch.calculateDistance(0, horizontal, 0, vertical) >> FIXED_POINT_FRACTION_BITS <= range;
    }

    /**
     * Returns the point where a bullet appearing at the target unit appears: the line from the center of the target's shrunk box to the unit is
     * clipped against that box (outcode clipping), and the point nearest the unit is packed as the horizontal coordinate in the low and the vertical
     * coordinate in the high DWORD.
     *
     * @param unit   the unit the bullet comes from
     * @param target the target unit
     * @return the packed appearance point
     * @throws IllegalStateException if the target or the unit has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    private long getTargetAppearancePosition(@NotNull Unit unit, @NotNull Unit target) {
        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        Sprite targetSprite = target.requireSprite();
        int targetType = target.getUnitId();
        int left = targetSprite.getX() - unitTypes.getDimensionLeft(targetType);
        int upper = targetSprite.getY() - unitTypes.getDimensionUp(targetType);
        int right = targetSprite.getX() + unitTypes.getDimensionRight(targetType);
        int lower = targetSprite.getY() + unitTypes.getDimensionDown(targetType);
        int marginWidth = (right - left) / APPEARANCE_MARGIN_DIVISOR;
        int marginHeight = (lower - upper) / APPEARANCE_MARGIN_DIVISOR;

        left += marginWidth;
        upper += marginHeight;
        right -= marginWidth;
        lower -= marginHeight;

        int centerX = left + (right - left) / CENTER_POINT_DIVISOR;
        int centerY = upper + (lower - upper) / CENTER_POINT_DIVISOR;
        Sprite sprite = unit.requireSprite();
        int fromX = centerX;
        int fromY = centerY;
        int toX = sprite.getX();
        int toY = sprite.getY();
        int fromFlags = getClipFlags(fromX, fromY, left, upper, right, lower);
        int toFlags = getClipFlags(toX, toY, left, upper, right, lower);

        while (true) {
            if ((fromFlags | toFlags) == 0) {
                return toX & DWORD_MASK | (long) toY << PACKED_COORDINATE_SHIFT;
            }

            if ((fromFlags & toFlags) != 0) {
                return centerX & DWORD_MASK | (long) centerY << PACKED_COORDINATE_SHIFT;
            }

            int flags = fromFlags != 0 ? fromFlags : toFlags;
            int clippedX;
            int clippedY;

            if ((flags & CLIP_ABOVE) != 0) {
                clippedX = fromX + (upper - fromY) * (toX - fromX) / (toY - fromY);
                clippedY = upper;
            } else if ((flags & CLIP_BELOW) != 0) {
                clippedX = fromX + (lower - fromY) * (toX - fromX) / (toY - fromY);
                clippedY = lower;
            } else if ((flags & CLIP_RIGHT) != 0) {
                clippedX = right;
                clippedY = fromY + (right - fromX) * (toY - fromY) / (toX - fromX);
            } else {
                clippedX = left;
                clippedY = fromY + (left - fromX) * (toY - fromY) / (toX - fromX);
            }

            if (fromFlags != 0) {
                fromX     = clippedX;
                fromY     = clippedY;
                fromFlags = getClipFlags(fromX, fromY, left, upper, right, lower);
            } else {
                toX     = clippedX;
                toY     = clippedY;
                toFlags = getClipFlags(toX, toY, left, upper, right, lower);
            }
        }
    }

    /**
     * Checks weapon range, position and facing before an attack starts.
     * <p>
     * Both weapon ranges are compared unsigned, so a range of {@code -1} reaches every target.
     * <p>
     * A unit already starting an attack returns before range, data and facing queries.
     *
     * @param unit   the attacking unit
     * @param weapon the weapon
     * @return {@code true} if the weapon can fire, {@code false} otherwise
     * @throws IllegalStateException if a unit in weapon range is away from its direction target and has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    public boolean canFireWeapon(@NotNull Unit unit, int weapon) {
        if ((unit.getMovementFlags() & MovementFlags.STARTING_ATTACK) != 0) {
            return false;
        }

        Unit target = unit.getOrderTargetUnit();
        Unit mainUnit = this.game.getUnitTargeting().getMainUnit(unit);
        int distance = target != null
                       ? this.game.getUnitTargeting().getDistance(mainUnit, target)
                       : this.game.getUnitTargeting().getDistanceTo(mainUnit, (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY());
        int minimumRange = this.game.requireGameData().getWeaponTypes().getMinimumRange(weapon);

        if (minimumRange != 0 && Integer.compareUnsigned(distance, minimumRange) < 0) {
            return false;
        }

        if (Integer.compareUnsigned(distance, this.game.getUnitTargeting().getWeaponMaximumRange(unit, weapon)) > 0) {
            return false;
        }

        if ((unit.getPathCollisionFlags() & UnitInitializer.ILLEGAL_POSITION_PATH_FLAG) != 0) {
            return false;
        }

        if (unit.getPositionX() != unit.getDirectionTargetX() || unit.getPositionY() != unit.getDirectionTargetY()) {
            Sprite sprite = unit.requireSprite();
            int direction = FlingyMotionUtils.calculateDirection(unit.getDirectionTargetX() - sprite.getX(),
                                                                 unit.getDirectionTargetY() - sprite.getY());

            if (this.game.getBaseType(unit) == ScrUnitIds.ZERG_LURKER) {
                unit.setHeading(direction);
            } else if (Math.abs((byte) (direction - unit.getHeading())) > this.game.requireGameData().getWeaponTypes().getAttackAngle(weapon)) {
                unit.setPrimaryOrderCountdown(0);

                return false;
            }
        }

        return true;
    }

    /**
     * Computes the weapon cooldown with acid spores and speed modifiers, clamped to {@code 5} through {@code 250}.
     *
     * @param unit   the attacking unit
     * @param weapon the weapon
     * @return the modified cooldown in frames
     * @throws NullPointerException if the world was built without game data
     */
    public int getModifiedCooldown(@NotNull Unit unit, int weapon) {
        int cooldown = this.game.requireGameData().getWeaponTypes().getCooldown(weapon);

        if (unit.getAcidSporeCount() != 0) {
            cooldown += Math.max(cooldown / ACID_SPORE_COOLDOWN_DIVISOR, MINIMUM_ACID_SPORE_COOLDOWN) * unit.getAcidSporeCount();
        }

        int modifier = 0;

        if (unit.getSpeedIncreaseByte() != 0) {
            modifier++;
        }

        if ((unit.getStatus() & UnitStatusFlags.COOLDOWN_UPGRADE) != 0) {
            modifier++;
        }

        if (unit.getSpeedDecreaseByte() != 0) {
            modifier--;
        }

        if (modifier > 0) {
            cooldown /= INCREASED_SPEED_COOLDOWN_DIVISOR;
        }

        if (modifier < 0) {
            cooldown += cooldown / SLOWED_COOLDOWN_DIVISOR;
        }

        return Math.max(MINIMUM_COOLDOWN, Math.min(MAXIMUM_COOLDOWN, cooldown));
    }


    /**
     * Updates the attack order by reacquiring a target, moving into range and firing.
     * <p>
     * A computer player's unit not attacking a building first returns a guard to its post, and a unit with a computer record may use a stim pack
     * before firing.
     * <p>
     * Only an admitted computer Marine or Firebat reaches the stim operation after target reacquisition and movement-range handling. Its random draws
     * and sound precede damage.
     *
     * @param unit             the attacking unit
     * @param instructionLimit the animation and order instruction budget
     * @throws NullPointerException if the world was built without game data
     */
    public void updateAttackUnit(@NotNull Unit unit, int instructionLimit) {
        Player owner = this.game.getPlayerTable().getPlayer(unit.getOwnerIndex());
        Unit orderTarget = unit.getOrderTargetUnit();

        if (owner != null && owner.getType() == PlayerTypes.COMPUTER &&
            (orderTarget == null || !UnitQueries.isBuilding(this.game.requireGameData().getUnitTypes(), orderTarget.getUnitId())) &&
            this.game.getComputerCombat().returnToGuardPost(unit, instructionLimit)) {
            return;
        }

        this.reacquireTarget(unit);

        if (this.moveInRange(unit, instructionLimit)) {
            if (unit.getComputerRecord() != null) {
                this.game.getComputerCombat().useStimPack(unit, instructionLimit);
            }

            this.fireWeapon(unit, instructionLimit);
        }
    }

    /**
     * Moves toward the order position and periodically acquires an attack target.
     *
     * @param unit             the attack-moving unit
     * @param instructionLimit the animation and order instruction budget
     */
    public void updateAttackMove(@NotNull Unit unit, int instructionLimit) {
        if (!this.moveAndAttackAutomaticTarget(unit, true, instructionLimit)) {
            return;
        }

        if (unit.getOrderTimer() != 0) {
            return;
        }

        unit.setOrderTimer(TARGET_SEARCH_DELAY);

        if (!this.game.getEngineGame().getMovement().isAtDestination(unit)) {
            this.acquireAttackMoveTarget(unit, true, instructionLimit);
        } else {
            this.game.getOrders().finishOrder(unit, instructionLimit);
        }
    }

    /**
     * Updates guard retaliation and periodic target acquisition.
     * <p>
     * Only a unit without a computer record retaliates against its automatic target here.
     *
     * @param unit             the guarding unit
     * @param instructionLimit the animation and order instruction budget
     * @throws IllegalStateException if a guarding turret lacks its parent
     */
    public void updatePlayerGuard(@NotNull Unit unit, int instructionLimit) {
        if (unit.getComputerRecord() == null && this.attackAutomaticTarget(unit, instructionLimit)) {
            return;
        }

        if (unit.getOrderTimer() != 0) {
            return;
        }

        unit.setOrderTimer(TARGET_SEARCH_DELAY);

        if (this.game.getUnitTargeting().isTurret(unit)) {
            Unit parent = unit.getSubunit();

            if (parent == null) {
                throw Exceptions.newIllegalStateException("A guarding turret has no parent unit.");
            }

            unit.setDirectionTargetX(parent.getDirectionTargetX());
            unit.setDirectionTargetY(parent.getDirectionTargetY());
        }

        if (this.game.getUnitOrders().getTargetAcquisitionRange(unit) == 0) {
            return;
        }

        Unit target = this.game.getUnitTargeting().findAcquireTarget(unit);

        if (target != null) {
            this.attackTarget(unit, target, instructionLimit);
        }
    }

    /**
     * Acquires a target and attacks while it remains in weapon movement range.
     * <p>
     * A unit with a computer record takes the nearest acquirable target instead of reacquiring, and attack-moves to the target position when it is
     * out of range.
     *
     * @param unit             the attacking unit
     * @param instructionLimit the animation and order instruction budget
     */
    public void updateAttackFixedRange(@NotNull Unit unit, int instructionLimit) {
        boolean computer = unit.getComputerRecord() != null;

        if (computer) {
            unit.setOrderTargetUnit(this.game.getUnitTargeting().findAcquireTarget(unit));
        } else {
            this.reacquireTarget(unit);
        }

        Unit target = unit.getOrderTargetUnit();

        if (target != null) {
            setOrderTargetPosition(unit, target);
        }

        if (!this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            this.game.getOrders().finishOrder(unit, instructionLimit);

            return;
        }

        if (!this.game.getUnitTargeting().isInWeaponMovementRange(unit, target)) {
            if (computer) {
                this.game.getOrders().issue(unit,
                                            ScrOrderIds.ATTACK_MOVE,
                                            null,
                                            (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY(),
                                            ScrUnitIds.NONE,
                                            false,
                                            instructionLimit);
            } else {
                this.game.getOrders().finishOrder(unit, instructionLimit);
            }

            return;
        }

        unit.setDirectionTargetX(unit.getOrderTargetX());
        unit.setDirectionTargetY(unit.getOrderTargetY());
        this.fireWeapon(unit, instructionLimit);
    }

    /**
     * Periodically acquires a target and issues a tower attack.
     * <p>
     * Disabled or waiting towers return before issuing an order.
     *
     * @param unit             the guarding tower
     * @param instructionLimit the animation and order instruction budget
     */
    public void updateTowerGuard(@NotNull Unit unit, int instructionLimit) {
        if (UnitOrderEligibility.isDisabled(unit) || unit.getOrderTimer() != 0) {
            return;
        }

        unit.setOrderTimer(TARGET_SEARCH_DELAY);

        Unit target = this.game.getUnitTargeting().findAcquireRandomTarget(unit);

        unit.setOrderTargetUnit(target);

        if (target == null) {
            return;
        }

        this.game.getOrders().issue(unit, ScrOrderIds.TOWER_ATTACK, target, 0, 0, ScrUnitIds.NONE, false, instructionLimit);
        unit.markReadyToAttack();
    }

    /**
     * Attacks the current target and updates the direction target.
     *
     * @param unit             the attacking tower
     * @param instructionLimit the animation and order instruction budget
     * @throws IllegalStateException if an enabled tower attacks and has no sprite
     */
    public void updateTowerAttack(@NotNull Unit unit, int instructionLimit) {
        if (UnitOrderEligibility.isDisabled(unit) || !this.tryAttackSomething(unit, instructionLimit)) {
            this.game.getOrders().finishOrder(unit, instructionLimit);

            return;
        }

        Sprite sprite = unit.requireSprite();

        this.game.getEngineGame().getMovement().setDestination(unit, sprite.getX(), sprite.getY());
        unit.setDirectionTargetX(unit.getOrderTargetX());
        unit.setDirectionTargetY(unit.getOrderTargetY());
    }

    /**
     * Holds position while attacking reachable or periodically acquired targets.
     *
     * @param unit             the holding unit
     * @param instructionLimit the animation and order instruction budget
     */
    public void updateHoldPosition(@NotNull Unit unit, int instructionLimit) {
        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            this.game.getUnitPointMovement().stopAndFaceDestination(unit);
            unit.markReadyToAttack();
            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);
        }

        if (this.tryAttackSomething(unit, instructionLimit)) {
            Unit turret = unit.getSubunit();

            if (turret == null || !this.game.getUnitTargeting().isTurret(turret) ||
                unit.getUnitId() == ScrUnitIds.TERRAN_GOLIATH || unit.getUnitId() == ScrUnitIds.HERO_ALAN_SCHEZAR) {
                unit.setDirectionTargetX(unit.getOrderTargetX());
                unit.setDirectionTargetY(unit.getOrderTargetY());
            }
        } else if (unit.getOrderTimer() == 0) {
            unit.setOrderTimer(TARGET_SEARCH_DELAY);

            Unit target = this.game.getUnitTargeting().findAcquireRandomTarget(unit);

            unit.setOrderTargetUnit(target);

            if (target != null) {
                unit.setPrimaryOrderCountdown(0);
            }
        }
    }

    /**
     * Reaches the target and detonates within the Infested Terran splash radius.
     * <p>
     * The finishing path marks the lifetime expired before requesting death.
     *
     * @param unit             the Infested Terran
     * @param instructionLimit the order and script budget
     * @throws IllegalStateException if the target cannot be attacked and has no sprite
     */
    public void updateSuicideUnit(@NotNull Unit unit, int instructionLimit) {
        Unit target = unit.getOrderTargetUnit();

        if (target == null) {
            this.game.getOrders().finishOrder(unit, instructionLimit);

            return;
        }

        if (!this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            Sprite targetSprite = target.requireSprite();

            this.game.getOrders().issue(unit,
                                        ScrOrderIds.MOVE,
                                        null,
                                        targetSprite.getX(), targetSprite.getY(),
                                        ScrUnitIds.NONE,
                                        false,
                                        instructionLimit);

            return;
        }

        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            if (!this.game.getUnitTargetMovement().moveToTarget(unit, target)) {
                return;
            }

            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);
        }

        if (unit.getPrimaryOrderState() == ORDER_STATE_RUNNING) {
            if (this.game.getEngineGame().getMovement().isAtDestination(unit) && (unit.getStatus() & UnitStatusFlags.IMMOVABLE) != 0 ||
                this.game.getUnitTargeting().isInRange(unit, target, SUICIDE_CONTACT_RANGE)) {
                this.game.getEngineGame().getMovement().stop(unit);

                if (!this.game.getUnitTargeting().isInRange(unit, target, this.getSuicideSplashRadius())) {
                    this.game.getOrders().finishOrder(unit, instructionLimit);

                    return;
                }

                this.startSuicide(unit, instructionLimit);
            } else if (this.game.getUnitTargeting().isInRange(unit, target, SUICIDE_AHEAD_RANGE) &&
                       (target.getMovementFlags() & MovementFlags.ACCELERATING) != 0) {
                this.game.getUnitHangar().moveAheadOfTarget(unit, target);
            } else {
                this.tryFollowTarget(unit, target, instructionLimit);
            }
        }

        this.finishSuicide(unit, instructionLimit);
    }

    /**
     * Moves to the order position and detonates.
     * <p>
     * The finishing path marks the lifetime expired before requesting death.
     *
     * @param unit             the Infested Terran
     * @param instructionLimit the order and script budget
     */
    public void updateSuicideLocation(@NotNull Unit unit, int instructionLimit) {
        int targetX = (short) unit.getOrderTargetX();
        int targetY = (short) unit.getOrderTargetY();

        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            this.game.getEngineGame().getMovement().setDestination(unit, targetX, targetY);
            unit.setDirectionTargetX(targetX);
            unit.setDirectionTargetY(targetY);
            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);
        }

        if (unit.getPrimaryOrderState() == ORDER_STATE_RUNNING && this.game.getEngineGame().getMovement().isAtDestination(unit)) {
            int horizontal = (targetX << FIXED_POINT_FRACTION_BITS) - unit.getFixedPositionX();
            int vertical = (targetY << FIXED_POINT_FRACTION_BITS) - unit.getFixedPositionY();

            if ((unit.getStatus() & UnitStatusFlags.IMMOVABLE) != 0 &&
                RegionPathSearch.calculateDistance(0, horizontal, 0, vertical) >> FIXED_POINT_FRACTION_BITS > this.getSuicideSplashRadius()) {
                this.game.getOrders().finishOrder(unit, instructionLimit);

                return;
            }

            this.startSuicide(unit, instructionLimit);
        }

        this.finishSuicide(unit, instructionLimit);
    }

    /**
     * Returns the outer splash radius of the Infested Terran suicide weapon.
     *
     * @return the outer splash radius in pixels
     * @throws NullPointerException if the world was built without game data
     */
    private int getSuicideSplashRadius() {
        return this.game.requireGameData().getWeaponTypes().getOuterSplashRadius(ScrWeaponIds.SUICIDE_INFESTED_TERRAN);
    }

    /**
     * Starts the detonation: the Infested Terran targets itself, runs the SpecialState1 animation, becomes uninterruptible and enters the
     * detonating state.
     *
     * @param unit             the Infested Terran
     * @param instructionLimit the script budget
     * @throws IllegalStateException if the unit has no sprite
     */
    private void startSuicide(@NotNull Unit unit, int instructionLimit) {
        unit.setOrderTargetUnit(unit);
        this.game.getIscriptExecutor().runSpriteAnimation(unit.requireSprite(), unit, IscriptExecutor.ANIMATION_SPECIAL_STATE_1, 0, instructionLimit);
        unit.setStatus(unit.getStatus() | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);
        unit.setPrimaryOrderState(SUICIDE_STATE_DETONATING);
    }

    private void finishSuicide(@NotNull Unit unit, int instructionLimit) {
        if (unit.getPrimaryOrderState() == SUICIDE_STATE_DETONATING && (unit.getOrderSignal() & Unit.ORDER_SIGNAL_ACTION_DONE) != 0) {
            unit.setStatus(unit.getStatus() | UnitStatusFlags.LIFETIME_EXPIRED);
            this.game.getUnitLifecycle().kill(unit, instructionLimit);
        }
    }

    /**
     * Stops once and waits for a queued order.
     * <p>
     * An empty pending queue returns without activating an order.
     *
     * @param unit             the Scourge or Infested Terran
     * @param instructionLimit the order activation budget
     */
    public void updateSuicideHoldPosition(@NotNull Unit unit, int instructionLimit) {
        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            this.game.getUnitPointMovement().stopAndFaceDestination(unit);
            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);
        }

        if (unit.getFirstOrder() != null) {
            this.game.getOrders().activateNext(unit, instructionLimit);
        }
    }

    /**
     * Keeps the target within weapon movement range without attacking.
     * <p>
     * A unit with a computer record does not queue the Move to an unattackable target, and an order whose first move starts a computer transport
     * pickup stays in its start state.
     *
     * @param unit             the following unit
     * @param instructionLimit the order activation instruction budget
     * @throws IllegalStateException if a Goliath or Alan Schezar that is not accelerating has no sprite
     */
    public void updateStayInRange(@NotNull Unit unit, int instructionLimit) {
        Unit target = unit.getOrderTargetUnit();

        if (target == null) {
            this.game.getUnitPointMovement().stopAndFaceDestination(unit);
            this.game.getOrders().finishOrder(unit, instructionLimit);

            return;
        }

        setOrderTargetPosition(unit, target);

        if (!this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            if ((unit.getStatus() & UnitStatusFlags.READY_TO_ATTACK) == 0 && unit.getComputerRecord() == null) {
                this.game.getOrders().queueFront(unit,
                                                 ScrOrderIds.MOVE,
                                                 null,
                                                 (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY(),
                                                 ScrUnitIds.NONE);
            }

            this.game.getOrders().finishOrder(unit, instructionLimit);

            return;
        }

        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            if (!this.game.getUnitTargeting().isInWeaponMovementRange(unit, target) &&
                !this.game.getUnitTargetMovement().moveToTarget(unit, target)) {
                return;
            }

            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);
        } else if (!this.game.getUnitTargeting().isInWeaponMovementRange(unit, target)) {
            if (!this.tryFollowTarget(unit, target, instructionLimit)) {
                return;
            }
        } else {
            this.game.getUnitPointMovement().stopAndFaceDestination(unit);
        }

        if ((unit.getUnitId() == ScrUnitIds.TERRAN_GOLIATH || unit.getUnitId() == ScrUnitIds.HERO_ALAN_SCHEZAR) &&
            (unit.getMovementFlags() & MovementFlags.ACCELERATING) == 0) {
            Sprite sprite = unit.requireSprite();
            int direction = FlingyMotionUtils.calculateDirection((short) unit.getOrderTargetX() - sprite.getX(),
                                                                 (short) unit.getOrderTargetY() - sprite.getY());

            if (Math.abs((byte) (direction - unit.getHeading())) >= FlingyMotionUtils.DIRECTION_EIGHTH_TURN) {
                unit.setDirectionTargetX(unit.getOrderTargetX());
                unit.setDirectionTargetY(unit.getOrderTargetY());
            }
        }
    }

    /**
     * Moves to the position, resolves a visible unit of the requested type and queues its target order.
     * <p>
     * On a target path, a turret's queued and activated order precedes the main unit's order.
     *
     * @param unit             the moving unit
     * @param instructionLimit the animation and order instruction budget
     * @throws IllegalStateException if the resolved target has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    public void updateMoveToTargetOrder(@NotNull Unit unit, int instructionLimit) {
        int positionX = (short) unit.getOrderTargetX();
        int positionY = (short) unit.getOrderTargetY();

        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            if ((unit.getStatus() & UnitStatusFlags.CAN_MOVE) != 0) {
                this.game.getEngineGame().getMovement().setDestination(unit, positionX, positionY);
                unit.setDirectionTargetX(positionX);
                unit.setDirectionTargetY(positionY);
            }

            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);

            return;
        }

        if (!this.game.getUnitTargeting().isPositionVisible(unit.getOwnerIndex(), positionX, positionY)) {
            return;
        }

        Unit target = this.game.getUnitTargeting().findUnitOfTypeAt(unit.getOrderTargetUnitType(), positionX, positionY);

        if (target != null) {
            ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
            int order = this.game.requireGameData().getOrderTypes().getObscuredOrder(unit.getPrimaryOrder());
            Sprite targetSprite = target.requireSprite();

            if (order == ScrOrderIds.ATTACK_DEFAULT) {
                order = unitTypes.getAttackUnitOrder(unit.getUnitId());

                Unit turret = unit.getSubunit();

                if (turret != null && this.game.getUnitTargeting().isTurret(turret)) {
                    this.game.getOrders().queueFront(turret,
                                                     unitTypes.getAttackUnitOrder(turret.getUnitId()),
                                                     target,
                                                     targetSprite.getX(), targetSprite.getY(),
                                                     ScrUnitIds.NONE);
                    this.game.getOrders().finishOrder(turret, instructionLimit);
                }
            }

            this.game.getOrders().queueFront(unit, order, target, targetSprite.getX(), targetSprite.getY(), ScrUnitIds.NONE);
        }

        this.game.getOrders().finishOrder(unit, instructionLimit);
    }

    /**
     * Queues the return leg, moves without stopping and acquires attack targets.
     * <p>
     * Startup records user action before common replacement and activation.
     *
     * @param unit             the patrolling unit
     * @param instructionLimit the animation and order instruction budget
     * @throws IllegalStateException if the unit or a Medic heal target it finds has no sprite
     */
    public void updatePatrol(@NotNull Unit unit, int instructionLimit) {
        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            OrderPool.Order next = unit.getFirstOrder();

            if (next == null || next.getOrderId() != ScrOrderIds.PATROL) {
                unit.setUserActionFlags(unit.getUserActionFlags() | Unit.USER_ACTION_ORDER_DONE);
                this.game.getOrders().queueReplacingOrders(unit,
                                                           ScrOrderIds.PATROL,
                                                           unit.getOrderTargetUnit(),
                                                           (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY(),
                                                           ScrUnitIds.NONE,
                                                           true);
                this.game.getOrders().activateNext(unit, instructionLimit);

                Sprite sprite = unit.requireSprite();

                this.game.getOrders().queueBack(unit, ScrOrderIds.PATROL, null, sprite.getX(), sprite.getY(), ScrUnitIds.NONE);

                return;
            }
        }

        if (!this.moveAndAttackAutomaticTarget(unit, false, instructionLimit)) {
            return;
        }

        if (unit.getOrderTimer() != 0) {
            return;
        }

        unit.setOrderTimer(TARGET_SEARCH_DELAY);

        if (this.game.getBaseType(unit) == ScrUnitIds.TERRAN_MEDIC && unit.getOrderTargetUnit() == null) {
            Unit target = this.game.getUnitMedic().findTarget(unit);

            if (target != null) {
                this.game.getOrders().queueFront(unit,
                                                 ScrOrderIds.PATROL,
                                                 null,
                                                 (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY(),
                                                 ScrUnitIds.NONE);

                Sprite targetSprite = target.requireSprite();

                this.game.getOrders().queueFront(unit,
                                                 ScrOrderIds.MEDIC_HEAL,
                                                 target,
                                                 targetSprite.getX(), targetSprite.getY(),
                                                 ScrUnitIds.NONE);
                unit.setAutomaticTargetUnit(null);
                this.game.getOrders().finishOrder(unit, instructionLimit);
                unit.markReadyToAttack();

                return;
            }
        }

        if (!this.game.getEngineGame().getMovement().isAtDestination(unit)) {
            this.acquireAttackMoveTarget(unit, false, instructionLimit);

            return;
        }

        if ((unit.getStatus() & UnitStatusFlags.IMMOVABLE) != 0) {
            this.game.getOrders().queueBack(unit,
                                            ScrOrderIds.PATROL,
                                            null,
                                            (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY(),
                                            ScrUnitIds.NONE);
        } else {
            Sprite sprite = unit.requireSprite();

            this.game.getOrders().queueBack(unit,
                                            ScrOrderIds.PATROL,
                                            null,
                                            sprite.getX(), sprite.getY(),
                                            ScrUnitIds.NONE);
        }

        this.game.getOrders().completeOrder(unit, instructionLimit);
    }


    /**
     * Launches the ground or air weapon selected by an {@code attackwith} script operand.
     *
     * @param unit             the script unit
     * @param weaponNumber     {@code 1} for the ground weapon, otherwise the air weapon
     * @param forwardOffset    the launch offset, or {@code -1} for the weapon's own offset
     * @param instructionLimit the bullet sprite instruction budget
     * @throws IllegalStateException if the unit lacks the weapon
     */
    public void attackWith(@NotNull Unit unit, int weaponNumber, int forwardOffset, int instructionLimit) {
        if (unit.getOrderTargetUnit() == null) {
            return;
        }

        int weapon = weaponNumber == GROUND_WEAPON_NUMBER
                     ? this.game.getUnitTargeting().getGroundWeapon(unit)
                     : this.game.getUnitTargeting().getAirWeapon(unit);

        if (weapon == ScrWeaponIds.NONE) {
            throw Exceptions.newIllegalStateException("attackwith {0} reached a unit without that weapon: {1}", weaponNumber, unit.getUnitId());
        }

        this.attackWithWeapon(unit, weapon, forwardOffset, instructionLimit);
    }

    /**
     * Launches the weapon matching the order target's flying state for the {@code attack} script operation.
     *
     * @param unit             the script unit
     * @param instructionLimit the bullet sprite instruction budget
     */
    public void attack(@NotNull Unit unit, int instructionLimit) {
        Unit target = unit.getOrderTargetUnit();

        if (target != null) {
            this.attackWith(unit,
                            (target.getStatus() & UnitStatusFlags.FLYING) == 0 ? GROUND_WEAPON_NUMBER : AIR_WEAPON_NUMBER,
                            WEAPON_FORWARD_OFFSET,
                            instructionLimit);
        }
    }

    /**
     * Applies the script {@code attack_with_weapon}, launching the weapon once, or twice for two-bullet weapons.
     *
     * @param unit             the script unit
     * @param weapon           the {@code weapons.dat} index
     * @param instructionLimit the bullet sprite budget
     * @throws NullPointerException if the world was built without game data
     */
    public void attackWithWeapon(@NotNull Unit unit, int weapon, int instructionLimit) {
        this.attackWithWeapon(unit, weapon, WEAPON_FORWARD_OFFSET, instructionLimit);
    }

    /**
     * Launches the weapon once, or twice for two-bullet weapons.
     *
     * @param unit             the script unit
     * @param weapon           the {@code weapons.dat} index
     * @param forwardOffset    the launch offset, or {@code -1} for the weapon's own offset
     * @param instructionLimit the bullet sprite budget
     * @throws NullPointerException if the world was built without game data
     */
    private void attackWithWeapon(@NotNull Unit unit, int weapon, int forwardOffset, int instructionLimit) {
        this.launch(unit, weapon, forwardOffset, instructionLimit);

        if (this.game.requireGameData().getWeaponTypes().getDamageFactor(weapon) == TWO_BULLET_DAMAGE_FACTOR) {
            this.launch(unit, weapon, forwardOffset, instructionLimit);
        }
    }

    /**
     * Creates one bullet of the weapon where its behavior places it: at the target appearance point, at the order target position or at the
     * forward and upward offsets from the unit; an appear-at-target-unit weapon without an order target creates none.
     *
     * @param unit             the script unit
     * @param weapon           the {@code weapons.dat} index
     * @param forwardOffset    the launch offset, or {@code -1} for the weapon's own offset
     * @param instructionLimit the bullet sprite budget
     * @throws IllegalStateException if the weapon launches from the unit and the unit has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    private void launch(@NotNull Unit unit, int weapon, int forwardOffset, int instructionLimit) {
        ScrWeaponTypeView weapons = this.game.requireGameData().getWeaponTypes();
        int behavior = weapons.getBehavior(weapon);
        int positionX;
        int positionY;

        if (behavior == WeaponBehaviors.APPEAR_AT_TARGET_UNIT) {
            Unit target = unit.getOrderTargetUnit();

            if (target == null) {
                return;
            }

            long position = this.getTargetAppearancePosition(unit, target);

            positionX = (int) position;
            positionY = (int) (position >>> PACKED_COORDINATE_SHIFT);
        } else if (behavior == WeaponBehaviors.APPEAR_AT_TARGET_POSITION) {
            positionX = (short) unit.getOrderTargetX();
            positionY = (short) unit.getOrderTargetY();
        } else {
            Sprite sprite = unit.requireSprite();
            int offset = forwardOffset == WEAPON_FORWARD_OFFSET ? (byte) weapons.getForwardOffset(weapon) : forwardOffset;
            long vector = FlingyMotionUtils.calculateDirectionVector(unit.getHeading(), offset);

            positionX = sprite.getX() + (int) vector;
            positionY = sprite.getY() + (int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) - (byte) weapons.getUpwardOffset(weapon);
        }

        this.game.getProjectilePhase().create(unit, weapon, positionX, positionY, instructionLimit);
    }


    /**
     * Attacks an attackable enemy automatic target; otherwise clears that target.
     * <p>
     * A missing automatic target returns without issuing an order.
     *
     * @param unit             the unit
     * @param instructionLimit the animation and order instruction budget
     * @return {@code true} if the unit attacks its automatic target, {@code false} otherwise
     */
    public boolean attackAutomaticTarget(@NotNull Unit unit, int instructionLimit) {
        Unit target = unit.getAutomaticTargetUnit();

        if (target == null) {
            return false;
        }

        if (this.game.getEngineGame().getCombat().isEnemy(unit, target) && this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            this.attackTarget(unit, target, instructionLimit);
            unit.setStatus(unit.getStatus() | UnitStatusFlags.AUTOMATIC_ATTACK_STARTED);

            return true;
        }

        unit.setAutomaticTargetUnit(null);

        return false;
    }

    /**
     * Issues the {@code units.dat} Attack Unit order on the target (on the Carrier of an Interceptor) to the unit and its turret and marks both ready
     * to attack; a sieged computer Siege Tank then unsieges or re-targets when the target is out of range.
     *
     * @param unit             the attacking unit
     * @param target           the target
     * @param instructionLimit the animation and order instruction budget
     * @throws NullPointerException if the world was built without game data
     */
    private void attackTarget(@NotNull Unit unit, @NotNull Unit target, int instructionLimit) {
        if (this.game.getBaseType(target) == ScrUnitIds.PROTOSS_INTERCEPTOR && target.getHangarParent() != null) {
            target = target.getHangarParent();
        }

        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();

        this.game.getOrders().issue(unit,
                                    unitTypes.getAttackUnitOrder(unit.getUnitId()),
                                    target,
                                    0, 0,
                                    ScrUnitIds.NONE,
                                    false,
                                    instructionLimit);
        unit.setStatus(unit.getStatus() | UnitStatusFlags.READY_TO_ATTACK);

        Unit subunit = unit.getSubunit();

        if (subunit != null) {
            if (this.game.getUnitTargeting().isTurret(subunit)) {
                this.game.getOrders().issue(subunit,
                                            unitTypes.getAttackUnitOrder(subunit.getUnitId()),
                                            target,
                                            0, 0,
                                            ScrUnitIds.NONE,
                                            false,
                                            instructionLimit);
            }

            subunit.setStatus(subunit.getStatus() | UnitStatusFlags.READY_TO_ATTACK);
        }

        this.game.getComputerCombat().attackOutOfSiegeRange(unit, target, 0, instructionLimit);
    }

    /**
     * Keeps the best attackable target in range or acquires a higher-priority target.
     * <p>
     * A unit with a computer record keeps a non-enemy order target without reacquiring and first chooses through the computer target choice (critters
     * allowed); order and automatic targets claimed by another computer unit are skipped.
     * <p>
     * A unit not ready to attack returns before these queries.
     *
     * @param unit the unit
     * @throws UnsupportedOperationException if a unit with a computer record has no computer-player phase
     * @throws NullPointerException          if the world was built without game data
     */
    public void reacquireTarget(@NotNull Unit unit) {
        if ((unit.getStatus() & UnitStatusFlags.READY_TO_ATTACK) == 0) {
            return;
        }

        Unit target = unit.getOrderTargetUnit();
        int targetPriority = Integer.MAX_VALUE;
        boolean computer = unit.getComputerRecord() != null;

        if (target != null) {
            if (computer && !this.game.getEngineGame().getCombat().isEnemy(unit, target)) {
                return;
            }

            if (this.game.getUnitTargeting().canAttackTarget(unit, target) && !this.isClaimedByComputer(unit, target)) {
                targetPriority = this.game.getUnitTargeting().getAttackPriority(unit, target);

                if (targetPriority == TOP_PRIORITY) {
                    if (this.game.getUnitTargeting().isInWeaponMovementRange(unit, target)) {
                        return;
                    }

                    targetPriority = OUT_OF_RANGE_TOP_PRIORITY;
                }
            } else {
                target = null;
            }
        }

        Unit automaticTarget = unit.getAutomaticTargetUnit();

        if (automaticTarget != null) {
            if (this.game.getUnitTargeting().canAttackTarget(unit, automaticTarget) &&
                this.game.getEngineGame().getCombat().isEnemy(unit, automaticTarget) &&
                !this.isClaimedByComputer(unit, automaticTarget)) {
                int automaticPriority = this.game.getUnitTargeting().getAttackPriority(unit, automaticTarget);

                if (automaticPriority == TOP_PRIORITY && !this.game.getUnitTargeting().isInWeaponMovementRange(unit, automaticTarget)) {
                    automaticPriority = OUT_OF_RANGE_TOP_PRIORITY;
                }

                if (automaticPriority < targetPriority) {
                    targetPriority = automaticPriority;
                    target         = automaticTarget;
                }
            } else {
                unit.setAutomaticTargetUnit(null);
            }
        }

        if (target == null || targetPriority != TOP_PRIORITY) {
            if (computer) {
                ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

                if (phase == null) {
                    throw Exceptions.newUnsupportedOperationException("Computer target reacquisition requires the computer-player phase.");
                }

                if (this.game.getComputerCombat().acquireTarget(unit, 0, true, false, phase.getInstructionLimit())) {
                    return;
                }
            }

            Unit acquired = this.game.getUnitTargeting().findAcquireTarget(unit);

            if (acquired != null && this.game.getUnitTargeting().getAttackPriority(unit, acquired) < targetPriority) {
                target = acquired;
            }
        }

        if (target == null || this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            unit.setOrderTargetUnit(target);

            Unit subunit = unit.getSubunit();

            if (subunit != null) {
                int subunitOrder = subunit.getPrimaryOrder();

                if (subunitOrder == this.game.requireGameData().getUnitTypes().getAttackUnitOrder(subunit.getUnitId()) ||
                    subunitOrder == ScrOrderIds.HOLD_POSITION) {
                    subunit.setOrderTargetUnit(target);
                }
            }
        }
    }

    /**
     * @param unit   the reacquiring unit
     * @param target the order or automatic target
     * @return {@code true} if the game has a computer-player phase and another computer unit claimed the target, {@code false} otherwise
     */
    private boolean isClaimedByComputer(@NotNull Unit unit, @NotNull Unit target) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        return phase != null && this.game.getComputerCombat().isClaimedElsewhere(unit, target);
    }

    /**
     * Moves into weapon range and handles lost or unreachable targets.
     * <p>
     * Only a unit without a computer record queues a move to an unattackable target; an order whose first move starts a computer transport pickup
     * stays in its start state; a unit with a computer record out of range that cannot chase the target safely or is blocked at its destination
     * chooses again before giving up, and the air reach test does not apply to it.
     *
     * @param unit             the attacking unit
     * @param instructionLimit the animation and order instruction budget
     * @return {@code true} if the unit is in range to fire, {@code false} otherwise
     * @throws IllegalStateException if the order target has no sprite while it is attackable or a unit without a computer record moves to it
     * @throws NullPointerException  if the world was built without game data
     */
    private boolean moveInRange(@NotNull Unit unit, int instructionLimit) {
        Unit target = unit.getOrderTargetUnit();

        if (!this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            if (target == null || (unit.getStatus() & UnitStatusFlags.READY_TO_ATTACK) != 0) {
                this.game.getEngineGame().getMovement().stop(unit);
                this.game.getOrders().finishOrder(unit, instructionLimit);
            } else {
                if (unit.getComputerRecord() == null) {
                    Sprite targetSprite = target.requireSprite();

                    this.game.getOrders().queueFront(unit, ScrOrderIds.MOVE, null, targetSprite.getX(), targetSprite.getY(), ScrUnitIds.NONE);
                }

                this.game.getOrders().finishOrder(unit, instructionLimit);
            }

            return false;
        }

        setOrderTargetPosition(unit, target);

        Sprite targetSprite = target.requireSprite();

        unit.setDirectionTargetX(targetSprite.getX());
        unit.setDirectionTargetY(targetSprite.getY());

        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            if (!this.game.getUnitTargeting().isInWeaponMovementRange(unit, target) &&
                !this.game.getUnitTargetMovement().moveToTarget(unit, target)) {
                return false;
            }

            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);

            return true;
        }

        if (this.game.getUnitTargeting().isOutOfMaximumRange(unit, target)) {
            boolean immovableAtTarget = this.game.getEngineGame().getMovement().isAtDestination(unit) &&
                                        (unit.getStatus() & UnitStatusFlags.IMMOVABLE) != 0;
            boolean airOutOfReach = (unit.getStatus() & UnitStatusFlags.READY_TO_ATTACK) != 0 &&
                                    (unit.getStatus() & UnitStatusFlags.AUTOMATIC_ATTACK_STARTED) == 0 &&
                                    (unit.getStatus() & UnitStatusFlags.FLYING) == 0 && (target.getStatus() & UnitStatusFlags.FLYING) != 0;

            if (unit.getComputerRecord() != null) {
                airOutOfReach = false;

                if (this.game.getComputerCombat().isChaseExcluded(unit, target) ||
                    ComputerOrders.getArrival(unit) == ComputerOrders.ARRIVAL_BLOCKED) {
                    if (this.game.getComputerCombat().acquireTarget(unit, 0, true, false, instructionLimit)) {
                        return false;
                    }

                    immovableAtTarget = true;
                } else {
                    immovableAtTarget = false;
                }
            }

            if (immovableAtTarget || airOutOfReach) {
                this.game.getUnitPointMovement().stopAndFaceDestination(unit);
                this.game.getOrders().finishOrder(unit, instructionLimit);

                return false;
            }

            int groundWeapon = this.game.requireGameData().getUnitTypes().getGroundWeapon(unit.getUnitId());

            if (this.game.getBaseType(unit) == ScrUnitIds.ZERG_SCOURGE ||
                groundWeapon != ScrWeaponIds.NONE &&
                this.game.requireGameData().getWeaponTypes().getFlingyType(groundWeapon) == SCOURGE_FLINGY) {
                this.followAhead(unit, target, instructionLimit);
            } else {
                this.tryFollowTarget(unit, target, instructionLimit);
            }

            return false;
        }

        if (this.game.getBaseType(unit) == ScrUnitIds.ZERG_SCOURGE) {
            this.followAhead(unit, target, instructionLimit);
        } else {
            this.game.getEngineGame().getMovement().stop(unit);
        }

        return true;
    }

    /**
     * Fires when the weapon cooldown and firing checks permit it.
     *
     * @param unit             the attacking unit
     * @param instructionLimit the animation instruction budget
     */
    public void fireWeapon(@NotNull Unit unit, int instructionLimit) {
        this.game.getProjectilePhase().resetPreviousSource();

        if ((unit.getStatus() & UnitStatusFlags.CANNOT_ATTACK) != 0) {
            return;
        }

        Unit target = unit.getOrderTargetUnit();
        boolean air = target != null && (target.getStatus() & UnitStatusFlags.FLYING) != 0;
        int weapon = air ? this.game.getUnitTargeting().getAirWeapon(unit) : this.game.getUnitTargeting().getGroundWeapon(unit);
        int cooldown = unit.getCooldownTimers() >>> (air ? Unit.AIR_COOLDOWN_SHIFT : Unit.GROUND_COOLDOWN_SHIFT) & BYTE_MASK;

        if (weapon == ScrWeaponIds.NONE) {
            return;
        }

        if (cooldown != 0) {
            waitForWeaponCooldown(unit, cooldown);

            return;
        }

        if (!this.canFireWeapon(unit, weapon)) {
            return;
        }

        this.startAttack(unit, weapon, air, FIRE_RANDOM_SOURCE, instructionLimit);
    }

    /**
     * Starts an attack: sets both weapon cooldowns to the modified cooldown with a random variation drawn from the random call site, and runs the
     * ground or air attack animation.
     *
     * @param unit             the attacking unit
     * @param weapon           the weapon
     * @param air              whether the attack uses the air attack animation
     * @param randomSource     the random call site of the cooldown variation
     * @param instructionLimit the animation instruction budget
     * @throws IllegalStateException if the unit has no sprite
     */
    private void startAttack(@NotNull Unit unit, int weapon, boolean air, int randomSource, int instructionLimit) {
        unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.STARTING_ATTACK);

        int cooldown = this.getModifiedCooldown(unit, weapon) + (this.game.getRandom().next(randomSource) & COOLDOWN_VARIATION_MASK) - 1;

        unit.setCooldownTimers(unit.getCooldownTimers() & WEAPON_COOLDOWNS_CLEARED | (cooldown & BYTE_MASK) << Unit.GROUND_COOLDOWN_SHIFT |
                               (cooldown & BYTE_MASK) << Unit.AIR_COOLDOWN_SHIFT);
        this.game.getIscriptExecutor().runSpriteAnimation(unit.requireSprite(),
                                                          unit,
                                                          air ? IscriptExecutor.ANIMATION_AIR_ATTACK_REPEAT
                                                              : IscriptExecutor.ANIMATION_GROUND_ATTACK_REPEAT,
                                                          0,
                                                          instructionLimit);
    }

    /**
     * Attacks a visible, attackable order target without moving; a unit with a turret leaves the attack to it.
     * <p>
     * Both weapon ranges are compared unsigned; this path draws its cooldown variation from its own random call site.
     * <p>
     * Target coordinates are stored before the turret, cooldown, starting-attack and minimum-range gates.
     *
     * @param unit             the attacking unit
     * @param instructionLimit the animation instruction budget
     * @return {@code true} if the unit attacks or waits for its cooldown or turn, {@code false} otherwise
     * @throws IllegalStateException if the unit or its order target has no sprite when the target is in weapon range
     * @throws NullPointerException  if the world was built without game data
     */
    public boolean tryAttackSomething(@NotNull Unit unit, int instructionLimit) {
        if (unit.getOrderTargetUnit() != null) {
            this.reacquireTarget(unit);
        }

        Unit target = unit.getOrderTargetUnit();

        if (target == null || !this.game.getUnitTargeting().canSeeTarget(unit, target) ||
            !this.game.getUnitTargeting().canAttackTarget(unit, target)) {
            return false;
        }

        setOrderTargetPosition(unit, target);

        Unit turret = unit.getSubunit();

        if (turret != null && this.game.getUnitTargeting().isTurret(turret)) {
            return true;
        }

        boolean air = (target.getStatus() & UnitStatusFlags.FLYING) != 0;
        int weapon = air ? this.game.getUnitTargeting().getAirWeapon(unit) : this.game.getUnitTargeting().getGroundWeapon(unit);
        int cooldown = unit.getCooldownTimers() >>> (air ? Unit.AIR_COOLDOWN_SHIFT : Unit.GROUND_COOLDOWN_SHIFT) & BYTE_MASK;

        if (weapon == ScrWeaponIds.NONE) {
            return false;
        }

        if (cooldown != 0) {
            waitForWeaponCooldown(unit, cooldown);

            return true;
        }

        if ((unit.getMovementFlags() & MovementFlags.STARTING_ATTACK) != 0) {
            return true;
        }

        int distance = this.game.getUnitTargeting().getDistance(this.game.getUnitTargeting().getMainUnit(unit), target);
        ScrWeaponTypeView weapons = this.game.requireGameData().getWeaponTypes();
        int minimumRange = weapons.getMinimumRange(weapon);

        if (minimumRange != 0 && Integer.compareUnsigned(distance, minimumRange) < 0) {
            return false;
        }

        if (Integer.compareUnsigned(distance, this.game.getUnitTargeting().getWeaponMaximumRange(unit, weapon)) > 0) {
            return false;
        }

        Sprite sprite = unit.requireSprite();
        Sprite targetSprite = target.requireSprite();
        int direction = FlingyMotionUtils.calculateDirection(targetSprite.getX() - sprite.getX(), targetSprite.getY() - sprite.getY());

        if (Math.abs((byte) (unit.getHeading() - direction)) > weapons.getAttackAngle(weapon)) {
            if ((unit.getStatus() & UnitStatusFlags.CAN_TURN) != 0) {
                unit.setPrimaryOrderCountdown(0);

                return true;
            }

            return false;
        }

        if ((unit.getStatus() & UnitStatusFlags.CANNOT_ATTACK) != 0) {
            return false;
        }

        if ((unit.getStatus() & UnitStatusFlags.IN_BUNKER) != 0) {
            this.game.getUnitTransport().createBunkerFireAnimation(unit, instructionLimit);
        }

        this.game.getProjectilePhase().resetPreviousSource();
        this.startAttack(unit, weapon, air, ATTACK_SOMETHING_RANDOM_SOURCE, instructionLimit);

        return true;
    }

    /**
     * Moves to the order position and interrupts the order for an attackable automatic target.
     * <p>
     * When the first move starts a computer transport pickup the order waits in its start state, and a unit with a computer record chooses through
     * the computer target choice and keeps its automatic target, going on with the order when nothing was chosen.
     *
     * @param unit                the moving unit
     * @param stopBeforeAttacking whether the unit stops before the attack interruption
     * @param instructionLimit    the animation and order instruction budget
     * @return {@code true} if the order goes on, {@code false} otherwise
     * @throws IllegalStateException if an enemy automatic target has no sprite
     */
    private boolean moveAndAttackAutomaticTarget(@NotNull Unit unit, boolean stopBeforeAttacking, int instructionLimit) {
        if (unit.getPrimaryOrderState() == ORDER_STATE_START) {
            int positionX = (short) unit.getOrderTargetX();
            int positionY = (short) unit.getOrderTargetY();
            boolean moved = this.game.getUnitPositioning().setMovementDestination(unit, positionX, positionY);

            unit.setDirectionTargetX(positionX);
            unit.setDirectionTargetY(positionY);

            if (!moved) {
                return false;
            }

            unit.setPrimaryOrderState(ORDER_STATE_RUNNING);
        }

        Unit target = unit.getAutomaticTargetUnit();

        if (target != null && this.game.getEngineGame().getCombat().isEnemy(unit, target) &&
            (target.requireSprite().getFlags() & Sprite.FLAG_HIDDEN) == 0 &&
            (target.getStatus() & UnitStatusFlags.INVINCIBLE) == 0 && !UnitTargeting.isUndetected(unit, target)) {
            if (unit.getComputerRecord() != null) {
                return !this.game.getComputerCombat().acquireTarget(unit, 0, false, false, instructionLimit);
            }

            unit.setAutomaticTargetUnit(null);
            this.interruptForAttack(unit, target, stopBeforeAttacking, instructionLimit);

            return false;
        }

        return true;
    }

    /**
     * Acquires a target for an attack-moving unit; a unit with a computer record chooses through the computer target choice instead.
     *
     * @param unit                the attack-moving unit
     * @param stopBeforeAttacking whether the unit stops before the attack interruption
     * @param instructionLimit    the animation and order instruction budget
     */
    private void acquireAttackMoveTarget(@NotNull Unit unit, boolean stopBeforeAttacking, int instructionLimit) {
        if (this.game.getUnitOrders().getTargetAcquisitionRange(unit) == 0) {
            return;
        }

        if (unit.getComputerRecord() != null) {
            this.game.getComputerCombat().acquireTarget(unit, 0, false, false, instructionLimit);

            return;
        }

        Unit target = this.game.getUnitTargeting().findAcquireTarget(unit);

        if (target != null) {
            this.interruptForAttack(unit, target, stopBeforeAttacking, instructionLimit);
        }
    }

    /**
     * Applies the attack interruption for a unit without a computer record: the unit stops, queues its current order behind the {@code units.dat}
     * Attack Unit order on the target, reactivates and is marked ready to attack with its subunit.
     *
     * @param unit                the unit
     * @param target              the target
     * @param stopBeforeAttacking whether the unit stops first
     * @param instructionLimit    the animation and order instruction budget
     * @throws IllegalStateException if the target has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    public void interruptForAttack(@NotNull Unit unit, @NotNull Unit target, boolean stopBeforeAttacking, int instructionLimit) {
        if (stopBeforeAttacking) {
            this.game.getEngineGame().getMovement().stop(unit);
        }

        this.game.getOrders().queueFront(unit,
                                         unit.getPrimaryOrder(),
                                         unit.getOrderTargetUnit(),
                                         (short) unit.getOrderTargetX(), (short) unit.getOrderTargetY(),
                                         ScrUnitIds.NONE);

        Sprite targetSprite = target.requireSprite();

        this.game.getOrders().queueFront(unit,
                                         this.game.requireGameData().getUnitTypes().getAttackUnitOrder(unit.getUnitId()),
                                         target,
                                         targetSprite.getX(), targetSprite.getY(),
                                         ScrUnitIds.NONE);
        this.game.getOrders().completeOrder(unit, instructionLimit);
        unit.markReadyToAttack();
    }

    /**
     * Follows the target unless the movement destination timer runs; an unseen target out of range ends the order when the common arrival query
     * reports a reached destination.
     * <p>
     * The result of the follow move is {@code false} when it started a computer transport pickup.
     * <p>
     * A running movement-destination timer returns before target movement or order completion.
     *
     * @param unit             the following unit
     * @param target           the target
     * @param instructionLimit the order activation budget
     * @return {@code true} if no arrival finish or transport pickup is requested, {@code false} otherwise
     */
    public boolean tryFollowTarget(@NotNull Unit unit, @NotNull Unit target, int instructionLimit) {
        if (unit.getMovementDestinationCountdown() != 0) {
            return true;
        }

        if (!UnitTargeting.isVisible(unit, target) && !this.game.getUnitTargeting().isInRange(unit, target, FOLLOW_UNSEEN_RANGE)) {
            if (ComputerOrders.getArrival(unit) == ComputerOrders.ARRIVAL_REACHED) {
                this.game.getOrders().finishOrder(unit, instructionLimit);

                return false;
            }

            return true;
        }

        return this.game.getUnitTargetMovement().moveToTarget(unit, target);
    }

    /**
     * Moves ahead of a visible moving target within the map, or follows the target.
     *
     * @param unit             the following unit
     * @param target           the target
     * @param instructionLimit the order activation budget
     * @throws IllegalStateException if a seen accelerating target has no sprite
     */
    private void followAhead(@NotNull Unit unit, @NotNull Unit target, int instructionLimit) {
        if (unit.getMovementDestinationCountdown() != 0) {
            return;
        }

        if (this.game.getUnitTargeting().canSeeTarget(unit, target) && (target.getMovementFlags() & MovementFlags.ACCELERATING) != 0) {
            Sprite targetSprite = target.requireSprite();
            int destinationX = targetSprite.getX();
            int destinationY = targetSprite.getY();
            long vector = FlingyMotionUtils.calculateDirectionVector(target.getTargetHeading(),
                                                                     this.game.getUnitTargeting().getHaltDistance(unit) * FOLLOW_AHEAD_HALT_DISTANCES);
            int positionX = targetSprite.getX() + ((int) vector >> FIXED_POINT_FRACTION_BITS);
            int positionY = targetSprite.getY() + ((int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) >> FIXED_POINT_FRACTION_BITS);

            if (Integer.compareUnsigned(positionX, this.game.getMap().getWidth() * TerrainTileset.TILE_SIZE) < 0 &&
                Integer.compareUnsigned(positionY, this.game.getMap().getHeight() * TerrainTileset.TILE_SIZE) < 0) {
                destinationX = positionX;
                destinationY = positionY;
            }

            this.game.getEngineGame().getMovement().setDestination(unit, destinationX, destinationY);
        } else {
            this.tryFollowTarget(unit, target, instructionLimit);
        }
    }
}
