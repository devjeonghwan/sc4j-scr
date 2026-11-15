package org.fogofwarlabs.sc4j.scr.game.unit;

/**
 * Names the bits of {@link Unit#getStatus()}.
 * <p>
 * Flags describe completion, movement, visibility, orders and unit effects; bits with unknown meaning retain their
 * numeric names.
 */
public final class UnitStatusFlags {
    /**
     * The unit is complete.
     */
    public static final int COMPLETED = 1;
    /**
     * The unit is a building on the ground.
     */
    public static final int GROUNDED_BUILDING = 2;
    /**
     * The unit is in the air.
     */
    public static final int FLYING = 4;
    /**
     * The attack was started by the automatic attack path.
     */
    public static final int AUTOMATIC_ATTACK_STARTED = 8;
    /**
     * The unit is burrowed.
     */
    public static final int BURROWED = 0x10;
    /**
     * The unit is loaded in a bunker.
     */
    public static final int IN_BUNKER = 0x20;
    /**
     * The unit is loaded in a transport.
     */
    public static final int LOADED = 0x40;
    /**
     * A computer player marked the unit as an unreachable chase target.
     */
    public static final int COMPUTER_TARGET_CLAIMED = 0x80;
    /**
     * The unit needs a detector to be seen.
     */
    public static final int REQUIRES_DETECTOR = 0x100;
    /**
     * The unit is cloaked.
     */
    public static final int CLOAKED = 0x200;
    /**
     * The unit is disabled.
     */
    public static final int DISABLED = 0x400;
    /**
     * The unit is cloaked by an arbiter or permanently.
     */
    public static final int PASSIVELY_CLOAKED = 0x800;
    /**
     * The current order cannot be interrupted.
     */
    public static final int ORDER_NOT_INTERRUPTIBLE = 0x1000;
    /**
     * The iscript is inside a {@code nobrkcodestart} section.
     */
    public static final int ISCRIPT_NO_BREAK = 0x2000;
    /**
     * Status bit {@code 0x4000}; its meaning is not established.
     */
    public static final int FLAG_4000 = 0x4000;
    /**
     * The unit cannot attack, for example under a disruption web.
     */
    public static final int CANNOT_ATTACK = 0x8000;
    /**
     * The unit can turn.
     */
    public static final int CAN_TURN = 0x10000;
    /**
     * The unit can move.
     */
    public static final int CAN_MOVE = 0x20000;
    /**
     * The unit collides with other units.
     */
    public static final int COLLISION = 0x40000;
    /**
     * The unit is immovable.
     */
    public static final int IMMOVABLE = 0x80000;
    /**
     * The unit is a ground unit.
     */
    public static final int GROUND_UNIT = 0x100000;
    /**
     * The unit does not collide.
     */
    public static final int NO_COLLIDE = 0x200000;
    /**
     * Status bit {@code 0x400000}; its meaning is not established.
     */
    public static final int FLAG_400000 = 0x400000;
    /**
     * The worker is gathering.
     */
    public static final int GATHERING = 0x800000;
    /**
     * The turret plays its walking animation.
     */
    public static final int TURRET_WALKING = 0x1000000;
    /**
     * The turret faces its base heading and turns with it.
     */
    public static final int TURRET_FOLLOWS_BASE = 0x2000000;
    /**
     * The unit is invincible.
     */
    public static final int INVINCIBLE = 0x4000000;
    /**
     * The unit is ready to attack.
     */
    public static final int READY_TO_ATTACK = 0x8000000;
    /**
     * The unit has its speed upgrade.
     */
    public static final int SPEED_UPGRADE = 0x10000000;
    /**
     * The unit has its cooldown upgrade.
     */
    public static final int COOLDOWN_UPGRADE = 0x20000000;
    /**
     * The unit is a hallucination.
     */
    public static final int HALLUCINATION = 0x40000000;
    /**
     * The unit dies from an expired lifetime.
     */
    public static final int LIFETIME_EXPIRED = 0x80000000;

    private UnitStatusFlags() {
    }
}
