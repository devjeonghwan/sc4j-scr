package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerColorRows;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Starts games on generated maps with each player color source and checks that the game, its replay and the color every interface shows agree.
 */
@RequiresInstallation
final class PlayerColorStartTest {
    private static final @NotNull String SCENARIO = "staredit\\scenario.chk";
    private static final @NotNull String HOST_NAME = "host";
    private static final int SEED = 0x1234567;
    private static final int HOST_SLOT = 0;
    private static final int HOST_PLAYER = 0;
    /**
     * A closed slot whose {@code CRGB} section fixes the map's own color.
     */
    private static final int MAP_COLOR_SLOT = 1;
    private static final int MELEE_FORCE = 1;
    /**
     * The generated {@code COLR} section: the slots take the colors {@code 3, 9, 2, 0, 11, 5, 6, 7}.
     */
    private static final byte @NotNull [] MAP_COLORS = {3, 9, 2, 0, 11, 5, 6, 7};
    /**
     * The network color id of pink, the host's lobby color.
     */
    private static final int PINK_COLOR_ID = 15;
    /**
     * The packed {@code 0xRRGGBB} map color of {@link #MAP_COLOR_SLOT}.
     */
    private static final int MAP_ORANGE = 0xFF8000;
    /**
     * The opaque alpha of a custom color.
     */
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final LobbyResolution.@NotNull Options FIXED_START_MELEE = new LobbyResolution.Options(true, false, 0, true, false);

    /**
     * Reads the map data of a Brood War test map with a section appended, which the map loaders read after the map's own sections (its own
     * {@code COLR} section keeps every color).
     */
    private static byte @NotNull [] readMapData(@NotNull String section, byte @NotNull [] payload) throws IOException {
        return readMapData(GameTestResources.MAP_ARCHIVE_DRAGON, section, payload);
    }

    private static byte @NotNull [] readMapData(@NotNull String map, @NotNull String section, byte @NotNull [] payload) throws IOException {
        byte[] scenario = MpqArchive.open(GameTestResources.getPath(map)).readFile(SCENARIO);

        return ByteBuffer.allocate(scenario.length + 2 * Integer.BYTES + payload.length)
                         .order(ByteOrder.LITTLE_ENDIAN)
                         .put(scenario)
                         .put(section.getBytes(StandardCharsets.US_ASCII))
                         .putInt(payload.length)
                         .put(payload)
                         .array();
    }

    private static @NotNull Game start(@NotNull ReplayRuntime runtime) {
        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        return runtime.getGame();
    }

