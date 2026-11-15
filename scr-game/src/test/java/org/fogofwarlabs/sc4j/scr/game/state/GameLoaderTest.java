package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayLoaderUtils;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks the initialization ledger that {@link GameLoader} creates from the unit and doodad sections of a map, and the replay inspection
 * state of the same map.
 */
@RequiresInstallation
final class GameLoaderTest {
    /**
     * The layout of a UNIT section record.
     */
    private static final int UNIT_RECORD_SIZE = 36;
    private static final int UNIT_HORIZONTAL_OFFSET = 4;
    private static final int UNIT_VERTICAL_OFFSET = 6;
    private static final int UNIT_TYPE_OFFSET = 8;
    private static final int UNIT_OWNER_OFFSET = 16;
    /**
     * The section tags of the map fixtures, read as little-endian DWORDs.
     */
    private static final int DIMENSIONS_TAG = 0x204d4944;
    private static final int TILESET_TAG = 0x20415245;
    private static final int TILE_MAP_TAG = 0x4d58544d;
    private static final int UNIT_TAG = 0x54494e55;
    private static final int DOODAD_TAG = 0x32474854;
    /**
     * The bytes of the map fixture: the dimension, tileset and tile map sections and two pairs of a unit and a doodad section.
     */
    private static final int MAP_FIXTURE_SIZE = 218;
    /**
     * The width and height of the fixture map in tiles and the bytes of its tile map section.
     */
    private static final int MAP_WIDTH = 8;
    private static final int MAP_HEIGHT = 4;
    private static final int TILE_MAP_SIZE = 64;
    /**
     * The number of unit and doodad section pairs, the serial number of the first unit and the pixel position of every unit.
     */
    private static final int SECTION_PAIR_COUNT = 2;
    private static final int FIRST_UNIT_SERIAL = 101;
    private static final int UNIT_POSITION = 64;
    /**
     * The doodad records: the record size, the type of the first and of the second record, the position, the owner of the first record and the
     * flags of the first and of the second record.
     */
    private static final int DOODAD_RECORD_SIZE = 10;
    private static final int FIRST_DOODAD_TYPE = 207;
    private static final int SECOND_DOODAD_TYPE = 36;
    private static final int DOODAD_HORIZONTAL_POSITION = 65;
    private static final int DOODAD_VERTICAL_POSITION = 66;
    private static final int FIRST_DOODAD_OWNER = 255;
    private static final int FIRST_DOODAD_FLAGS = 0x8000;
    private static final int SECOND_DOODAD_FLAGS = 0x1000;
    /**
     * The expected section header offsets and record offsets of the four initialization requests.
     */
    private static final @NotNull List<Integer> SECTION_HEADER_OFFSETS = List.of(138, 200, 94, 156);
    private static final @NotNull List<Integer> RECORD_OFFSETS = List.of(146, 208, 102, 164);
    /**
     * The owner of a neutral unit.
     */
    private static final int NEUTRAL_PLAYER = 11;

    private static @Nullable GameData gameData;

    /**
     * Loads the installed game resources.
     *
     * @throws IOException if the installed game resources cannot be read
     */
    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    /**
     * Closes the installed game resources when they were loaded.
     */
    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void create_initialization_ledger_should_keep_thingy_provenance_with_repeated_unit_sections() throws IOException {
        // The replay supplies the game settings of the ledger and the replay of the inspection state.
        Path replayPath = GameTestResources.getPath(GameTestResources.REPLAY_PVP_FORTRESS);

        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(replayPath));
        ByteBuffer content = ByteBuffer.allocate(MAP_FIXTURE_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        content.putInt(DIMENSIONS_TAG).putInt(2 * Short.BYTES).putShort((short) MAP_WIDTH).putShort((short) MAP_HEIGHT);
        content.putInt(TILESET_TAG).putInt(Short.BYTES).putShort((short) 0);
        content.putInt(TILE_MAP_TAG).putInt(TILE_MAP_SIZE).put(new byte[TILE_MAP_SIZE]);

        for (int recordIndex = 0; recordIndex < SECTION_PAIR_COUNT; recordIndex++) {
            content.putInt(UNIT_TAG).putInt(UNIT_RECORD_SIZE);

            int unitOffset = content.position();

            content.putInt(unitOffset, FIRST_UNIT_SERIAL + recordIndex);
            content.putShort(unitOffset + UNIT_HORIZONTAL_OFFSET, (short) UNIT_POSITION)
                   .putShort(unitOffset + UNIT_VERTICAL_OFFSET, (short) UNIT_POSITION);
            content.putShort(unitOffset + UNIT_TYPE_OFFSET, (short) ScrUnitIds.RESOURCE_MINERAL_FIELD);
            content.put(unitOffset + UNIT_OWNER_OFFSET, (byte) PlayerTable.NEUTRAL_PLAYER_ID);
            content.position(unitOffset + UNIT_RECORD_SIZE);
            content.putInt(DOODAD_TAG).putInt(DOODAD_RECORD_SIZE);
            content.putShort((short) (recordIndex == 0 ? FIRST_DOODAD_TYPE : SECOND_DOODAD_TYPE))
                   .putShort((short) DOODAD_HORIZONTAL_POSITION)
                   .putShort((short) DOODAD_VERTICAL_POSITION);
            content.put((byte) (recordIndex == 0 ? FIRST_DOODAD_OWNER : 0))
                   .put((byte) 0)
                   .putShort((short) (recordIndex == 0 ? FIRST_DOODAD_FLAGS : SECOND_DOODAD_FLAGS));
        }

        ChkFile mapFile = ChkFile.parseTolerant(content.array());
        List<GameLoader.InitializationRequest> requests = GameLoader.createInitializationLedger(replay.getHeader().getSettings(), mapFile);

        Assertions.assertEquals(List.of(GameLoader.InitializationRoute.THG2_UNIT_WRAPPER, GameLoader.InitializationRoute.THG2_SPRITE_CONSTRUCTOR,
                                        GameLoader.InitializationRoute.UNIT_RECORD, GameLoader.InitializationRoute.UNIT_RECORD),
                                requests.stream().map(GameLoader.InitializationRequest::getRoute).toList());
        Assertions.assertEquals(SECTION_HEADER_OFFSETS, requests.stream().map(GameLoader.InitializationRequest::getSectionHeaderOffset).toList());
        Assertions.assertEquals(RECORD_OFFSETS, requests.stream().map(GameLoader.InitializationRequest::getRecordOffset).toList());
        Assertions.assertEquals(NEUTRAL_PLAYER, requests.getFirst().getOwner());
        Assertions.assertEquals(DOODAD_HORIZONTAL_POSITION, requests.getFirst().getHorizontal());
        Assertions.assertEquals(FIRST_DOODAD_FLAGS, requests.getFirst().getFlags());
        Assertions.assertThrows(UnsupportedOperationException.class, requests::clear);

        Assertions.assertNotNull(gameData);

        Game state = ReplayLoaderUtils.createReplayInspectionState(replay, mapFile, gameData);

        Assertions.assertNull(state.getUnitSystem().getUnitPool());
        Assertions.assertTrue(state.getMapUnitCreations().isEmpty());
    }
}
