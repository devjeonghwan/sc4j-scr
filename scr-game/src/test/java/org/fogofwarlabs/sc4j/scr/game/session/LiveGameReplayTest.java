package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.ProductionStatistics;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameRulesVersion;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a saved single-player replay reproduces the live simulation frame by frame.
 */
final class LiveGameReplayTest {
    private static final int SIMULATION_HASH_MULTIPLIER = 31;
    private static final long PLAYER_RESOURCE_HASH_MULTIPLIER = 7L;
    private static final int MAP_SETTINGS_FRAME_COUNT = 600;
    private static final int DETAIL_FRAME_LIMIT = 20;
    private static final int SELECT_WORKERS_FRAME = 10;
    private static final int GAME_SPEED_COMMAND_FRAME = 300;
    private static final int FIRST_CHEAT_FRAME = 30;
    private static final int REJECTED_TEXT_FRAME = 31;
    private static final int LATER_CHEATS_FRAME = 500;
    /**
     * The Unix time of a typed line.
     */
    private static final int TYPED_TIME = 0x6543210F;
    private static final int FIRST_TRAIN_FRAME = 40;
    private static final int SECOND_TRAIN_FRAME = 400;

    private static final int MATCHED_PROFILE_SPEED = 4;

    private static final int FRAMES = 1500;
    private static final int GRANTED_MINERALS_THRESHOLD = 9000;

    private static final int RIGHT_CLICK_PAYLOAD_SIZE = 11;
    private static final int RIGHT_CLICK_X_OFFSET = 0;
    private static final int RIGHT_CLICK_Y_OFFSET = 2;
    private static final int RIGHT_CLICK_TARGET_OFFSET = 4;
    private static final int RIGHT_CLICK_UNIT_TYPE_OFFSET = 8;

    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 0x6AB9B92E;

    /**
     * The text results of a discarded text and of a cheat.
     */
    private static final int TEXT_DISCARDED = 0;
    private static final int TEXT_CHEAT = 1;
    /**
     * The game type of a Use Map Settings game.
     */
    private static final int USE_MAP_SETTINGS_GAME_TYPE = 10;

    /**
     * Selects the human's workers and sends them to the nearest mineral field, then trains workers from the town hall.
     *
     * @param game   the live command-stream session
     * @param state  the simulation world
     * @param player the commanding player slot
     * @param frame  the current script frame
     */
    private static void issueScriptedCommands(@NotNull LiveGame game, @NotNull Game state, int player, int frame) {
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        if (frame == SELECT_WORKERS_FRAME) {
            List<Integer> workers = findUnits(state, player, ScrUnitIds.TERRAN_SCV);
            byte[] selection = new byte[1 + workers.size() * Integer.BYTES];

            selection[0] = (byte) workers.size();

            for (int index = 0; index < workers.size(); index++) {
                writeInteger(selection, 1 + index * Integer.BYTES, pool.getHandle(workers.get(index)));
            }

            game.submit(ReplayCommandIds.SELECT_121, selection);

            Unit worker = pool.getUnit(workers.get(0));
            int mineral = findNearestUnit(state, worker, ScrUnitIds.RESOURCE_MINERAL_FIELD);
            Unit target = pool.getUnit(mineral);
            byte[] click = new byte[RIGHT_CLICK_PAYLOAD_SIZE];

            writeShort(click, RIGHT_CLICK_X_OFFSET, target.getPositionX());
            writeShort(click, RIGHT_CLICK_Y_OFFSET, target.getPositionY());
            writeInteger(click, RIGHT_CLICK_TARGET_OFFSET, pool.getHandle(mineral));
            writeShort(click, RIGHT_CLICK_UNIT_TYPE_OFFSET, target.getUnitId());
            game.submit(ReplayCommandIds.RIGHT_CLICK_121, click);
        }

        if (frame == GAME_SPEED_COMMAND_FRAME) {
            game.submit(ReplayCommandIds.GAME_SPEED, new byte[]{MATCHED_PROFILE_SPEED});
        }

        if (frame == FIRST_CHEAT_FRAME) {
            GameFrame resourceCheatFrame = state.getFrame();

            Assertions.assertNotNull(resourceCheatFrame);

            Assertions.assertEquals(TEXT_CHEAT, game.submitText("show me the money", resourceCheatFrame.getVisibility().getCheatFlags(), true,
                                                                state.getRuntimeRandom(), TYPED_TIME));
        }

        if (frame == REJECTED_TEXT_FRAME) {
            GameFrame discardedTextFrame = state.getFrame();

            Assertions.assertNotNull(discardedTextFrame);

            Assertions.assertEquals(TEXT_DISCARDED, game.submitText("hello", discardedTextFrame.getVisibility().getCheatFlags(), true,
                                                                    state.getRuntimeRandom(), TYPED_TIME));
        }

        if (frame == LATER_CHEATS_FRAME) {
            GameFrame speedCheatFrame = state.getFrame();

            Assertions.assertNotNull(speedCheatFrame);

            Assertions.assertEquals(TEXT_CHEAT, game.submitText("operation cwal", speedCheatFrame.getVisibility().getCheatFlags(), true,
                                                                state.getRuntimeRandom(), TYPED_TIME));

            GameFrame damageCheatFrame = state.getFrame();

            Assertions.assertNotNull(damageCheatFrame);

            Assertions.assertEquals(TEXT_CHEAT, game.submitText("power overwhelming", damageCheatFrame.getVisibility().getCheatFlags(), true,
                                                                state.getRuntimeRandom(), TYPED_TIME));
        }

        if (frame == FIRST_TRAIN_FRAME || frame == SECOND_TRAIN_FRAME) {
            int hall = findUnits(state, player, ScrUnitIds.TERRAN_COMMAND_CENTER).get(0);
            byte[] selection = new byte[1 + Integer.BYTES];

            selection[0] = 1;
            writeInteger(selection, 1, pool.getHandle(hall));
            game.submit(ReplayCommandIds.SELECT_121, selection);
            game.submit(ReplayCommandIds.TRAIN, new byte[]{(byte) ScrUnitIds.TERRAN_SCV, 0});
        }
    }

