package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.engine.lockstep.LockstepTransport;
import org.fogofwarlabs.sc4j.engine.lockstep.PeerTurn;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.NetworkPeerUtils;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Compares synchronization checksum words and sent commands with recorded host and joiner vectors.
 */
@RequiresInstallation
final class LanSyncCaptureTest {
    /**
     * Update number to the host's recorded WORD (turn {@code 321 + n}, turn {@code 323} repeating update {@code 1}).
     */
    private static final @NotNull Map<Integer, Integer> KIND_2_WORDS = Map.of(1, 0xD271, 3, 0x2113, 5, 0x1FD2, 7, 0xFCD0, 9, 0x7748);
    /**
     * Kind {@code 1} updates (the even ones) and the host's recorded WORDs (turns {@code 324}, {@code 326}, ...).
     */
    private static final @NotNull Map<Integer, Integer> KIND_1_WORDS = Map.of(2, 0x0B5E, 4, 0xBB36, 6, 0x1DBE, 8, 0xE9F6,
                                                                              10, 0x0EEB, 12, 0x7793, 14, 0x1382, 16, 0x952F,
                                                                              18, 0x1C4A, 20, 0x0F56);

    /**
     * The recorded host's Sync commands of its in-game turns {@code 322..356} ({@code 7} bytes each).
     */
    private static final @NotNull String @NotNull [] HOST_SYNCS = {
            "371271d2000000", "371271d2000000", "37215e0b000000", "37321321000000",
            "374136bb314011", "3752d21f314011", "3761be1d314011", "3772d0fc314011",
            "3781f6e9314011", "37924877314011", "37a1eb0e314011", "37b2b076314011",
            "37c19377314011", "37d279c3314011", "37e18213314011", "37f2c2cf314011",
            "37012f95314011", "3712a766314011", "37214a1c314011", "3732c018314011",
            "3741560f314011", "3752ccf2314011", "37614a12314011", "3772734e314011",
            "3781547c314011", "37929059314011", "37a1ecaa314011", "37b2b840314011",
            "37c137e7314011", "37d2582d314011", "37e16485314011", "37f232d6314011",
            "37013cc5314011", "37121572314011", "3721c582314011"
    };
    /**
     * The recorded host's camera: its first drawn row {@code 0x40} is the camera's tile row less {@code 4}.
     */
    private static final int HOST_CAMERA_ROW = 0x44;
    /**
     * The recorded joiner's camera: it drew from row {@code 0x41}.
     */
    private static final int JOINER_CAMERA_ROW = 0x45;
    /**
     * The recorded joiner's Sync commands of its in-game turns {@code 322..355}.
     */
    private static final @NotNull String @NotNull [] JOINER_SYNCS = {
            "371271d2000000", "371271d2000000", "37215e0b000000", "37321321000000",
            "374136bb00410d", "3752d21f00410d", "3761be1d00410d", "3772d0fc00410d",
            "3781f6e900410d", "3792487700410d", "37a1eb0e00410d", "37b2b07600410d",
            "37c1937700410d", "37d279c300410d", "37e1821300410d", "37f2c2cf00410d",
            "37012f9500410d", "3712a76600410d", "37214a1c00410d", "3732c01800410d",
            "3741560f00410d", "3752ccf200410d", "37614a1200410d", "3772734e00410d",
            "3781547c00410d", "3792905900410d", "37a1ecaa00410d", "37b2b84000410d",
            "37c137e700410d", "37d2582d00410d", "37e1648500410d", "37f232d600410d",
            "37013cc500410d", "3712157200410d"
    };

    private static final int SEED = 0x6ABCBEE2;
    private static final int HOST_PLAYER = 0;
    private static final int JOINER_PLAYER = 1;
    private static final int HOST_TEAM = 1;
    private static final int JOINER_TEAM = 2;
    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";

    private static final int UPDATES = 20;
    /**
     * The frames a Sync record update lags behind the update number.
     */
    private static final int UPDATE_FRAME_LAG = 3;

    private static final int SYNC_COMMAND_SIZE = 7;
    private static final int FIRST_GAME_TURN = 322;
    /**
     * The lobby ended with its turns {@code 320} and {@code 321} in transit, which the game start step and the first frame
     * consume.
     */
    private static final int LOBBY_TURNS_IN_TRANSIT = -2;
    private static final int TURNS_IN_TRANSIT = 3;

    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;

