package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Checks that canceling build queue entries shifts the later entries and refunds the canceled unit, that a queue reset
 * cancels every entry and restarts the queue, that emptying the current queue slot writes past the five slots, that a
 * dying hallucination leaves its death sprite with the hallucination's visibility, that a dying refinery leaves a
 * Vespene Geyser that the neutral player owns, sprite included, and that a hidden refinery dies without restoring the
 * geyser and leaves the hidden list.
 * <p>
 * The current slot test pins the {@code 488}-byte unit layout after the build queue, because maps read and write it through EUD:
 * queue index {@code 5} is the energy and queue index {@code 6} the queue index with the slot's generation.
 */
final class UnitLifecycleTest {
    private static final int HALLUCINATION_VISIBILITY = 0b11;
    /**
     * The build queue index whose WORD is the energy, the first WORD after the five queue slots.
     */
    private static final int ENERGY_QUEUE_INDEX = 5;
    /**
     * The build queue index whose WORD is the queue index and the slot's generation.
     */
    private static final int GENERATION_QUEUE_INDEX = 6;
    /**
     * The native offset of the slot's generation byte, after the queue index.
     */
    private static final int GENERATION_OFFSET = 0xE9;
    private static final int ENERGY = 0x1234;
    private static final int GENERATION = 7;
    /**
     * The queue index of the reset test, so that the queue wraps past the fifth slot.
     */
    private static final int RESET_QUEUE_INDEX = 3;
    /**
     * The queue index of the current slot test, the third queue slot.
     */
    private static final int CURRENT_QUEUE_INDEX = 2;
    /**
     * The unit type identifier {@code None} that an emptied queue WORD holds.
     */
    private static final int NO_UNIT_TYPE = 228;
    private static final int INSTRUCTION_LIMIT = 1000;
    /**
     * The position of the refinery in the running game.
     */
    private static final int REFINERY_X = 3560;
    private static final int REFINERY_Y = 2160;
    /**
     * The position of the hallucination in the running game.
     */
    private static final int HALLUCINATION_X = 3560;
    private static final int HALLUCINATION_Y = 2160;
    /**
     * The owner of a neutral unit and the unit type of a Vespene Geyser.
     */
    private static final int NEUTRAL_PLAYER = 11;
    private static final int VESPENE_GEYSER_TYPE = 188;

    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int PLAYER = 0;

    private static final int REFINERY_SEARCH_FRAME_LIMIT = 9000;
    private static final int STEP_MILLISECONDS = 42;

    /**
     * Creates a {@link Game} instance on an empty map with one player.
     *
     * @param data   the game data
     * @param player the player in slot {@link #PLAYER}
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(@NotNull GameData data, @NotNull Player player) {
        Game game = Game.create(data, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(1));

        game.getPlayerTable().setPlayer(PLAYER, player);

        return game;
    }

    /**
     * Sets circular queue entries in the fixture slot order.
     *
     * @param unit  the fixture unit
     * @param head  the first queue slot
     * @param types the ordered unit types
     */
    private static void setQueue(@NotNull Unit unit, int head, int @NotNull ... types) {
        unit.setBuildQueueIndex(head);

        int[] slots = {ScrUnitIds.NONE, ScrUnitIds.NONE, ScrUnitIds.NONE, ScrUnitIds.NONE, ScrUnitIds.NONE};

        for (int index = 0; index < types.length; index++) {
            slots[(head + index) % Unit.BUILD_QUEUE_SLOTS] = types[index];
        }

        unit.setFirstBuildQueueType(slots[Unit.FIRST_QUEUE_SLOT]);
        unit.setSecondBuildQueueType(slots[Unit.SECOND_QUEUE_SLOT]);
        unit.setThirdBuildQueueType(slots[Unit.THIRD_QUEUE_SLOT]);
        unit.setFourthBuildQueueType(slots[Unit.FOURTH_QUEUE_SLOT]);
        unit.setFifthBuildQueueType(slots[Unit.FIFTH_QUEUE_SLOT]);
    }

