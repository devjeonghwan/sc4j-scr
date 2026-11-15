package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.engine.map.MapModel;
import org.fogofwarlabs.sc4j.engine.map.PreplacedUnit;
import org.fogofwarlabs.sc4j.engine.map.StartLocation;
import org.fogofwarlabs.sc4j.engine.terrain.TerrainGrid;
import org.fogofwarlabs.sc4j.scr.content.ScrNativeIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills the engine map model ({@link MapModel}) from a parsed CHK map: the game's terrain, the tileset name of the ERA section, the start
 * locations and the preplaced units of the UNIT section, and the CHK file itself as the mod's map data (triggers, locations, strings, the SC:R
 * unit properties and the rest of its sections).
 * <p>
 * A start location record of a playable slot ({@code 0} through {@code 7}) is a start location; every other record of a unit type of the content
 * is a preplaced unit, before the game's preplacement filters (map settings, melee restrictions) the SC:R loader applies.
 */
public final class ScrMapModelUtils {
    /**
     * The mask of an unsigned WORD: a CHK unit type or coordinate.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of an unsigned BYTE: a CHK owner.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Creates a {@link MapModel} instance of a CHK map.
     *
     * @param mapFile the parsed map
     * @param terrain the terrain of the map
     * @return the created {@link MapModel} instance
     * @throws IllegalArgumentException if the map's tileset is unsupported
     */
    public static @NotNull MapModel create(@NotNull ChkFile mapFile, @NotNull TerrainGrid terrain) {
        List<StartLocation> startLocations = new ArrayList<>();
        List<PreplacedUnit> preplacedUnits = new ArrayList<>();

        for (ChkUnit record : mapFile.getUnits()) {
            int unitType = record.getUnitId() & WORD_MASK;
            int owner = record.getOwner() & BYTE_MASK;
            int positionX = record.getX() & WORD_MASK;
            int positionY = record.getY() & WORD_MASK;

            if (unitType == ScrUnitIds.SPECIAL_START_LOCATION) {
                if (owner < PlayerTable.PLAYABLE_PLAYER_SLOTS) {
                    startLocations.add(new StartLocation(owner, positionX, positionY));
                }

                continue;
            }

            UnitType type = ScrNativeIds.unitOf(unitType);

            if (type != null) {
                preplacedUnits.add(new PreplacedUnit(type, owner, positionX, positionY));
            }
        }

        return new MapModel(terrain, TerrainTileset.getTilesetName(mapFile.getTileset()), startLocations, preplacedUnits, mapFile);
    }

    private ScrMapModelUtils() {
    }
}