    /**
     * The host's Sync of turn {@code 330} (record {@code 8}), whose WORD the mismatch test changes.
     */
    private static final int CHANGED_WORD_RECORD = 8;
    /**
     * The host's Sync of turn {@code 340}, whose minimap count parity the mismatch test changes.
     */
    private static final int CHANGED_PARITY_RECORD = 18;
    /**
     * The host's Syncs the mismatch test changes.
     */
    private static final int CHANGED_RECORDS = 2;
    /**
     * The hex digits of a Sync command's WORD, from the start to the end.
     */
    private static final int WORD_DIGITS_START = 4;
    private static final int WORD_DIGITS_END = 8;
    /**
     * The hex digits of a Sync command's minimap count parity, from the start.
     */
    private static final int PARITY_DIGITS_START = 12;

    private static @NotNull MeleeGameSetup createCapturedGameSetup(byte @NotNull [] mapData) throws IOException {
        LanLobby.Setup setup = LanTestResources.createCapturedLobbySetup("B");
        MeleeGameSetup.PlayerRecord[] records = new MeleeGameSetup.PlayerRecord[LanLobby.RECORDS];
        byte[] lobbyData = new byte[LanLobby.LOBBY_DATA_SIZE];

        for (int index = 0; index < LanLobby.RECORDS; index++) {
            records[index] = setup.getRecord(index);
        }

        records[HOST_PLAYER]   = new MeleeGameSetup.PlayerRecord(HOST_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.ZERG, HOST_TEAM, "B");
        records[JOINER_PLAYER] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.TERRAN, JOINER_TEAM, "A");

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            lobbyData[LanLobby.LOBBY_DATA_COLORS + slot] = (byte) LobbyResolution.COLOR_NONE;
        }

        LobbyResolution.Table table = LanLobby.createResolutionTable(LanLobby.createGameSettings(setup), records, lobbyData);

        return MeleeGameSetup.createNetwork(mapData, "B",
                                            table, LanLobby.createResolutionOptions(LanGameOptions.TOP_VS_BOTTOM_1),
                                            LanGameOptions.TOP_VS_BOTTOM_1.createTemplate(),
                                            GameSpeed.FASTEST, true);
    }


    @Test
    void kind_2_and_kind_1_sync_words_equal_the_captured_host_syncs() throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        LiveGame live = new LiveGame(createCapturedGameSetup(mapData), SEED, HOST_PLAYER);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(mapData),
                                                                 gameData,
                                                                 live.getLocalPlayer(),
                                                                 VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                 INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Game game = runtime.getGame();

            for (int update = 1; update <= UPDATES; update++) {
                while (game.getCurrentFrame() < Math.max(0, update - UPDATE_FRAME_LAG)) {
                    runtime.step(FRAME_MILLISECONDS, 0);
                }

                Integer expected = KIND_2_WORDS.get(update);
                Integer expectedUnits = KIND_1_WORDS.get(update);

                if (expected != null) {
                    int word = SyncChecksumUtils.calculateRecordWord(SyncChecksumUtils.calculateResourceHash(game),
                                                                     SyncChecksumUtils.calculateTileRowHash(game.getMap(), update - 1),
                                                                     SyncChecksumUtils.SLOT_MASKS);

                    Assertions.assertEquals(expected.intValue(), word, "update " + update);
                }

                if (expectedUnits != null) {
                    int word = SyncChecksumUtils.calculateRecordWord(SyncChecksumUtils.calculateUnitHash(game),
                                                                     SyncChecksumUtils.calculateTileRowHash(game.getMap(), update - 1),
                                                                     SyncChecksumUtils.SLOT_MASKS);

                    Assertions.assertEquals(expectedUnits.intValue(), word, "kind-1 update " + update);
                }
            }
        }
    }

    @Test
    void host_sync_commands_equal_the_captured_ones_byte_for_byte() throws Exception {
        // The host also checks the joiner's recorded Syncs against its own records: all match.
        Assertions.assertEquals(0, this.assertSyncCommands(HOST_PLAYER, HOST_CAMERA_ROW, HOST_SYNCS, JOINER_SYNCS));
    }

    @Test
    void joiner_sync_commands_equal_the_captured_ones_byte_for_byte() throws Exception {
        // The joiner (player 1, slot 1) drew from row 0x41.
        Assertions.assertEquals(0, this.assertSyncCommands(JOINER_PLAYER, JOINER_CAMERA_ROW, JOINER_SYNCS, HOST_SYNCS));
    }

    @Test
    void received_sync_that_differs_from_the_local_record_is_a_mismatch() throws Exception {
        String[] changed = HOST_SYNCS.clone();

        // A different WORD in the host's Sync of turn 330 (record 8) and a different minimap count parity in turn 340.
        changed[CHANGED_WORD_RECORD]   = changed[CHANGED_WORD_RECORD].substring(0, WORD_DIGITS_START) + "0000" +
                                         changed[CHANGED_WORD_RECORD].substring(WORD_DIGITS_END);
        changed[CHANGED_PARITY_RECORD] = changed[CHANGED_PARITY_RECORD].substring(0, PARITY_DIGITS_START) + "10";

        Assertions.assertEquals(CHANGED_RECORDS, this.assertSyncCommands(JOINER_PLAYER, JOINER_CAMERA_ROW, JOINER_SYNCS, changed));
    }

    /**
     * Plays game {@code 1} as the given player, feeding the other player's recorded Syncs as its turns, and compares the sent
     * Syncs with the recorded ones.
     *
     * @param localPlayerIdentifier the local player's network id
     * @param cameraRow             the local camera's tile row
     * @param expected              the local player's recorded Syncs
     * @param other                 the other player's recorded Syncs
     * @return the received Syncs that did not match the local records
     * @throws Exception if the map or the game data cannot be read
     */
    private int assertSyncCommands(int localPlayerIdentifier, int cameraRow, @NotNull String @NotNull [] expected, @NotNull String @NotNull [] other) throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        LiveGame live = new LiveGame(createCapturedGameSetup(mapData), SEED, localPlayerIdentifier);
        List<byte[]> sent = new ArrayList<>();
        // The lobby ended with its turns 320 and 321 in transit: the game start step consumes 320, the first frame 321.
        int[] received = {LOBBY_TURNS_IN_TRANSIT};

        live.attachNetwork(new LockstepTransport() {
            /**
             * @return the local turns sent and not yet received back
             * @implSpec The receive count includes the seeded lobby turns.
             */
            @Override
            public int getTurnsInTransit() {
                return sent.size() - received[0];
            }


            /**
             * Sends the local player's next turn.
             *
             * @param turn the turn bytes
             */
            @Override
            public void sendTurn(byte @NotNull [] turn) {
                sent.add(turn);
            }

            /**
             * Takes the next turn of every participant once all of them arrived.
             *
             * @return the participants' turns in execution order
             * @implSpec Advances the fixture receive index and immediately returns the opposing turn followed by the local turn.
             */
            @Override
            public @NotNull List<@NotNull PeerTurn> receiveTurns() {
                received[0]++;

                // The lobby's turns (320, 321) carry no Sync.
                byte[] own = received[0] <= 0 ? new byte[]{ReplayCommandIds.KEEP_ALIVE} : sent.get(received[0] - 1);
                byte[] others = received[0] <= 0 || received[0] > other.length
                                ? new byte[]{ReplayCommandIds.KEEP_ALIVE}
                                : HexFormat.of().parseHex(other[received[0] - 1]);

                return List.of(new PeerTurn(NetworkPeerUtils.getPeer(1 - localPlayerIdentifier), others),
                               new PeerTurn(NetworkPeerUtils.getPeer(localPlayerIdentifier), own));
            }
        }, TURNS_IN_TRANSIT, false);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(mapData),
                                                                 gameData,
                                                                 live.getLocalPlayer(),
                                                                 VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                 INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Game game = runtime.getGame();

            live.setSyncSource(game, () -> cameraRow);

            ReplaySession session = runtime.getSession();

            Assertions.assertNotNull(session);

            live.attachReplaySession(session);

            while (sent.size() < expected.length) {
                Assertions.assertTrue(live.exchangeTurn(game.getCurrentFrame()));
                runtime.step(FRAME_MILLISECONDS, 0);
            }
        }

        for (int turn = 0; turn < expected.length; turn++) {
            Assertions.assertEquals(expected[turn], HexFormat.of().formatHex(sent.get(turn), 0, SYNC_COMMAND_SIZE),
                                    "turn " + (FIRST_GAME_TURN + turn));
        }

        return live.getSyncMismatches();
    }
}