    /**
     * Returns the queue entries in traversal order.
     *
     * @param unit the fixture unit
     * @return the ordered queue entries
     */
    private static int @NotNull [] getQueue(@NotNull Unit unit) {
        int[] result = new int[Unit.BUILD_QUEUE_SLOTS];

        for (int index = 0; index < Unit.BUILD_QUEUE_SLOTS; index++) {
            result[index] = unit.getBuildQueueUnitType((unit.getBuildQueueIndex() + index) % Unit.BUILD_QUEUE_SLOTS);
        }

        return result;
    }


    @RequiresInstallation
    @Test
    void build_queue_cancel_shifts_later_entries_and_refunds_the_canceled_unit() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Player player = new Player(PLAYER, PLAYER, PlayerTypes.HUMAN, ScrRaceIds.TERRAN, 0);
            Game game = createGame(data, player);
            Unit barracks = UnitTestUtils.createUnit(1);

            barracks.setOwnerIndex(PLAYER);

            setQueue(barracks, 3, ScrUnitIds.TERRAN_MARINE, ScrUnitIds.TERRAN_FIREBAT, ScrUnitIds.TERRAN_GHOST, ScrUnitIds.TERRAN_MEDIC);
            game.getUnitLifecycle().cancelBuildQueueEntry(barracks, 1, INSTRUCTION_LIMIT);

            Assertions.assertArrayEquals(new int[]{0, 1, 34, 228, 228}, getQueue(barracks), "Later entries shift towards the cancelled slot.");
            Assertions.assertEquals(data.getUnits().get("Mineral Cost", ScrUnitIds.TERRAN_FIREBAT), player.getMinerals());
            Assertions.assertEquals(3, barracks.getBuildQueueIndex(), "The head index is unchanged.");

            setQueue(barracks,
                     2,
                     ScrUnitIds.TERRAN_MARINE,
                     ScrUnitIds.TERRAN_FIREBAT,
                     ScrUnitIds.TERRAN_GHOST,
                     ScrUnitIds.TERRAN_MEDIC,
                     ScrUnitIds.TERRAN_SCV);
            player.setMinerals(0);
            game.getUnitLifecycle().cancelBuildQueueEntry(barracks, 2, INSTRUCTION_LIMIT);

            Assertions.assertArrayEquals(new int[]{0, 32, 34, 7, 228}, getQueue(barracks), "A full queue empties its fifth entry.");
            Assertions.assertEquals(data.getUnits().get("Mineral Cost", ScrUnitIds.TERRAN_GHOST), player.getMinerals());

            player.setMinerals(0);
            game.getUnitLifecycle().cancelBuildQueueEntry(barracks, 4, INSTRUCTION_LIMIT);

            Assertions.assertArrayEquals(new int[]{0, 32, 34, 7, 228}, getQueue(barracks), "An empty entry has no effect.");

            game.getUnitLifecycle().cancelBuildQueueEntry(barracks, 7, INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, player.getMinerals());

            game.getUnitLifecycle().cancelBuildQueueEntry(barracks, 3, INSTRUCTION_LIMIT);

