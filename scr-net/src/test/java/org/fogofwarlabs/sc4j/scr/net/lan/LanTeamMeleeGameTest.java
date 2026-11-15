package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifies a Team Melee game of four sc4j clients in memory ({@code Templates/Team Melee(1).got}: two teams): each
 * team's leader takes a start slot and its teammate commands the leader's units (shared control), so both teammates play
 * the same player; every client runs the same commands and hashes alike.
 */
@RequiresInstallation
final class LanTeamMeleeGameTest {
    /**
     * {@code Team Melee(1).got} at {@code 0x41}: type {@code 0x0b}, subtype {@code 1} (value {@code 2}), teams {@code 2},
     * allies on, {@code 50} minerals.
     */
    private static final byte @NotNull [] TEAM_MELEE_2 = HexFormat.of().parseHex(
            "0b0001000200000001010102020001010201000000000032000000" + "0000000000"
    );
    private static final @NotNull String MAP_NAME = "(4)Blood Bath.scm";

    private static final int FRAMES = 300;
    private static final int LATENCY = 2;
    private static final int TURNS_IN_TRANSIT = 3;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;
    private static final int ITERATION_LIMIT = 300_000;

    /**
     * The milliseconds the lobby gets for the map transfer and for the countdown.
     */
    private static final int LOBBY_LIMIT_MILLISECONDS = 20_000;
    /**
     * The milliseconds the lobby runs before and after the race choices.
     */
    private static final int SETTLE_MILLISECONDS = 1000;

    /**
     * The clients of two teams of two humans each.
     */
    private static final int PLAYERS_WITH_TEAMMATES = 4;
    /**
     * The clients of two teams of one human each.
     */
    private static final int PLAYERS_WITHOUT_TEAMMATES = 2;
    private static final int HOST = 0;

    private static final int TEAMS = 2;
    /**
     * The lobby slots of one team of Team Melee with two teams.
     */
    private static final int TEAM_SLOTS = 4;
    private static final int TERRAN_RACE = 1;
    /**
     * The supply cap a leader has per Terran slot of its force, and at least.
     */
    private static final int TERRAN_SUPPLY_CAP = 400;

    /**
     * The most units of one selection.
     */
    private static final int SELECTION_LIMIT = 12;
    /**
     * The right-click command: x, y, target handle, target unit type, queued flag.
     */
    private static final int RIGHT_CLICK_SIZE = 11;
    /**
     * The select command's unit count byte, ahead of the units' handles.
     */
    private static final int SELECT_COUNT_SIZE = 1;
    private static final int FIRST_MOVE_FRAME = 40;
    private static final int MOVE_FRAME_SPACING = 20;
    private static final int MOVE_ORIGIN = 1200;
    private static final int MOVE_CLIENT_SPACING = 100;
    private static final int VISION_FRAME = 200;

    /**
     * The shared vision mask of every slot.
     */
    private static final int ALL_SLOTS_MASK = 0xFF;
    /**
     * The sprite flags of the dashed team selection circle.
     */
    private static final int TEAM_SELECTION_CIRCLE = 0x06;
    /**
     * The commands each client sends: one select and one order.
     */
    private static final int COMMANDS_PER_CLIENT = 2;
    /**
     * The vision command client {@code 0} sends.
     */
    private static final int VISION_COMMANDS = 1;

    /**
     * Selects the workers of the slot the local client commands and orders them to a point.
     *
     * @param live    the client's live game
     * @param game    the client's game
     * @param targetX the target x in pixels
     */
    private static void moveWorkers(@NotNull LiveGame live, @NotNull Game game, int targetX) {
        UnitPool pool = game.getUnitSystem().getUnitPool();
        List<Integer> handles = new ArrayList<>();

        Assertions.assertNotNull(pool);

        for (int index = pool.getFirstCreatedIndex(true); index != 0 && handles.size() < SELECTION_LIMIT; index = pool.getNextCreatedIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getOwnerIndex() == live.getLocalPlayer() &&
                (unit.getUnitId() == ScrUnitIds.TERRAN_SCV || unit.getUnitId() == ScrUnitIds.ZERG_DRONE ||
                 unit.getUnitId() == ScrUnitIds.PROTOSS_PROBE)) {
                handles.add(pool.getHandle(index));
            }
        }

        Assertions.assertFalse(handles.isEmpty(), "the commanded slot has workers");

        ByteBuffer select = ByteBuffer.allocate(SELECT_COUNT_SIZE + Integer.BYTES * handles.size()).order(ByteOrder.LITTLE_ENDIAN);

        select.put((byte) handles.size());

