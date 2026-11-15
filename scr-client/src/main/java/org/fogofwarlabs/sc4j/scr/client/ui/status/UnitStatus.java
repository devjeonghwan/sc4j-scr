package org.fogofwarlabs.sc4j.scr.client.ui.status;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUpgradeTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Retains the selection-panel values read for one unit.
 * Hit points and energy use domain fixed-point values. Queue entries start at the current build slot.
 * Each read refreshes production progress while reusing the existing queue array.
 */
public final class UnitStatus {
    /**
     * The progress value of a unit without production, research or construction.
     */
    public static final int NO_PROGRESS = -1;

    /**
     * The mask of the upgrade research time, a WORD.
     */
    private static final int RESEARCH_TIME_MASK = 0xFFFF;

    private final int @NotNull [] queue;

    private int unitType;
    private int owner;
    private int hitPoints;
    private int energy;
    private boolean building;
    private boolean armed;
    private int resources;
    private int status;
    private int queueCount;
    private int research;
    private int upgrade;
    private int progress;
    private int progressTotal;

    /**
     * Creates an empty {@link UnitStatus} instance.
     */
    public UnitStatus() {
        this.queue = new int[Unit.BUILD_QUEUE_SLOTS];

        this.unitType      = 0;
        this.owner         = 0;
        this.hitPoints     = 0;
        this.energy        = 0;
        this.building      = false;
        this.armed         = false;
        this.resources     = 0;
        this.status        = 0;
        this.queueCount    = 0;
        this.research      = 0;
        this.upgrade       = 0;
        this.progress      = NO_PROGRESS;
        this.progressTotal = 0;
    }

    /**
     * @return the units.dat type
     */
    public int getUnitType() {
        return this.unitType;
    }

    /**
     * @return the owning player
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the unit status flags
     */
    public int getStatus() {
        return this.status;
    }

    /**
     * @return the hit points in {@code 1/256} units
     */
    public int getHitPoints() {
        return this.hitPoints;
    }

    /**
     * @return {@code true} if the unit type is a building (units.dat flag {@code 1}), {@code false} otherwise
     */
    public boolean isBuilding() {
        return this.building;
    }

    /**
     * @return {@code true} if the unit can attack, {@code false} otherwise
     */
    public boolean isArmed() {
        return this.armed;
    }

    /**
     * @return the energy in {@code 1/256} units
     */
    public int getEnergy() {
        return this.energy;
    }

    /**
     * @return the remaining resources of a resource container
     */
    public int getResources() {
        return this.resources;
    }

    /**
     * @return the number of queued production entries
     */
    public int getQueueCount() {
        return this.queueCount;
    }

    /**
     * @param index the queue position from {@code 0}, the entry in production
     * @return the queued units.dat type
     */
    public int getQueuedType(int index) {
        return this.queue[index];
    }

    /**
     * @return the technology a building researches, or {@code 44}
     */
    public int getResearch() {
        return this.research;
    }

    /**
     * @return the upgrade a building researches, or {@code 61}
     */
    public int getUpgrade() {
        return this.upgrade;
    }

    /**
     * @return the elapsed frames of the construction, research, upgrade or production, or {@link #NO_PROGRESS}
     */
    public int getProgress() {
        return this.progress;
    }

    /**
     * @return the total frames of the progress
     */
    public int getProgressTotal() {
        return this.progressTotal;
    }


    /**
     * Reads the displayed values of a unit.
     *
     * @param view the game view
     * @param unit the unit
     * @throws NullPointerException if the world was built without game data
     */
    public void read(@NotNull ScrGameView view, @NotNull Unit unit) {
        ScrUnitTypeView units = view.requireGameData().getUnitTypes();

        this.unitType = unit.getUnitId();
        this.owner    = unit.getOwnerIndex();
        this.status   = unit.getStatus();

        this.hitPoints = unit.getHitPoints();

        int abilities = units.getFlags(this.unitType);

        this.building = (abilities & UnitTypeFlags.BUILDING) != 0;
        this.armed    = view.getUnitQueries().canAttack(unit);

        this.energy = unit.getEnergy();

        this.resources = unit.getResources();

        // Research and upgrade fields belong to buildings; other units reuse the same storage.
        this.research   = this.building ? unit.getResearchType() : ScrTechIds.NONE;
        this.upgrade    = this.building ? unit.getUpgradeType() : ScrUpgradeIds.NONE;
        this.queueCount = 0;

        Arrays.fill(this.queue, ScrUnitIds.NONE);

        int head = unit.getBuildQueueIndex();

        for (int offset = 0; offset < Unit.BUILD_QUEUE_SLOTS; offset++) {
            int queued = unit.getBuildQueueUnitType((head + offset) % Unit.BUILD_QUEUE_SLOTS);

            if (queued == ScrUnitIds.NONE) {
                break;
            }

            this.queue[this.queueCount++] = queued;
        }

        this.readProgress(view, unit, units);
    }

    /**
     * Reads the active construction, research, upgrade or queued-unit progress.
     *
     * @param view  the game view
     * @param unit  the displayed unit
     * @param units the unit type definitions
     * @throws NullPointerException if the world was built without game data
     */
    private void readProgress(@NotNull ScrGameView view, @NotNull Unit unit, @NotNull ScrUnitTypeView units) {
        this.progress      = NO_PROGRESS;
        this.progressTotal = 0;

        if ((this.status & UnitStatusFlags.COMPLETED) == 0) {
            this.progressTotal = units.getBuildTime(this.unitType);
            this.progress      = this.progressTotal - unit.getRemainingBuildTime();

            return;
        }

        if (this.research != ScrTechIds.NONE) {
            this.progressTotal = view.requireGameData().getTechTypes().getResearchTime(this.research);
            this.progress      = this.progressTotal - unit.getUpgradeResearchTime();

            return;
        }

        if (this.upgrade != ScrUpgradeIds.NONE) {
            // The level in progress is the current level; its time is the base plus the factor per level.
            ScrUpgradeTypeView upgrades = view.requireGameData().getUpgradeTypes();
            int level = StatusPanel.getUpgradeLevel(view, this.owner, this.upgrade);

            this.progressTotal = upgrades.getResearchTime(this.upgrade) + upgrades.getResearchTimeFactor(this.upgrade) * level & RESEARCH_TIME_MASK;
            this.progress      = this.progressTotal - unit.getUpgradeResearchTime();

            return;
        }

        Unit built = unit.getCurrentBuildUnit();

        if (this.queueCount > 0 && built != null && (built.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            this.progressTotal = units.getBuildTime(built.getUnitId());
            this.progress      = this.progressTotal - built.getRemainingBuildTime();
        }
    }
}
