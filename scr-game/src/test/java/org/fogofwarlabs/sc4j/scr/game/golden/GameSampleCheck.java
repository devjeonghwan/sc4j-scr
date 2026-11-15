package org.fogofwarlabs.sc4j.scr.game.golden;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.recording.RecorderStateUtils;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerLocation;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.screcorder.format.recording.SampleDigester;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.fogofwarlabs.screcorder.sample.eud.EudHostsSample;
import org.fogofwarlabs.screcorder.sample.eud.EudSample;
import org.fogofwarlabs.screcorder.sample.frame.FrameStateSample;
import org.fogofwarlabs.screcorder.sample.location.LocationState;
import org.fogofwarlabs.screcorder.sample.location.LocationsSample;
import org.fogofwarlabs.screcorder.sample.player.PlayerState;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.random.RandomCountersSample;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.fogofwarlabs.screcorder.sample.trigger.TriggerWaitState;
import org.fogofwarlabs.screcorder.sample.trigger.TriggersSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitState;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSampleDefinition;
import org.fogofwarlabs.screcorder.sample.unitlist.UnitListsSample;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects the SC4J simulation state at a frame boundary onto the comparison samples of the native recorder
 * ({@link SampleDefinitions#getComparisonDefinitions()}), so that their digests ({@link SampleDigester}) can be compared with the digests of a native
 * recording.
 * <p>
 * The unit rows use the native field layout of {@link RecorderStateUtils}; the class-dependent unit union is decoded by unit type exactly as the
 * recorder does ({@link UnitsSampleDefinition#getUnionKind(int)}). The replay session values of the {@code triggers} sample (the local and storm
 * player DWORDs and the session flags) are fixed per run and supplied by the caller, because a replay simulation does not hold the native client's
 * values.
 * <p>
 * Instances are confined to one thread.
 */
final class GameSampleCheck {
    private static final int PLAYER_COUNT = 8;
    private static final int TRIGGER_WAIT_PLAYERS = 8;
    private static final int WORD_MASK = 0xFFFF;
    private static final int UNION_SECOND_WORD_SHIFT = 16;
    private static final int UNION_UPPER_WORDS_SHIFT = 32;

    private static @NotNull PlayersSample projectPlayers(@NotNull Game game) {
        List<PlayerState> players = new ArrayList<>(PLAYER_COUNT);

        for (int player = 0; player < PLAYER_COUNT; player++) {
            int[] values = RecorderStateUtils.getPlayerValues(game, player);
            int zerg = 0;
            int terran = RecorderStateUtils.SUPPLY_FIELDS_PER_RACE;
            int protoss = terran + RecorderStateUtils.SUPPLY_FIELDS_PER_RACE;

            players.add(new PlayerState(player,
                                        values[RecorderStateUtils.MINERALS_FIELD], values[RecorderStateUtils.GAS_FIELD],
                                        values[RecorderStateUtils.GATHERED_MINERALS_FIELD], values[RecorderStateUtils.GATHERED_GAS_FIELD],
                                        values[RecorderStateUtils.SUPPLY_PROVIDED_FIELD + zerg],
                                        values[RecorderStateUtils.SUPPLY_USED_FIELD + zerg],
                                        values[RecorderStateUtils.SUPPLY_CAP_FIELD + zerg],
                                        values[RecorderStateUtils.SUPPLY_PROVIDED_FIELD + terran],
                                        values[RecorderStateUtils.SUPPLY_USED_FIELD + terran],
                                        values[RecorderStateUtils.SUPPLY_CAP_FIELD + terran],
                                        values[RecorderStateUtils.SUPPLY_PROVIDED_FIELD + protoss],
                                        values[RecorderStateUtils.SUPPLY_USED_FIELD + protoss],
                                        values[RecorderStateUtils.SUPPLY_CAP_FIELD + protoss]));
        }

        return new PlayersSample(players);
    }

    private static @NotNull UnitsSample projectUnits(@NotNull UnitPool pool) {
        List<UnitState> units = new ArrayList<>();

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!RecorderStateUtils.isLive(pool, index)) {
                continue;
            }

            int[] values = RecorderStateUtils.getUnitValues(pool, index);
            Unit unit = pool.getUnit(index);
            int type = values[RecorderStateUtils.TYPE_FIELD];
            int unionKind = UnitsSampleDefinition.getUnionKind(type);
            long unionValue = switch (unionKind) {
                case UnitState.UNION_UNIT -> {
                    Unit target = RecorderStateUtils.getUnionPointerTarget(unit);

                    yield target == null ? 0 : target.getIndex();
                }
                case UnitState.UNION_SPRITE -> unit.getNukeDot() != null || unit.getPsiFieldSprite() != null ? 1 : 0;
                default -> Integer.toUnsignedLong(values[RecorderStateUtils.RESOURCES_FIELD] & WORD_MASK |
                                                  (values[RecorderStateUtils.UNION_WORD_2_FIELD] & WORD_MASK) << UNION_SECOND_WORD_SHIFT) |
                           Integer.toUnsignedLong(values[RecorderStateUtils.UNION_WORDS_3_4_FIELD]) << UNION_UPPER_WORDS_SHIFT;
            };

            units.add(new UnitState(index, values[RecorderStateUtils.GENERATION_FIELD], type, values[RecorderStateUtils.OWNER_FIELD],
                                    values[RecorderStateUtils.POSITION_X_FIELD], values[RecorderStateUtils.POSITION_Y_FIELD],
                                    values[RecorderStateUtils.EXACT_X_FIELD], values[RecorderStateUtils.EXACT_Y_FIELD],
                                    values[RecorderStateUtils.HIT_POINTS_FIELD], values[RecorderStateUtils.SHIELDS_FIELD],
                                    values[RecorderStateUtils.ENERGY_FIELD],
                                    values[RecorderStateUtils.ORDER_FIELD], values[RecorderStateUtils.ORDER_STATE_FIELD],
                                    values[RecorderStateUtils.ORDER_TIMER_FIELD],
                                    values[RecorderStateUtils.ORDER_TARGET_FIELD],
                                    values[RecorderStateUtils.ORDER_TARGET_X_FIELD], values[RecorderStateUtils.ORDER_TARGET_Y_FIELD],
                                    values[RecorderStateUtils.SECONDARY_ORDER_FIELD],
                                    values[RecorderStateUtils.MOVEMENT_STATE_FIELD], values[RecorderStateUtils.MOVEMENT_FLAGS_FIELD],
                                    values[RecorderStateUtils.HEADING_FIELD],
                                    values[RecorderStateUtils.CURRENT_SPEED_FIELD], Integer.toUnsignedLong(values[RecorderStateUtils.STATUS_FIELD]),
                                    unionKind, unionValue,
                                    values[RecorderStateUtils.CARRYING_FLAGS_FIELD], values[RecorderStateUtils.REMAINING_BUILD_TIME_FIELD]));
        }

        return new UnitsSample(units);
    }

    private static @NotNull FrameStateSample projectFrame(@NotNull Game game) {
        LcgRandom random = game.getRandom();
        UnitPool pool = game.getUnitSystem().requireUnitPool();
        GameFrame frame = game.getFrame();

        if (frame == null) {
            throw Exceptions.newIllegalStateException("The game has no frame pipeline.");
        }

        return new FrameStateSample(Integer.toUnsignedLong(random.getState()), Integer.toUnsignedLong(random.getTotalRandomCounts()),
                                    Integer.toUnsignedLong(requireTriggers(game).getElapsedClockTicks()), pool.getAllocatedCount(),
                                    Integer.toUnsignedLong(frame.getUnits().getPrimaryRedistributionTimer()),
                                    Integer.toUnsignedLong(frame.getUnits().getSecondaryRedistributionTimer()), pool.getCapacity());
    }

    private static @NotNull LocationsSample projectLocations(@NotNull TriggerContext triggers) {
        List<LocationState> locations = new ArrayList<>(TriggerContext.LOCATION_COUNT);

        for (int index = 0; index < TriggerContext.LOCATION_COUNT; index++) {
            TriggerLocation location = triggers.getLocation(index);

            locations.add(new LocationState(index + 1, Integer.toUnsignedLong(location.getLeft()), Integer.toUnsignedLong(location.getTop()),
                                            Integer.toUnsignedLong(location.getRight()), Integer.toUnsignedLong(location.getBottom()),
                                            location.getStringIndex() & WORD_MASK, location.getElevationFlags() & WORD_MASK));
        }

        return new LocationsSample(locations);
    }

    private static int @NotNull [] toArray(@NotNull List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static @NotNull TriggerContext requireTriggers(@NotNull Game game) {
        TriggerContext triggers = game.getTriggerContext();

        if (triggers == null) {
            throw Exceptions.newIllegalStateException("The game has no trigger context.");
        }

        return triggers;
    }


    private final @NotNull SampleDigester digester;

    private final int localPlayer;
    private final int stormPlayer;
    private final int multiplayerFlag;
    private final int replayFlag;

    /**
     * Creates a {@link GameSampleCheck} instance.
     *
     * @param localPlayer     the native local player DWORD of the session
     * @param stormPlayer     the native storm player DWORD of the session
     * @param multiplayerFlag the native multiplayer BYTE of the session
     * @param replayFlag      the native replay BYTE of the session
     */
    GameSampleCheck(int localPlayer, int stormPlayer, int multiplayerFlag, int replayFlag) {
        this.digester = new SampleDigester();

        this.localPlayer     = localPlayer;
        this.stormPlayer     = stormPlayer;
        this.multiplayerFlag = multiplayerFlag;
        this.replayFlag      = replayFlag;
    }

    /**
     * Computes the digest of one comparison sample.
     *
     * @param definition the comparison sample
     * @param game       the game at a frame boundary
     * @return the digest, or {@code null} when SC4J cannot project the sample in this game (an active EUD emulator)
     * @throws IOException if the projected sample cannot be encoded
     */
    byte @Nullable [] digest(@NotNull SampleDefinition<?> definition, @NotNull Game game) throws IOException {
        Sample sample = this.project(definition, game);

        return sample == null ? null : this.digestSample(definition, sample);
    }

    /**
     * Projects one comparison sample.
     *
     * @param definition the comparison sample
     * @param game       the game at a frame boundary
     * @return the sample, or {@code null} when SC4J cannot project it in this game
     * @throws IllegalArgumentException if {@code definition} is not a comparison sample
     */
    @Nullable Sample project(@NotNull SampleDefinition<?> definition, @NotNull Game game) {
        if (definition == SampleDefinitions.PLAYERS) {
            return projectPlayers(game);
        }

        if (definition == SampleDefinitions.UNITS) {
            return projectUnits(game.getUnitSystem().requireUnitPool());
        }

        if (definition == SampleDefinitions.RANDOM) {
            LcgRandom random = game.getRandom();

            return new RandomSample(Integer.toUnsignedLong(random.getState()), Integer.toUnsignedLong(random.getTotalRandomCounts()),
                                    Integer.toUnsignedLong(random.getEnabled()));
        }

        if (definition == SampleDefinitions.FRAME) {
            return projectFrame(game);
        }

        if (definition == SampleDefinitions.RANDOM_COUNTERS) {
            long[] counters = new long[RandomCountersSample.COUNTER_COUNT];

            for (int source = 0; source < counters.length; source++) {
                counters[source] = Integer.toUnsignedLong(game.getRandom().getCallCount(source));
            }

            return new RandomCountersSample(counters);
        }

        if (definition == SampleDefinitions.LOCATIONS) {
            return projectLocations(requireTriggers(game));
        }

        if (definition == SampleDefinitions.LISTS) {
            UnitPool pool = game.getUnitSystem().requireUnitPool();

            return new UnitListsSample(toArray(RecorderStateUtils.getUnitList(pool, true)), toArray(RecorderStateUtils.getUnitList(pool, false)));
        }

        if (definition == SampleDefinitions.TRIGGERS) {
            return this.projectTriggers(requireTriggers(game));
        }

        if (definition == SampleDefinitions.EUD) {
            int mode = requireTriggers(game).getExtendedDeathsMode();

            return mode == 0 ? new EudSample(0, 0, new byte[0], null, null, null, List.of()) : null;
        }

        if (definition == SampleDefinitions.EUD_HOSTS) {
            return requireTriggers(game).getExtendedDeathsMode() == 0 ? new EudHostsSample(List.of()) : null;
        }

        throw Exceptions.newIllegalArgumentException("{0} is not a comparison sample.", definition.getName());
    }

    private <S extends Sample> byte @NotNull [] digestSample(@NotNull SampleDefinition<S> definition, @NotNull Sample sample) throws IOException {
        return this.digester.digest(definition, definition.getSampleClass().cast(sample));
    }

    private @NotNull TriggersSample projectTriggers(@NotNull TriggerContext triggers) {
        List<TriggerWaitState> waits = new ArrayList<>(TRIGGER_WAIT_PLAYERS);

        for (int player = 0; player < TRIGGER_WAIT_PLAYERS; player++) {
            waits.add(new TriggerWaitState(player, triggers.isWaiting(player) ? 1 : 0, Integer.toUnsignedLong(triggers.getWaitTimer(player))));
        }

        return new TriggersSample(triggers.getCycleCountdown() & WORD_MASK, waits,
                                  this.localPlayer, this.stormPlayer, this.multiplayerFlag, this.replayFlag);
    }
}
