package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameRoundTripScenario;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Checks extended-deaths scanning, masked memory writes, address failure boundaries and the memory-writing Set Deaths mode that the triggers
 * of EUD maps switch to in a live game.
 */
final class ExtendedDeathsModeTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;
    /**
     * The local player of a replay observer, outside the playable slots.
     */
    private static final int REPLAY_OBSERVER = 8;

    /**
     * The classic address without a region that a trigger reads.
     */
    private static final int UNMAPPED_READ_ADDRESS = 0x59C2B8;
    /**
     * The classic address without a region that a trigger writes.
     */
    private static final int UNMAPPED_WRITE_ADDRESS = 0x68C260;
    /**
     * The classic address of a native region that the address space does not represent.
     */
    private static final int UNREPRESENTED_REGION_ADDRESS = 0x640B5C;

    private static final int SEED = 0x6AB9B92E;
    private static final int HUMAN_LOBBY_SLOT = 0;
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final @NotNull String SCENARIO_PATH = "staredit\\scenario.chk";
    /**
     * The frame the EUD maps run to before their trigger mode is checked.
     */
    private static final int EUD_CHECK_FRAME = 2000;
    /**
     * The extended deaths mode bit of a map with an extended Set Deaths action.
     */
    private static final int EXTENDED_SET_DEATHS_MODE = 2;

    /**
     * Advances trigger evaluation through its first execution pass.
     *
     * @param context the trigger evaluation context
     */
    private static void runFirstPass(@NotNull TriggerContext context) {
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
    }

    /**
     * Creates initialized player records in slot order.
     *
     * @return the initialized player slots
     */
    private static @NotNull Player @NotNull [] createPlayers() {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, index == 0 ? PlayerTypes.HUMAN : PlayerTypes.NONE, 0, 0);
        }

        return players;
    }

    /**
     * Creates the player records and evaluation state for the encoded triggers.
     *
     * @param players    the initialized player slots
     * @param accounting the score and death counters
     * @param records    the encoded trigger records
     * @return the initialized trigger context
     */
    private static @NotNull TriggerContext createContext(@NotNull Player @NotNull [] players,
                                                         @NotNull UnitAccounting accounting,
                                                         byte @NotNull [] records) {
        int[] types = new int[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            types[index] = players[index].getType();
        }

        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(accounting, new PlayerRelations(types, false, false, false), new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        return context;
    }

    /**
     * Writes one condition and its extended-deaths operands.
     *
     * @param record     the destination record
     * @param index      the condition index
     * @param player     the player operand
     * @param unitType   the unit-type operand
     * @param amount     the comparison amount
     * @param comparison the comparison modifier
     * @param type       the condition type or zero for deaths
     */
    private static void writeCondition(@NotNull ByteBuffer record, int index, int player, int unitType, int amount, int comparison, int type) {
        int base = index * TriggerRecordCodec.CONDITION_SIZE;

        record.putInt(base + TriggerRecordCodec.CONDITION_PLAYER_FIELD, player);
        record.putInt(base + TriggerRecordCodec.CONDITION_AMOUNT_FIELD, amount);
        record.putShort(base + TriggerRecordCodec.CONDITION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.CONDITION_COMPARISON_FIELD, (byte) comparison);
        record.put(base + TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) (type == 0 ? TriggerConditionIds.DEATHS : type));
    }

    /**
     * Writes one action and its optional extended-deaths mask.
     *
     * @param record   the destination record
     * @param index    the action index
     * @param type     the action type
     * @param player   the player operand
     * @param unitType the unit-type operand
     * @param modifier the arithmetic modifier
     * @param amount   the arithmetic amount
     * @param mask     the write mask
     * @param masked   whether to write the mask signature
     */
    private static void writeAction(@NotNull ByteBuffer record, int index,
                                    int type,
                                    int player, int unitType,
                                    int modifier, int amount,
                                    int mask, boolean masked) {
        int base = TriggerRecordCodec.ACTIONS_OFFSET + index * TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_LOCATION_FIELD, mask);
        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, player);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, amount);
        record.putShort(base + TriggerRecordCodec.ACTION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) modifier);

        if (masked) {
            record.put(base + TriggerRecordCodec.ACTION_MARKER_FIELD, (byte) 'S');
            record.put(base + TriggerRecordCodec.ACTION_MARKER_FIELD + 1, (byte) 'C');
        }
    }


    @Test
    void scan_extended_deaths_should_set_one_bit_for_conditions_and_one_for_actions() {
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 3];
        ByteBuffer plain = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(plain, 0, 5, 10, 0, TriggerModifiers.EXACTLY, 0);
        writeAction(plain, 0, TriggerActionIds.SET_DEATHS, 5, 10, TriggerModifiers.SET_TO, 1, 0, false);

        ByteBuffer condition = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(condition, 0, 12, 0, 0, TriggerModifiers.EXACTLY, 0);

        Assertions.assertEquals(0, TriggerRecordCodec.scanExtendedDeaths(records, 0));
        Assertions.assertEquals(1, TriggerRecordCodec.scanExtendedDeaths(records, TriggerRecordCodec.RECORD_SIZE));

        ByteBuffer action = TriggerRecordScenario.createRecord(records, 2);

        writeAction(action, 3, TriggerActionIds.SET_DEATHS, 0, 233, TriggerModifiers.SET_TO, 1, 0, false);
        writeCondition(action, 2, -5, 0, 0, 12, 0);

        Assertions.assertEquals(2, TriggerRecordCodec.scanExtendedDeaths(records, TriggerRecordCodec.RECORD_SIZE * 2),
                                "A comparison of 12 or more is not scanned.");
    }

    @Test
    void extended_death_operands_address_classic_death_table() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.getScores().modifyDeaths(0, 6, TriggerModifiers.SET_TO, 3);

        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer record = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(record, 0, 12, 5, 3, TriggerModifiers.EXACTLY, 0);
        writeAction(record, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO, 50,
                    0, false);
        record.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers();
        TriggerContext context = createContext(players, accounting, records);

        Assertions.assertEquals(1, context.getExtendedDeathsMode());

        runFirstPass(context);

        Assertions.assertEquals(50, players[0].getMinerals(), "Player 12 of unit 5 is the classic address of player 0, unit 6.");
    }

    @Test
    void set_deaths_writes_through_mask_keeping_other_bits() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.getScores().modifyDeaths(1, 20, TriggerModifiers.SET_TO, 0x12345678);

        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer record = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(record, 0, 0, 0, 0, 0, TriggerConditionIds.ALWAYS);
        writeAction(record, 0, TriggerActionIds.SET_DEATHS, 1, 20, TriggerModifiers.ADD, 0x00010000, 0x00FF0000, true);

        int unmapped = TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_SIZE;

        record.putInt(unmapped + TriggerRecordCodec.ACTION_PLAYER_FIELD, 12);
        record.putShort(unmapped + TriggerRecordCodec.ACTION_UNIT_FIELD, (short) 233);
        record.put(unmapped + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_DEATHS);
        record.put(unmapped + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);
        record.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        TriggerContext context = createContext(createPlayers(), accounting, records);

        Assertions.assertEquals(2, context.getExtendedDeathsMode());

        UnsupportedOperationException boundary = Assertions.assertThrows(UnsupportedOperationException.class, () -> runFirstPass(context));

        Assertions.assertTrue(boundary.getMessage().contains("0x0058CF44"), boundary.getMessage());
        Assertions.assertEquals(0x12355678, accounting.getScores().getDeaths(1, 20),
                                "The add stays inside mask 0x00FF0000 and the write before the unmapped address is kept.");
    }

    @Test
    void address_without_native_region_stops_every_later_action_and_trigger() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer failing = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(failing, 0, 0, 0, 0, 0, TriggerConditionIds.ALWAYS);
        writeAction(failing, 0, TriggerActionIds.SET_DEATHS, 5, 1532, TriggerModifiers.SET_TO, 1, 0, false);
        writeAction(failing, 1,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO, 50,
                    0, false);
        failing.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer later = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(later, 0, 0, 0, 0, 0, TriggerConditionIds.ALWAYS);
        writeAction(later, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO, 70,
                    0, false);
        later.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers();
        TriggerContext context = createContext(players, accounting, records);

        runFirstPass(context);

        Assertions.assertEquals(0, players[0].getMinerals(), "The address without a region stops every later action and trigger.");

        runFirstPass(context);

        Assertions.assertEquals(0, players[0].getMinerals());
    }

    @Test
    void failed_trigger_reads_yield_zero_and_notify_failure_listener() {
        ClassicAddressSpace space = ClassicAddressSpace.create(PlayerProgressTestUtils.createPlayerScores(), null, null, new MessageLines());
        int[] failed = {0};

        space.setFailureListener(address -> failed[0] = address);

        Assertions.assertEquals(0, space.readForTrigger(UNMAPPED_READ_ADDRESS));
        Assertions.assertEquals(UNMAPPED_READ_ADDRESS, failed[0]);
        Assertions.assertFalse(space.writeForTrigger(UNMAPPED_WRITE_ADDRESS, 1));
        Assertions.assertEquals(UNMAPPED_WRITE_ADDRESS, failed[0]);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> space.readForTrigger(UNREPRESENTED_REGION_ADDRESS),
                                "0x640B5C has a native region that is not represented.");
    }

    @CorpusTest({ExternalInput.INSTALLATION, ExternalInput.MAPS})
    @ParameterizedTest
    @ValueSource(strings = {"[EUD]지렁이 키우기 1.2.scx", "[EUD]지오메트리 대쉬 1.1.scx"})
    void eud_map_triggers_switch_to_memory_writing_set_deaths_mode(@NotNull String name) throws Exception {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationDirectory);

        Path mapPath = ExternalInput.MAPS.resolveFile(name);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_PATH);
        MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, HUMAN_LOBBY_SLOT, GameSpeed.FASTEST);

        SaveGameRoundTripScenario.assumeSelected(name);

        try (GameData gameData = GameData.loadFrom(installationDirectory)) {
            LiveGame live = new LiveGame(setup, SEED);
            ReplayRuntime runtime = LiveMapGameScenario.startLive(gameData, live, mapPath);

            while (runtime.getGame().getCurrentFrame() < EUD_CHECK_FRAME) {
                LiveMapGameScenario.stepFrame(runtime, live);
            }

            TriggerContext triggers = runtime.getGame().getTriggerContext();

            Assertions.assertNotNull(triggers);
            Assertions.assertTrue((triggers.getExtendedDeathsMode() & EXTENDED_SET_DEATHS_MODE) != 0, "the map's triggers write memory");
        }
    }
}
