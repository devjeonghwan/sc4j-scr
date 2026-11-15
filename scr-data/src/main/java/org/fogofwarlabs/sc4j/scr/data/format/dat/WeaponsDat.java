package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrWeaponTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides the column schema of {@code arr/weapons.dat}.
 * <p>
 * The schema has 130 weapon entries in classic field order. Attack angle, launch spin and the two launch offsets are separate 130-byte arrays.
 */
public final class WeaponsDat {
    /**
     * The number of weapons.dat entries.
     */
    public static final int WEAPON_COUNT = 130;
    /**
     * The id of the first weapon a mod adds, the first id after the none id {@link #WEAPON_COUNT}.
     */
    public static final int FIRST_ADDED_WEAPON = WEAPON_COUNT + 1;
    /**
     * The {@code Label} column name.
     */
    public static final @NotNull String LABEL = "Label";
    /**
     * The {@code Sprite} column name.
     */
    public static final @NotNull String FLINGY_TYPE = "Sprite";
    /**
     * The {@code Special Attack} column name.
     */
    public static final @NotNull String SPECIAL_ATTACK = "Special Attack";
    /**
     * The {@code Target Flags} column name.
     */
    public static final @NotNull String TARGET_FLAGS = "Target Flags";
    /**
     * The {@code Minimum Range} column name.
     */
    public static final @NotNull String MINIMUM_RANGE = "Minimum Range";
    /**
     * The {@code Maximum Range} column name.
     */
    public static final @NotNull String MAXIMUM_RANGE = "Maximum Range";
    /**
     * The {@code Upgrade Group} column name.
     */
    public static final @NotNull String DAMAGE_UPGRADE = "Upgrade Group";
    /**
     * The {@code Damage Type} column name.
     */
    public static final @NotNull String DAMAGE_TYPE = "Damage Type";
    /**
     * The {@code Weapon Behavior} column name.
     */
    public static final @NotNull String BEHAVIOR = "Weapon Behavior";
    /**
     * The {@code Remove After} column name.
     */
    public static final @NotNull String LIFETIME = "Remove After";
    /**
     * The {@code Explosion Type} column name.
     */
    public static final @NotNull String EXPLOSION_TYPE = "Explosion Type";
    /**
     * The {@code Inner Splash Radius} column name.
     */
    public static final @NotNull String INNER_SPLASH_RADIUS = "Inner Splash Radius";
    /**
     * The {@code Medium Splash Radius} column name.
     */
    public static final @NotNull String MEDIUM_SPLASH_RADIUS = "Medium Splash Radius";
    /**
     * The {@code Outer Splash Radius} column name.
     */
    public static final @NotNull String OUTER_SPLASH_RADIUS = "Outer Splash Radius";
    /**
     * The {@code Damage Amount} column name.
     */
    public static final @NotNull String DAMAGE_AMOUNT = "Damage Amount";
    /**
     * The {@code Damage Bonus} column name.
     */
    public static final @NotNull String DAMAGE_BONUS = "Damage Bonus";
    /**
     * The {@code Weapon Cooldown} column name.
     */
    public static final @NotNull String COOLDOWN = "Weapon Cooldown";
    /**
     * The {@code Damage Factor} column name.
     */
    public static final @NotNull String DAMAGE_FACTOR = "Damage Factor";
    /**
     * The {@code Attack Angle} column name.
     */
    public static final @NotNull String ATTACK_ANGLE = "Attack Angle";
    /**
     * The {@code Launch Spin} column name.
     */
    public static final @NotNull String LAUNCH_SPIN = "Launch Spin";
    /**
     * The {@code Forward Offset} column name.
     */
    public static final @NotNull String FORWARD_OFFSET = "Forward Offset";
    /**
     * The {@code Upward Offset} column name.
     */
    public static final @NotNull String UPWARD_OFFSET = "Upward Offset";
    /**
     * The {@code Target Error Message} column name.
     */
    public static final @NotNull String TARGET_ERROR_MESSAGE = "Target Error Message";
    /**
     * The {@code Icon} column name.
     */
    public static final @NotNull String ICON = "Icon";
    /**
     * The alignment of the weapons.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/weapons.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU16(LABEL, WEAPON_COUNT)
                .fieldU32(FLINGY_TYPE, WEAPON_COUNT)
                .fieldU8(SPECIAL_ATTACK, WEAPON_COUNT)
                .fieldU16(TARGET_FLAGS, WEAPON_COUNT)
                .fieldU32(MINIMUM_RANGE, WEAPON_COUNT)
                .fieldU32(MAXIMUM_RANGE, WEAPON_COUNT)
                .fieldU8(DAMAGE_UPGRADE, WEAPON_COUNT)
                .fieldU8(DAMAGE_TYPE, WEAPON_COUNT)
                .fieldU8(BEHAVIOR, WEAPON_COUNT)
                .fieldU8(LIFETIME, WEAPON_COUNT)
                .fieldU8(EXPLOSION_TYPE, WEAPON_COUNT)
                .fieldU16(INNER_SPLASH_RADIUS, WEAPON_COUNT)
                .fieldU16(MEDIUM_SPLASH_RADIUS, WEAPON_COUNT)
                .fieldU16(OUTER_SPLASH_RADIUS, WEAPON_COUNT)
                .fieldU16(DAMAGE_AMOUNT, WEAPON_COUNT)
                .fieldU16(DAMAGE_BONUS, WEAPON_COUNT)
                .fieldU8(COOLDOWN, WEAPON_COUNT)
                .fieldU8(DAMAGE_FACTOR, WEAPON_COUNT)
                .fieldU8(ATTACK_ANGLE, WEAPON_COUNT)
                .fieldU8(LAUNCH_SPIN, WEAPON_COUNT)
                .fieldU8(FORWARD_OFFSET, WEAPON_COUNT)
                .fieldU8(UPWARD_OFFSET, WEAPON_COUNT)
                .fieldU16(TARGET_ERROR_MESSAGE, WEAPON_COUNT)
                .fieldU16(ICON, WEAPON_COUNT);
    }

    /**
     * Creates a {@link ScrWeaponTypeView} instance over the columns of a parsed table; later writes to the table (EUD, map settings) are visible
     * through it. An id outside a column reads the game's memory around the column ({@link DatTable#readNativeElement(String, int)}). A column the
     * table lacks (a partial schema) reads as an empty column, whose accessors throw {@link IllegalArgumentException} for every id.
     *
     * @param weapons the parsed {@code weapons.dat} table
     * @return the created {@link ScrWeaponTypeView} instance
     */
    public static @NotNull ScrWeaponTypeView createTypeTable(@NotNull DatTable weapons) {
        return new ScrWeaponTypeView(column -> weapons.getColumnOrEmpty(getColumnName(column)),
                                     (column, index) -> weapons.readNativeElement(getColumnName(column), index));
    }

