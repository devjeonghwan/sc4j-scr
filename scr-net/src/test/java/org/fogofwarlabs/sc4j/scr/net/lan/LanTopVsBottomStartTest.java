package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Compares Top vs Bottom start slots and initial synchronization records with recorded fixtures.
 */
@RequiresInstallation
final class LanTopVsBottomStartTest {
    /**
     * The game options our host advertised (type {@code 0x0f}, four sides).
     */
    private static final @NotNull LanGameOptions TOP_VS_BOTTOM_4 = LanGameOptions.parse(
            HexFormat.of().parseHex("0f00040004000000010101020200010100010000000000320000000000000000")
    );
    private static final @NotNull String MAP_NAME = "(8)The Hunters.scm";
    private static final int MAP_SIZE = 71818;
    private static final int MAP_CHECKSUM = 0xC2ED13D0;

    private static final int HOST_LOBBY_SLOT = 0;
    /**
     * The joiner's lobby slot: the first slot of the bottom force.
     */
    private static final int JOINER_LOBBY_SLOT = 4;
    private static final int HOST_PLAYER = 0;
    private static final int JOINER_PLAYER = 1;
    private static final int NO_PLAYER = -1;
    private static final int TOP_FORCE = 1;
    private static final int BOTTOM_FORCE = 2;

    /**
     * The seed of the first recorded game.
     */
    private static final int FIRST_GAME_SEED = 0x6ABE337B;
    private static final int FIRST_GAME_JOINER_SLOT = 3;
    private static final int FIRST_GAME_HOST_SLOT = 5;
    /**
     * The seed of the second recorded game.
     */
    private static final int SECOND_GAME_SEED = 0x6ABE33B5;
    private static final int SECOND_GAME_JOINER_SLOT = 5;
    private static final int SECOND_GAME_HOST_SLOT = 2;

    /**
     * The Sync records compared, {@code 1..4} (index {@code 0} unused).
     */
    private static final int RECORDS = 5;
    /**
     * The frames a Sync record update lags behind the update number.
     */
    private static final int UPDATE_FRAME_LAG = 3;

    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;

    private static final int HUMAN_TYPE = 2;
    private static final int NONE_TYPE = 0;

    @Test
    void first_game_starts_where_the_native_joiner_started() throws Exception {
        // Recorded synchronization values use joiner slot 3 and host slot 5.
        int[] records = this.resolveLobbyAndPlay(FIRST_GAME_SEED, FIRST_GAME_JOINER_SLOT, FIRST_GAME_HOST_SLOT);

        Assertions.assertEquals(0x69B9, records[1], "record 1");
        Assertions.assertEquals(0xE56F, records[2], "record 2");
        Assertions.assertEquals(0xC572, records[3], "record 3");
        Assertions.assertEquals(0x9B9A, records[4], "record 4");
    }

    @Test
    void second_game_starts_where_the_native_joiner_started() throws Exception {
        // Recorded synchronization values use host slot 2 and joiner slot 5.
        int[] records = this.resolveLobbyAndPlay(SECOND_GAME_SEED, SECOND_GAME_JOINER_SLOT, SECOND_GAME_HOST_SLOT);

        Assertions.assertEquals(0x41F7, records[1], "record 1");
        Assertions.assertEquals(0x6FB1, records[2], "record 2");
    }

    /**
     * Resolves the lobby with the seed, checks the final slots, and plays the game to frame {@code 1}.
     *
     * @param seed       the game seed
     * @param joinerSlot the joiner's expected start slot
     * @param hostSlot   the host's expected start slot
     * @return the WORDs of the Sync records {@code 1..4} (index {@code 0} unused)
     * @throws Exception if the map or the game data cannot be read
     */
    private int @NotNull [] resolveLobbyAndPlay(int seed, int joinerSlot, int hostSlot) throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        ChkFile map = ChkFile.parseTolerant(mapData);
        LanLobby.Setup setup = LanLobby.Setup.create(map, TOP_VS_BOTTOM_4, "host", MAP_NAME, MAP_SIZE, MAP_CHECKSUM);
        MeleeGameSetup.PlayerRecord[] lobby = new MeleeGameSetup.PlayerRecord[LanLobby.RECORDS];
        byte[] lobbyData = new byte[LanLobby.LOBBY_DATA_SIZE];

        for (int index = 0; index < LanLobby.RECORDS; index++) {
            lobby[index] = setup.getRecord(index);
        }

        lobby[HOST_LOBBY_SLOT]   = new MeleeGameSetup.PlayerRecord(HOST_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.TERRAN, TOP_FORCE, "host");
        lobby[JOINER_LOBBY_SLOT] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, BOTTOM_FORCE, "joiner");

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            lobbyData[LanLobby.LOBBY_DATA_COLORS + slot] = (byte) LobbyResolution.COLOR_NONE;
        }

        LobbyResolution.Table table = LanLobby.createResolutionTable(LanLobby.createGameSettings(setup), lobby, lobbyData);
        LobbyResolution.Options resolution = LanLobby.createResolutionOptions(TOP_VS_BOTTOM_4);
        LobbyResolution.Result result = LobbyResolution.resolve(table, resolution, seed);

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            MeleeGameSetup.PlayerRecord record = result.getRecords()[slot];
            int expected = slot == hostSlot ? HOST_PLAYER : slot == joinerSlot ? JOINER_PLAYER : NO_PLAYER;

            Assertions.assertEquals(expected, record.getPlayerId(), "slot " + slot);
            Assertions.assertEquals(expected >= 0 ? HUMAN_TYPE : NONE_TYPE, record.getType(), "slot " + slot + " type");
        }

        MeleeGameSetup gameSetup = MeleeGameSetup.createNetwork(mapData, "host",
                                                                table, resolution,
                                                                TOP_VS_BOTTOM_4.createTemplate(),
                                                                GameSpeed.FASTEST, true);
        LiveGame live = new LiveGame(gameSetup, seed, HOST_PLAYER);
        int[] words = new int[RECORDS];

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), map,
                                                                 gameData,
                                                                 live.getLocalPlayer(),
                                                                 VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                 INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Game game = runtime.getGame();

            // Record n: tile row n - 1 and the state of frame max(0, n - 3); the odd records are kind 2.
            for (int update = 1; update < words.length; update++) {
                while (game.getCurrentFrame() < Math.max(0, update - UPDATE_FRAME_LAG)) {
                    runtime.step(FRAME_MILLISECONDS, 0);
                }

                int hash = update % 2 == 1 ? SyncChecksumUtils.calculateResourceHash(game) : SyncChecksumUtils.calculateUnitHash(game);

                words[update] = SyncChecksumUtils.calculateRecordWord(hash, SyncChecksumUtils.calculateTileRowHash(game.getMap(), update - 1),
                                                                      SyncChecksumUtils.SLOT_MASKS);
            }
        }

        return words;
    }
}
