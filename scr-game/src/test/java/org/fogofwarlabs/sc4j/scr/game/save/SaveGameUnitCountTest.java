package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayWriter;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.save.pool.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameRulesVersion;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

/**
 * Checks the unit count in complete saves and the observable cursor and load prefix of the saved unit format.
 */
@RequiresInstallation
final class SaveGameUnitCountTest {
    private static final int UNIT_CAPACITY = 3400;
    private static final int UNIT_POSITION = 64;
    private static final int ENTRY_SENTINEL = 0x6A9C32D7;
    private static final @NotNull String UNIT_COUNT_FAILURE = "units for a pool";

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }
    }

    private static @NotNull GameData requireGameData() {
        Assertions.assertNotNull(gameData);

        return gameData;
    }

    private static @NotNull ReplayFile createReplay() throws IOException {
        ReplayFile original = ReplayFile.parse(GameTestResources.readAllBytes(GameTestResources.REPLAY_TVT_PYTHON));
        byte[] header = original.getHeaderSection();
        byte[] mapData = original.getMapData();

        Assertions.assertNotNull(header);
        Assertions.assertNotNull(mapData);

        return ReplayFile.create(header,
                                 List.of(),
                                 mapData,
                                 ReplayWriter.CURRENT_LIMITS.clone(),
                                 ReplayWriter.encodeVersionBlock(GameRulesVersion.CURRENT_BUILD, 0),
                                 ReplayWriter.CURRENT_FIX_FLAGS);
    }

    private static void fillUnits(@NotNull Game game, int count) {
        UnitPool pool = game.getUnitSystem().getUnitPool();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();
        UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();
        int owner = -1;

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(accounting);
        Assertions.assertNotNull(creation);
        Assertions.assertEquals(UNIT_CAPACITY, pool.getCapacity());

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
            Player player = game.getPlayerTable().getPlayer(playerIndex);

            if (player != null && player.getType() == PlayerTypes.HUMAN) {
                owner = playerIndex;

                break;
            }
        }

        Assertions.assertTrue(owner >= 0);

        while (pool.getAllocatedCount() < count) {
            Unit unit = game.getUnitStorageConstructor().create(creation,
                                                                accounting,
                                                                ScrUnitIds.TERRAN_MARINE,
                                                                UNIT_POSITION, UNIT_POSITION,
                                                                owner,
                                                                0,
                                                                RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(unit);
        }

        Assertions.assertEquals(count, pool.getAllocatedCount());
    }

    private static @NotNull SaveGameFile writeGame(@NotNull ReplayRuntime runtime) {
        SaveGameDescription description = SaveGameRoundTripScenario.createDescription(false, "unit count");

        return SaveGameUtils.write(new SaveGameContext(runtime, null), description);
    }

    private static @NotNull SaveFileReader openUnits(@NotNull SaveGameFile file) {
        SaveFileReader input = file.openBody();

        SaveGameComparisonCheck.readCountedRecords(input, ImagePoolSection.ENTRY_SIZE);
        SaveGameComparisonCheck.readCountedRecords(input, SpritePoolSection.ENTRY_SIZE);
        input.readBytes(SpritePoolSection.ROW_ARRAYS_SIZE);
        SaveGameComparisonCheck.readCountedRecords(input, ThingyPoolSection.ENTRY_SIZE);
        SaveGameComparisonCheck.readCountedRecords(input, ThingyPoolSection.ENTRY_SIZE);
        SaveGameComparisonCheck.readCountedRecords(input, FlingyPoolSection.ENTRY_SIZE);

        return input;
    }

    private static void assertSavedUnits(@NotNull SaveGameFile file, @NotNull UnitPool pool, int expectedCount) {
        SaveFileReader input = openUnits(file);
        byte[] flags = input.readSection(pool.getCapacity());
        int count = input.readDword();
        int flagged = 0;
        int[] limits = file.getLimits();

        Assertions.assertNotNull(limits);
        Assertions.assertEquals(UNIT_CAPACITY, limits[GameSetup.UNIT_LIMIT]);
        Assertions.assertEquals(expectedCount, count);

        for (int slot = 0; slot < flags.length; slot++) {
            Assertions.assertEquals(pool.isAllocated(slot + 1) ? 1 : 0, flags[slot]);

            flagged += flags[slot];
        }

        Assertions.assertEquals(count, flagged);

        ByteBuffer entries = ByteBuffer.wrap(input.readSection(count * UnitPoolSection.ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN);
        boolean highestSlot = false;

        for (int entryIndex = 0; entryIndex < count; entryIndex++) {
            int slot = entries.getInt(entryIndex * UnitPoolSection.ENTRY_SIZE);

            Assertions.assertEquals(1, flags[slot]);

            flags[slot] = 0;
            highestSlot |= slot == pool.getCapacity() - 1;
        }

        Assertions.assertTrue(highestSlot);
    }


    @Test
    void read_should_reject_a_complete_save_when_every_unit_slot_is_allocated() throws IOException {
        ReplayFile replay = createReplay();
        ReplayRuntime saved = SaveGameRoundTripScenario.startReplay(requireGameData(), replay);
        Game game = saved.getGame();

        fillUnits(game, UNIT_CAPACITY);

        SaveGameFile file = writeGame(saved);

        assertSavedUnits(file, game.getUnitSystem().requireUnitPool(), UNIT_CAPACITY);

        ReplayRuntime loaded = SaveGameRoundTripScenario.startSavedGame(requireGameData(), file, replay, game.getCurrentFrame());
        UnitPool pool = loaded.getGame().getUnitSystem().requireUnitPool();
        UncheckedIOException failure = Assertions.assertThrows(UncheckedIOException.class, () -> {
            SaveGameUtils.read(file, new SaveGameContext(loaded, null));

            Assertions.assertEquals(UNIT_CAPACITY, pool.getAllocatedCount());

            System.out.println("Complete save restored all " + pool.getAllocatedCount() + " units.");
        });

        Assertions.assertTrue(failure.getCause().getMessage().contains(UNIT_COUNT_FAILURE));
        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, loaded.getGame().getUnitSystem().requireUnitSpatialIndex().getCount());
    }

    @Test
    void read_should_restore_a_complete_save_with_one_free_unit_slot() throws IOException {
        ReplayFile replay = createReplay();
        ReplayRuntime saved = SaveGameRoundTripScenario.startReplay(requireGameData(), replay);
        Game game = saved.getGame();
        UnitPool source = game.getUnitSystem().requireUnitPool();

        fillUnits(game, UNIT_CAPACITY - 1);

        SaveGameFile file = writeGame(saved);

        assertSavedUnits(file, source, UNIT_CAPACITY - 1);

        ReplayRuntime loaded = SaveGameRoundTripScenario.startSavedGame(requireGameData(), file, replay, game.getCurrentFrame());

        SaveGameUtils.read(file, new SaveGameContext(loaded, null));

        UnitPool target = loaded.getGame().getUnitSystem().requireUnitPool();

        Assertions.assertEquals(UNIT_CAPACITY - 1, target.getAllocatedCount());

        for (int poolIndex = 1; poolIndex <= source.getCapacity(); poolIndex++) {
            Assertions.assertEquals(source.isAllocated(poolIndex), target.isAllocated(poolIndex));

            if (source.isAllocated(poolIndex)) {
                Unit expected = source.getUnit(poolIndex);
                Unit actual = target.getUnit(poolIndex);

                Assertions.assertEquals(expected.getUnitId(), actual.getUnitId());
                Assertions.assertEquals(expected.getOwnerIndex(), actual.getOwnerIndex());
                Assertions.assertEquals(expected.getPositionX(), actual.getPositionX());
                Assertions.assertEquals(expected.getPositionY(), actual.getPositionY());
            }
        }
    }

    @Test
    void read_should_accept_zero_units_and_consume_the_list_heads() throws IOException {
        ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(requireGameData(), createReplay());
        SaveGameContext context = new SaveGameContext(runtime, null);
        SaveFileWriter output = new SaveFileWriter();

        output.writeSection(new byte[UNIT_CAPACITY]);
        output.writeDword(0);
        output.writeBytes(new byte[UnitPoolSection.HEADS_SIZE]);

        SaveFileReader input = new SaveFileReader(output.toByteArray(), 0);

        new UnitPoolSection().read(context, input);

        Assertions.assertEquals(0, input.getRemaining());
        Assertions.assertEquals(0, context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount());

        fillUnits(context.getGame(), 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {UNIT_CAPACITY + 1, Integer.MIN_VALUE, -1})
    void read_should_clear_units_before_rejecting_an_unsigned_count_and_leave_entries_unread(int count) throws IOException {
        ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(requireGameData(), createReplay());
        SaveGameContext context = new SaveGameContext(runtime, null);
        SaveFileWriter output = new SaveFileWriter();

        Assertions.assertTrue(context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount() > 0);
        Assertions.assertTrue(context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount() > 0);

        output.writeSection(new byte[UNIT_CAPACITY]);
        output.writeDword(count);

        int prefixEnd = output.getPosition();

        output.writeDword(ENTRY_SENTINEL);

        SaveFileReader input = new SaveFileReader(output.toByteArray(), 0);
        UncheckedIOException failure = Assertions.assertThrows(UncheckedIOException.class, () -> new UnitPoolSection().read(context, input));

        Assertions.assertTrue(failure.getCause().getMessage().contains(UNIT_COUNT_FAILURE));
        Assertions.assertEquals(prefixEnd, input.getPosition());
        Assertions.assertEquals(ENTRY_SENTINEL, input.readDword());
        Assertions.assertEquals(0, context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount());
        Assertions.assertEquals(0, context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, Short.BYTES, Integer.BYTES - 1})
    void read_should_preserve_units_when_the_plain_count_is_truncated(int countBytes) throws IOException {
        ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(requireGameData(), createReplay());
        SaveGameContext context = new SaveGameContext(runtime, null);
        int allocated = context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount();
        int spatial = context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount();
        SaveFileWriter output = new SaveFileWriter();

        Assertions.assertTrue(allocated > 0);
        Assertions.assertTrue(spatial > 0);

        output.writeSection(new byte[UNIT_CAPACITY]);

        int prefixEnd = output.getPosition();

        output.writeDword(0);

        SaveFileReader input = new SaveFileReader(Arrays.copyOf(output.toByteArray(), prefixEnd + countBytes), 0);

        Assertions.assertThrows(UncheckedIOException.class, () -> new UnitPoolSection().read(context, input));
        Assertions.assertEquals(prefixEnd + (countBytes >= Short.BYTES ? Short.BYTES : 0), input.getPosition());
        Assertions.assertEquals(allocated, context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount());
        Assertions.assertEquals(spatial, context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount());
    }

    @Test
    void read_should_preserve_units_when_the_flags_are_missing() throws IOException {
        ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(requireGameData(), createReplay());
        SaveGameContext context = new SaveGameContext(runtime, null);
        int allocated = context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount();
        int spatial = context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount();
        SaveFileReader input = new SaveFileReader(new byte[0], 0);

        Assertions.assertTrue(allocated > 0);
        Assertions.assertTrue(spatial > 0);

        Assertions.assertThrows(UncheckedIOException.class, () -> new UnitPoolSection().read(context, input));
        Assertions.assertEquals(0, input.getPosition());
        Assertions.assertEquals(allocated, context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount());
        Assertions.assertEquals(spatial, context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount());
    }

    @Test
    void read_should_clear_units_before_an_accepted_count_with_missing_entries_fails() throws IOException {
        ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(requireGameData(), createReplay());
        SaveGameContext context = new SaveGameContext(runtime, null);
        SaveFileWriter output = new SaveFileWriter();

        output.writeSection(new byte[UNIT_CAPACITY]);
        output.writeDword(1);

        Assertions.assertTrue(context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount() > 0);
        Assertions.assertTrue(context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount() > 0);

        SaveFileReader input = new SaveFileReader(output.toByteArray(), 0);

        Assertions.assertThrows(UncheckedIOException.class, () -> new UnitPoolSection().read(context, input));
        Assertions.assertEquals(output.getPosition(), input.getPosition());
        Assertions.assertEquals(0, context.getGame().getUnitSystem().requireUnitPool().getAllocatedCount());
        Assertions.assertEquals(0, context.getGame().getUnitSystem().requireUnitSpatialIndex().getCount());
    }

    @Test
    void read_should_keep_the_first_record_when_a_later_saved_slot_is_invalid() throws IOException {
        ReplayFile replay = createReplay();
        ReplayRuntime saved = SaveGameRoundTripScenario.startReplay(requireGameData(), replay);
        SaveGameContext sourceContext = new SaveGameContext(saved, null);
        SaveFileWriter sourceOutput = new SaveFileWriter();

        new UnitPoolSection().write(sourceContext, sourceOutput);

        SaveFileReader sourceInput = new SaveFileReader(sourceOutput.toByteArray(), 0);
        byte[] flags = sourceInput.readSection(UNIT_CAPACITY);
        int count = sourceInput.readDword();

        Assertions.assertTrue(count > 1 && count < UNIT_CAPACITY);

        byte[] entries = sourceInput.readSection(count * UnitPoolSection.ENTRY_SIZE);
        ByteBuffer entryBuffer = ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN);
        int firstSlot = entryBuffer.getInt(0);
        Unit first = sourceContext.getGame().getUnitSystem().requireUnitPool().getUnit(firstSlot + 1);

        entryBuffer.putInt(UnitPoolSection.ENTRY_SIZE, UNIT_CAPACITY);

        SaveFileWriter output = new SaveFileWriter();

        output.writeSection(flags);
        output.writeDword(count);
        output.writeSection(entries);
        output.writeBytes(sourceInput.readBytes(sourceInput.getRemaining()));

        ReplayRuntime loaded = SaveGameRoundTripScenario.startReplay(requireGameData(), replay);
        SaveGameContext targetContext = new SaveGameContext(loaded, null);
        SaveFileReader input = new SaveFileReader(output.toByteArray(), 0);
        UncheckedIOException failure = Assertions.assertThrows(UncheckedIOException.class, () -> new UnitPoolSection().read(targetContext, input));
        UnitPool pool = loaded.getGame().getUnitSystem().requireUnitPool();
        Unit restored = pool.getUnit(firstSlot + 1);

        Assertions.assertTrue(failure.getCause().getMessage().contains("names the slot"));
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertTrue(pool.isAllocated(firstSlot + 1));
        Assertions.assertEquals(first.getUnitId(), restored.getUnitId());
        Assertions.assertEquals(first.getOwnerIndex(), restored.getOwnerIndex());
        Assertions.assertEquals(first.getPositionX(), restored.getPositionX());
        Assertions.assertEquals(first.getPositionY(), restored.getPositionY());
    }
}