    /**
     * Hashes the synchronized game state: random generator and its source counters, players, triggers, accounting, registry, statistics, projectiles,
     * tiles and every unit slot. The replay viewer's progress display, which depends on the header frame count, is deliberately excluded.
     *
     * @param state the simulation world
     * @return the ordered simulation hash
     */
    private static long computeSimulationHash(@NotNull Game state) {
        long hash = state.getCurrentFrame();

        hash = hash * SIMULATION_HASH_MULTIPLIER + state.getRandom().getState();
        hash = hash * SIMULATION_HASH_MULTIPLIER + state.getRandom().getTotalRandomCounts();

        for (int source = 0; source < LcgRandom.SOURCE_COUNT; source++) {
            hash = hash * SIMULATION_HASH_MULTIPLIER + state.getRandom().getCallCount(source);
        }

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            Player slotPlayer = state.getPlayerTable().getPlayer(player);

            Assertions.assertNotNull(slotPlayer);

            hash = hash * SIMULATION_HASH_MULTIPLIER + slotPlayer.getMinerals();
            hash = hash * SIMULATION_HASH_MULTIPLIER + slotPlayer.getGas();
            hash = hash * SIMULATION_HASH_MULTIPLIER + slotPlayer.getVictoryState();
        }

        TriggerContext triggers = state.getTriggerContext();

        Assertions.assertNotNull(triggers);

        hash = hash * SIMULATION_HASH_MULTIPLIER + triggers.getElapsedClockTicks();

        UnitAccounting accounting = state.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        hash = accounting.computeStateHash(hash);

        ProductionStatistics statistics = state.getPlayerTable().getProductionStatistics();

        Assertions.assertNotNull(statistics);

        hash = hash * SIMULATION_HASH_MULTIPLIER + statistics.computeStateHash(0);

        GameFrame frame = state.getFrame();

        Assertions.assertNotNull(frame);

        hash = hash * SIMULATION_HASH_MULTIPLIER + frame.getProjectiles().computeStateHash(0);

        for (int tile = 0; tile < state.getMap().getTileFlagCount(); tile++) {
            hash = hash * SIMULATION_HASH_MULTIPLIER + (state.getMap().getTileFlags(tile) & ~MapTileFlags.TEMPORARY_CREEP);
        }

        hash = hash * SIMULATION_HASH_MULTIPLIER + state.getRequestedGameSpeed();

        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        hash = hash * SIMULATION_HASH_MULTIPLIER + pool.getAllocatedCount();

        for (int index = 1; index <= pool.getCapacity(); index++) {
            hash = hash * SIMULATION_HASH_MULTIPLIER + pool.getGeneration(index);
            hash = hash * SIMULATION_HASH_MULTIPLIER + (pool.isAllocated(index) ? 1 : 0);
            hash = pool.getUnit(index).computeStateHash((int) hash);
        }

