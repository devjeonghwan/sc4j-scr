package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code techdata.dat} data of every technology, indexed by its {@code int} id (the constants of {@link ScrTechIds}).
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD and map-setting writes to the columns are visible. An id outside a column has no
 * bound check: it reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class ScrTechTypeView {
    /**
     * Names the columns a loader supplies to {@link ScrTechTypeView#ScrTechTypeView(Function, String, String, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrTechTypeView#getMineralCost(int)}.
         */
        MINERAL_COST,
        /**
         * The column of {@link ScrTechTypeView#getGasCost(int)}.
         */
        GAS_COST,
        /**
         * The column of {@link ScrTechTypeView#getResearchTime(int)}.
         */
        RESEARCH_TIME,
        /**
         * The column of {@link ScrTechTypeView#getEnergyCost(int)}.
         */
        ENERGY_COST,
        /**
         * The column of {@link ScrTechTypeView#getResearchRequirementIndex(int)}.
         */
        RESEARCH_REQUIREMENT_INDEX,
        /**
         * The column of {@link ScrTechTypeView#getUseRequirementIndex(int)}.
         */
        USE_REQUIREMENT_INDEX,
        /**
         * The column of {@link ScrTechTypeView#getIcon(int)}.
         */
        ICON,
        /**
         * The column of {@link ScrTechTypeView#getLabel(int)}.
         */
        LABEL,
        /**
         * The column of {@link ScrTechTypeView#getRace(int)}.
         */
        RACE,
        /**
         * The column of {@link ScrTechTypeView#getResearchedFlag(int)}.
         */
        RESEARCHED_FLAG,
        /**
         * The column of {@link ScrTechTypeView#getBroodWarFlag(int)}.
         */
        BROOD_WAR_FLAG
    }

    private final @NotNull DatColumn mineralCost;
    private final @NotNull DatColumn gasCost;
    private final @NotNull DatColumn researchTime;
    private final @NotNull DatColumn energyCost;

    private final @NotNull DatColumn researchRequirementIndex;
    private final @NotNull DatColumn useRequirementIndex;

    private final @NotNull DatColumn icon;
    private final @NotNull DatColumn label;
    private final @NotNull DatColumn race;

    private final @NotNull DatColumn researchedFlag;
    private final @NotNull DatColumn broodWarFlag;

    private final @NotNull RequirementIndexColumn researchRequirementIndices;
    private final @NotNull RequirementIndexColumn useRequirementIndices;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrTechTypeView} instance over the columns of a loader.
     *
     * @param columns                      the column of each {@link Column}, shared and not copied
     * @param researchRequirementIndexName the loader's name of the research requirement index column, shown in requirement diagnostics
     * @param useRequirementIndexName      the loader's name of the use requirement index column, shown in requirement diagnostics
     * @param overrunReader                the reader of an element outside a column
     */
    public ScrTechTypeView(@NotNull Function<Column, @NotNull DatColumn> columns,
                           @NotNull String researchRequirementIndexName, @NotNull String useRequirementIndexName,
                           @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.mineralCost  = columns.apply(Column.MINERAL_COST);
        this.gasCost      = columns.apply(Column.GAS_COST);
        this.researchTime = columns.apply(Column.RESEARCH_TIME);
        this.energyCost   = columns.apply(Column.ENERGY_COST);

        this.researchRequirementIndex = columns.apply(Column.RESEARCH_REQUIREMENT_INDEX);
        this.useRequirementIndex      = columns.apply(Column.USE_REQUIREMENT_INDEX);

        this.icon  = columns.apply(Column.ICON);
        this.label = columns.apply(Column.LABEL);
        this.race  = columns.apply(Column.RACE);

        this.researchedFlag = columns.apply(Column.RESEARCHED_FLAG);
        this.broodWarFlag   = columns.apply(Column.BROOD_WAR_FLAG);

        this.researchRequirementIndices = new RequirementIndexColumn(researchRequirementIndexName, this.researchRequirementIndex);
        this.useRequirementIndices      = new RequirementIndexColumn(useRequirementIndexName, this.useRequirementIndex);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.mineralCost.getLength();
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the mineral cost of the research
     */
    public int getMineralCost(int technologyIdentifier) {
        DatColumn values = this.mineralCost;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.MINERAL_COST, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the vespene gas cost of the research
     */
    public int getGasCost(int technologyIdentifier) {
        DatColumn values = this.gasCost;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.GAS_COST, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the research time in frames
     */
    public int getResearchTime(int technologyIdentifier) {
        DatColumn values = this.researchTime;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.RESEARCH_TIME, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the energy cost of a use in whole points
     */
    public int getEnergyCost(int technologyIdentifier) {
        DatColumn values = this.energyCost;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.ENERGY_COST, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the position of the technology in the research requirement script, {@code 0xFFFF} until built
     */
    public int getResearchRequirementIndex(int technologyIdentifier) {
        DatColumn values = this.researchRequirementIndex;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.RESEARCH_REQUIREMENT_INDEX, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the position of the technology in the use requirement script, {@code 0xFFFF} until built
     */
    public int getUseRequirementIndex(int technologyIdentifier) {
        DatColumn values = this.useRequirementIndex;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.USE_REQUIREMENT_INDEX, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the icon frame
     */
    public int getIcon(int technologyIdentifier) {
        DatColumn values = this.icon;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.ICON, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the stat_txt.tbl string of the technology name
     */
    public int getLabel(int technologyIdentifier) {
        DatColumn values = this.label;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.LABEL, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the race
     */
    public int getRace(int technologyIdentifier) {
        DatColumn values = this.race;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.RACE, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return the {@code Researched} byte
     */
    public int getResearchedFlag(int technologyIdentifier) {
        DatColumn values = this.researchedFlag;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.RESEARCHED_FLAG, technologyIdentifier);
    }

    /**
     * @param technologyIdentifier the techdata.dat entry
     * @return non-zero if the technology belongs to Brood War
     */
    public int getBroodWarFlag(int technologyIdentifier) {
        DatColumn values = this.broodWarFlag;

        return technologyIdentifier >= 0 && technologyIdentifier < values.getLength()
               ? values.get(technologyIdentifier)
               : this.overrunReader.read(Column.BROOD_WAR_FLAG, technologyIdentifier);
    }

    /**
     * @return the writable {@link #getResearchRequirementIndex(int)} column, which the requirement tests build at run time
     */
    public @NotNull RequirementIndexColumn getResearchRequirementIndices() {
        return this.researchRequirementIndices;
    }

    /**
     * @return the writable {@link #getUseRequirementIndex(int)} column, which the requirement tests build at run time
     */
    public @NotNull RequirementIndexColumn getUseRequirementIndices() {
        return this.useRequirementIndices;
    }
}
