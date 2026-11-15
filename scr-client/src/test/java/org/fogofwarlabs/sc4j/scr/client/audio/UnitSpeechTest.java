package org.fogofwarlabs.sc4j.scr.client.audio;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks unit selection and acknowledgement responses on replay state.
 */
@RequiresInstallation
final class UnitSpeechTest {
    private static final long HOST_TICK_MILLISECONDS = 100;
    private static final int ALERT_COOLDOWN_TICKS = 100;
    private static final int SELECTION_GUARD_TICKS = 10;
    private static final int HIT_POINT_FRACTION_BITS = 8;
    /**
     * {@code 100} of {@code 1500} hit points, with {@link #HIT_POINT_FRACTION_BITS} fraction bits: a heavily damaged Command Center.
     */
    private static final int HEAVILY_DAMAGED_HIT_POINTS = 100 << HIT_POINT_FRACTION_BITS;
    /**
     * Hit points below {@code 0}, with {@link #HIT_POINT_FRACTION_BITS} fraction bits, whose negative percentage is not heavily damaged.
     */
    private static final int NEGATIVE_HIT_POINTS = -100 << HIT_POINT_FRACTION_BITS;

    private static final int LOCAL_PLAYER = 0;
    private static final int OTHER_PLAYER = 1;
    private static final int ZERG_RACE = 0;
    private static final int MAXIMUM_ALERT_MARK = 255;
    private static final int WHAT_RESPONSE_CLICKS = 4;
    private static final int RESPONSE_REPLAY_FRAME = 3000;
    /**
     * The C runtime generator step: {@code state * 214013 + 2531011}.
     */
    private static final int RUNTIME_RANDOM_MULTIPLIER = 214013;
    private static final int RUNTIME_RANDOM_INCREMENT = 2531011;

    private static final class Recorder implements UnitSpeech.Output {
        private static final int SOUND_LENGTH_MILLISECONDS = 2500;

        final @NotNull List<Integer> sounds;
        final @NotNull List<Long> talks;

        private Recorder() {
            this.sounds = new ArrayList<>();
            this.talks  = new ArrayList<>();
        }

        /**
         * Requests an interface sound when enabled.
         *
         * @param sound   the sound index
         * @param unit    the speaking unit, or {@code null}
         * @param enabled whether the caller enables this response
         * @return {@code true} if the sound started, {@code false} otherwise
         * @implSpec Records the sound index when enabled.
         */
        @Override
        public boolean play(int sound, @Nullable Unit unit, boolean enabled) {
            if (!enabled) {
                return false;
            }

            this.sounds.add(sound);

            return true;
        }

        /**
         * @param sound the sound index
         * @return the decoded length in milliseconds, {@code 0} if unknown
         * @implSpec Returns the fixed fixture sound length.
         */
        @Override
        public int getLength(int sound) {
            return SOUND_LENGTH_MILLISECONDS;
        }

        /**
         * Requests a talking portrait for the unit.
         *
         * @param unit                 the speaking unit
         * @param durationMilliseconds the talk duration in milliseconds
         * @implSpec Records the requested talk duration.
         */
        @Override
        public void talk(@NotNull Unit unit, long durationMilliseconds) {
            this.talks.add(durationMilliseconds);
        }
    }

