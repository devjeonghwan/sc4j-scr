package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.order.ScrOrderSystem;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Moves a completed grounded building for the Move Unit trigger action, updating its add-ons, visibility and creep.
 */
public final class UnitBuildingMove {
    private static final int WORD_MASK = 0xFFFF;
    private static final int FOOTPRINT_CENTER_DIVISOR = 2;

    /**
     * The mask that rounds a pixel coordinate down to its tile.
     */
    private static final int TILE_ROUNDING_MASK = ~(TerrainTileset.TILE_SIZE - 1);

    /**
     * Clamps a coordinate as a WORD so that the unit extent stays inside the map: a target whose extent leaves the map is
     * pulled back inside.
     *
     * @param coordinate    the requested coordinate
     * @param minimumExtent the unit extent below the coordinate
     * @param maximumExtent the unit extent above the coordinate
     * @param mapExtent     the map extent in pixels
     * @return the clamped coordinate
     */
    static int clampToMap(int coordinate, int minimumExtent, int maximumExtent, int mapExtent) {
        int low = (short) (coordinate - minimumExtent);
        int high = (short) (maximumExtent + coordinate);

        if (low < 0) {
            return coordinate - low;
        }

        if (high >= mapExtent) {
            return coordinate - 1 + (mapExtent - high);
        }

        return coordinate;
    }

    /**
     * Snaps a coordinate to the tile grid: the placement box's left edge, clamped to the map, is rounded down to its
     * tile.
     *
     * @param coordinate the clamped coordinate
     * @param halfExtent the half placement box extent
     * @param mapExtent  the map extent in pixels
     * @return the snapped coordinate
     */
    static int snapToTileGrid(int coordinate, int halfExtent, int mapExtent) {
        int edge = coordinate - halfExtent;

        if (edge < 0) {
            edge = 0;
        } else if (edge >= mapExtent) {
            edge = mapExtent - 1;
        }

        return (edge & TILE_ROUNDING_MASK) + halfExtent;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitBuildingMove} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitBuildingMove(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Moves a grounded building.
     * <ol>
     *     <li>Only a completed building moves.</li>
     *     <li>The target is kept inside the map by the unit dimensions and snapped to the tile grid by the placement
     *     box.</li>
     *     <li>The site must pass the trigger move placement test and the bounds test.</li>
     *     <li>A Terran building leaves its add-on, cancels an add-on under construction; an add-on leaves its
     *     parent.</li>
     *     <li>Detach, move and refresh; a Terran building then connects to an add-on at its new site.</li>
     *     <li>Creep: the old position stops providing; a creep spreader spreads under itself, provides again and takes
     *     secondary order SpreadCreep; a creep-requiring building fills its footprint and provides.</li>
     *     <li>The building power refresh is requested.</li>
     * </ol>
     *
     * @param building         the grounded building
     * @param positionX        the requested X (the location center)
     * @param positionY        the requested Y
     * @param instructionLimit the order and animation budget
     * @throws UnsupportedOperationException if the unit creation context or the creep field is not established
     * @throws NullPointerException          if the world was built without game data
     * @throws IllegalStateException         if the building has no sprite
     */
    public void move(@NotNull Unit building, int positionX, int positionY, int instructionLimit) {
        if ((building.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            return;
        }

        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        GameMap map = this.game.getMap();
        int type = building.getUnitId();
        int mapWidth = map.getWidth() * TerrainTileset.TILE_SIZE & WORD_MASK;
        int mapHeight = map.getHeight() * TerrainTileset.TILE_SIZE & WORD_MASK;
        int targetX = clampToMap(positionX, units.getDimensionLeft(type), units.getDimensionRight(type), mapWidth);
        int targetY = clampToMap(positionY, units.getDimensionUp(type), units.getDimensionDown(type), mapHeight);
        int halfWidth = (short) units.getPlacementWidth(type) / FOOTPRINT_CENTER_DIVISOR;
        int halfHeight = (short) units.getPlacementHeight(type) / FOOTPRINT_CENTER_DIVISOR;

        targetX = snapToTileGrid(targetX, halfWidth, mapWidth);
        targetY = snapToTileGrid(targetY, halfHeight, mapHeight);

        int tileX = (short) (targetX / TerrainTileset.TILE_SIZE - halfWidth / TerrainTileset.TILE_SIZE);
        int tileY = (short) (targetY / TerrainTileset.TILE_SIZE - halfHeight / TerrainTileset.TILE_SIZE);

        if (this.game.getUnitPlacement().evaluateTriggerMove(building.getOwnerIndex(), type, tileX, tileY) != 0) {
            return;
        }

        if (!(targetX - (short) units.getDimensionLeft(type) >= 0 && (short) units.getDimensionRight(type) + targetX < mapWidth &&
              targetY - (short) units.getDimensionUp(type) >= 0 && (short) units.getDimensionDown(type) + targetY < mapHeight)) {
            return;
        }

        Sprite sprite = building.requireSprite();
        int oldX = sprite.getX();
        int oldY = sprite.getY();
        boolean terran = UnitQueries.isGroundedBuilding(building) && UnitRacesUtils.getDisplayRace(units, type) == ScrRaceIds.TERRAN;

        if (terran) {
            if (building.getAddon() != null) {
                this.game.getUnitAddons().abandon(building, instructionLimit);
            }

            Unit addonUnderConstruction = building.getCurrentBuildUnit();

            if (building.getSecondaryOrder() == ScrOrderIds.BUILD_ADDON && addonUnderConstruction != null &&
                (addonUnderConstruction.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
                this.game.getUnitConstruction().cancelBuildingUnit(addonUnderConstruction, instructionLimit);
            }

            if ((units.getFlags(type) & UnitTypeFlags.ADDON) != 0) {
                Unit parent = this.findParent(building);

                if (parent != null) {
                    this.game.getUnitAddons().abandon(parent, instructionLimit);
                }
            }
        }

        if ((sprite.getFlags() & Sprite.FLAG_HIDDEN) == 0) {
            this.game.getUnitLifecycle().detach(building, false);
        }

        this.game.getUnitPositioning().move(building, targetX, targetY);

        UnitCreationContext context = this.game.getUnitSystem().getUnitCreationContext();

        if (context == null) {
            throw Exceptions.newUnsupportedOperationException("Moving a building requires the unit creation context.");
        }

        this.game.getUnitVisibility().refreshPosition(building, context.isFoliageEnabled());

        if (terran) {
            this.game.getUnitAddons().findAndConnect(building, instructionLimit);
        }

        CreepField creep = this.game.getCreepField();

        if (creep == null) {
            throw Exceptions.newUnsupportedOperationException("Moving a building requires the creep field.");
        }

        // Ignore the removal scan result; exhaustion of recession records does not retry the scan.
        creep.removeProvider(this.game, type, oldX, oldY, true);

        if (creep.addBuilding(this.game, building)) {
            ScrOrderSystem.setSecondaryOrder(building, ScrOrderIds.SPREAD_CREEP);
        }

        this.game.requestBuildingPowerRefresh();
    }

    /**
     * Finds the first active unit whose add-on is the given unit.
     *
     * @param addon the add-on
     * @return the parent of the add-on, or {@code null} if no active unit has it
     * @throws UnsupportedOperationException if the unit pool is not initialized
     */
    private @Nullable Unit findParent(@NotNull Unit addon) {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
            if (pool.getUnit(index).getAddon() == addon) {
                return pool.getUnit(index);
            }
        }

        return null;
    }
}
