package org.fogofwarlabs.screcorder.sample;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.ai.AiSample;
import org.fogofwarlabs.screcorder.sample.ai.AiSampleDefinition;
import org.fogofwarlabs.screcorder.sample.camera.CameraSample;
import org.fogofwarlabs.screcorder.sample.camera.CameraSampleDefinition;
import org.fogofwarlabs.screcorder.sample.context.ContextSample;
import org.fogofwarlabs.screcorder.sample.context.ContextSampleDefinition;
import org.fogofwarlabs.screcorder.sample.eud.EudHostsSample;
import org.fogofwarlabs.screcorder.sample.eud.EudHostsSampleDefinition;
import org.fogofwarlabs.screcorder.sample.eud.EudSample;
import org.fogofwarlabs.screcorder.sample.eud.EudSampleDefinition;
import org.fogofwarlabs.screcorder.sample.frame.FrameStateSample;
import org.fogofwarlabs.screcorder.sample.frame.FrameStateSampleDefinition;
import org.fogofwarlabs.screcorder.sample.location.LocationsSample;
import org.fogofwarlabs.screcorder.sample.location.LocationsSampleDefinition;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.player.PlayersSampleDefinition;
import org.fogofwarlabs.screcorder.sample.random.*;
import org.fogofwarlabs.screcorder.sample.sync.SyncSample;
import org.fogofwarlabs.screcorder.sample.sync.SyncSampleDefinition;
import org.fogofwarlabs.screcorder.sample.tile.TilesSample;
import org.fogofwarlabs.screcorder.sample.tile.TilesSampleDefinition;
import org.fogofwarlabs.screcorder.sample.trigger.TriggersSample;
import org.fogofwarlabs.screcorder.sample.trigger.TriggersSampleDefinition;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSampleDefinition;
import org.fogofwarlabs.screcorder.sample.unitlist.UnitListsSample;
import org.fogofwarlabs.screcorder.sample.unitlist.UnitListsSampleDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds the supported sample definitions in stable wire identifier order.
 */
public final class SampleDefinitions {
    /**
     * Captures player resource and supply values.
     */
    public static final @NotNull SampleDefinition<PlayersSample> PLAYERS = new PlayersSampleDefinition();

    /**
     * Captures occupied unit state.
     */
    public static final @NotNull SampleDefinition<UnitsSample> UNITS = new UnitsSampleDefinition();

    /**
     * Captures random state and counters.
     */
    public static final @NotNull SampleDefinition<RandomSample> RANDOM = new RandomSampleDefinition();

    /**
     * Captures simulation clock and unit counters.
     */
    public static final @NotNull SampleDefinition<FrameStateSample> FRAME = new FrameStateSampleDefinition();

    /**
     * Captures per-source random counters.
     */
    public static final @NotNull SampleDefinition<RandomCountersSample> RANDOM_COUNTERS = new RandomCountersSampleDefinition();

    /**
     * Captures map location rectangles.
     */
    public static final @NotNull SampleDefinition<LocationsSample> LOCATIONS = new LocationsSampleDefinition();

    /**
     * Captures visible and hidden unit lists.
     */
    public static final @NotNull SampleDefinition<UnitListsSample> LISTS = new UnitListsSampleDefinition();

    /**
     * Captures raw game context bytes.
     */
    public static final @NotNull SampleDefinition<ContextSample> CONTEXT = new ContextSampleDefinition();

    /**
     * Captures trigger waits and local session flags.
     */
    public static final @NotNull SampleDefinition<TriggersSample> TRIGGERS = new TriggersSampleDefinition();

    /**
     * Captures emulated region definitions and raw state.
     */
    public static final @NotNull SampleDefinition<EudSample> EUD = new EudSampleDefinition();

    /**
     * Captures emulated region hosts and served data.
     */
    public static final @NotNull SampleDefinition<EudHostsSample> EUD_HOSTS = new EudHostsSampleDefinition();