    private static @NotNull Unit requireUnit(@NotNull Game game, int unitTypeIdentifier) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getOwnerIndex() == LOCAL_PLAYER && pool.getUnit(index).getUnitId() == unitTypeIdentifier) {
                return pool.getUnit(index);
            }
        }

        throw Exceptions.newAssertionError("no unit");
    }


    @Test
    void alert_mark_cadence_follows_host_ticks_across_session_restart() throws IOException {
        try (ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(),
                                                        GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN))) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();

            Game original = session.getGame();
            UnitPool pool = original.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);
            Assertions.assertNotEquals(0, pool.getFirstCreatedIndex(true));

            Unit active = pool.getUnit(pool.getFirstCreatedIndex(true));
            UnitSpeech speech = new UnitSpeech(SoundTable.load(session.getStorage()));

            active.setAlertMark(MAXIMUM_ALERT_MARK);
            speech.tick(1000, original.getLocalInterfaceEffects());

            Assertions.assertEquals(254, active.getAlertMark(), "The first accepted tick is due.");

            speech.tick(1000, original.getLocalInterfaceEffects());
            speech.tick(1099, original.getLocalInterfaceEffects());
            speech.tick(1100, original.getLocalInterfaceEffects());

            Assertions.assertEquals(254, active.getAlertMark());

            speech.tick(1200, original.getLocalInterfaceEffects());

            Assertions.assertEquals(253, active.getAlertMark());

            speech.tick(2200, original.getLocalInterfaceEffects());
            speech.tick(2200, original.getLocalInterfaceEffects());
            speech.tick(2299, original.getLocalInterfaceEffects());

            Assertions.assertEquals(253, active.getAlertMark(), "An elapsed second accepts one tick without catch-up.");

            speech.tick(2300, original.getLocalInterfaceEffects());
            speech.tick(2400, null);
            speech.tick(2500, null);

            Assertions.assertEquals(252, active.getAlertMark());
            Assertions.assertEquals(0, session.getLastCompletedFrame(), "Host ticks need no simulation frame.");

            session.reset();

            Game restarted = session.getGame();
            UnitPool restartedPool = restarted.getUnitSystem().getUnitPool();

            Assertions.assertNotSame(original, restarted);
            Assertions.assertNotNull(restartedPool);
            Assertions.assertNotEquals(0, restartedPool.getFirstCreatedIndex(true));

            Unit restartedActive = restartedPool.getUnit(restartedPool.getFirstCreatedIndex(true));

            restartedActive.setAlertMark(MAXIMUM_ALERT_MARK);
            speech.tick(2600, restarted.getLocalInterfaceEffects());

            Assertions.assertEquals(MAXIMUM_ALERT_MARK, restartedActive.getAlertMark(), "A missing world consumed the previous due tick.");

            speech.tick(2700, restarted.getLocalInterfaceEffects());

            Assertions.assertEquals(254, restartedActive.getAlertMark());
            Assertions.assertEquals(252, active.getAlertMark(), "The current world receives the write.");

            SoundSettings settings = new SoundSettings();

            settings.setUnitSpeech(false);

            UnitSpeech muted = new UnitSpeech(SoundTable.load(session.getStorage()), settings);
            Recorder output = new Recorder();

            muted.requestAttackAlert(restartedActive, ZERG_RACE, output);

            Assertions.assertTrue(muted.isAlertCoolingDown());
            Assertions.assertTrue(output.sounds.isEmpty());
            Assertions.assertEquals(1, output.talks.size());

            for (int tick = 0; tick < ALERT_COOLDOWN_TICKS - 1; tick++) {
                muted.tick(tick * HOST_TICK_MILLISECONDS, restarted.getLocalInterfaceEffects());
            }

            Assertions.assertTrue(muted.isAlertCoolingDown());

            muted.tick((ALERT_COOLDOWN_TICKS - 1) * HOST_TICK_MILLISECONDS, restarted.getLocalInterfaceEffects());

            Assertions.assertFalse(muted.isAlertCoolingDown(), "Disabling sound leaves the host cooldown active.");
        }
    }

    @Test
    void selection_cycles_what_then_pissed_and_acknowledges_with_yes() throws IOException {
        try (ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(),
                                                        GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN))) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();

            while (session.getLastCompletedFrame() < RESPONSE_REPLAY_FRAME && session.stepFrame(false)) {
            }

            Game game = session.getGame();
            Unit worker = requireUnit(game, ScrUnitIds.TERRAN_SCV);
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            DatTable units = gameData.getUnits();
            int whatStart = units.get("What Sound Start", ScrUnitIds.TERRAN_SCV);
            int whatEnd = units.get("What Sound End", ScrUnitIds.TERRAN_SCV);
            int pissStart = units.get("Piss Sound Start", ScrUnitIds.TERRAN_SCV);
            int pissEnd = units.get("Piss Sound End", ScrUnitIds.TERRAN_SCV);
            UnitSpeech speech = new UnitSpeech(SoundTable.load(session.getStorage()));
            Recorder output = new Recorder();
            long now = 0;

            for (int click = 0; click < WHAT_RESPONSE_CLICKS + (pissEnd - pissStart + 1) + 1; click++) {
                speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, output);

                for (int tick = 0; tick < SELECTION_GUARD_TICKS; tick++) {
                    speech.tick(now += HOST_TICK_MILLISECONDS, null);
                }
            }

            for (int index = 0; index < WHAT_RESPONSE_CLICKS; index++) {
                Assertions.assertTrue(output.sounds.get(index) >= whatStart && output.sounds.get(index) <= whatEnd, "What sounds first");
            }

            for (int index = 0; index <= pissEnd - pissStart; index++) {
                Assertions.assertEquals(pissStart + index, output.sounds.get(WHAT_RESPONSE_CLICKS + index), "Pissed sounds in order");
            }

            int after = output.sounds.getLast();

            Assertions.assertTrue(after >= whatStart && after <= whatEnd, "the cycle restarts with a What sound");
            Assertions.assertEquals(2000L, output.talks.getFirst(), "talk time is the whole seconds of the sound");

            int before = output.sounds.size();

            speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, output);
            speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, output);

            Assertions.assertEquals(before + 1, output.sounds.size(), "the re-click guard silences a quick second click");

            speech.requestOrderAcknowledgment(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, output);

            int yes = output.sounds.getLast();

            Assertions.assertTrue(yes >= units.get("Yes Sound Start", ScrUnitIds.TERRAN_SCV) &&
                                  yes <= units.get("Yes Sound End", ScrUnitIds.TERRAN_SCV));

            int count = output.sounds.size();

            speech.requestOrderAcknowledgment(new ScrGameView(game), new ScrPresentation(game), worker, OTHER_PLAYER, false, output);
            speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, true, output);

            Assertions.assertEquals(count, output.sounds.size(), "no response for another player's unit or in a replay");

            // Unit noise off: the unit's selection and acknowledgement play nothing and the selection does not talk.
            SoundSettings quiet = new SoundSettings();

            quiet.setUnitAcknowledgments(false);

            UnitSpeech muted = new UnitSpeech(SoundTable.load(session.getStorage()), quiet);
            Recorder silent = new Recorder();

            muted.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, silent);
            muted.requestOrderAcknowledgment(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, silent);

            Assertions.assertTrue(silent.sounds.isEmpty(), "unitnoise off silences the unit responses");
            Assertions.assertTrue(silent.talks.isEmpty(), "a silenced unit response does not talk");
        }
    }

    @Test
    void selection_response_draws_from_the_game_generator_that_creep_shares() throws IOException {
        try (ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(),
                                                        GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN))) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();
            session.stepFrame(false);

            Game game = session.getGame();
            Unit worker = requireUnit(game, ScrUnitIds.TERRAN_SCV);
            UnitSpeech speech = new UnitSpeech(SoundTable.load(session.getStorage()));
            RuntimeRandom runtimeRandom = game.getRuntimeRandom();
            int state = runtimeRandom.getState();
            int draws = runtimeRandom.getDrawCount();

            speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), worker, LOCAL_PLAYER, false, new Recorder());

            Assertions.assertEquals(draws + 1, runtimeRandom.getDrawCount(), "the What response draws once");
            Assertions.assertEquals(state * RUNTIME_RANDOM_MULTIPLIER + RUNTIME_RANDOM_INCREMENT, runtimeRandom.getState(),
                                    "the draw advances the game's generator");
        }
    }

    @Test
    void building_response_reads_negative_hit_points_as_not_heavily_damaged() throws IOException {
        try (ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(),
                                                        GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN))) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();
            session.stepFrame(false);

            Game game = session.getGame();
            Unit commandCenter = requireUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);
            UnitSpeech speech = new UnitSpeech(SoundTable.load(session.getStorage()));
            int burning = speech.getSoundIdentifier("SND_BIG_ON_FIRE");
            Recorder output = new Recorder();
            long now = 0;

            commandCenter.setHitPoints(HEAVILY_DAMAGED_HIT_POINTS);
            speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), commandCenter, LOCAL_PLAYER, false, output);

            Assertions.assertEquals(burning, output.sounds.getLast(), "a heavily damaged Terran building burns");

            int responses = output.sounds.size();

            for (int tick = 0; tick < SELECTION_GUARD_TICKS; tick++) {
                speech.tick(now += HOST_TICK_MILLISECONDS, null);
            }

            commandCenter.setHitPoints(NEGATIVE_HIT_POINTS);
            speech.requestSelectionResponse(new ScrGameView(game), new ScrPresentation(game), commandCenter, LOCAL_PLAYER, false, output);

            Assertions.assertEquals(responses + 1, output.sounds.size());
            Assertions.assertNotEquals(burning, output.sounds.getLast(), "a negative percentage is above one third unsigned");
        }
    }
}