        for (int handle : handles) {
            select.putInt(handle);
        }

        Assertions.assertTrue(live.submit(ReplayCommandIds.SELECT_121, select.array()));

        ByteBuffer order = ByteBuffer.allocate(RIGHT_CLICK_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        order.putShort((short) targetX).putShort((short) MOVE_ORIGIN).putInt(0).putShort((short) ScrUnitIds.NONE).put((byte) 0);

        Assertions.assertTrue(live.submit(ReplayCommandIds.RIGHT_CLICK_121, order.array()));
    }


    @Test
    void teammates_share_their_leaders_units() throws Exception {
        this.play(PLAYERS_WITH_TEAMMATES);
    }

    /**
     * Plays one human per team: both are leaders.
     */
    @Test
    void one_human_per_team_plays_as_its_team_leader_in_sync() throws Exception {
        this.play(PLAYERS_WITHOUT_TEAMMATES);
    }

    private void play(int players) throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapFile = Files.readAllBytes(mapPath);
        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        ChkFile scenario = ChkFile.parseTolerant(mapData);
        LanGameOptions options = LanGameOptions.parse(TEAM_MELEE_2);

        Assertions.assertTrue(options.isTeamGame());

        InMemoryLanScenario network = new InMemoryLanScenario(players);
        LanLobby.Setup setup = LanLobby.Setup.create(scenario, options,
                                                     "host",
                                                     MAP_NAME,
                                                     mapFile.length, LanMapTransfer.calculateChecksum(mapFile, mapFile.length));
        LanLobby[] lobbies = network.lobbies;

        network.latency = LATENCY;
        network.host(setup, options.encode(), mapPath);

        for (int joiner = 1; joiner < players; joiner++) {
            network.join(joiner, mapPath.getParent(), mapPath.getParent().resolve("Download"));
        }

        network.runUntil(LOBBY_LIMIT_MILLISECONDS, lan -> {
            LanLobby host = lan.lobbies[HOST];

            Assertions.assertNotNull(host);

            return host.isMapTransferComplete();
        });
        network.run(SETTLE_MILLISECONDS);

        // Every client chose a race: the start check refuses race 7.
        for (LanLobby lobby : lobbies) {
            Assertions.assertNotNull(lobby);

            lobby.changeRace(TERRAN_RACE);
        }

        network.run(SETTLE_MILLISECONDS);

        LanLobby host = lobbies[HOST];

        Assertions.assertNotNull(host);
        Assertions.assertTrue(host.startGame());

        network.runUntil(LOBBY_LIMIT_MILLISECONDS, lan -> {
            for (LanLobby lobby : lan.lobbies) {
                Assertions.assertNotNull(lobby);

                if (lobby.getState() != LanLobby.STATE_GAME) {
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
                                                                     live.getLocalPlayer(), live.getLocalSlot(), true,
                                                                     live.getTeamSlotRaces(),
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

            // Two teams: two leaders, each commanded by two clients.
            List<Integer> leaders = new ArrayList<>();

            for (LiveGame live : games) {
                if (!leaders.contains(live.getLocalPlayer())) {
                    leaders.add(live.getLocalPlayer());
                }
            }

            Assertions.assertEquals(TEAMS, leaders.size(), "two teams, each playing its leader's slot");

            {
                UnitPool pool = runtimes.getFirst().getGame().getUnitSystem().getUnitPool();
                int[] owned = new int[PlayerTable.PLAYER_SLOTS];

                Assertions.assertNotNull(pool);

                for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                    owned[pool.getUnit(index).getOwnerIndex()]++;
                }

                // One base per team, at its leader's start location; the teammates' slots own nothing.
                for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                    Assertions.assertEquals(leaders.contains(slot), owned[slot] > 0, "slot " + slot + " owns units only as a leader");
                }

                // Only a leader has supply caps, 400 per Terran slot of its force (teams of four slots).
                Game first = runtimes.getFirst().getGame();
                int[] slotRaces = games.getFirst().getTeamSlotRaces();

                Assertions.assertNotNull(slotRaces);

                for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                    int expected = 0;

                    if (leaders.contains(slot)) {
                        Player leader = first.getPlayerTable().getPlayer(slot);

                        Assertions.assertNotNull(leader);

                        int force = leader.getTeam();
                        int terranSlots = 0;

                        for (int position = (force - 1) * TEAM_SLOTS; position < force * TEAM_SLOTS; position++) {
                            if (slotRaces[position] == TERRAN_RACE) {
                                terranSlots++;
                            }
                        }

                        expected = Math.max(terranSlots * TERRAN_SUPPLY_CAP, TERRAN_SUPPLY_CAP);
                    }

                    UnitAccounting accounting = first.getPlayerTable().getUnitAccounting();

                    Assertions.assertNotNull(accounting);
                    Assertions.assertEquals(expected, accounting.getSupplyCap(PlayerTable.PLAYER_SLOTS + slot), "Terran cap of slot " + slot);
                }
            }

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

                    // Every client, follower or leader, orders its team's workers.
                    if (frame == FIRST_MOVE_FRAME + MOVE_FRAME_SPACING * index) {
                        moveWorkers(live, game, MOVE_ORIGIN + MOVE_CLIENT_SPACING * index);
                    }

                    // The alliance dialog's Accept: client 0 shares vision with every slot.
                    if (frame == VISION_FRAME && index == HOST) {
                        Assertions.assertTrue(live.submit(ReplayCommandIds.VISION, new byte[]{(byte) ALL_SLOTS_MASK, 0}));
                    }

                    if (!live.exchangeTurn(frame)) {
                        continue;
                    }

                    ReplayRuntime runtime = runtimes.get(index);

                    runtime.step(FRAME_MILLISECONDS, network.now);

                    Assertions.assertNull(runtime.getFailure(), () -> "frame " + game.getCurrentFrame() + ": " + runtime.getFailure());
                }

