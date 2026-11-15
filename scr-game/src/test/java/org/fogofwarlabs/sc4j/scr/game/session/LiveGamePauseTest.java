package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks trigger-driven pause iterations, at the profile speed and at the speed override of a game name, against saved replay playback.
 */
@RequiresInstallation
final class LiveGamePauseTest {
    private static final int MATCHED_PROFILE_SPEED = 4;

    private static final int ITERATIONS = 150;
    private static final int WAIT_MILLISECONDS = 1000;
    private static final int TRIGGER_UPDATE_MILLISECONDS = 56;
    /**
     * A network game's name with the third speed override, and the frame duration in milliseconds the override selects.
     */
    private static final @NotNull String OVERRIDE_GAME_NAME = "Wait #x3";
    private static final int OVERRIDE_INDEX = 3;
    private static final int OVERRIDE_TRIGGER_UPDATE_MILLISECONDS = 21;
    private static final int PAUSED_ITERATION_TOLERANCE = 3;

    private static final int TRIGGER_SIZE = 2400;
    private static final int CONDITION_COUNT = 16;
    private static final int CONDITION_SIZE = 20;
    private static final int CONDITION_TYPE_OFFSET = 15;
    private static final int ACTION_SIZE = 32;
    private static final int ACTION_TIME_OFFSET = 12;
    private static final int ACTION_TYPE_OFFSET = 26;
    private static final int FIRST_PLAYER_EXECUTION_OFFSET = 0x944;
    private static final byte CONDITION_ALWAYS = 22;
    private static final byte ACTION_WAIT = 4;
    private static final byte ACTION_PAUSE_GAME = 5;
    private static final byte ACTION_UNPAUSE_GAME = 6;

    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 12345;

    /**
     * Collects the ordered frame, random, trigger and allocation checkpoint.
     *
     * @param state the simulation world
     * @return the checkpoint text
     */
    private static @NotNull String createTrace(@NotNull Game state) {
        long currentFrame = state.getCurrentFrame();
        int randomState = state.getRandom().getState();
        int totalRandomCounts = state.getRandom().getTotalRandomCounts();
        TriggerContext triggers = state.getTriggerContext();

        Assertions.assertNotNull(triggers);

        int phaseGate = triggers.getPhaseGate();
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        return currentFrame + " " + randomState + " " + totalRandomCounts + " " + phaseGate + " " + pool.getAllocatedCount();
    }

    /**
     * Appends a TRIG section with one trigger for player 1: condition Always; actions Pause Game, Wait, Unpause Game.
     *
     * @param scenario the original scenario bytes
     * @return the scenario with the ordered pause, wait and unpause trigger
     */
    private static byte @NotNull [] appendPauseTrigger(byte @NotNull [] scenario) {
        byte[] trigger = new byte[TRIGGER_SIZE];

        // Condition 0: Always.
        trigger[CONDITION_TYPE_OFFSET] = CONDITION_ALWAYS;

        int actions = CONDITION_COUNT * CONDITION_SIZE;

        // Action 0: Pause Game; action 1: Wait with its time; action 2: Unpause Game.
        trigger[actions + ACTION_TYPE_OFFSET] = ACTION_PAUSE_GAME;
        writeInteger(trigger, actions + ACTION_SIZE + ACTION_TIME_OFFSET, WAIT_MILLISECONDS);
        trigger[actions + ACTION_SIZE + ACTION_TYPE_OFFSET]     = ACTION_WAIT;
        trigger[actions + 2 * ACTION_SIZE + ACTION_TYPE_OFFSET] = ACTION_UNPAUSE_GAME;
        trigger[FIRST_PLAYER_EXECUTION_OFFSET]                  = 1;

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.writeBytes(scenario);
        output.writeBytes(new byte[]{'T', 'R', 'I', 'G'});

        byte[] size = new byte[Integer.BYTES];

        writeInteger(size, 0, trigger.length);
        output.writeBytes(size);
        output.writeBytes(trigger);

        return output.toByteArray();
    }

