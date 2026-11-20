package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.game.extension.ScrSimulationExtension;
import org.fogofwarlabs.sc4j.scr.game.extension.ScrSimulationPlugin;
import org.fogofwarlabs.sc4j.scr.game.extension.simulation.SimulationDecision;
import org.fogofwarlabs.sc4j.scr.game.extension.simulation.SimulationEvent;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * A simulation extension of the tests, named by the service file of the tests, that creates a plugin only while a thread records with it
 * ({@link #record}): the plugin keeps the scenarios its games were created with and the events, mod commands and frame starts of the games the
 * thread creates in a {@link Recording} and lets the recording's decider decide. Without recording it creates no plugin, so the other tests run without one.
 */
public final class RecordingSimulationPluginSource implements ScrSimulationExtension {
    /**
     * The recording of the games the current thread creates, or {@code null} while the thread does not record.
     */
    private static final @NotNull ThreadLocal<@Nullable Recording> RECORDINGS = new ThreadLocal<>();

    /**
     * What the plugins of a recording heard.
     */
    public static final class Recording {
        private final @NotNull List<@Nullable ChkFile> scenarios;
        private final @NotNull List<SimulationEvent> events;
        private final @NotNull List<Player> modCommandPlayers;
        private final @NotNull List<byte[]> modCommandData;
        private final @NotNull List<Integer> frameValues;

        private @Nullable ToIntFunction<Game> frameProbe;
        private @Nullable Consumer<SimulationDecision> decider;

        /**
         * Creates a {@link Recording} instance without a frame probe or a decider.
         */
        public Recording() {
            this.scenarios         = new ArrayList<>();
            this.events            = new ArrayList<>();
            this.modCommandPlayers = new ArrayList<>();
            this.modCommandData    = new ArrayList<>();
            this.frameValues       = new ArrayList<>();

            this.frameProbe = null;
            this.decider    = null;
        }

        /**
         * @return the scenarios of the games' setups when their plugins were created, in creation order
         */
        public @NotNull List<@Nullable ChkFile> getScenarios() {
            return this.scenarios;
        }

        /**
         * @return the events in report order
         */
        public @NotNull List<SimulationEvent> getEvents() {
            return this.events;
        }

        /**
         * Finds the events of a kind.
         *
         * @param <E>  the event class
         * @param kind the event class
         * @return the events of the kind in report order
         */
        public <E extends SimulationEvent> @NotNull List<E> findEvents(@NotNull Class<E> kind) {
            List<E> found = new ArrayList<>();

            for (SimulationEvent event : this.events) {
                if (kind.isInstance(event)) {
                    found.add(kind.cast(event));
                }
            }

            return found;
        }

        /**
         * @return the players of the received mod commands in run order
         */
        public @NotNull List<Player> getModCommandPlayers() {
            return this.modCommandPlayers;
        }

        /**
         * @return the data of the received mod commands in run order
         */
        public @NotNull List<byte[]> getModCommandData() {
            return this.modCommandData;
        }

        /**
         * @return the values the frame probe read at each frame start, or none without a probe
         */
        public @NotNull List<Integer> getFrameValues() {
            return this.frameValues;
        }

        /**
         * Sets what the plugin reads from its game at each frame start.
         *
         * @param frameProbe the probe, or {@code null} for none
         */
        public void setFrameProbe(@Nullable ToIntFunction<Game> frameProbe) {
            this.frameProbe = frameProbe;
        }

        /**
         * Sets how the plugin decides.
         *
         * @param decider the decider, or {@code null} to leave every decision unchanged
         */
        public void setDecider(@Nullable Consumer<SimulationDecision> decider) {
            this.decider = decider;
        }
    }

    /**
     * Runs an action with this extension's plugin in every game the current thread creates.
     *
     * @param recording the recording the plugins write
     * @param action    the action
     * @throws Throwable if the action fails
     */
    public static void record(@NotNull Recording recording, @NotNull Executable action) throws Throwable {
        RECORDINGS.set(recording);

        try {
            action.execute();
        } finally {
            RECORDINGS.remove();
        }
    }

    /**
     * Creates a plugin while the current thread records.
     *
     * @param game the game, attached and not yet initialized
     * @return the created plugin, or {@code null} while the thread does not record
     */
    @Override
    public @Nullable ScrSimulationPlugin createPlugin(@NotNull Game game) {
        Recording recording = RECORDINGS.get();

        if (recording == null) {
            return null;
        }

        recording.scenarios.add(game.getSetup().getScenario());

        return new ScrSimulationPlugin() {
            @Override
            public void onFrameStarted() {
                ToIntFunction<Game> probe = recording.frameProbe;

                if (probe != null) {
                    recording.frameValues.add(probe.applyAsInt(game));
                }
            }

            @Override
            public void onEvent(@NotNull SimulationEvent event) {
                recording.events.add(event);
            }

            @Override
            public void decide(@NotNull SimulationDecision decision) {
                Consumer<SimulationDecision> decider = recording.decider;

                if (decider != null) {
                    decider.accept(decision);
                }
            }

            @Override
            public void onModCommand(@NotNull Player player, byte @NotNull [] data) {
                recording.modCommandPlayers.add(player);
                recording.modCommandData.add(data);
            }
        };
    }
}
