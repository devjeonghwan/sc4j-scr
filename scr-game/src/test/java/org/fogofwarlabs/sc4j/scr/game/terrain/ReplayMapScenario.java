package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Loads the map of a repository replay with its installed tileset, its expanded tile flags and its terrain borders.
 */
final class ReplayMapScenario {
    /**
     * Creates a {@link ReplayMapScenario} instance from the scenario of a repository replay: the installed tileset of the scenario, and a
     * {@link GameMap} with the tile identifiers, their expanded tile flags and the terrain borders.
     *
     * @param storage        the installed game storage that holds the tileset
     * @param replayResource the replay, one of the {@link GameTestResources} replays
     * @return the created {@link ReplayMapScenario} instance
     * @throws IOException if the replay, its scenario or the tileset cannot be read
     */
    static @NotNull ReplayMapScenario load(@NotNull CascStorage storage, @NotNull String replayResource) throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(replayResource)));
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        ChkFile mapFile = ChkFile.parseTolerant(mapData);
        TerrainTileset terrain = TerrainTileset.load(storage, mapFile.getTileset());
        int[] identifiers = mapFile.getTileIds(terrain.getGroupCount());
        GameMap map = new GameMap(mapFile.getWidth(), mapFile.getHeight(), mapFile.getTileset(), identifiers, terrain.expandTileFlags(identifiers));

        map.initializeTerrainBorders();

        return new ReplayMapScenario(terrain, identifiers, map);
    }

    private final @NotNull TerrainTileset terrain;
    private final int @NotNull [] tileIdentifiers;
    private final @NotNull GameMap map;

    /**
     * Creates a {@link ReplayMapScenario} instance.
     *
     * @param terrain         the installed tileset of the map
     * @param tileIdentifiers the tile identifiers of the map in row-major order
     * @param map             the map with the expanded tile flags and the terrain borders
     */
    private ReplayMapScenario(@NotNull TerrainTileset terrain, int @NotNull [] tileIdentifiers, @NotNull GameMap map) {
        this.terrain         = terrain;
        this.tileIdentifiers = tileIdentifiers;
        this.map             = map;
    }

    /**
     * @return the installed tileset of the map
     */
    @NotNull TerrainTileset getTerrain() {
        return this.terrain;
    }

    /**
     * @return the tile identifiers of the map in row-major order
     */
    int @NotNull [] getTileIdentifiers() {
        return this.tileIdentifiers;
    }

    /**
     * @return the map with the expanded tile flags and the terrain borders
     */
    @NotNull GameMap getMap() {
        return this.map;
    }
}
