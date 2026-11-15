package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code orders.dat} data of every order, indexed by its {@code int} id (the constants of {@link ScrOrderIds}).
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD and map-setting writes to the columns are visible. An id outside a column has no
 * bound check: it reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class ScrOrderTypeView {
    /**
     * The highlight ({@link #getHighlight(int)}) of an order that highlights no command icon.
     */
    public static final int NO_HIGHLIGHT = 0xFFFF;

    /**
     * Names the columns a loader supplies to {@link ScrOrderTypeView#ScrOrderTypeView(Function, String, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrOrderTypeView#getLabel(int)}.
         */
        LABEL,
        /**
         * The column of {@link ScrOrderTypeView#getUseWeaponTargeting(int)}.
         */
        USE_WEAPON_TARGETING,
        /**
         * The column of {@link ScrOrderTypeView#getSubunitInheritance(int)}.
         */
        SUBUNIT_INHERITANCE,
        /**
         * The column of {@link ScrOrderTypeView#getCanBeInterrupted(int)}.
         */
        CAN_BE_INTERRUPTED,
        /**
         * The column of {@link ScrOrderTypeView#getCanBeLifted(int)}.
         */
        CAN_BE_LIFTED,
        /**
         * The column of {@link ScrOrderTypeView#getCanBeQueued(int)}.
         */
        CAN_BE_QUEUED,
        /**
         * The column of {@link ScrOrderTypeView#getDisablingKeepsTarget(int)}.
         */
        DISABLING_KEEPS_TARGET,
        /**
         * The column of {@link ScrOrderTypeView#getCanBeObstructed(int)}.
         */
        CAN_BE_OBSTRUCTED,
        /**
         * The column of {@link ScrOrderTypeView#getFleeable(int)}.
         */
        FLEEABLE,
        /**
         * The column of {@link ScrOrderTypeView#getWeapon(int)}.
         */
        WEAPON,
        /**
         * The column of {@link ScrOrderTypeView#getTechnology(int)}.
         */
        TECHNOLOGY,
        /**
         * The column of {@link ScrOrderTypeView#getAnimation(int)}.
         */
        ANIMATION,
        /**
         * The column of {@link ScrOrderTypeView#getHighlight(int)}.
         */
        HIGHLIGHT,
        /**
         * The column of {@link ScrOrderTypeView#getRequirementIndex(int)}.
         */
        REQUIREMENT_INDEX,
        /**
         * The column of {@link ScrOrderTypeView#getObscuredOrder(int)}.
         */
        OBSCURED_ORDER
    }

    private final @NotNull DatColumn label;

    private final @NotNull DatColumn useWeaponTargeting;
    private final @NotNull DatColumn subunitInheritance;
    private final @NotNull DatColumn canBeInterrupted;
    private final @NotNull DatColumn canBeLifted;
    private final @NotNull DatColumn canBeQueued;
    private final @NotNull DatColumn disablingKeepsTarget;
    private final @NotNull DatColumn canBeObstructed;
    private final @NotNull DatColumn fleeable;

    private final @NotNull DatColumn weapon;
    private final @NotNull DatColumn technology;

    private final @NotNull DatColumn animation;
    private final @NotNull DatColumn highlight;

    private final @NotNull DatColumn requirementIndex;

    private final @NotNull DatColumn obscuredOrder;

    private final @NotNull RequirementIndexColumn requirementIndices;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrOrderTypeView} instance over the columns of a loader.
     *
     * @param columns              the column of each {@link Column}, shared and not copied
     * @param requirementIndexName the loader's name of the column, shown in requirement diagnostics
     * @param overrunReader        the reader of an element outside a column
     */
    public ScrOrderTypeView(@NotNull Function<Column, @NotNull DatColumn> columns,
                            @NotNull String requirementIndexName,
                            @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.label = columns.apply(Column.LABEL);

        this.useWeaponTargeting   = columns.apply(Column.USE_WEAPON_TARGETING);
        this.subunitInheritance   = columns.apply(Column.SUBUNIT_INHERITANCE);
        this.canBeInterrupted     = columns.apply(Column.CAN_BE_INTERRUPTED);
        this.canBeLifted          = columns.apply(Column.CAN_BE_LIFTED);
        this.canBeQueued          = columns.apply(Column.CAN_BE_QUEUED);
        this.disablingKeepsTarget = columns.apply(Column.DISABLING_KEEPS_TARGET);
        this.canBeObstructed      = columns.apply(Column.CAN_BE_OBSTRUCTED);
        this.fleeable             = columns.apply(Column.FLEEABLE);

        this.weapon     = columns.apply(Column.WEAPON);
        this.technology = columns.apply(Column.TECHNOLOGY);

        this.animation = columns.apply(Column.ANIMATION);
        this.highlight = columns.apply(Column.HIGHLIGHT);

        this.requirementIndex = columns.apply(Column.REQUIREMENT_INDEX);

        this.obscuredOrder = columns.apply(Column.OBSCURED_ORDER);

        this.requirementIndices = new RequirementIndexColumn(requirementIndexName, this.requirementIndex);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.label.getLength();
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the stat_txt.tbl string of the order name
     */
    public int getLabel(int orderIdentifier) {
        DatColumn values = this.label;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.LABEL, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return non-zero if the order targets with the weapon target flags
     */
    public int getUseWeaponTargeting(int orderIdentifier) {
        DatColumn values = this.useWeaponTargeting;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.USE_WEAPON_TARGETING, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return non-zero if a subunit takes over the order
     */
    public int getSubunitInheritance(int orderIdentifier) {
        DatColumn values = this.subunitInheritance;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.SUBUNIT_INHERITANCE, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return non-zero if the order can be interrupted
     */
    public int getCanBeInterrupted(int orderIdentifier) {
        DatColumn values = this.canBeInterrupted;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.CAN_BE_INTERRUPTED, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the {@code Can Be Lifted} byte
     */
    public int getCanBeLifted(int orderIdentifier) {
        DatColumn values = this.canBeLifted;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.CAN_BE_LIFTED, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return non-zero if the order can be queued
     */
    public int getCanBeQueued(int orderIdentifier) {
        DatColumn values = this.canBeQueued;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.CAN_BE_QUEUED, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return non-zero if the order keeps its target when the unit is disabled
     */
    public int getDisablingKeepsTarget(int orderIdentifier) {
        DatColumn values = this.disablingKeepsTarget;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.DISABLING_KEEPS_TARGET, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return non-zero if the order can be obstructed
     */
    public int getCanBeObstructed(int orderIdentifier) {
        DatColumn values = this.canBeObstructed;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.CAN_BE_OBSTRUCTED, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the {@code Fleeable} byte
     */
    public int getFleeable(int orderIdentifier) {
        DatColumn values = this.fleeable;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.FLEEABLE, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the weapons.dat entry the order uses, {@code 130} for none
     */
    public int getWeapon(int orderIdentifier) {
        DatColumn values = this.weapon;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.WEAPON, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the techdata.dat entry whose energy the order spends, {@code 44} for none
     */
    public int getTechnology(int orderIdentifier) {
        DatColumn values = this.technology;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.TECHNOLOGY, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the iscript animation the order plays
     */
    public int getAnimation(int orderIdentifier) {
        DatColumn values = this.animation;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.ANIMATION, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the command icon highlighted while the order runs, {@link #NO_HIGHLIGHT} for none
     */
    public int getHighlight(int orderIdentifier) {
        DatColumn values = this.highlight;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.HIGHLIGHT, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the position of the order in the order requirement script, {@code 0xFFFF} until built
     */
    public int getRequirementIndex(int orderIdentifier) {
        DatColumn values = this.requirementIndex;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.REQUIREMENT_INDEX, orderIdentifier);
    }

    /**
     * @param orderIdentifier the orders.dat entry
     * @return the order used when the target is obscured
     */
    public int getObscuredOrder(int orderIdentifier) {
        DatColumn values = this.obscuredOrder;

        return orderIdentifier >= 0 && orderIdentifier < values.getLength()
               ? values.get(orderIdentifier)
               : this.overrunReader.read(Column.OBSCURED_ORDER, orderIdentifier);
    }

    /**
     * @return the writable {@link #getRequirementIndex(int)} column, which the requirement tests build at run time
     */
    public @NotNull RequirementIndexColumn getRequirementIndices() {
        return this.requirementIndices;
    }
}
