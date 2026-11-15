package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.ScrOrderTypeView;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the column schema of {@code arr/orders.dat} (189 entries, 4,158 bytes).
 */
public final class OrdersDat {
    /**
     * The number of orders.dat entries.
     */
    public static final int ORDER_COUNT = 189;
    /**
     * The {@code Label} column name.
     */
    public static final @NotNull String LABEL = "Label";
    /**
     * The {@code Use Weapon Targeting} column name.
     */
    public static final @NotNull String USE_WEAPON_TARGETING = "Use Weapon Targeting";
    /**
     * The {@code Unused1} column name.
     */
    public static final @NotNull String UNUSED1 = "Unused1";
    /**
     * The {@code Unused2} column name.
     */
    public static final @NotNull String UNUSED2 = "Unused2";
    /**
     * The {@code Subunit Inheritance} column name.
     */
    public static final @NotNull String SUBUNIT_INHERITANCE = "Subunit Inheritance";
    /**
     * The {@code Unused3} column name.
     */
    public static final @NotNull String UNUSED3 = "Unused3";
    /**
     * The {@code Can Be Interrupted} column name.
     */
    public static final @NotNull String CAN_BE_INTERRUPTED = "Can Be Interrupted";
    /**
     * The {@code Can Be Lifted} column name.
     */
    public static final @NotNull String CAN_BE_LIFTED = "Can Be Lifted";
    /**
     * The {@code Can Be Queued} column name.
     */
    public static final @NotNull String CAN_BE_QUEUED = "Can Be Queued";
    /**
     * The {@code Disabling Keeps Target} column name.
     */
    public static final @NotNull String DISABLING_KEEPS_TARGET = "Disabling Keeps Target";
    /**
     * The {@code Can Be Obstructed} column name.
     */
    public static final @NotNull String CAN_BE_OBSTRUCTED = "Can Be Obstructed";
    /**
     * The {@code Fleeable} column name.
     */
    public static final @NotNull String FLEEABLE = "Fleeable";
    /**
     * The {@code Unused4} column name.
     */
    public static final @NotNull String UNUSED4 = "Unused4";
    /**
     * The {@code Weapon} column name.
     */
    public static final @NotNull String WEAPON = "Weapon";
    /**
     * The {@code Energy} column name.
     */
    public static final @NotNull String TECHNOLOGY = "Energy";
    /**
     * The {@code Animation} column name.
     */
    public static final @NotNull String ANIMATION = "Animation";
    /**
     * The {@code Highlight} column name.
     */
    public static final @NotNull String HIGHLIGHT = "Highlight";
    /**
     * The {@code DatReqs Index} column name.
     */
    public static final @NotNull String REQUIREMENT_INDEX = "DatReqs Index";
    /**
     * The {@code Obscured} column name.
     */
    public static final @NotNull String OBSCURED_ORDER = "Obscured";
    /**
     * The alignment of the orders.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/orders.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU16(LABEL, ORDER_COUNT)
                .fieldU8(USE_WEAPON_TARGETING, ORDER_COUNT)
                .fieldU8(UNUSED1, ORDER_COUNT)
                .fieldU8(UNUSED2, ORDER_COUNT)
                .fieldU8(SUBUNIT_INHERITANCE, ORDER_COUNT)
                .fieldU8(UNUSED3, ORDER_COUNT)
                .fieldU8(CAN_BE_INTERRUPTED, ORDER_COUNT)
                .fieldU8(CAN_BE_LIFTED, ORDER_COUNT)
                .fieldU8(CAN_BE_QUEUED, ORDER_COUNT)
                .fieldU8(DISABLING_KEEPS_TARGET, ORDER_COUNT)
                .fieldU8(CAN_BE_OBSTRUCTED, ORDER_COUNT)
                .fieldU8(FLEEABLE, ORDER_COUNT)
                .fieldU8(UNUSED4, ORDER_COUNT)
                .fieldU8(WEAPON, ORDER_COUNT)
                .fieldU8(TECHNOLOGY, ORDER_COUNT)
                .fieldU8(ANIMATION, ORDER_COUNT)
                .fieldU16(HIGHLIGHT, ORDER_COUNT)
                .fieldU16(REQUIREMENT_INDEX, ORDER_COUNT)
                .fieldU8(OBSCURED_ORDER, ORDER_COUNT);
    }

    /**
     * Creates a {@link ScrOrderTypeView} instance over the columns of a parsed table; later writes to the table (EUD, map settings, requirement
     * indices) are visible through it. An id outside a column reads the game's memory around the column
     * ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a partial schema) reads as an empty column, whose accessors throw
     * {@link IllegalArgumentException} for every id.
     *
     * @param orders the parsed {@code orders.dat} table
     * @return the created {@link ScrOrderTypeView} instance
     */
    public static @NotNull ScrOrderTypeView createTypeTable(@NotNull DatTable orders) {
        return new ScrOrderTypeView(column -> orders.getColumnOrEmpty(getColumnName(column)),
                                    REQUIREMENT_INDEX,
                                    (column, index) -> orders.readNativeElement(getColumnName(column), index));
    }

    /**
     * @param column a column of the order type table
     * @return the orders.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrOrderTypeView.@NotNull Column column) {
        return switch (column) {
            case LABEL -> LABEL;
            case USE_WEAPON_TARGETING -> USE_WEAPON_TARGETING;
            case SUBUNIT_INHERITANCE -> SUBUNIT_INHERITANCE;
            case CAN_BE_INTERRUPTED -> CAN_BE_INTERRUPTED;
            case CAN_BE_LIFTED -> CAN_BE_LIFTED;
            case CAN_BE_QUEUED -> CAN_BE_QUEUED;
            case DISABLING_KEEPS_TARGET -> DISABLING_KEEPS_TARGET;
            case CAN_BE_OBSTRUCTED -> CAN_BE_OBSTRUCTED;
            case FLEEABLE -> FLEEABLE;
            case WEAPON -> WEAPON;
            case TECHNOLOGY -> TECHNOLOGY;
            case ANIMATION -> ANIMATION;
            case HIGHLIGHT -> HIGHLIGHT;
            case REQUIREMENT_INDEX -> REQUIREMENT_INDEX;
            case OBSCURED_ORDER -> OBSCURED_ORDER;
        };
    }


    private OrdersDat() {
    }
}
