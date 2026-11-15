package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies created hallucinations' accounting, constructor metadata and counted hangar copies while keeping preplaced conversion distinct.
 */
@RequiresInstallation
final class HallucinationCreationTest {
    private static final int SOURCE_X = 3560;
    private static final int SOURCE_Y = 2160;
    private static final int CASTER_DISTANCE = 96;
    private static final int MAXIMUM_CASTER_ENERGY = 200;
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int COPY_COUNT = 2;
    private static final int SOURCE_LIST_LENGTH = 2;
    private static final int CREATION_RANDOM_SOURCE = 14;
    private static final int WORD_MASK = 0xFFFF;

    private static final int MAXIMUM_BYTE_COUNT = 255;
    private static final int INSIDE_TAG_BASE = 0x1100;
    private static final int OUTSIDE_TAG_BASE = 0x2200;
    private static final int WIDTH_TAG_BASE = 0x3300;
    private static final int PARTIAL_FREE_SLOTS = 3;
    private static final int PARTIAL_CREATION_ATTEMPTS = 5;
    private static final int HALLUCINATION_REMOVAL_TIMER = 1350;
    private static final int HALLUCINATION_MODIFIER = 16;
    private static final int HALLUCINATION2_ORDER = 149;
    private static final int CAST_HALLUCINATION_ORDER = 148;
    private static final int COMPLETED_HALLUCINATION_STATUS_FLAGS = 0x40000001;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        runtime = null;

        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }

        gameData = null;
    }

    private static @NotNull Unit createSource(@NotNull Game game, int type) {
        return RunningGameScenario.createUnit(game, type, SOURCE_X, SOURCE_Y, RunningGameScenario.HUMAN);
    }

    private static @NotNull Unit createCaster(@NotNull Game game, @NotNull Unit target) {
        Unit caster = RunningGameScenario.createUnit(game,
                                                     ScrUnitIds.PROTOSS_HIGH_TEMPLAR,
                                                     SOURCE_X + CASTER_DISTANCE, SOURCE_Y,
                                                     RunningGameScenario.HUMAN);
        Sprite targetSprite = target.requireSprite();
        Sprite casterSprite = caster.requireSprite();
        int direction = FlingyMotionUtils.calculateDirection(targetSprite.getX() - casterSprite.getX(), targetSprite.getY() - casterSprite.getY());

        targetSprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
        caster.resetDirection(game, direction);
        caster.setEnergy(MAXIMUM_CASTER_ENERGY << FIXED_POINT_FRACTION_BITS);
        caster.setPrimaryOrder(ScrOrderIds.CAST_HALLUCINATION);
        caster.setPrimaryOrderState(0);
        caster.setOrderTargetUnit(target);

        return caster;
    }

    private static @NotNull Unit createChild(@NotNull Game game, @NotNull Unit parent, int type, boolean outside, int tag) {
        Unit child = createSource(game, type);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        pool.setCreationTag(child.getIndex(), tag);
        child.setHangarParent(parent);
        game.getUnitLifecycle().hide(child, true);
        UnitHangar.pushFront(parent, child, outside);
        child.setHangarOutside(outside);

        return child;
    }

    private static @NotNull List<Unit> cast(@NotNull Game game, @NotNull Unit caster, @NotNull Unit source, int expectedCopies) {
        int initialEnergy = caster.getEnergy();
        int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.HALLUCINATION) << FIXED_POINT_FRACTION_BITS;

        game.getUnitSpells().updateHallucination(caster, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertEquals(initialEnergy - cost, caster.getEnergy(), "The cast actually spends energy.");
        Assertions.assertNotEquals(CAST_HALLUCINATION_ORDER, caster.getPrimaryOrder(), "The cast actually finishes.");

        UnitPool pool = game.getUnitSystem().getUnitPool();
        List<Unit> copies = new ArrayList<>();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit candidate = pool.getUnit(index);
            Sprite sprite = candidate.getSpriteOrNull();

            if (candidate != source && candidate.getUnitId() == source.getUnitId() &&
                candidate.getOwnerIndex() == RunningGameScenario.HUMAN && (candidate.getStatus() & UnitStatusFlags.HALLUCINATION) != 0 &&
                candidate.getHangarParent() == null && sprite != null && (sprite.getFlags() & Sprite.FLAG_HIDDEN) == 0) {
                copies.add(candidate);
            }
        }

        Assertions.assertEquals(expectedCopies, copies.size(), "Successful placed copies are observable.");

        return copies;
    }

    private static void checkCreated(@NotNull Game game, @NotNull Unit unit) {
        Assertions.assertEquals(COMPLETED_HALLUCINATION_STATUS_FLAGS, unit.getStatus() & (UnitStatusFlags.COMPLETED | UnitStatusFlags.HALLUCINATION));
        Assertions.assertEquals(game.requireGameData().getUnitTypes().getHitPoints(unit.getUnitId()), unit.getHitPoints());
        Assertions.assertEquals(HALLUCINATION_REMOVAL_TIMER, unit.getRemainingLifetime());
        Assertions.assertNotEquals(HALLUCINATION2_ORDER, unit.getSecondaryOrder());
    }


    @BeforeEach
    void startGame() throws IOException {
        Assertions.assertNotNull(gameData);

        runtime = RunningGameScenario.start(gameData);

        runtime.getGame().getRandom().setEnabled(1);
    }

    @AfterEach
    void releaseGame() {
        runtime = null;
    }


    @Test
    void update_hallucination_should_preserve_real_completed_and_production_accounts() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.TERRAN_MARINE);
            Unit caster = createCaster(game, source);
            UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(accounting);

            int owner = RunningGameScenario.HUMAN;
            int type = source.getUnitId();
            int completed = accounting.getCompletedTypeCount(type, owner);
            int ordinary = accounting.getTypeCount(type, owner);
            int produced = accounting.getProducedUnitCount(owner);
            int score = accounting.getUnitProductionScore(owner);
            int supply = accounting.getSupplyUsed(PlayerTable.PLAYER_SLOTS + owner);
            int creations = game.getRandom().getCallCount(CREATION_RANDOM_SOURCE);

            List<Unit> copies = cast(game, caster, source, COPY_COUNT);

            Assertions.assertEquals(completed, accounting.getCompletedTypeCount(type, owner));
            Assertions.assertEquals(ordinary, accounting.getTypeCount(type, owner));
            Assertions.assertEquals(produced, accounting.getProducedUnitCount(owner));
            Assertions.assertEquals(score, accounting.getUnitProductionScore(owner));
            Assertions.assertEquals(supply, accounting.getSupplyUsed(PlayerTable.PLAYER_SLOTS + owner));
            Assertions.assertEquals(COPY_COUNT, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE) - creations);

            for (Unit copy : copies) {
                checkCreated(game, copy);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 0x1ABCD})
    void update_hallucination_should_inherit_source_word_tags_without_converting_turrets(int tag) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE);
            Unit caster = createCaster(game, source);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            pool.setCreationTag(source.getIndex(), tag);

            for (Unit copy : cast(game, caster, source, COPY_COUNT)) {
                Assertions.assertEquals(tag & WORD_MASK, pool.getCreationTag(copy.getIndex()));

                Unit turret = copy.getSubunit();

                Assertions.assertNotNull(turret);
                Assertions.assertEquals(0, turret.getStatus() & UnitStatusFlags.HALLUCINATION);
                Assertions.assertEquals(tag & WORD_MASK, pool.getCreationTag(turret.getIndex()));
                checkCreated(game, copy);
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "0,1", "1,1"})
    void update_hallucination_should_copy_counted_prefixes_stored_before_launched(int inside, int outside) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.PROTOSS_REAVER);
            Unit caster = createCaster(game, source);
            Unit insideTail = createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, false, INSIDE_TAG_BASE + 1);
            Unit insideHead = createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, false, INSIDE_TAG_BASE + SOURCE_LIST_LENGTH);
            Unit outsideTail = createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, true, OUTSIDE_TAG_BASE + 1);
            Unit outsideHead = createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, true, OUTSIDE_TAG_BASE + SOURCE_LIST_LENGTH);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            source.setHangarCount(false, inside);
            source.setHangarCount(true, outside);

            int creations = game.getRandom().getCallCount(CREATION_RANDOM_SOURCE);

            try {
                for (Unit copy : cast(game, caster, source, COPY_COUNT)) {
                    Assertions.assertEquals(inside + outside, copy.getHangarCount(false));
                    Assertions.assertEquals(0, copy.getHangarCount(true));
                    Assertions.assertNull(copy.getFirstHangarUnit(true));

                    Unit child = copy.getFirstHangarUnit(false);

                    if (outside != 0) {
                        Assertions.assertNotNull(child);
                        Assertions.assertEquals(OUTSIDE_TAG_BASE + SOURCE_LIST_LENGTH, pool.getCreationTag(child.getIndex()));
                        HangarAssert.assertStored(copy, child);
                        checkCreated(game, child);

                        child = child.getNextHangarUnit();
                    }

                    if (inside != 0) {
                        Assertions.assertNotNull(child);
                        Assertions.assertEquals(INSIDE_TAG_BASE + SOURCE_LIST_LENGTH, pool.getCreationTag(child.getIndex()));
                        HangarAssert.assertStored(copy, child);
                        checkCreated(game, child);

                        child = child.getNextHangarUnit();
                    }

                    Assertions.assertNull(child);
                }

                Assertions.assertEquals(COPY_COUNT * (1 + inside + outside), game.getRandom().getCallCount(CREATION_RANDOM_SOURCE) - creations);
                Assertions.assertSame(insideHead, source.getFirstHangarUnit(false));
                Assertions.assertSame(insideTail, insideHead.getNextHangarUnit());
                Assertions.assertSame(outsideHead, source.getFirstHangarUnit(true));
                Assertions.assertSame(outsideTail, outsideHead.getNextHangarUnit());
                Assertions.assertEquals(inside, source.getHangarCount(false));
                Assertions.assertEquals(outside, source.getHangarCount(true));
            } finally {
                source.setHangarCount(true, SOURCE_LIST_LENGTH);
                source.setHangarCount(false, SOURCE_LIST_LENGTH);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {ScrUnitIds.TERRAN_MARINE, ScrUnitIds.TERRAN_VULTURE})
    void update_hallucination_should_store_children_regardless_of_fighter_type(int childType) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.PROTOSS_REAVER);
            Unit caster = createCaster(game, source);
            Unit child = createChild(game, source, childType, false, INSIDE_TAG_BASE);

            for (Unit copy : cast(game, caster, source, COPY_COUNT)) {
                Assertions.assertEquals(1, copy.getHangarCount(false));

                Unit copiedChild = copy.getFirstHangarUnit(false);

                Assertions.assertNotNull(copiedChild);
                Assertions.assertEquals(child.getUnitId(), copiedChild.getUnitId());
                Assertions.assertNull(copiedChild.getNextHangarUnit());
                HangarAssert.assertStored(copy, copiedChild);
                checkCreated(game, copiedChild);
            }
        });
    }

    @Test
    void update_hallucination_should_keep_byte_wrapping_for_counted_successful_children() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.PROTOSS_REAVER);
            Unit caster = createCaster(game, source);

            for (int index = 0; index < MAXIMUM_BYTE_COUNT; index++) {
                createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, false, WIDTH_TAG_BASE + index);
            }

            createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, true, OUTSIDE_TAG_BASE);

            int creations = game.getRandom().getCallCount(CREATION_RANDOM_SOURCE);

            for (Unit copy : cast(game, caster, source, COPY_COUNT)) {
                Assertions.assertEquals(0, copy.getHangarCount(false));

                Unit child = copy.getFirstHangarUnit(false);

                for (int visited = 0; visited <= MAXIMUM_BYTE_COUNT; visited++) {
                    Assertions.assertNotNull(child);
                    HangarAssert.assertStored(copy, child);

                    child = child.getNextHangarUnit();
                }

                Assertions.assertNull(child);
            }

            Assertions.assertEquals(COPY_COUNT * (1 + MAXIMUM_BYTE_COUNT + 1), game.getRandom().getCallCount(CREATION_RANDOM_SOURCE) - creations);
        });
    }

    @Test
    void update_hallucination_should_continue_child_failures_then_stop_the_next_outer_attempt() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.PROTOSS_REAVER);
            Unit caster = createCaster(game, source);

            createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, false, INSIDE_TAG_BASE + 1);
            createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, false, INSIDE_TAG_BASE + SOURCE_LIST_LENGTH);
            createChild(game, source, ScrUnitIds.PROTOSS_SCARAB, true, OUTSIDE_TAG_BASE);

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            int[] heldIndices = new int[pool.getCapacity()];
            int heldCount = 0;

            try {
                while (pool.getAllocatedCount() < pool.getCapacity() - PARTIAL_FREE_SLOTS) {
                    int index = UnitPoolScenario.allocate(pool);

                    Assertions.assertNotEquals(0, index);

                    heldIndices[heldCount++] = index;
                }

                int creations = game.getRandom().getCallCount(CREATION_RANDOM_SOURCE);
                Unit copy = cast(game, caster, source, 1).getFirst();

                Assertions.assertEquals(PARTIAL_CREATION_ATTEMPTS, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE) - creations);
                Assertions.assertEquals(SOURCE_LIST_LENGTH, copy.getHangarCount(false));
                Assertions.assertEquals(pool.getCapacity(), pool.getAllocatedCount());

                Unit first = copy.getFirstHangarUnit(false);

                Assertions.assertNotNull(first);
                Assertions.assertEquals(INSIDE_TAG_BASE + 1, pool.getCreationTag(first.getIndex()));

                Unit second = first.getNextHangarUnit();

                Assertions.assertNotNull(second);
                Assertions.assertEquals(INSIDE_TAG_BASE + SOURCE_LIST_LENGTH, pool.getCreationTag(second.getIndex()));
                Assertions.assertNull(second.getNextHangarUnit());
                Assertions.assertEquals(SOURCE_LIST_LENGTH, source.getHangarCount(false));
                Assertions.assertEquals(1, source.getHangarCount(true));
            } finally {
                for (int position = heldCount - 1; position >= 0; position--) {
                    int index = heldIndices[position];

                    IgnoreException.execute(
                            () -> pool.recycleFinalized(index)
                    );
                }

                for (int position = 0; position < heldCount; position++) {
                    Assertions.assertFalse(pool.isAllocated(heldIndices[position]), "Every fixture reservation is released.");
                }
            }
        });
    }

    @Test
    void preplaced_conversion_keeps_secondary_turret_and_drawing_behavior() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit source = createSource(game, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE);
            UnitCreationContext context = game.getUnitSystem().getUnitCreationContext();

            Assertions.assertNotNull(context);

            game.getUnitHallucination().convertToHallucination(source,
                                                               context.preservesNegativeTypeCounts(),
                                                               true,
                                                               RunningGameScenario.HUMAN,
                                                               RunningGameScenario.INSTRUCTION_LIMIT);

            Unit turret = source.getSubunit();

            Assertions.assertNotNull(turret);
            Assertions.assertEquals(HALLUCINATION2_ORDER, source.getSecondaryOrder());
            Assertions.assertNotEquals(0, turret.getStatus() & UnitStatusFlags.HALLUCINATION);

            SpriteImage image = source.requireSprite().getMainImage();

            Assertions.assertNotNull(image);
            Assertions.assertEquals(HALLUCINATION_MODIFIER, image.getModifier());
        });
    }
}