    /**
     * Captures computer player state and routes.
     */
    public static final @NotNull SampleDefinition<AiSample> AI = new AiSampleDefinition();

    /**
     * Captures camera and input state.
     */
    public static final @NotNull SampleDefinition<CameraSample> CAMERA = new CameraSampleDefinition();

    /**
     * Captures network synchronization state.
     */
    public static final @NotNull SampleDefinition<SyncSample> SYNC = new SyncSampleDefinition();

    /**
     * Captures map tile identifiers.
     */
    public static final @NotNull SampleDefinition<TilesSample> TILES = new TilesSampleDefinition();

    /**
     * Captures per-thread C runtime random states.
     */
    public static final @NotNull SampleDefinition<RuntimeRandomSample> RUNTIME_RANDOM = new RuntimeRandomSampleDefinition();

    private static final @NotNull List<SampleDefinition<?>> ALL = List.of(
            PLAYERS, UNITS, RANDOM, FRAME, RANDOM_COUNTERS, LOCATIONS, LISTS,
            CONTEXT, TRIGGERS, EUD, EUD_HOSTS, AI, CAMERA, SYNC, TILES, RUNTIME_RANDOM
    );

    private static final @NotNull List<SampleDefinition<?>> COMPARISON = List.of(
            PLAYERS, UNITS, RANDOM, FRAME, RANDOM_COUNTERS, LOCATIONS, LISTS, TRIGGERS, EUD, EUD_HOSTS
    );

    static {
        int previousIdentifier = 0;

        for (int index = 0; index < ALL.size(); index++) {
            SampleDefinition<?> definition = ALL.get(index);

            if (definition.getIdentifier() <= previousIdentifier) {
                throw Exceptions.newIllegalStateException("Sample identifiers must be positive, unique and ordered");
            }

            for (int previousIndex = 0; previousIndex < index; previousIndex++) {
                SampleDefinition<?> previous = ALL.get(previousIndex);

                if (previous.getSampleClass() == definition.getSampleClass() || previous.getName().equals(definition.getName())) {
                    throw Exceptions.newIllegalStateException("Duplicate sample class or name: {0}", definition.getName());
                }
            }

            previousIdentifier = definition.getIdentifier();
        }
    }


    /**
     * @return all immutable supported definitions in wire identifier order
     */
    public static @NotNull List<SampleDefinition<?>> getAll() {
        return ALL;
    }

    /**
     * Returns the samples whose digests are compared across processes and with a simulation: {@code players},
     * {@code units}, {@code random}, {@code frame}, {@code random-counters}, {@code locations}, {@code lists},
     * {@code triggers}, {@code eud} and {@code eud-hosts}. The {@code context}, {@code ai}, {@code camera} and {@code sync}
     * samples hold client, render or process-local state, and the {@code tiles} and {@code runtime-random} samples hold
     * state that the C runtime random generator of each machine decides (creep tile variants); they are recorded for
     * diagnosis only.
     *
     * @return the immutable comparison definitions in wire identifier order
     */
    public static @NotNull List<SampleDefinition<?>> getComparisonDefinitions() {
        return COMPARISON;
    }

    /**
     * Finds a definition by its stable wire identifier.
     *
     * @param identifier the wire identifier
     * @return the matching definition, or {@code null} if unsupported
     */
    public static @Nullable SampleDefinition<?> findByIdentifier(int identifier) {
        for (SampleDefinition<?> definition : ALL) {
            if (definition.getIdentifier() == identifier) {
                return definition;
            }
        }

        return null;
    }

    /**
     * Finds a definition by its command-line name.
     *
     * @param name the sample name
     * @return the matching definition, or {@code null} if unsupported
     */
    public static @Nullable SampleDefinition<?> findByName(@NotNull String name) {
        for (SampleDefinition<?> definition : ALL) {
            if (definition.getName().equals(name)) {
                return definition;
            }
        }

        return null;
    }

    private SampleDefinitions() {
    }
}
