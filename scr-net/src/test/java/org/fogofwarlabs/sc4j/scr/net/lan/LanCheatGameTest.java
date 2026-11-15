package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exercises opt-in local cheats in a solo LAN game, multiplayer rejection and recorded cheat playback.
 */
@RequiresInstallation
final class LanCheatGameTest {
    private static final int FRAMES = 200;
    private static final int CHEAT_FRAME = 60;
    /**
     * The Unix time of a typed line.
     */
    private static final int TYPED_TIME = 0x6543210F;
    private static final int NOT_APPLIED = -1;
    private static final int FRAME_MILLISECONDS = 42;

    private static final @NotNull String MAP_NAME = "(4)Blood Bath.scm";
    private static final @NotNull String SCENARIO = "staredit\\scenario.chk";

    private static final @NotNull String CHEAT_CODE = "show me the money";
    private static final int CHEAT_MINERALS = 10_000;

    private static final int SOLO_PLAYERS = 1;
    private static final int TWO_PLAYERS = 2;
    private static final int HOST_CLIENT = 0;

    private static final int LATENCY = 2;
    private static final int TURNS_IN_TRANSIT = 3;

    private static final int MAP_TRANSFER_LIMIT_MILLISECONDS = 20_000;
    private static final int LOBBY_SETTLE_MILLISECONDS = 1000;
    private static final int GAME_START_LIMIT_MILLISECONDS = 20_000;

    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;
    private static final int ITERATION_LIMIT = 300_000;

    private static final int STATE_GAME = 9;

    private static final int TEXT_CHEAT = 1;
    private static final int TEXT_DISCARDED = 0;

    @Test
    void solo_lan_game_applies_the_cheat_on_every_client_and_in_the_replay() throws Exception {
        this.play(SOLO_PLAYERS);
    }

    /**
     * Checks that another human prevents local cheat execution.
     */
    @Test
    void another_human_keeps_cheats_refused_on_every_client() throws Exception {
        this.play(TWO_PLAYERS);
    }

    private void play(int players) throws Exception {
        boolean solo = players == SOLO_PLAYERS;
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapFile = Files.readAllBytes(mapPath);
        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO);
        ChkFile scenario = ChkFile.parseTolerant(mapData);
        LanGameOptions options = LanGameOptions.MELEE;
        InMemoryLanScenario network = new InMemoryLanScenario(players);
        LanLobby.Setup setup = LanLobby.Setup.create(scenario, options,
                                                     "host",
                                                     MAP_NAME, mapFile.length, LanMapTransfer.calculateChecksum(mapFile, mapFile.length));
        LanLobby[] lobbies = network.lobbies;

        network.latency = LATENCY;
        network.host(setup, options.encode(), mapPath);

        for (int joiner = 1; joiner < players; joiner++) {
            network.join(joiner, mapPath.getParent(), mapPath.getParent().resolve("Download"));
        }

        network.runUntil(MAP_TRANSFER_LIMIT_MILLISECONDS, lan -> {
            LanLobby host = lan.lobbies[HOST_CLIENT];

            Assertions.assertNotNull(host);

            return host.isMapTransferComplete();
        });
        network.run(LOBBY_SETTLE_MILLISECONDS);

        for (LanLobby lobby : lobbies) {
            Assertions.assertNotNull(lobby);

            lobby.changeRace(ScrRaceIds.TERRAN);
        }

        network.run(LOBBY_SETTLE_MILLISECONDS);

        LanLobby host = lobbies[HOST_CLIENT];

        Assertions.assertNotNull(host);
        Assertions.assertTrue(host.startGame());

        network.runUntil(GAME_START_LIMIT_MILLISECONDS, lan -> {
            for (LanLobby lobby : lan.lobbies) {
                Assertions.assertNotNull(lobby);

                if (lobby.getState() != STATE_GAME) {
                    return false;
                }
            }

            return true;
        });

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            List<LiveGame> games = new ArrayList<>();
            List<ReplayRuntime> runtimes = new ArrayList<>();

