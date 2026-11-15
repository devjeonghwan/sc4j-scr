package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names weapons.dat projectile behaviors that control movement and hit positions.
 */
public final class WeaponBehaviors {
    /**
     * The bullet flies to the target position and does not follow a moving target.
     */
    public static final int FLY = 0;
    /**
     * The bullet flies to the target unit and follows it.
     */
    public static final int FOLLOW_TARGET = 1;
    /**
     * The bullet appears on the target unit.
     */
    public static final int APPEAR_AT_TARGET_UNIT = 2;
    /**
     * The bullet stays on the target position and hits repeatedly until its lifetime ends.
     */
    public static final int PERSIST_AT_TARGET_POSITION = 3;
    /**
     * The bullet appears on the target position.
     */
    public static final int APPEAR_AT_TARGET_POSITION = 4;
    /**
     * The bullet appears on its attacker, the melee behavior.
     */
    public static final int APPEAR_AT_SOURCE_UNIT = 5;
    /**
     * The bullet hits and its attacker destroys itself.
     */
    public static final int SELF_DESTRUCT = 6;
    /**
     * The bullet bounces from its target to further enemies near it.
     */
    public static final int BOUNCE = 7;
    /**
     * The bullet flies to one of a cycle of positions around the target.
     */
    public static final int ATTACK_TARGET_POSITION = 8;
    /**
     * The bullet flies in the attacker's direction to the weapon's maximum range.
     */
    public static final int EXTEND_TO_MAXIMUM_RANGE = 9;

    private WeaponBehaviors() {
    }
}
