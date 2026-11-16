package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Compares installed-map lobby setup with recorded settings and exercises game-type slot and force tables.
 */
@RequiresInstallation
final class LanLobbySetupTest {
    private static final @NotNull String HOST_NAME = "B";
    private static final int HOST_SLOT = 0;
    private static final int OPEN_SLOT = 1;
    private static final int UNUSED_SLOT = 2;
    private static final int MELEE_FORCE = 0;
    private static final int FIRST_FORCE = 1;

    private static final int HUMAN_TYPE = 2;
    private static final int OPEN_TYPE = 6;
    private static final int NONE_TYPE = 0;
    private static final int SELECTABLE_RACE = 7;

    /**
     * The {@code CRGB} section: eight RGB triples, then eight color modes.
     */
    private static final int CRGB_SIZE = 32;
    private static final int CRGB_MODES = 24;
    private static final int CRGB_TRIPLE_SIZE = 3;
    private static final int CRGB_RANDOM = 0;
    private static final int CRGB_PLAYER_CHOICE = 1;
    private static final int CRGB_MAP_COLOR = 2;
    private static final int CRGB_COLOR_INDEX = 3;
    /**
     * The slots of the generated {@code CRGB} section: the map's own color, a color index, a random color and the player's choice.
     */
    private static final int MAP_COLOR_SLOT = 0;
    private static final int COLOR_INDEX_SLOT = 1;
    private static final int RANDOM_COLOR_SLOT = 2;
    private static final int CHOSEN_COLOR_SLOT = 3;
    private static final int MAP_ORANGE = 0xFF8000;
    private static final int PINK_COLOR_ID = 15;
    private static final int MAP_SPECIFIED_COLOR = 0x17;