                long frame = runtimes.getFirst().getGame().getCurrentFrame();

                for (int index = 1; index < players; index++) {
                    if (runtimes.get(index).getGame().getCurrentFrame() == frame) {
                        Assertions.assertEquals(SyncChecksumUtils.calculateUnitHash(runtimes.getFirst().getGame()),
                                                SyncChecksumUtils.calculateUnitHash(runtimes.get(index).getGame()),
                                                "unit hash at frame " + frame);
                    }
                }

                if (done) {
                    break;
                }
            }

            for (int index = 0; index < players; index++) {
                Assertions.assertEquals(FRAMES, runtimes.get(index).getGame().getCurrentFrame());
                Assertions.assertEquals(0, games.get(index).getSyncMismatches());
            }

            // Every human selects in its own slot; a teammate's selection marks its units with the dashed team circle count
            // for the local slot, here on client 0.
            {
                Game game = runtimes.getFirst().getGame();
                int localSlot = games.getFirst().getLocalSlot();

                for (int index = 0; index < players; index++) {
                    int slot = games.get(index).getLocalSlot();

                    UnitSelection selection = game.getSelection();

                    Assertions.assertNotNull(selection);
                    Assertions.assertNotEquals(0, selection.getSelectedPoolIndex(slot, 0), "slot " + slot + " keeps its own selection");

                    if (slot == localSlot) {
                        continue;
                    }

                    Player selectedPlayer = game.getPlayerTable().getPlayer(slot);

                    Assertions.assertNotNull(selectedPlayer);

                    int selectedTeam = selectedPlayer.getTeam();
                    Player localPlayer = game.getPlayerTable().getPlayer(localSlot);

                    Assertions.assertNotNull(localPlayer);

                    if (selectedTeam != localPlayer.getTeam()) {
                        continue;
                    }

                    UnitSelection currentSelection = game.getSelection();

                    Assertions.assertNotNull(currentSelection);

                    Unit unit = currentSelection.getSelectedUnit(slot, 0);

                    Assertions.assertNotNull(unit);

                    Sprite sprite = unit.getSpriteOrNull();

                    Assertions.assertNotNull(sprite);
                    Assertions.assertNotEquals(0, sprite.getFlags() & TEAM_SELECTION_CIRCLE, "the teammate's selection is marked");
                }
            }

            // Every client's select and order ran: 2 commands per client plus the vision command, recorded under the senders' ids.
            List<ReplayCommand> history = games.getFirst().getHistory();

            Assertions.assertEquals(COMMANDS_PER_CLIENT * players + VISION_COMMANDS, history.size(), "every client's commands ran");

            for (ReplayRuntime runtime : runtimes) {
                PlayerRelations relations = runtime.getGame().getPlayerTable().getPlayerRelations();

                Assertions.assertNotNull(relations);
                Assertions.assertEquals(ALL_SLOTS_MASK, relations.getSharedVision(games.getFirst().getLocalPlayer()) & ALL_SLOTS_MASK,
                                        "the vision command ran for the sender's commanded slot on every client");
            }

            // Replay engine: the saved replay of client 0 plays (shared control, team bases, team triggers).
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

            // Replay and live team-base worker races are excluded from this comparison.
        }
    }
}
