package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * Searches the ordered unit-finder edges for the resource depot of a returning worker.
 */
public final class UnitResourceSearch {
    /**
     * The mask of the owner byte.
     */
    private static final int BYTE_MASK = 0xFF;


    private final @NotNull Game game;
    private final @NotNull BiPredicate<Unit, Unit> depotPredicate;

    /**
     * Creates a {@link UnitResourceSearch} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitResourceSearch(@NotNull Game game) {
        this.game           = game;
        this.depotPredicate = this::isReachableDepot;
    }


    /**
     * Finds the nearest active resource depot of the worker's owner that its ground-path query accepts.
     * <p>
     * No finder or terrain state is acquired when the owner has none of the completed depot types.
     * Otherwise the ordered edge search runs each candidate test before distance comparison, retaining directional tie order.
     *
     * @param worker the returning worker
     * @return the nearest qualifying depot, or {@code null}
     * @throws UnsupportedOperationException if the unit spatial index is missing or the unit accounting is not initialized
     */
    public @Nullable Unit findDepot(@NotNull Unit worker) {
        int owner = worker.getOwnerIndex() & BYTE_MASK;
        UnitAccounting accounting = this.game.getPlayerTable().requireUnitAccounting();

        if (accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HATCHERY, owner) == 0 &&
            accounting.getCompletedTypeCount(ScrUnitIds.PROTOSS_NEXUS, owner) == 0 &&
            accounting.getCompletedTypeCount(ScrUnitIds.TERRAN_COMMAND_CENTER, owner) == 0 &&
            accounting.getCompletedTypeCount(ScrUnitIds.ZERG_LAIR, owner) == 0 &&
            accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HIVE, owner) == 0) {
            return null;
        }

        UnitSpatialIndex index = this.game.getUnitSystem().getUnitSpatialIndex();

        if (index == null) {
            throw Exceptions.newUnsupportedOperationException("Resource depot search requires the unit spatial index.");
        }

        return index.findNearest(worker,
                                 0, 0,
                                 this.game.getMap().getWidth() * TerrainTileset.TILE_SIZE, this.game.getMap().getHeight() * TerrainTileset.TILE_SIZE,
                                 this.depotPredicate);
    }

    private boolean isReachableDepot(@NotNull Unit candidate, @NotNull Unit worker) {
        if (!this.isActiveDepot(candidate) || candidate.getOwnerIndex() != worker.getOwnerIndex()) {
            return false;
        }

        int candidateY = (short) candidate.requireSprite().getY();
        int candidateX = (short) candidate.requireSprite().getX();

        return this.game.getEngineGame().getPathfinding().isReachable(worker, candidateX, candidateY);
    }


    /**
     * Checks whether a unit is an active resource depot: a grounded resource depot building that is completed, a Lair or
     * Hive, or a Hatchery morphing into one of the Zerg buildings that stay depots.
     *
     * @param unit the unit
     * @return {@code true} if workers may return resources to the unit, {@code false} otherwise
     * @throws NullPointerException if the unit is a grounded building and the world was built without game data
     */
    public boolean isActiveDepot(@NotNull Unit unit) {
        if (!UnitQueries.isGroundedBuilding(unit) ||
            (this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_DEPOT) == 0) {
            return false;
        }

        if ((unit.getStatus() & UnitStatusFlags.COMPLETED) != 0 ||
            unit.getUnitId() == ScrUnitIds.ZERG_LAIR ||
            unit.getUnitId() == ScrUnitIds.ZERG_HIVE) {
            return true;
        }

        return unit.getUnitId() == ScrUnitIds.ZERG_HATCHERY && UnitQueries.isBuildingMorph(this.game.requireGameData().getUnitTypes(), unit);
    }
}