    private static @NotNull ChkFile loadBottleneckScenario() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, LanTestResources.CAPTURED_MAP_FILE_NAME);

        return ChkFile.parseTolerant(MpqArchive.open(mapPath).readFile("staredit\\scenario.chk"));
    }

    private static @NotNull LanLobby.Setup createLobbySetup(@NotNull LanGameOptions options) throws IOException {
        return createLobbySetup(loadBottleneckScenario(), options);
    }

    private static @NotNull LanLobby.Setup createLobbySetup(@NotNull ChkFile scenario, @NotNull LanGameOptions options) {
        return LanLobby.Setup.create(scenario, options,
                                     HOST_NAME,
                                     LanTestResources.CAPTURED_MAP_FILE_NAME,
                                     LanTestResources.CAPTURED_MAP_FILE_SIZE, LanTestResources.CAPTURED_MAP_CHECKSUM);
    }


    @Test
    void top_vs_bottom_on_bottleneck_matches_the_captured_lobby() throws IOException {
        LanLobby.Setup setup = createLobbySetup(LanGameOptions.TOP_VS_BOTTOM_1);
        LanLobby.Setup captured = LanTestResources.createCapturedLobbySetup(HOST_NAME);

        Assertions.assertEquals(HexFormat.of().formatHex(LanLobby.createGameSettings(captured)),
                                HexFormat.of().formatHex(LanLobby.createGameSettings(setup)));

        for (int index = 0; index < LanLobby.RECORDS; index++) {
            MeleeGameSetup.PlayerRecord expected = captured.getRecord(index);
            MeleeGameSetup.PlayerRecord actual = setup.getRecord(index);
            String where = "record " + index;

            Assertions.assertEquals(expected.getPlayerId(), actual.getPlayerId(), where);
            Assertions.assertEquals(expected.getType(), actual.getType(), where);
            Assertions.assertEquals(expected.getRace(), actual.getRace(), where);
            Assertions.assertEquals(expected.getTeam(), actual.getTeam(), where);
            Assertions.assertEquals(expected.getName(), actual.getName(), where);
        }
    }

    @Test
    void melee_opens_every_start_location_without_forces() throws IOException {
        LanLobby.Setup setup = createLobbySetup(LanGameOptions.MELEE);

        Assertions.assertEquals(0, setup.getRecord(HOST_SLOT).getPlayerId());
        Assertions.assertEquals(HUMAN_TYPE, setup.getRecord(HOST_SLOT).getType());
        Assertions.assertEquals(MELEE_FORCE, setup.getRecord(HOST_SLOT).getTeam());
        Assertions.assertEquals(OPEN_TYPE, setup.getRecord(OPEN_SLOT).getType());
        Assertions.assertEquals(SELECTABLE_RACE, setup.getRecord(OPEN_SLOT).getRace());
        Assertions.assertEquals(MELEE_FORCE, setup.getRecord(OPEN_SLOT).getTeam());
        Assertions.assertEquals(NONE_TYPE, setup.getRecord(UNUSED_SLOT).getType());
    }

    @Test
    void use_map_settings_keeps_the_map_forces_and_sends_the_force_names() throws IOException {
        LanLobby.Setup setup = createLobbySetup(LanGameOptions.USE_MAP_SETTINGS);

        Assertions.assertEquals(FIRST_FORCE, setup.getRecord(HOST_SLOT).getTeam());
        Assertions.assertEquals(FIRST_FORCE, setup.getRecord(OPEN_SLOT).getTeam());
        Assertions.assertEquals(OPEN_TYPE, setup.getRecord(OPEN_SLOT).getType());
        Assertions.assertNotNull(setup.getForceNames());
    }

    @Test
    void the_map_custom_colors_section_fixes_the_lobby_colors_and_the_map_colors() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, LanTestResources.CAPTURED_MAP_FILE_NAME);
        byte[] scenario = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        byte[] colors = new byte[CRGB_SIZE];

        colors[MAP_COLOR_SLOT * CRGB_TRIPLE_SIZE]       = (byte) (MAP_ORANGE >>> Short.SIZE);
        colors[MAP_COLOR_SLOT * CRGB_TRIPLE_SIZE + 1]   = (byte) (MAP_ORANGE >>> Byte.SIZE);
        colors[COLOR_INDEX_SLOT * CRGB_TRIPLE_SIZE + 2] = PINK_COLOR_ID;

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            colors[CRGB_MODES + slot] = CRGB_PLAYER_CHOICE;
        }

        colors[CRGB_MODES + MAP_COLOR_SLOT]    = CRGB_MAP_COLOR;
        colors[CRGB_MODES + COLOR_INDEX_SLOT]  = CRGB_COLOR_INDEX;
        colors[CRGB_MODES + RANDOM_COLOR_SLOT] = CRGB_RANDOM;

        // The generated map: the installed map with a CRGB section appended.
        ChkFile map = ChkFile.parseTolerant(ByteBuffer.allocate(scenario.length + 2 * Integer.BYTES + CRGB_SIZE)
                                                      .order(ByteOrder.LITTLE_ENDIAN)
                                                      .put(scenario)
                                                      .put(ChkFile.PLAYER_COLORS_SECTION.getBytes(StandardCharsets.US_ASCII))
                                                      .putInt(CRGB_SIZE)
                                                      .put(colors)
                                                      .array());
        InMemoryLanScenario network = new InMemoryLanScenario(1);

        network.host(createLobbySetup(map, LanGameOptions.MELEE), LanGameOptions.MELEE.encode(), null);

        LanLobby host = network.lobbies[0];

        Assertions.assertNotNull(host);

        LobbyResolution.Table table = host.createResolutionTable();

        Assertions.assertTrue(table.customColorFlag, "a CRGB section sets the custom color flag");
        Assertions.assertEquals(MAP_SPECIFIED_COLOR, table.lobbyColor[MAP_COLOR_SLOT]);
        Assertions.assertTrue(table.colorKept[MAP_COLOR_SLOT] && table.colorLock[MAP_COLOR_SLOT]);
        Assertions.assertEquals(MAP_ORANGE, table.mapColor[MAP_COLOR_SLOT], "the lobby data carries the map color");
        Assertions.assertEquals(PINK_COLOR_ID, table.lobbyColor[COLOR_INDEX_SLOT]);
        Assertions.assertTrue(table.colorLock[COLOR_INDEX_SLOT] && !table.colorKept[COLOR_INDEX_SLOT]);
        Assertions.assertEquals(-1, table.mapColor[COLOR_INDEX_SLOT]);
        Assertions.assertEquals(LobbyResolution.COLOR_NONE, table.lobbyColor[RANDOM_COLOR_SLOT]);
        Assertions.assertTrue(table.colorLock[RANDOM_COLOR_SLOT]);
        Assertions.assertFalse(table.colorLock[CHOSEN_COLOR_SLOT], "the player chooses the color");
    }
}