    private static @NotNull ReplayRuntime createRuntime(@NotNull LiveGame live, @NotNull GameData gameData, boolean network) throws IOException {
        return ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(live.getSetup().getMapData()), gameData,
                                            live.getLocalPlayer(), network,
                                            RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                            RunningGameScenario.INSTRUCTION_LIMIT);
    }

    private static @NotNull ReplayRuntime createPlayback(@NotNull LiveGame live, @NotNull GameData gameData) throws IOException {
        ReplayFile replay = ReplayFile.parse(live.writeReplay(0, 0));

        return new ReplayRuntime(replay, ChkFile.parseTolerant(live.getSetup().getMapData()), gameData,
                                 RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                 RunningGameScenario.INSTRUCTION_LIMIT);
    }

    /**
     * Checks the rows of every playable slot: the {@code tunit.pcx} row of the color its map color names, as the rows were before the section.
     */
    private static void assertMapColorRows(@NotNull Game game, int @NotNull [] @NotNull [] tables) {
        PlayerColorRows rows = game.getPlayerTable().getPlayerColorRows();

        Assertions.assertNotNull(rows);

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            int named = MAP_COLORS[slot];
            Player owner = named < PlayerTable.PLAYABLE_PLAYER_SLOTS ? game.getPlayerTable().getPlayer(named) : null;
            int color = owner != null ? owner.getColor() : named;

            for (int index = 0; index < PlayerColorRows.UNIT_ROW; index++) {
                Assertions.assertEquals(tables[color][index], rows.getUnitColor(slot, index), "slot " + slot + " index " + index);
            }

            Assertions.assertEquals(tables[color][PlayerColorRows.UNIT_ROW], rows.getMinimapColor(slot), "slot " + slot);
            Assertions.assertEquals(named == slot ? 0 : named, rows.getMapColor(slot), "slot " + slot);
        }

        Assertions.assertFalse(rows.isCustomColorsActive(), "a single-player game keeps no custom colors");
        Assertions.assertEquals(rows.getUnitColor(HOST_SLOT, 0), rows.getPlayerColor(HOST_SLOT, index -> index),
                                "the host is shown in its remapped row's color");
    }

    /**
     * Creates the lobby table of a network melee game: the host in slot 0 with pink, the other slots closed, slot 1 keeping its map color.
     */
    private static LobbyResolution.@NotNull Table createNetworkTable() {
        LobbyResolution.Table table = new LobbyResolution.Table();

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            table.type[slot]         = LobbyResolution.TYPE_CLOSED;
            table.templateType[slot] = LobbyResolution.TYPE_OPEN;
            table.savedType[slot]    = LobbyResolution.TYPE_OPEN;
            table.race[slot]         = ScrRaceIds.TERRAN;
            table.templateRace[slot] = ScrRaceIds.TERRAN;
            table.team[slot]         = MELEE_FORCE;
            table.templateTeam[slot] = MELEE_FORCE;
        }

        table.playerId[HOST_SLOT]   = HOST_PLAYER;
        table.type[HOST_SLOT]       = MeleeGameSetup.TYPE_HUMAN;
        table.name[HOST_SLOT]       = HOST_NAME;
        table.lobbyColor[HOST_SLOT] = PINK_COLOR_ID;

        table.colorKept[MAP_COLOR_SLOT] = true;
        table.mapColor[MAP_COLOR_SLOT]  = MAP_ORANGE;

        return table;
    }

    @Test
    void the_map_color_section_remaps_the_rows_of_the_game_and_of_its_replay() throws IOException {
        MeleeGameSetup setup = new MeleeGameSetup(readMapData(ChkFile.COLORS_SECTION, MAP_COLORS), HOST_NAME, ScrRaceIds.TERRAN, HOST_SLOT,
                                                  new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);
        LiveGame live = new LiveGame(setup, SEED);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int[][] tables = gameData.getPlayerColorTables();

            assertMapColorRows(start(createRuntime(live, gameData, false)), tables);
            assertMapColorRows(start(createPlayback(live, gameData)), tables);
        }
    }

    @Test
    void a_network_game_shows_its_custom_colors_in_the_game_and_in_its_replay() throws IOException {
        MeleeGameSetup setup = MeleeGameSetup.createNetwork(readMapData(ChkFile.COLORS_SECTION, MAP_COLORS), HOST_NAME, createNetworkTable(),
                                                            FIXED_START_MELEE, GameTemplate.MELEE, GameSpeed.FASTEST, true);
        LiveGame live = new LiveGame(setup, SEED, HOST_PLAYER);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (Game game : new Game[]{start(createRuntime(live, gameData, true)), start(createPlayback(live, gameData))}) {
                PlayerColorRows rows = game.getPlayerTable().getPlayerColorRows();

                Assertions.assertNotNull(rows);
                Assertions.assertTrue(rows.isCustomColorsActive(), "a network melee game keeps its custom colors");
                Assertions.assertEquals(LobbyResolution.getColorRgb(PINK_COLOR_ID) << Byte.SIZE | OPAQUE_ALPHA,
                                        rows.getPlayerColor(HOST_SLOT, index -> index), "the host is shown in its lobby color");
                Assertions.assertEquals(MAP_ORANGE << Byte.SIZE | OPAQUE_ALPHA, rows.getPlayerColor(MAP_COLOR_SLOT, index -> index),
                                        "a kept slot is shown in its map color");
                Assertions.assertEquals(gameData.getPlayerColorTables()[MAP_COLORS[HOST_SLOT]][0], rows.getUnitColor(HOST_SLOT, 0),
                                        "the rows still follow the map color section");
            }
        }
    }

    @Test
    void an_original_map_keeps_its_rows_whatever_its_map_color_section() throws IOException {
        // The map loaders of the original versions read no COLR section.
        byte[] mapData = readMapData(GameTestResources.MAP_ARCHIVE_NEW_AVALON, ChkFile.COLORS_SECTION, MAP_COLORS);
        MeleeGameSetup setup = new MeleeGameSetup(mapData, HOST_NAME, ScrRaceIds.TERRAN, HOST_SLOT, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);
        LiveGame live = new LiveGame(setup, SEED);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = start(createRuntime(live, gameData, false));
            PlayerColorRows rows = game.getPlayerTable().getPlayerColorRows();

            Assertions.assertNotNull(rows);

            for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                Player player = game.getPlayerTable().getPlayer(slot);
                int color = player != null ? player.getColor() : slot;

                Assertions.assertEquals(gameData.getPlayerColorTables()[color][0], rows.getUnitColor(slot, 0), "slot " + slot);
                Assertions.assertEquals(0, rows.getMapColor(slot), "slot " + slot);
            }
        }
    }
}