        return hash;
    }

    /**
     * Collects ordered simulation component hashes for diagnostics.
     *
     * @param state the simulation world
     * @return the component diagnostic text
     */
    private static @NotNull String describeComponents(@NotNull Game state) {
        StringBuilder text = new StringBuilder();

        text.append("rng=").append(state.getRandom().getState()).append('/').append(state.getRandom().getTotalRandomCounts());

        long players = 0;

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            Player slotPlayer = state.getPlayerTable().getPlayer(player);

            Assertions.assertNotNull(slotPlayer);

            long resourceContribution = slotPlayer.getMinerals() * PLAYER_RESOURCE_HASH_MULTIPLIER;

            players = players * SIMULATION_HASH_MULTIPLIER + resourceContribution + slotPlayer.getVictoryState();
        }

        text.append(" players=").append(players);
        text.append(" trig=");

        TriggerContext triggers = state.getTriggerContext();

        Assertions.assertNotNull(triggers);

        text.append(triggers.computeStateHash(0));
        text.append(" acc=");

        UnitAccounting accounting = state.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        text.append(accounting.computeStateHash(0));
        text.append(" reg=");

        UnitRegistry registry = state.getUnitSystem().getUnitRegistry();

        Assertions.assertNotNull(registry);

        text.append(registry.computeStateHash(0));
        text.append(" stat=");

        ProductionStatistics statistics = state.getPlayerTable().getProductionStatistics();

        Assertions.assertNotNull(statistics);

        text.append(statistics.computeStateHash(0));
        text.append(" proj=");

        GameFrame frame = state.getFrame();

        Assertions.assertNotNull(frame);

        text.append(frame.getProjectiles().computeStateHash(0));

        long tiles = 0;

        for (int tile = 0; tile < state.getMap().getTileFlagCount(); tile++) {
            tiles = tiles * SIMULATION_HASH_MULTIPLIER + (state.getMap().getTileFlags(tile) & ~MapTileFlags.TEMPORARY_CREEP);
        }

        text.append(" tiles=").append(tiles);

        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index)) {
                text.append(" u").append(index).append('=').append(pool.getUnit(index).computeStateHash(0));
            }
        }

        return text.toString();
    }

    /**
     * Collects allocated units matching the owner and type.
     *
     * @param state    the simulation world
     * @param owner    the unit owner
     * @param unitType the native unit type
     * @return the ordered matching slot indices
     */
    private static @NotNull List<Integer> findUnits(@NotNull Game state, int owner, int unitType) {
        List<Integer> result = new ArrayList<>();
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getSpriteOrNull() != null &&
                pool.getUnit(index).getOwnerIndex() == owner && pool.getUnit(index).getUnitId() == unitType) {
                result.add(index);
            }
        }

        return result;
    }

    /**
     * Finds the nearest allocated unit of the requested type.
     *
     * @param state    the simulation world
     * @param origin   the origin unit
     * @param unitType the native unit type
     * @return the matching slot, or zero when none exists
     */
    private static int findNearestUnit(@NotNull Game state, @NotNull Unit origin, int unitType) {
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        int best = 0;
        long bestDistance = Long.MAX_VALUE;

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index) || pool.getUnit(index).getSpriteOrNull() == null || pool.getUnit(index).getUnitId() != unitType) {
                continue;
            }

            long horizontalDifference = pool.getUnit(index).getPositionX() - origin.getPositionX();
            long verticalDifference = pool.getUnit(index).getPositionY() - origin.getPositionY();

            if (horizontalDifference * horizontalDifference + verticalDifference * verticalDifference < bestDistance) {
                bestDistance = horizontalDifference * horizontalDifference + verticalDifference * verticalDifference;
                best         = index;
            }
        }

        return best;
    }

    /**
     * Writes the low integer bits as two little-endian bytes.
     *
     * @param data   the destination bytes
     * @param offset the destination byte offset
     * @param value  the complete integer value
     */
    private static void writeShort(byte @NotNull [] data, int offset, int value) {
        data[offset]     = (byte) value;
        data[offset + 1] = (byte) (value >>> Byte.SIZE);
    }

    /**
     * Writes the complete integer as four little-endian bytes.
     *
     * @param data   the destination bytes
     * @param offset the destination byte offset
     * @param value  the complete integer value
     */
    private static void writeInteger(byte @NotNull [] data, int offset, int value) {
        writeShort(data, offset, value);
        writeShort(data, offset + Short.BYTES, value >>> Short.SIZE);
    }


    @RequiresInstallation
    @Test
    void saved_replay_reproduces_scripted_live_melee_game_frame_by_frame() throws IOException {
        byte[] mapData = LiveSessionScenario.readBottleneckScenario();

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            MeleeGameSetup setup = new MeleeGameSetup(mapData,
                                                      PLAYER_NAME, ScrRaceIds.TERRAN, 0,
                                                      new int[]{ScrRaceIds.RANDOM},
                                                      GameSpeed.FASTEST);
            LiveGame game = new LiveGame(setup, GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertTrue(live.getGame().getRulesVersion() == GameRulesVersion.newGame());

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();
            int player = game.getLocalPlayer();
            List<Long> liveHashes = new ArrayList<>();
            List<String> liveDetails = new ArrayList<>();
            int initialWorkers = findUnits(state, player, ScrUnitIds.TERRAN_SCV).size();

            for (int frame = 0; frame < FRAMES; frame++) {
                issueScriptedCommands(game, state, player, frame);
                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                liveHashes.add(computeSimulationHash(state));

                if (frame < DETAIL_FRAME_LIMIT) {
                    liveDetails.add(describeComponents(state));
                }

                game.updateOutcome(state);
            }

            Assertions.assertTrue(findUnits(state, player, ScrUnitIds.TERRAN_SCV).size() > initialWorkers, "The trained workers must exist.");

            Player resourcePlayer = state.getPlayerTable().getPlayer(player);

            Assertions.assertNotNull(resourcePlayer);

            Assertions.assertTrue(resourcePlayer.getMinerals() > GRANTED_MINERALS_THRESHOLD, "show me the money must have granted resources.");
            Assertions.assertFalse(game.getHistory().isEmpty());
            // Commands queued while frame F is current execute and are recorded at frame F + 1.
            Assertions.assertEquals(SELECT_WORKERS_FRAME + 1, game.getHistory().get(0).getFrame());

            byte[] file = game.writeReplay(state.getCurrentFrame(), 0);
            ReplayFile saved = ReplayFile.parse(file);

            Assertions.assertEquals(FRAMES, saved.getHeader().getFrames());
            Assertions.assertEquals(game.getHistory().size(), saved.getCommands().size());

            byte[] savedMap = saved.getMapData();

            Assertions.assertNotNull(savedMap);

            ReplayRuntime playback = new ReplayRuntime(saved, ChkFile.parseTolerant(savedMap), gameData,
                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            playback.start(0, 0);

            Assertions.assertNull(playback.getFailure());

            for (int frame = 0; frame < FRAMES; frame++) {
                Assertions.assertTrue(playback.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                long expected = liveHashes.get(frame);
                long actual = computeSimulationHash(playback.getGame());

                if (expected != actual && frame < DETAIL_FRAME_LIMIT) {
                    System.out.println("MISMATCH frame " + (frame + 1) + " LIVE " + liveDetails.get(frame) + " PLAY " +
                                       describeComponents(playback.getGame()));
                }

                Assertions.assertEquals(expected, actual, "simulation hash after frame " + (frame + 1));
            }

            Assertions.assertFalse(playback.step(GameSpeed.FASTEST_DURATION, 0), "The replay ends at its recorded frame count.");
            Assertions.assertEquals(state.getRandom().getState(), playback.getGame().getRandom().getState());
            Assertions.assertEquals(state.getRandom().getTotalRandomCounts(), playback.getGame().getRandom().getTotalRandomCounts());
        }
    }

    @RequiresInstallation
    @Test
    void use_map_settings_live_game_replays_identically() throws IOException {
        Path recording = GameTestResources.getPath(GameTestResources.REPLAY_UMS_SURVIVAL);

        byte[] mapData = ReplayFile.parse(Files.readAllBytes(recording)).getMapData();

        Assertions.assertNotNull(mapData);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, GameSpeed.FASTEST);
            LiveGame game = new LiveGame(setup, GAME_SEED);

            Assertions.assertEquals(USE_MAP_SETTINGS_GAME_TYPE, game.getReplay().getHeader().getType());
            Assertions.assertTrue(game.getReplay().getHeader().getSettings().usesMapSettings());

            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            List<Long> hashes = new ArrayList<>();

            for (int frame = 0; frame < MAP_SETTINGS_FRAME_COUNT; frame++) {
                game.exchangeTurn(live.getGame().getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                hashes.add(computeSimulationHash(live.getGame()));
            }

            ReplayFile saved = ReplayFile.parse(game.writeReplay(live.getGame().getCurrentFrame(), 0));
            byte[] savedMap = saved.getMapData();

            Assertions.assertNotNull(savedMap);

            ReplayRuntime playback = new ReplayRuntime(saved, ChkFile.parseTolerant(savedMap), gameData,
                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            playback.start(0, 0);

            Assertions.assertNull(playback.getFailure());

            for (int frame = 0; frame < MAP_SETTINGS_FRAME_COUNT; frame++) {
                Assertions.assertTrue(playback.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
                Assertions.assertEquals(hashes.get(frame), computeSimulationHash(playback.getGame()), "simulation hash after frame " + (frame + 1));
            }
        }
    }
}
