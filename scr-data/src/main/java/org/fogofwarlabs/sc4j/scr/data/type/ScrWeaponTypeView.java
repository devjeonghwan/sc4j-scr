package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code weapons.dat} data of every weapon, indexed by its {@code int} id (the constants of {@link ScrWeaponIds}).
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD and map-setting writes to the columns are visible. An id outside a column has no
 * bound check: it reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}, and a unit without a weapon ({@code 130})
 * reads those values. Reads allocate nothing.
 */
public final class ScrWeaponTypeView {
    /**
     * Names the columns a loader supplies to {@link ScrWeaponTypeView#ScrWeaponTypeView(Function, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrWeaponTypeView#getLabel(int)}.
         */
        LABEL,
        /**
         * The column of {@link ScrWeaponTypeView#getFlingyType(int)}.
         */
        FLINGY_TYPE,
        /**
         * The column of {@link ScrWeaponTypeView#getSpecialAttack(int)}.
         */
        SPECIAL_ATTACK,
        /**
         * The column of {@link ScrWeaponTypeView#getTargetFlags(int)}.
         */
        TARGET_FLAGS,
        /**
         * The column of {@link ScrWeaponTypeView#getMinimumRange(int)}.
         */
        MINIMUM_RANGE,
        /**
         * The column of {@link ScrWeaponTypeView#getMaximumRange(int)}.
         */
        MAXIMUM_RANGE,
        /**
         * The column of {@link ScrWeaponTypeView#getDamageUpgrade(int)}.
         */
        DAMAGE_UPGRADE,
        /**
         * The column of {@link ScrWeaponTypeView#getDamageType(int)}.
         */
        DAMAGE_TYPE,
        /**
         * The column of {@link ScrWeaponTypeView#getBehavior(int)}.
         */
        BEHAVIOR,
        /**
         * The column of {@link ScrWeaponTypeView#getLifetime(int)}.
         */
        LIFETIME,
        /**
         * The column of {@link ScrWeaponTypeView#getExplosionType(int)}.
         */
        EXPLOSION_TYPE,
        /**
         * The column of {@link ScrWeaponTypeView#getInnerSplashRadius(int)}.
         */
        INNER_SPLASH_RADIUS,
        /**
         * The column of {@link ScrWeaponTypeView#getMediumSplashRadius(int)}.
         */
        MEDIUM_SPLASH_RADIUS,
        /**
         * The column of {@link ScrWeaponTypeView#getOuterSplashRadius(int)}.
         */
        OUTER_SPLASH_RADIUS,
        /**
         * The column of {@link ScrWeaponTypeView#getDamageAmount(int)}.
         */
        DAMAGE_AMOUNT,
        /**
         * The column of {@link ScrWeaponTypeView#getDamageBonus(int)}.
         */
        DAMAGE_BONUS,
        /**
         * The column of {@link ScrWeaponTypeView#getCooldown(int)}.
         */
        COOLDOWN,
        /**
         * The column of {@link ScrWeaponTypeView#getDamageFactor(int)}.
         */
        DAMAGE_FACTOR,
        /**
         * The column of {@link ScrWeaponTypeView#getAttackAngle(int)}.
         */
        ATTACK_ANGLE,
        /**
         * The column of {@link ScrWeaponTypeView#getLaunchSpin(int)}.
         */
        LAUNCH_SPIN,
        /**
         * The column of {@link ScrWeaponTypeView#getForwardOffset(int)}.
         */
        FORWARD_OFFSET,
        /**
         * The column of {@link ScrWeaponTypeView#getUpwardOffset(int)}.
         */
        UPWARD_OFFSET,
        /**
         * The column of {@link ScrWeaponTypeView#getTargetErrorMessage(int)}.
         */
        TARGET_ERROR_MESSAGE,
        /**
         * The column of {@link ScrWeaponTypeView#getIcon(int)}.
         */
        ICON
    }

    private final @NotNull DatColumn label;
    private final @NotNull DatColumn flingyType;
    private final @NotNull DatColumn specialAttack;
    private final @NotNull DatColumn targetFlags;

    private final @NotNull DatColumn minimumRange;
    private final @NotNull DatColumn maximumRange;

    private final @NotNull DatColumn damageUpgrade;
    private final @NotNull DatColumn damageType;

    private final @NotNull DatColumn behavior;
    private final @NotNull DatColumn lifetime;
    private final @NotNull DatColumn explosionType;

    private final @NotNull DatColumn innerSplashRadius;
    private final @NotNull DatColumn mediumSplashRadius;
    private final @NotNull DatColumn outerSplashRadius;

    private final @NotNull DatColumn damageAmount;
    private final @NotNull DatColumn damageBonus;
    private final @NotNull DatColumn cooldown;
    private final @NotNull DatColumn damageFactor;

    private final @NotNull DatColumn attackAngle;
    private final @NotNull DatColumn launchSpin;
    private final @NotNull DatColumn forwardOffset;
    private final @NotNull DatColumn upwardOffset;

    private final @NotNull DatColumn targetErrorMessage;
    private final @NotNull DatColumn icon;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrWeaponTypeView} instance over the columns of a loader.
     *
     * @param columns       the column of each {@link Column}, shared and not copied
     * @param overrunReader the reader of an element outside a column
     */
    public ScrWeaponTypeView(@NotNull Function<Column, @NotNull DatColumn> columns, @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.label         = columns.apply(Column.LABEL);
        this.flingyType    = columns.apply(Column.FLINGY_TYPE);
        this.specialAttack = columns.apply(Column.SPECIAL_ATTACK);
        this.targetFlags   = columns.apply(Column.TARGET_FLAGS);

        this.minimumRange = columns.apply(Column.MINIMUM_RANGE);
        this.maximumRange = columns.apply(Column.MAXIMUM_RANGE);

        this.damageUpgrade = columns.apply(Column.DAMAGE_UPGRADE);
        this.damageType    = columns.apply(Column.DAMAGE_TYPE);

        this.behavior      = columns.apply(Column.BEHAVIOR);
        this.lifetime      = columns.apply(Column.LIFETIME);
        this.explosionType = columns.apply(Column.EXPLOSION_TYPE);

        this.innerSplashRadius  = columns.apply(Column.INNER_SPLASH_RADIUS);
        this.mediumSplashRadius = columns.apply(Column.MEDIUM_SPLASH_RADIUS);
        this.outerSplashRadius  = columns.apply(Column.OUTER_SPLASH_RADIUS);

        this.damageAmount = columns.apply(Column.DAMAGE_AMOUNT);
        this.damageBonus  = columns.apply(Column.DAMAGE_BONUS);
        this.cooldown     = columns.apply(Column.COOLDOWN);
        this.damageFactor = columns.apply(Column.DAMAGE_FACTOR);

        this.attackAngle   = columns.apply(Column.ATTACK_ANGLE);
        this.launchSpin    = columns.apply(Column.LAUNCH_SPIN);
        this.forwardOffset = columns.apply(Column.FORWARD_OFFSET);
        this.upwardOffset  = columns.apply(Column.UPWARD_OFFSET);

        this.targetErrorMessage = columns.apply(Column.TARGET_ERROR_MESSAGE);
        this.icon               = columns.apply(Column.ICON);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.label.getLength();
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the stat_txt.tbl string of the weapon name
     */
    public int getLabel(int weaponTypeIdentifier) {
        DatColumn values = this.label;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.LABEL, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the flingy.dat entry of the projectile
     */
    public int getFlingyType(int weaponTypeIdentifier) {
        DatColumn values = this.flingyType;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.FLINGY_TYPE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the unused special attack byte
     */
    public int getSpecialAttack(int weaponTypeIdentifier) {
        DatColumn values = this.specialAttack;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.SPECIAL_ATTACK, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the target flags
     */
    public int getTargetFlags(int weaponTypeIdentifier) {
        DatColumn values = this.targetFlags;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.TARGET_FLAGS, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the minimum range in pixels
     */
    public int getMinimumRange(int weaponTypeIdentifier) {
        DatColumn values = this.minimumRange;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.MINIMUM_RANGE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the maximum range in pixels
     */
    public int getMaximumRange(int weaponTypeIdentifier) {
        DatColumn values = this.maximumRange;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.MAXIMUM_RANGE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the upgrades.dat entry that raises the damage
     */
    public int getDamageUpgrade(int weaponTypeIdentifier) {
        DatColumn values = this.damageUpgrade;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.DAMAGE_UPGRADE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the damage type (independent, explosive, concussive, normal, ignore armor)
     */
    public int getDamageType(int weaponTypeIdentifier) {
        DatColumn values = this.damageType;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.DAMAGE_TYPE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the projectile behavior
     */
    public int getBehavior(int weaponTypeIdentifier) {
        DatColumn values = this.behavior;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.BEHAVIOR, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the projectile lifetime in frames
     */
    public int getLifetime(int weaponTypeIdentifier) {
        DatColumn values = this.lifetime;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.LIFETIME, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the explosion (hit) type
     */
    public int getExplosionType(int weaponTypeIdentifier) {
        DatColumn values = this.explosionType;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.EXPLOSION_TYPE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the inner splash radius in pixels
     */
    public int getInnerSplashRadius(int weaponTypeIdentifier) {
        DatColumn values = this.innerSplashRadius;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.INNER_SPLASH_RADIUS, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the medium splash radius in pixels
     */
    public int getMediumSplashRadius(int weaponTypeIdentifier) {
        DatColumn values = this.mediumSplashRadius;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.MEDIUM_SPLASH_RADIUS, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the outer splash radius in pixels
     */
    public int getOuterSplashRadius(int weaponTypeIdentifier) {
        DatColumn values = this.outerSplashRadius;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.OUTER_SPLASH_RADIUS, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the base damage
     */
    public int getDamageAmount(int weaponTypeIdentifier) {
        DatColumn values = this.damageAmount;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.DAMAGE_AMOUNT, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the damage added per upgrade level
     */
    public int getDamageBonus(int weaponTypeIdentifier) {
        DatColumn values = this.damageBonus;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.DAMAGE_BONUS, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the cooldown in frames
     */
    public int getCooldown(int weaponTypeIdentifier) {
        DatColumn values = this.cooldown;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.COOLDOWN, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the number of projectiles (damage multiplier)
     */
    public int getDamageFactor(int weaponTypeIdentifier) {
        DatColumn values = this.damageFactor;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.DAMAGE_FACTOR, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the attack angle in 1/256 turns
     */
    public int getAttackAngle(int weaponTypeIdentifier) {
        DatColumn values = this.attackAngle;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.ATTACK_ANGLE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the launch spin (projectile heading offset) as an unsigned byte
     */
    public int getLaunchSpin(int weaponTypeIdentifier) {
        DatColumn values = this.launchSpin;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.LAUNCH_SPIN, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the forward launch offset in pixels as an unsigned byte
     */
    public int getForwardOffset(int weaponTypeIdentifier) {
        DatColumn values = this.forwardOffset;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.FORWARD_OFFSET, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the upward launch offset in pixels as an unsigned byte
     */
    public int getUpwardOffset(int weaponTypeIdentifier) {
        DatColumn values = this.upwardOffset;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.UPWARD_OFFSET, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the stat_txt.tbl string shown for an invalid target
     */
    public int getTargetErrorMessage(int weaponTypeIdentifier) {
        DatColumn values = this.targetErrorMessage;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.TARGET_ERROR_MESSAGE, weaponTypeIdentifier);
    }

    /**
     * @param weaponTypeIdentifier the weapons.dat entry
     * @return the icon frame
     */
    public int getIcon(int weaponTypeIdentifier) {
        DatColumn values = this.icon;

        return weaponTypeIdentifier >= 0 && weaponTypeIdentifier < values.getLength()
               ? values.get(weaponTypeIdentifier)
               : this.overrunReader.read(Column.ICON, weaponTypeIdentifier);
    }
}