            Assertions.assertArrayEquals(new int[]{0, 32, 34, 228, 228}, getQueue(barracks));
        }
    }

    @RequiresInstallation
    @Test
    void reset_build_queue_should_cancel_every_occupied_entry_and_move_the_queue_index_to_the_first_slot() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Player player = new Player(PLAYER, PLAYER, PlayerTypes.HUMAN, ScrRaceIds.TERRAN, 0);
            Game game = createGame(data, player);
            Unit barracks = UnitTestUtils.createUnit(1);

            barracks.setOwnerIndex(PLAYER);

            // An empty entry between two units, as a map can write it, does not end the reset.
            setQueue(barracks, RESET_QUEUE_INDEX, ScrUnitIds.TERRAN_MARINE, ScrUnitIds.NONE, ScrUnitIds.TERRAN_FIREBAT);
            game.getUnitLifecycle().resetBuildQueue(barracks, INSTRUCTION_LIMIT);

            ScrUnitTypeView units = data.getUnitTypes();

            Assertions.assertEquals(units.getMineralCost(ScrUnitIds.TERRAN_MARINE) + units.getMineralCost(ScrUnitIds.TERRAN_FIREBAT),
                                    player.getMinerals());
            Assertions.assertEquals(0, barracks.getBuildQueueIndex());
            Assertions.assertArrayEquals(new int[]{228, 228, 228, 228, 228}, getQueue(barracks));
        }
    }

    @Test
    void clear_current_queue_slot_should_empty_the_word_at_the_queue_index_past_the_five_queue_slots() {
        Unit unit = UnitTestUtils.createUnit(1);

        setQueue(unit, CURRENT_QUEUE_INDEX, ScrUnitIds.TERRAN_MARINE, ScrUnitIds.TERRAN_FIREBAT);
        UnitLifecycle.clearCurrentQueueSlot(unit);

        Assertions.assertEquals(CURRENT_QUEUE_INDEX, unit.getBuildQueueIndex());
        Assertions.assertArrayEquals(new int[]{228, 32, 228, 228, 228}, getQueue(unit));

        unit.setBuildQueueIndex(ENERGY_QUEUE_INDEX);
        unit.setEnergy(ENERGY);
        UnitLifecycle.clearCurrentQueueSlot(unit);

        Assertions.assertEquals(NO_UNIT_TYPE, unit.getEnergy(), "Queue index 5 empties the energy.");

        unit.setBuildQueueIndex(GENERATION_QUEUE_INDEX);
        NativeUnitFields.writeByte(unit, GENERATION_OFFSET, GENERATION);
        UnitLifecycle.clearCurrentQueueSlot(unit);

        Assertions.assertEquals(NO_UNIT_TYPE, unit.getBuildQueueIndex(), "Queue index 6 empties the queue index.");
        Assertions.assertEquals(0, NativeUnitFields.readByte(unit, GENERATION_OFFSET), "Queue index 6 clears the generation byte.");
    }

    @RequiresInstallation
    @Test
    void die_should_give_the_hallucination_death_sprite_the_visibility_of_the_hallucination() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                UnitCreationContext context = game.getUnitSystem().getUnitCreationContext();
                SpriteEffectPool spriteEffects = game.getSpriteSystem().getSpriteEffectPool();

                Assertions.assertNotNull(context);
                Assertions.assertNotNull(spriteEffects);

                Unit zealot = RunningGameScenario.createUnit(game,
                                                             ScrUnitIds.PROTOSS_ZEALOT,
                                                             HALLUCINATION_X, HALLUCINATION_Y,
                                                             RunningGameScenario.COMPUTER);
                Sprite sprite = zealot.getSpriteOrNull();

                Assertions.assertNotNull(sprite);

                game.getUnitHallucination().convertToHallucination(zealot,
                                                                   context.preservesNegativeTypeCounts(),
                                                                   context.isCompletionRefreshSuppressed(),
                                                                   context.getLocalPlayer(),
                                                                   RunningGameScenario.INSTRUCTION_LIMIT);

                int tileIndex = sprite.getY() / TerrainTileset.TILE_SIZE * game.getMap().getWidth() + sprite.getX() / TerrainTileset.TILE_SIZE;

                // Every player sees the tile under the hallucination, but only the first two players see the hallucination.
                game.getMap().applyMask(tileIndex, 0);
                sprite.setVisibilityFlags(HALLUCINATION_VISIBILITY);
                game.getUnitLifecycle().die(zealot, RunningGameScenario.INSTRUCTION_LIMIT);

                Sprite deathSprite = null;

                for (int index = spriteEffects.getFirstActiveIndex(); index >= 0; index = spriteEffects.getNextIndex(index)) {
                    Sprite effectSprite = spriteEffects.getSpriteEffect(index).getSpriteOrNull();

                    if (effectSprite != null && effectSprite.getSpriteTypeId() == SpriteIds.HALLUCINATION_DEATH1) {
                        deathSprite = effectSprite;
                    }
                }

                Assertions.assertNotNull(deathSprite);
                Assertions.assertEquals(HALLUCINATION_VISIBILITY, deathSprite.getVisibilityFlags());
            });
        }
    }

    @RequiresInstallation
    @Test
    void die_should_give_the_restored_geyser_the_neutral_sprite_owner_when_the_refinery_is_already_neutral() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit refinery = RunningGameScenario.createUnit(game,
                                                               ScrUnitIds.PROTOSS_ASSIMILATOR,
                                                               REFINERY_X, REFINERY_Y,
                                                               RunningGameScenario.COMPUTER);

                // A departing player's refinery passes to the neutral player and keeps its sprite owner.
                game.getUnitOwnership().makeNeutral(refinery, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(NEUTRAL_PLAYER, refinery.getOwnerIndex());
                Assertions.assertEquals(RunningGameScenario.COMPUTER, refinery.requireSprite().getOwner());

                game.getUnitLifecycle().die(refinery, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(VESPENE_GEYSER_TYPE, refinery.getUnitId());
                Assertions.assertEquals(NEUTRAL_PLAYER, refinery.getOwnerIndex());
                Assertions.assertEquals(NEUTRAL_PLAYER, refinery.requireSprite().getOwner());
                Assertions.assertFalse(game.getUnitTargeting().isEnemy(RunningGameScenario.HUMAN, refinery));
            });
        }
    }

    @RequiresInstallation
    @Test
    void hidden_refinery_dies_without_restoring_the_geyser_and_leaves_the_hidden_list() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_PVP_FORTRESS)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            int tick = 0;
            Unit refinery = null;

            while (refinery == null && game.getCurrentFrame() < REFINERY_SEARCH_FRAME_LIMIT) {
                Assertions.assertTrue(runtime.step(STEP_MILLISECONDS, tick += STEP_MILLISECONDS));

                for (int index = pool.getFirstCreatedIndex(true); index != 0 && refinery == null; index = pool.getNextCreatedIndex(index)) {
                    Unit unit = pool.getUnit(index);
                    int type = unit.getUnitId();

                    if ((type == ScrUnitIds.TERRAN_REFINERY || type == ScrUnitIds.ZERG_EXTRACTOR ||
                         type == ScrUnitIds.PROTOSS_ASSIMILATOR) && (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0) {
                        refinery = unit;
                    }
                }
            }

            Assertions.assertNotNull(refinery, "The replay builds a refinery.");

            int poolIndex = refinery.getIndex();

            // A hidden refinery dies without restoring a geyser or re-entering the hidden list.
            game.getUnitLifecycle().hide(refinery, true);
            game.getUnitLifecycle().kill(refinery, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertTrue(runtime.step(STEP_MILLISECONDS, tick + STEP_MILLISECONDS));
            Assertions.assertNull(runtime.getFailure());
            Assertions.assertNotEquals(VESPENE_GEYSER_TYPE, refinery.getUnitId(), "A hidden refinery does not turn back into a geyser.");
            Assertions.assertNull(refinery.getSpriteOrNull(), "The hidden refinery's sprite is destroyed with it.");

            int steps = 0;

            for (int index = pool.getFirstCreatedIndex(false); index != 0; index = pool.getNextCreatedIndex(index)) {
                Assertions.assertNotEquals(poolIndex, index, "The dead refinery left the hidden list.");
                Assertions.assertTrue(++steps <= pool.getCapacity(), "The hidden list ends.");
            }
        }
    }
}
