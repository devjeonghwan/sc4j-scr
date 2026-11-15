package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Checks hangar lists, which lose their dying, released and docking units whatever the parent type, fighter training and the creation of stored
 * subordinates.
 */
@RequiresInstallation
final class UnitHangarTest {
    private static final int GANTRITHOR_X = 3560;
    private static final int GANTRITHOR_Y = 2160;
    private static final int WARBRINGER_X = 3700;
    private static final int WARBRINGER_Y = 2440;
    private static final int CARRIER_X = 3400;
    private static final int CARRIER_Y = 2300;
    private static final int SHUTTLE_X = 3300;
    private static final int SHUTTLE_Y = 2000;
    private static final int PARENT_POOL_INDEX = 1;
    private static final int MAXIMUM_HANGAR_COUNT = 255;
    private static final int TARGET_DISTANCE = 20;
    private static final int DAMAGED_INTERCEPTOR_DIVISOR = 2;

    private static final int PENDING_CARRIER_MINERALS = 10000;
    private static final int TRAINING_STATE_START = 0;
    private static final int TRAINING_STATE_RETRY = 1;
    private static final int TRAINING_STATE_BUILDING = 2;
    private static final int TRAINING_STATE_REPAIRING = 3;
    private static final int GENERATION_ALIAS_QUEUE_INDEX = 6;
    private static final int MAXIMUM_QUEUE_INDEX = 0xFF;
    private static final int RETAINED_GENERATION = 0x7D;
    private static final int RETAINED_ENERGY = 0x5A6B;

    private static final int HUMAN_PLAYER_TYPE = 2;
    private static final int DIE_ORDER = 0;
    private static final int TRAIN_FIGHTER_ORDER = 63;
    private static final int CARRIER_TYPE = 72;
    private static final int INTERCEPTOR_TYPE = 73;
    private static final int EMPTY_UNIT_TYPE = 0xE4;
    private static final int FIRST_QUEUE_INDEX = 0;
    private static final int SECOND_QUEUE_INDEX = 1;
    private static final int QUEUE_SLOT_COUNT = 5;

