package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code upgrades.dat} data of every upgrade, indexed by its {@code int} id (the constants of {@link ScrUpgradeIds}).
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD and map-setting writes to the columns are visible. An id outside a column has no
 * bound check: it reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class ScrUpgradeTypeView {
    /**
     * Names the columns a loader supplies to {@link ScrUpgradeTypeView#ScrUpgradeTypeView(Function, String, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrUpgradeTypeView#getMineralCost(int)}.
         */
        MINERAL_COST,
        /**
         * The column of {@link ScrUpgradeTypeView#getMineralCostFactor(int)}.
         */
        MINERAL_COST_FACTOR,
        /**
         * The column of {@link ScrUpgradeTypeView#getGasCost(int)}.
         */
        GAS_COST,
        /**
         * The column of {@link ScrUpgradeTypeView#getGasCostFactor(int)}.
         */
        GAS_COST_FACTOR,
        /**
         * The column of {@link ScrUpgradeTypeView#getResearchTime(int)}.
         */
        RESEARCH_TIME,
        /**
         * The column of {@link ScrUpgradeTypeView#getResearchTimeFactor(int)}.
         */
        RESEARCH_TIME_FACTOR,
        /**
         * The column of {@link ScrUpgradeTypeView#getRequirementIndex(int)}.
         */
        REQUIREMENT_INDEX,
        /**
         * The column of {@link ScrUpgradeTypeView#getIcon(int)}.
         */
        ICON,
        /**
         * The column of {@link ScrUpgradeTypeView#getLabel(int)}.
         */
        LABEL,
        /**
         * The column of {@link ScrUpgradeTypeView#getRace(int)}.
         */
        RACE,
        /**
         * The column of {@link ScrUpgradeTypeView#getMaximumLevel(int)}.
         */
        MAXIMUM_LEVEL,
        /**
         * The column of {@link ScrUpgradeTypeView#getBroodWarFlag(int)}.
         */
        BROOD_WAR_FLAG
    }

    private final @NotNull DatColumn mineralCost;
    private final @NotNull DatColumn mineralCostFactor;
    private final @NotNull DatColumn gasCost;
    private final @NotNull DatColumn gasCostFactor;
    private final @NotNull DatColumn researchTime;
    private final @NotNull DatColumn researchTimeFactor;

    private final @NotNull DatColumn requirementIndex;

    private final @NotNull DatColumn icon;
    private final @NotNull DatColumn label;
    private final @NotNull DatColumn race;

    private final @NotNull DatColumn maximumLevel;
    private final @NotNull DatColumn broodWarFlag;

    private final @NotNull RequirementIndexColumn requirementIndices;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrUpgradeTypeView} instance over the columns of a loader.
     *
     * @param columns              the column of each {@link Column}, shared and not copied
     * @param requirementIndexName the loader's name of the column, shown in requirement diagnostics
     * @param overrunReader        the reader of an element outside a column
     */
    public ScrUpgradeTypeView(@NotNull Function<Column, @NotNull DatColumn> columns,
                              @NotNull String requirementIndexName,
                              @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.mineralCost        = columns.apply(Column.MINERAL_COST);
        this.mineralCostFactor  = columns.apply(Column.MINERAL_COST_FACTOR);
        this.gasCost            = columns.apply(Column.GAS_COST);
        this.gasCostFactor      = columns.apply(Column.GAS_COST_FACTOR);
        this.researchTime       = columns.apply(Column.RESEARCH_TIME);
        this.researchTimeFactor = columns.apply(Column.RESEARCH_TIME_FACTOR);

        this.requirementIndex = columns.apply(Column.REQUIREMENT_INDEX);

        this.icon  = columns.apply(Column.ICON);
        this.label = columns.apply(Column.LABEL);
        this.race  = columns.apply(Column.RACE);

        this.maximumLevel = columns.apply(Column.MAXIMUM_LEVEL);
        this.broodWarFlag = columns.apply(Column.BROOD_WAR_FLAG);

        this.requirementIndices = new RequirementIndexColumn(requirementIndexName, this.requirementIndex);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.mineralCost.getLength();
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the mineral cost of the first level
     */
    public int getMineralCost(int upgradeIdentifier) {
        DatColumn values = this.mineralCost;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.MINERAL_COST, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the mineral cost added per level
     */
    public int getMineralCostFactor(int upgradeIdentifier) {
        DatColumn values = this.mineralCostFactor;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.MINERAL_COST_FACTOR, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the vespene gas cost of the first level
     */
    public int getGasCost(int upgradeIdentifier) {
        DatColumn values = this.gasCost;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.GAS_COST, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the vespene gas cost added per level
     */
    public int getGasCostFactor(int upgradeIdentifier) {
        DatColumn values = this.gasCostFactor;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.GAS_COST_FACTOR, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the research time of the first level in frames
     */
    public int getResearchTime(int upgradeIdentifier) {
        DatColumn values = this.researchTime;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.RESEARCH_TIME, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the research time added per level in frames
     */
    public int getResearchTimeFactor(int upgradeIdentifier) {
        DatColumn values = this.researchTimeFactor;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.RESEARCH_TIME_FACTOR, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the position of the upgrade in the upgrade requirement script, {@code 0xFFFF} until built
     */
    public int getRequirementIndex(int upgradeIdentifier) {
        DatColumn values = this.requirementIndex;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.REQUIREMENT_INDEX, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the icon frame
     */
    public int getIcon(int upgradeIdentifier) {
        DatColumn values = this.icon;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.ICON, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the stat_txt.tbl string of the upgrade name
     */
    public int getLabel(int upgradeIdentifier) {
        DatColumn values = this.label;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.LABEL, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the race
     */
    public int getRace(int upgradeIdentifier) {
        DatColumn values = this.race;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.RACE, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return the maximum level
     */
    public int getMaximumLevel(int upgradeIdentifier) {
        DatColumn values = this.maximumLevel;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.MAXIMUM_LEVEL, upgradeIdentifier);
    }

    /**
     * @param upgradeIdentifier the upgrades.dat entry
     * @return non-zero if the upgrade belongs to Brood War
     */
    public int getBroodWarFlag(int upgradeIdentifier) {
        DatColumn values = this.broodWarFlag;

        return upgradeIdentifier >= 0 && upgradeIdentifier < values.getLength()
               ? values.get(upgradeIdentifier)
               : this.overrunReader.read(Column.BROOD_WAR_FLAG, upgradeIdentifier);
    }

    /**
     * @return the writable {@link #getRequirementIndex(int)} column, which the requirement tests build at run time
     */
    public @NotNull RequirementIndexColumn getRequirementIndices() {
        return this.requirementIndices;
    }
}