    /**
     * Writes the complete integer as four little-endian bytes.
     *
     * @param data   the destination bytes
     * @param offset the destination byte offset
     * @param value  the complete integer value
     */
    private static void writeInteger(byte @NotNull [] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }


    @Test
    void pause_trigger_holds_frames_until_unpause_and_replays_identically() throws IOException {
        this.playPauseTrigger(null, TRIGGER_UPDATE_MILLISECONDS);
    }

    @Test
    void pause_trigger_wait_counts_the_speed_override_of_the_game_name_and_replays_identically() throws IOException {
        this.playPauseTrigger(OVERRIDE_GAME_NAME, OVERRIDE_TRIGGER_UPDATE_MILLISECONDS);
    }

    /**
     * Plays the pause trigger's game live, then its written replay, and compares the checkpoints of every iteration.
     *
     * @param gameName                  the network game's name the setup takes, or {@code null} for none
     * @param triggerUpdateMilliseconds the milliseconds the Wait counts per trigger update
     * @throws IOException if the map or the written replay cannot be read
     */
    private void playPauseTrigger(@Nullable String gameName, int triggerUpdateMilliseconds) throws IOException {
        byte[] mapData = appendPauseTrigger(LiveSessionScenario.readBottleneckScenario());

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, MATCHED_PROFILE_SPEED);

            if (gameName != null) {
                setup.setNetworkGameInformation(gameName, 0);
            }

            LiveGame game = new LiveGame(setup, GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());
            Assertions.assertEquals(triggerUpdateMilliseconds, live.getFrameDuration());

            Game state = live.getGame();
            List<String> liveTrace = new ArrayList<>();
            int pausedIterations = 0;

            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                game.exchangeTurn(state.getCurrentFrame());

                long before = state.getCurrentFrame();
                TriggerContext triggersBeforeStep = state.getTriggerContext();

                Assertions.assertNotNull(triggersBeforeStep);

                int gateBefore = triggersBeforeStep.getPhaseGate();

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, iteration * GameSpeed.FASTEST_DURATION));

                if (state.getCurrentFrame() == before) {
                    pausedIterations++;

                    Assertions.assertNotEquals(0, gateBefore, "A frame is held only while the game was paused.");
                }

                liveTrace.add(createTrace(state));
            }

            // Wait counts the frame duration per trigger update (56 ms at the profile speed: 1000 ms hold about 18 iterations; 21 ms with the
            // override: about 47), then Unpause Game resumes frames.
            Assertions.assertTrue(pausedIterations >= WAIT_MILLISECONDS / triggerUpdateMilliseconds &&
                                  pausedIterations <= WAIT_MILLISECONDS / triggerUpdateMilliseconds + PAUSED_ITERATION_TOLERANCE,
                                  "paused iterations " + pausedIterations);

            TriggerContext triggersAfterPause = state.getTriggerContext();

            Assertions.assertNotNull(triggersAfterPause);
            Assertions.assertEquals(0, triggersAfterPause.getPhaseGate());
            Assertions.assertEquals(ITERATIONS - pausedIterations, state.getCurrentFrame());

            ReplayFile saved = ReplayFile.parse(game.writeReplay(state.getCurrentFrame(), 0));
            byte[] savedMap = saved.getMapData();

            Assertions.assertNotNull(savedMap);
            Assertions.assertEquals(gameName == null ? 0 : OVERRIDE_INDEX, saved.getSpeedOverrideIndex());

            ReplayRuntime playback = new ReplayRuntime(saved, ChkFile.parseTolerant(savedMap), gameData,
                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            playback.start(0, 0);

            Assertions.assertNull(playback.getFailure());

            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                Assertions.assertTrue(playback.step(GameSpeed.FASTEST_DURATION, iteration * GameSpeed.FASTEST_DURATION), "iteration " + iteration);
                Assertions.assertEquals(liveTrace.get(iteration), createTrace(playback.getGame()), "iteration " + iteration);
            }
        }
    }
}