    private static final int HANGAR_PARENT_X = 3560;
    private static final int HANGAR_PARENT_Y = 2160;
    private static final int INSIDE_TAG_BASE = 0x1100;
    private static final int STORED_SUBORDINATE_COUNT = 2;
    /**
     * The largest WORD creation tag.
     */
    private static final int MAXIMUM_WORD_TAG = 0xFFFF;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        Path installationPath = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationPath);

        gameData = GameData.loadFrom(installationPath);
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        runtime = null;

        if (installedData != null) {
            IgnoreException.execute(
                    installedData::close
            );
        }

        gameData = null;
    }


    private static @NotNull Unit createPendingCarrier(@NotNull Game game, int queueIndex) {
        Assertions.assertNotNull(game.getGameData());
        Assertions.assertNotNull(game.getUnitSystem().getUnitCreationContext());
        Assertions.assertNotNull(game.getPlayerTable().getUnitAccounting());

        Player player = game.getPlayerTable().getPlayer(RunningGameScenario.HUMAN);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(player);
        Assertions.assertNotNull(pool);
        Assertions.assertEquals(HUMAN_PLAYER_TYPE, player.getType());

        Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.HUMAN);

        Assertions.assertTrue(pool.isAllocated(carrier.getIndex()));
        Assertions.assertSame(carrier, pool.getUnit(carrier.getIndex()));
        Assertions.assertNotNull(carrier.getSpriteOrNull());
        Assertions.assertNull(carrier.getComputerRecord());
        Assertions.assertNotEquals(0, carrier.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(RunningGameScenario.HUMAN, carrier.getOwnerIndex());

        player.setMinerals(PENDING_CARRIER_MINERALS);
        carrier.setBuildQueueIndex(queueIndex);

        Assertions.assertEquals(queueIndex, game.getUnitProduction().pushBuildQueue(carrier, ScrUnitIds.PROTOSS_INTERCEPTOR));

        carrier.setSecondaryOrder(ScrOrderIds.TRAIN_FIGHTER);
        carrier.setSecondaryOrderState(TRAINING_STATE_START);
        game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

        Unit child = carrier.getCurrentBuildUnit();

        Assertions.assertEquals(TRAINING_STATE_BUILDING, carrier.getSecondaryOrderState());
        Assertions.assertNotNull(child);
        Assertions.assertTrue(pool.isAllocated(child.getIndex()));
        Assertions.assertSame(child, pool.getUnit(child.getIndex()));
        Assertions.assertNotNull(child.getSpriteOrNull());
        Assertions.assertEquals(INTERCEPTOR_TYPE, child.getUnitId());
        Assertions.assertEquals(RunningGameScenario.HUMAN, child.getOwnerIndex());
        Assertions.assertSame(carrier, child.getHangarParent());
        Assertions.assertEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertNotEquals(0, child.requireSprite().getFlags() & Sprite.FLAG_HIDDEN);
        Assertions.assertEquals(0, carrier.getHangarCount(false));
        Assertions.assertNull(carrier.getFirstHangarUnit(false));

        child.setRemainingBuildTime(0);

        return carrier;
    }

    /**
     * Starts a runtime of its own for one test, with the random number generator enabled as during a game.
     *
     * @return the started runtime
     * @throws IOException if the replay cannot be read
     */
    private static @NotNull ReplayRuntime startRandomEnabledGame() throws IOException {
        Assertions.assertNotNull(gameData);

        ReplayRuntime startedRuntime = RunningGameScenario.start(gameData);

        startedRuntime.getGame().getRandom().setEnabled(1);

        return startedRuntime;
    }


    @Test
    void destroy_should_unlink_dying_stored_interceptor_from_gantrithor() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit gantrithor = RunningGameScenario.createUnit(game,
                                                             ScrUnitIds.HERO_GANTRITHOR,
                                                             GANTRITHOR_X, GANTRITHOR_Y,
                                                             RunningGameScenario.COMPUTER);
            Unit dying = game.getUnitHangar().createStoredSubordinate(gantrithor,
                                                                      ScrUnitIds.PROTOSS_INTERCEPTOR,
                                                                      RunningGameScenario.INSTRUCTION_LIMIT);
            Unit kept = game.getUnitHangar().createStoredSubordinate(gantrithor,
                                                                     ScrUnitIds.PROTOSS_INTERCEPTOR,
                                                                     RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(dying);
            Assertions.assertNotNull(kept);

            game.getUnitLifecycle().die(dying, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(1, gantrithor.getHangarCount(false), "The dying Interceptor leaves the inside count.");
            Assertions.assertSame(kept, gantrithor.getFirstHangarUnit(false));
            Assertions.assertNull(kept.getNextHangarUnit(), "The dying Interceptor leaves the inside list.");
        });
    }

    @Test
    void destroy_should_unlink_dying_launched_scarab_from_warbringer_and_keep_its_own_links() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit warbringer = RunningGameScenario.createUnit(game,
                                                             ScrUnitIds.HERO_WARBRINGER,
                                                             WARBRINGER_X, WARBRINGER_Y,
                                                             RunningGameScenario.COMPUTER);
            Unit scarab = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_SCARAB, WARBRINGER_X, WARBRINGER_Y, RunningGameScenario.COMPUTER);

            scarab.setHangarParent(warbringer);
            UnitHangar.pushFront(warbringer, scarab, true);
            scarab.setHangarOutside(true);

            game.getUnitLifecycle().die(scarab, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, warbringer.getHangarCount(true), "The dying Scarab leaves the outside count.");
            Assertions.assertNull(warbringer.getFirstHangarUnit(true), "The dying Scarab leaves the outside list.");
            Assertions.assertSame(warbringer, scarab.getHangarParent(), "The dying Scarab keeps its own parent.");
        });
    }

    @Test
    void destroy_should_release_stored_interceptors_of_dying_carrier_before_they_die() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.COMPUTER);
            Unit first = game.getUnitHangar().createStoredSubordinate(carrier, ScrUnitIds.PROTOSS_INTERCEPTOR, RunningGameScenario.INSTRUCTION_LIMIT);
            Unit second = game.getUnitHangar().createStoredSubordinate(carrier,
                                                                       ScrUnitIds.PROTOSS_INTERCEPTOR,
                                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(first);
            Assertions.assertNotNull(second);

            game.getUnitLifecycle().die(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, carrier.getHangarCount(false), "The released hangar is empty.");
            Assertions.assertNull(carrier.getFirstHangarUnit(false));

            for (Unit interceptor : new Unit[]{first, second}) {
                Assertions.assertNull(interceptor.getHangarParent(), "A released Interceptor has no parent.");
                Assertions.assertEquals(DIE_ORDER, interceptor.getPrimaryOrder(), "A released stored Interceptor dies.");
            }
        });
    }

    @Test
    void update_interceptor_return_should_dock_into_parent_of_any_type() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit shuttle = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_SHUTTLE, SHUTTLE_X, SHUTTLE_Y, RunningGameScenario.COMPUTER);
            Unit interceptor = RunningGameScenario.createUnit(game,
                                                              ScrUnitIds.PROTOSS_INTERCEPTOR,
                                                              SHUTTLE_X, SHUTTLE_Y,
                                                              RunningGameScenario.COMPUTER);

            game.getUnitPositioning().move(interceptor, shuttle.getPositionX(), shuttle.getPositionY());
            interceptor.setHangarParent(shuttle);
            UnitHangar.pushFront(shuttle, interceptor, true);
            interceptor.setHangarOutside(true);

            game.getUnitHangar().updateInterceptorReturn(interceptor, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, shuttle.getHangarCount(true), "The docking Interceptor leaves the outside count.");
            Assertions.assertEquals(1, shuttle.getHangarCount(false), "The docking Interceptor enters the inside count.");
            Assertions.assertSame(interceptor, shuttle.getFirstHangarUnit(false));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void destroy_should_wrap_zero_count_when_unlinking_fighter(boolean outside) {
        Assertions.assertNotNull(runtime);

        Unit parent = UnitTestUtils.createUnit(PARENT_POOL_INDEX);
        Unit fighter = UnitTestUtils.createUnit(parent.getIndex() + 1);

        parent.setUnitId(ScrUnitIds.HERO_GANTRITHOR);
        parent.setFirstHangarUnit(outside, fighter);
        fighter.setUnitId(ScrUnitIds.PROTOSS_INTERCEPTOR);
        fighter.setStatus(UnitStatusFlags.COMPLETED);
        fighter.setHangarParent(parent);
        fighter.setHangarOutside(outside);

        runtime.getGame().getUnitHangar().destroy(fighter, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertEquals(MAXIMUM_HANGAR_COUNT, parent.getHangarCount(outside));
        Assertions.assertEquals(MAXIMUM_HANGAR_COUNT, UnitHangar.getInterceptorCount(runtime.getGame().requireGameData().getUnitTypes(), parent));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void push_front_should_wrap_maximum_count(boolean outside) {
        Unit parent = UnitTestUtils.createUnit(PARENT_POOL_INDEX);
        Unit fighter = UnitTestUtils.createUnit(parent.getIndex() + 1);

        parent.setHangarCount(outside, MAXIMUM_HANGAR_COUNT);

        UnitHangar.pushFront(parent, fighter, outside);

        Assertions.assertEquals(0, parent.getHangarCount(outside));
        Assertions.assertSame(fighter, parent.getFirstHangarUnit(outside));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void update_carrier_should_decide_inside_front_by_parent_pointer(boolean releasedFromFront) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.COMPUTER);
            Unit interceptor = game.getUnitHangar().createStoredSubordinate(carrier,
                                                                            ScrUnitIds.PROTOSS_INTERCEPTOR,
                                                                            RunningGameScenario.INSTRUCTION_LIMIT);
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_SCV,
                                                         CARRIER_X + TARGET_DISTANCE, CARRIER_Y,
                                                         RunningGameScenario.HUMAN);

            Assertions.assertNotNull(interceptor);

            Unit kept = null;

            if (releasedFromFront) {
                interceptor.setPreviousHangarUnit(target);
                target.setNextHangarUnit(interceptor);
            } else {
                kept = game.getUnitHangar().createStoredSubordinate(carrier, ScrUnitIds.PROTOSS_INTERCEPTOR, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertNotNull(kept);

                kept.setHitPoints(kept.getHitPoints() / DAMAGED_INTERCEPTOR_DIVISOR);
                interceptor.setPreviousHangarUnit(null);
            }

            target.requireSprite().setVisibilityFlags(1 << carrier.getOwnerIndex());
            carrier.setOrderTargetUnit(target);
            carrier.setOrderTargetX(target.getPositionX());
            carrier.setOrderTargetY(target.getPositionY());
            carrier.setPrimaryOrder(ScrOrderIds.CARRIER);
            carrier.setCooldownTimers(0);

            game.getUnitHangar().updateCarrier(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertTrue(interceptor.isHangarOutside());
            Assertions.assertSame(interceptor, carrier.getFirstHangarUnit(true));
            Assertions.assertSame(kept, carrier.getFirstHangarUnit(false));
            Assertions.assertEquals(releasedFromFront ? 0 : 1, carrier.getHangarCount(false));
        });
    }

    @Test
    void update_train_fighter_should_advance_after_completing_an_empty_current_slot() {
        ReplayRuntime activeRuntime = runtime;

        Assertions.assertNotNull(activeRuntime);

        RunningGameScenario.runInCommandDispatch(activeRuntime, () -> {
            Game game = activeRuntime.getGame();
            Unit carrier = createPendingCarrier(game, Unit.FIRST_QUEUE_SLOT);
            Unit child = carrier.getCurrentBuildUnit();

            Assertions.assertNotNull(child);

            carrier.setFirstBuildQueueType(ScrUnitIds.NONE);
            game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertNotEquals(0, child.requireSprite().getFlags() & Sprite.FLAG_HIDDEN);
            Assertions.assertSame(carrier, child.getHangarParent());
            Assertions.assertSame(child, carrier.getFirstHangarUnit(false));
            Assertions.assertEquals(1, carrier.getHangarCount(false));
            Assertions.assertFalse(child.isHangarOutside());
            Assertions.assertEquals(EMPTY_UNIT_TYPE, carrier.getFirstBuildQueueType());
            Assertions.assertNull(carrier.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, carrier.getSecondaryOrderState());
            Assertions.assertEquals(SECOND_QUEUE_INDEX, carrier.getBuildQueueIndex());
        });
    }

    @Test
    void update_train_fighter_should_clear_completed_fifth_slot_and_reset_the_index() {
        ReplayRuntime activeRuntime = runtime;

        Assertions.assertNotNull(activeRuntime);

        RunningGameScenario.runInCommandDispatch(activeRuntime, () -> {
            Game game = activeRuntime.getGame();
            Unit carrier = createPendingCarrier(game, Unit.FIFTH_QUEUE_SLOT);
            Unit child = carrier.getCurrentBuildUnit();

            Assertions.assertNotNull(child);

            game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertNotEquals(0, child.requireSprite().getFlags() & Sprite.FLAG_HIDDEN);
            Assertions.assertSame(carrier, child.getHangarParent());
            Assertions.assertSame(child, carrier.getFirstHangarUnit(false));
            Assertions.assertEquals(1, carrier.getHangarCount(false));
            Assertions.assertFalse(child.isHangarOutside());
            Assertions.assertEquals(EMPTY_UNIT_TYPE, carrier.getFifthBuildQueueType());
            Assertions.assertEquals(FIRST_QUEUE_INDEX, carrier.getBuildQueueIndex());
            Assertions.assertNull(carrier.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, carrier.getSecondaryOrderState());
        });
    }

    @Test
    void update_train_fighter_should_reread_generation_alias_and_apply_five_slot_threshold() {
        ReplayRuntime activeRuntime = runtime;

        Assertions.assertNotNull(activeRuntime);

        RunningGameScenario.runInCommandDispatch(activeRuntime, () -> {
            Game game = activeRuntime.getGame();
            Unit carrier = createPendingCarrier(game, Unit.FIRST_QUEUE_SLOT);
            Unit child = carrier.getCurrentBuildUnit();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(child);
            Assertions.assertNotNull(pool);

            pool.setGeneration(carrier.getIndex(), RETAINED_GENERATION);
            carrier.setEnergy(RETAINED_ENERGY);
            carrier.setBuildQueueIndex(GENERATION_ALIAS_QUEUE_INDEX);

            try {
                game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT);
            } catch (IllegalArgumentException exception) {
                Assertions.assertSame(child, carrier.getCurrentBuildUnit());
                Assertions.assertEquals(TRAINING_STATE_BUILDING, carrier.getSecondaryOrderState());
                Assertions.assertEquals(GENERATION_ALIAS_QUEUE_INDEX, carrier.getBuildQueueIndex());
                Assertions.assertEquals(RETAINED_GENERATION, pool.getGeneration(carrier.getIndex()));

                throw exception;
            } finally {
                Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
                Assertions.assertNotEquals(0, child.requireSprite().getFlags() & Sprite.FLAG_HIDDEN);
                Assertions.assertSame(carrier, child.getHangarParent());
                Assertions.assertSame(child, carrier.getFirstHangarUnit(false));
                Assertions.assertEquals(1, carrier.getHangarCount(false));
                Assertions.assertFalse(child.isHangarOutside());
            }

            Assertions.assertEquals(FIRST_QUEUE_INDEX, carrier.getBuildQueueIndex());
            Assertions.assertEquals(0, pool.getGeneration(carrier.getIndex()));
            Assertions.assertEquals(RETAINED_ENERGY, carrier.getEnergy());
            Assertions.assertEquals(INTERCEPTOR_TYPE, carrier.getFirstBuildQueueType());
            Assertions.assertNull(carrier.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, carrier.getSecondaryOrderState());
        });
    }

    @Test
    void update_train_fighter_should_wrap_maximum_byte_before_the_five_slot_threshold() {
        ReplayRuntime activeRuntime = runtime;

        Assertions.assertNotNull(activeRuntime);

        RunningGameScenario.runInCommandDispatch(activeRuntime, () -> {
            Game game = activeRuntime.getGame();
            Unit carrier = createPendingCarrier(game, Unit.FIRST_QUEUE_SLOT);
            Unit child = carrier.getCurrentBuildUnit();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(child);
            Assertions.assertNotNull(pool);

            pool.setGeneration(carrier.getIndex(), RETAINED_GENERATION);
            carrier.setEnergy(RETAINED_ENERGY);
            carrier.setBuildQueueIndex(MAXIMUM_QUEUE_INDEX);

            try {
                game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT);
            } catch (IllegalArgumentException exception) {
                Assertions.assertSame(child, carrier.getCurrentBuildUnit());
                Assertions.assertEquals(TRAINING_STATE_BUILDING, carrier.getSecondaryOrderState());
                Assertions.assertEquals(MAXIMUM_QUEUE_INDEX, carrier.getBuildQueueIndex());
                Assertions.assertEquals(RETAINED_GENERATION, pool.getGeneration(carrier.getIndex()));

                throw exception;
            } finally {
                Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
                Assertions.assertNotEquals(0, child.requireSprite().getFlags() & Sprite.FLAG_HIDDEN);
                Assertions.assertSame(carrier, child.getHangarParent());
                Assertions.assertSame(child, carrier.getFirstHangarUnit(false));
                Assertions.assertEquals(1, carrier.getHangarCount(false));
                Assertions.assertFalse(child.isHangarOutside());
            }

            Assertions.assertEquals(FIRST_QUEUE_INDEX, carrier.getBuildQueueIndex());
            Assertions.assertEquals(RETAINED_GENERATION, pool.getGeneration(carrier.getIndex()));
            Assertions.assertEquals(RETAINED_ENERGY, carrier.getEnergy());
            Assertions.assertEquals(INTERCEPTOR_TYPE, carrier.getFirstBuildQueueType());
            Assertions.assertNull(carrier.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, carrier.getSecondaryOrderState());
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {TRAINING_STATE_START, TRAINING_STATE_RETRY})
    void update_train_fighter_should_read_an_empty_energy_alias(int trainingState) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.COMPUTER);
            UnitPool pool = game.getUnitSystem().getUnitPool();
            Sprite sprite = carrier.getSpriteOrNull();

            Assertions.assertNotNull(pool);
            Assertions.assertTrue(pool.isAllocated(carrier.getIndex()));
            Assertions.assertEquals(CARRIER_TYPE, carrier.getUnitId());
            Assertions.assertEquals(RunningGameScenario.COMPUTER, carrier.getOwnerIndex());
            Assertions.assertTrue((carrier.getStatus() & UnitStatusFlags.COMPLETED) != 0);
            Assertions.assertNotNull(sprite);
            Assertions.assertNotNull(sprite.getMainImage());
            Assertions.assertNull(carrier.getCurrentBuildUnit());

            carrier.setFirstBuildQueueType(ScrUnitIds.PROTOSS_INTERCEPTOR);
            carrier.setBuildQueueIndex(Unit.BUILD_QUEUE_SLOTS);
            carrier.setEnergy(ScrUnitIds.NONE);
            carrier.setSecondaryOrder(ScrOrderIds.TRAIN_FIGHTER);
            carrier.setSecondaryOrderState(trainingState);

            Assertions.assertEquals(QUEUE_SLOT_COUNT, carrier.getBuildQueueIndex());
            Assertions.assertEquals(EMPTY_UNIT_TYPE, carrier.getEnergy());

            int allocatedCount = pool.getAllocatedCount();
            int randomCounts = game.getRandom().getTotalRandomCounts();

            Assertions.assertDoesNotThrow(() -> game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT));

            Assertions.assertEquals(TRAINING_STATE_REPAIRING, carrier.getSecondaryOrderState());
            Assertions.assertEquals(TRAIN_FIGHTER_ORDER, carrier.getSecondaryOrder());
            Assertions.assertNull(carrier.getCurrentBuildUnit());
            Assertions.assertEquals(QUEUE_SLOT_COUNT, carrier.getBuildQueueIndex());
            Assertions.assertEquals(EMPTY_UNIT_TYPE, carrier.getEnergy());
            Assertions.assertEquals(INTERCEPTOR_TYPE, carrier.getFirstBuildQueueType());
            Assertions.assertEquals(allocatedCount, pool.getAllocatedCount());
            Assertions.assertEquals(randomCounts, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void update_train_fighter_should_store_a_changed_type_and_wrap_the_inside_byte() throws IOException {
        ReplayRuntime activeRuntime = startRandomEnabledGame();

        RunningGameScenario.runInCommandDispatch(activeRuntime, () -> {
            Game game = activeRuntime.getGame();
            Unit parent = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.PROTOSS_REAVER,
                                                         HANGAR_PARENT_X, HANGAR_PARENT_Y,
                                                         RunningGameScenario.HUMAN);
            UnitCreationContext context = game.getUnitSystem().getUnitCreationContext();
            UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(context);
            Assertions.assertNotNull(accounting);

            Unit child = game.getUnitStorageConstructor().create(context,
                                                                 accounting,
                                                                 ScrUnitIds.TERRAN_MARINE,
                                                                 HANGAR_PARENT_X, HANGAR_PARENT_Y,
                                                                 RunningGameScenario.HUMAN,
                                                                 INSIDE_TAG_BASE,
                                                                 RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(child);

            child.setHangarParent(parent);
            child.setRemainingBuildTime(0);
            parent.setCurrentBuildUnit(child);
            parent.setSecondaryOrderState(TRAINING_STATE_BUILDING);
            parent.setHangarCount(false, MAXIMUM_HANGAR_COUNT);

            game.getUnitHangar().updateTrainFighter(parent, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, parent.getHangarCount(false));
            Assertions.assertSame(child, parent.getFirstHangarUnit(false));
            Assertions.assertNull(parent.getCurrentBuildUnit());
            HangarAssert.assertStored(parent, child);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {ScrUnitIds.PROTOSS_CARRIER, ScrUnitIds.HERO_GANTRITHOR, ScrUnitIds.PROTOSS_REAVER, ScrUnitIds.HERO_WARBRINGER})
    void create_stored_subordinate_should_keep_completion_tags_and_front_order(int parentType) throws IOException {
        ReplayRuntime activeRuntime = startRandomEnabledGame();

        RunningGameScenario.runInCommandDispatch(activeRuntime, () -> {
            Game game = activeRuntime.getGame();
            Unit parent = RunningGameScenario.createUnit(game, parentType, HANGAR_PARENT_X, HANGAR_PARENT_Y, RunningGameScenario.HUMAN);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            pool.setCreationTag(parent.getIndex(), MAXIMUM_WORD_TAG);

            int subordinateType = UnitHangar.getSubordinateType(game.requireGameData().getUnitTypes(), parent.getUnitId());
            Unit first = game.getUnitHangar().createStoredSubordinate(parent, subordinateType, RunningGameScenario.INSTRUCTION_LIMIT);
            Unit second = game.getUnitHangar().createStoredSubordinate(parent, subordinateType, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(first);
            Assertions.assertNotNull(second);
            Assertions.assertSame(second, parent.getFirstHangarUnit(false));
            Assertions.assertSame(first, second.getNextHangarUnit());
            Assertions.assertSame(second, first.getPreviousHangarUnit());
            Assertions.assertNull(second.getPreviousHangarUnit());
            Assertions.assertNull(first.getNextHangarUnit());
            Assertions.assertEquals(STORED_SUBORDINATE_COUNT, parent.getHangarCount(false));
            Assertions.assertEquals(0, parent.getHangarCount(true));
            Assertions.assertEquals(MAXIMUM_WORD_TAG, pool.getCreationTag(first.getIndex()));
            Assertions.assertEquals(MAXIMUM_WORD_TAG, pool.getCreationTag(second.getIndex()));
            Assertions.assertNotEquals(0, first.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertNotEquals(0, second.getStatus() & UnitStatusFlags.COMPLETED);
            HangarAssert.assertStored(parent, first);
            HangarAssert.assertStored(parent, second);
        });
    }
}