    /**
     * Creates a {@link ScrWeaponTypeView} instance over the columns of a game's table and the added columns of its added weapons: an id from
     * {@link #FIRST_ADDED_WEAPON} on whose element lies in the added columns reads the added column, every other id (the none id among them)
     * reads as {@link #createTypeTable(DatTable)} reads it. Without added weapons the view is the native one.
     *
     * @param weapons      the game's {@code weapons.dat} table
     * @param addedColumns the added column of each {@code weapons.dat} field, by field name, one element per added weapon
     * @return the created {@link ScrWeaponTypeView} instance
     */
    public static @NotNull ScrWeaponTypeView createTypeTable(@NotNull DatTable weapons, @NotNull Function<String, @NotNull DatColumn> addedColumns) {
        Map<ScrWeaponTypeView.Column, DatColumn> added = new EnumMap<>(ScrWeaponTypeView.Column.class);

        for (ScrWeaponTypeView.Column column : ScrWeaponTypeView.Column.values()) {
            added.put(column, addedColumns.apply(getColumnName(column)));
        }

        return new ScrWeaponTypeView(column -> weapons.getColumnOrEmpty(getColumnName(column)), (column, element) -> {
            DatColumn values = added.get(column);
            int addedElement = element - FIRST_ADDED_WEAPON;

            return addedElement >= 0 && addedElement < values.getLength()
                   ? values.get(addedElement)
                   : weapons.readNativeElement(getColumnName(column), element);
        });
    }

    /**
     * @param column a column of the weapon type table
     * @return the weapons.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrWeaponTypeView.@NotNull Column column) {
        return switch (column) {
            case LABEL -> LABEL;
            case FLINGY_TYPE -> FLINGY_TYPE;
            case SPECIAL_ATTACK -> SPECIAL_ATTACK;
            case TARGET_FLAGS -> TARGET_FLAGS;
            case MINIMUM_RANGE -> MINIMUM_RANGE;
            case MAXIMUM_RANGE -> MAXIMUM_RANGE;
            case DAMAGE_UPGRADE -> DAMAGE_UPGRADE;
            case DAMAGE_TYPE -> DAMAGE_TYPE;
            case BEHAVIOR -> BEHAVIOR;
            case LIFETIME -> LIFETIME;
            case EXPLOSION_TYPE -> EXPLOSION_TYPE;
            case INNER_SPLASH_RADIUS -> INNER_SPLASH_RADIUS;
            case MEDIUM_SPLASH_RADIUS -> MEDIUM_SPLASH_RADIUS;
            case OUTER_SPLASH_RADIUS -> OUTER_SPLASH_RADIUS;
            case DAMAGE_AMOUNT -> DAMAGE_AMOUNT;
            case DAMAGE_BONUS -> DAMAGE_BONUS;
            case COOLDOWN -> COOLDOWN;
            case DAMAGE_FACTOR -> DAMAGE_FACTOR;
            case ATTACK_ANGLE -> ATTACK_ANGLE;
            case LAUNCH_SPIN -> LAUNCH_SPIN;
            case FORWARD_OFFSET -> FORWARD_OFFSET;
            case UPWARD_OFFSET -> UPWARD_OFFSET;
            case TARGET_ERROR_MESSAGE -> TARGET_ERROR_MESSAGE;
            case ICON -> ICON;
        };
    }


    private WeaponsDat() {
    }
}