            for (int index = 0; index < players; index++) {
                LanLobby lobby = lobbies[index];

                Assertions.assertNotNull(lobby);

                MeleeGameSetup gameSetup = lobby.createGameSetup(mapData, GameSpeed.FASTEST, true);
                LiveGame live = new LiveGame(gameSetup, lobby.getSeed(), network.sessions[index].getLocalIdentifier());

                live.attachNetwork(new LanTurnTransport(network.sessions[index], network::deliver), TURNS_IN_TRANSIT, false);

                ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), scenario,
                                                                     data,
                                                                     live.getLocalPlayer(), true,
                                                                     VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                     INSTRUCTION_LIMIT);

                runtime.setSourceFile(GameSourceFile.create(mapPath, mapFile));
                live.setSyncSource(runtime.getGame(), () -> 0);
                runtime.start(0, network.now);

                Assertions.assertNull(runtime.getFailure());

                ReplaySession session = runtime.getSession();

                Assertions.assertNotNull(session);

                live.attachReplaySession(session);
                games.add(live);
                runtimes.add(runtime);
            }

            Game firstGame = runtimes.getFirst().getGame();

            Assertions.assertTrue(firstGame.areCheatsEnabled(), "The Melee template enables cheats");

            int localPlayer = games.getFirst().getLocalPlayer();
            Player initialPlayer = firstGame.getPlayerTable().getPlayer(localPlayer);

            Assertions.assertNotNull(initialPlayer);

            int initialMinerals = initialPlayer.getMinerals();
            long[] appliedFrame = new long[players];

            Arrays.fill(appliedFrame, NOT_APPLIED);

            for (int iteration = 0; iteration < ITERATION_LIMIT; iteration++) {
                boolean done = true;

                network.now++;

                for (int index = 0; index < players; index++) {
                    LiveGame live = games.get(index);
                    Game game = runtimes.get(index).getGame();
                    long frame = game.getCurrentFrame();

                    if (frame >= FRAMES) {
                        continue;
                    }

                    done = false;

                    // The switch starts off: the typed code is chat. Switched on, alone with computers, it is a cheat; with
                    // another human it stays chat.
                    if (index == HOST_CLIENT && frame == CHEAT_FRAME) {
                        GameFrame gameFrame = game.getFrame();

                        Assertions.assertNotNull(gameFrame);

                        int cheatFlags = gameFrame.getVisibility().getCheatFlags();

                        Assertions.assertEquals(!solo, live.hasOtherHuman());
                        Assertions.assertFalse(live.isLocalCheatsAllowed(), "off at the game start");
                        Assertions.assertEquals(TEXT_DISCARDED, live.submitText(CHEAT_CODE, cheatFlags, true, game.getRuntimeRandom(), TYPED_TIME));

                        live.setLocalCheatsAllowed(true);

                        Assertions.assertEquals(solo ? TEXT_CHEAT : TEXT_DISCARDED,
                                                live.submitText(CHEAT_CODE, cheatFlags, true, game.getRuntimeRandom(), TYPED_TIME));
                    }

                    if (!live.exchangeTurn(frame)) {
                        continue;
                    }

                    ReplayRuntime runtime = runtimes.get(index);

                    runtime.step(FRAME_MILLISECONDS, network.now);

                    Assertions.assertNull(runtime.getFailure(), () -> "frame " + game.getCurrentFrame() + ": " + runtime.getFailure());

                    if (appliedFrame[index] < 0) {
                        Player player = game.getPlayerTable().getPlayer(localPlayer);

                        Assertions.assertNotNull(player);

                        if (player.getMinerals() >= initialMinerals + CHEAT_MINERALS) {
                            appliedFrame[index] = game.getCurrentFrame();
                        }
                    }
                }

                long frame = runtimes.getFirst().getGame().getCurrentFrame();

                for (int index = 1; index < players; index++) {
                    if (runtimes.get(index).getGame().getCurrentFrame() == frame) {
                        Assertions.assertEquals(SyncChecksumUtils.calculateUnitHash(firstGame),
                                                SyncChecksumUtils.calculateUnitHash(runtimes.get(index).getGame()),
                                                "unit hash at frame " + frame);
                    }
                }

                if (done) {
                    break;
                }
            }

            if (!solo) {
                Player player = firstGame.getPlayerTable().getPlayer(localPlayer);

                Assertions.assertNotNull(player);
                Assertions.assertTrue(player.getMinerals() < initialMinerals + CHEAT_MINERALS, "nothing applied");

                for (long frame : appliedFrame) {
                    Assertions.assertEquals(NOT_APPLIED, frame, "no client applies a cheat");
                }

                for (LiveGame live : games) {
                    Assertions.assertEquals(0, live.getSyncMismatches());
                }

                return;
            }

            for (int index = 0; index < players; index++) {
                Assertions.assertTrue(appliedFrame[index] > CHEAT_FRAME, "client " + index + " applied the cheat");
                Assertions.assertEquals(appliedFrame[HOST_CLIENT], appliedFrame[index], "every client applies it on the same frame");

                Player expectedPlayer = firstGame.getPlayerTable().getPlayer(localPlayer);

                Assertions.assertNotNull(expectedPlayer);

                int expectedMinerals = expectedPlayer.getMinerals();
                Player actualPlayer = runtimes.get(index).getGame().getPlayerTable().getPlayer(localPlayer);

                Assertions.assertNotNull(actualPlayer);
                Assertions.assertEquals(expectedMinerals, actualPlayer.getMinerals());
                Assertions.assertEquals(0, games.get(index).getSyncMismatches());
            }

            // Replay playback: the saved replay applies the recorded Cheat command too.
            ReplayFile saved = ReplayFile.parse(games.getFirst().writeReplay(FRAMES, 0));
            byte[] savedMapData = saved.getMapData();

            Assertions.assertNotNull(savedMapData);

            ReplayRuntime playback = new ReplayRuntime(saved, ChkFile.parseTolerant(savedMapData),
                                                       data,
                                                       VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                       INSTRUCTION_LIMIT);

            playback.start(0, 0);

            Assertions.assertNull(playback.getFailure());

            for (int frame = 0; frame < FRAMES; frame++) {
                Assertions.assertTrue(playback.step(FRAME_MILLISECONDS, frame * FRAME_MILLISECONDS),
                                      () -> "replay frame failed: " + playback.getFailure());
            }

            Player expectedPlayer = firstGame.getPlayerTable().getPlayer(localPlayer);

            Assertions.assertNotNull(expectedPlayer);

            int expectedMinerals = expectedPlayer.getMinerals();
            Player replayPlayer = playback.getGame().getPlayerTable().getPlayer(localPlayer);

            Assertions.assertNotNull(replayPlayer);
            Assertions.assertEquals(expectedMinerals, replayPlayer.getMinerals(), "the replay applies the cheat");
        }
    }
}
