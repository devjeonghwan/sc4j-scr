package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.*;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks encoded memory triggers, completion bits and partial action failure.
 */
final class ClassicUnitMemoryTriggerTest {
    private static final int DEATH_TABLE = 0x58A364;
    /**
     * The execution flags of a disabled record whose actions run.
     */
    private static final int DISABLED_RUNNING_FLAGS = 0x9;
    /**
     * The execution flags of a preserved record.
     */
    private static final int PRESERVE_FLAGS = 0x4;
    /**
     * The execution flags of a record whose actions run.
     */
    private static final int RUNNING_FLAGS = 0x1;
    private static final int HIT_POINTS = 0x08;
    private static final int SHIELD_POINTS = 0x60;
    private static final int UNSUPPORTED_FIELD = 0xA0;

    private static final int CONDITION_MASK_FIELD = 0;
    private static final int CONDITION_PLAYER_FIELD = 4;
    private static final int CONDITION_AMOUNT_FIELD = 8;
    private static final int CONDITION_COMPARISON_FIELD = 14;
    private static final int CONDITION_TYPE_FIELD = 15;
    private static final int CONDITION_FLAGS_FIELD = 17;
    private static final int CONDITION_EXTENSION_FIELD = 18;
    private static final int ACTIONS_OFFSET = 320;
    private static final int ACTION_SIZE = 32;
    private static final int SECOND_ACTION = ACTIONS_OFFSET + ACTION_SIZE;
    private static final int FOURTH_ACTION = ACTIONS_OFFSET + 3 * ACTION_SIZE;
    private static final int ACTION_PLAYER_FIELD = 16;
    private static final int ACTION_AMOUNT_FIELD = 20;
    private static final int ACTION_TYPE_FIELD = 26;
    private static final int ACTION_MODIFIER_FIELD = 27;
    private static final int ACTION_FLAGS_FIELD = 28;
    private static final int ACTION_EXTENSION_FIELD = 29;
    private static final int FLAGS_OFFSET = 0x940;
    private static final int OWNERS_OFFSET = 0x944;
    private static final int ACTION_INDEX_OFFSET = 0x95F;
    private static final int MASK_MARKER = 0x4353;
    private static final int INVALID_ENTRY_TYPE = 255;

    private static final int FRAME_DURATION = 42;
    private static final int LATER_FRAME_COUNT = 31;
    private static final int WRITTEN_SHIELD_POINTS = 512;

    /**
     * Packs a Deaths condition and Set Deaths action for the unit memory fixture.
     *
     * @param hitPoints the expected hit points DWORD
     * @return the packed trigger record
     */
    private static byte @NotNull [] createResource(int hitPoints) {
        byte[] resource = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer input = ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN);

        input.putInt(CONDITION_PLAYER_FIELD, getPlayerOperand(ClassicUnitTableRegion.BASE + HIT_POINTS));
        input.putInt(CONDITION_AMOUNT_FIELD, hitPoints);
        input.put(CONDITION_COMPARISON_FIELD, (byte) TriggerModifiers.EXACTLY);
        input.put(CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.DEATHS);
        input.putInt(ACTIONS_OFFSET + ACTION_PLAYER_FIELD, getPlayerOperand(ClassicUnitTableRegion.BASE + SHIELD_POINTS));
        input.putInt(ACTIONS_OFFSET + ACTION_AMOUNT_FIELD, WRITTEN_SHIELD_POINTS);
        input.put(ACTIONS_OFFSET + ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_DEATHS);
        input.put(ACTIONS_OFFSET + ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);
        input.put(OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS, (byte) 1);

        return resource;
    }

    /**
     * Encodes the extended Deaths player operand that selects an address with unit operand {@code 0}.
     *
     * @param address the selected address
     * @return the player operand
     */
    private static int getPlayerOperand(int address) {
        return (address - DEATH_TABLE) / Integer.BYTES;
    }

    /**
     * Creates a {@link TriggerContext} instance bound to one active player and the supplied unit pool.
     *
     * @param resource the packed trigger record
     * @param units    the authoritative unit pool
     * @return the created {@link TriggerContext} instance after its initial update
     */
    private static @NotNull TriggerContext createContext(byte @NotNull [] resource, @NotNull UnitPool units) {
        int[] playerTypes = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        TriggerContext context = new TriggerContext();
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int player = 0; player < players.length; player++) {
            players[player] = new Player(player, player, playerTypes[player], 0, 0);
        }

        context.initializeClassicPlayerRecords(players);
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(),
                                     new PlayerRelations(playerTypes, false, false, false),
                                     new int[TriggerContext.CONTROL_COUNT]);
        context.loadAllPlayersResource(resource);
        context.setExtendedDeathsOperations(new ClassicUnitMemory(units));
        runFrame(context);

        return context;
    }

    /**
     * Runs one frame of trigger evaluation with the replay flag, the game-paused DWORD and the trigger pause BYTE set.
     *
     * @param context the trigger context
     */
    private static void runFrame(@NotNull TriggerContext context) {
        ScrTriggerSystem.update(context, FRAME_DURATION, 1, 1, 1);
    }


    @Test
    void memory_trigger_writes_game_fields_and_disables_the_completed_trigger() {
        UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);

        units.getUnit(1).setHitPoints(-1);

        byte[] resource = createResource(-1);

        TriggerContext context = createContext(resource, units);

        runFrame(context);

        Assertions.assertEquals(WRITTEN_SHIELD_POINTS, units.getUnit(1).getShieldPoints());

        ByteBuffer record = ByteBuffer.wrap(context.getPlayerRecord(0, 0)).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(DISABLED_RUNNING_FLAGS, record.getInt(FLAGS_OFFSET));
        Assertions.assertEquals(64, record.get(ACTION_INDEX_OFFSET));

        units.getUnit(1).setShieldPoints(123);

        for (int frame = 0; frame < LATER_FRAME_COUNT; frame++) {
            runFrame(context);
        }

        Assertions.assertEquals(123, units.getUnit(1).getShieldPoints());
    }

    @Test
    void memory_trigger_leaves_game_state_untouched_when_condition_does_not_match() {
        UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);

        units.getUnit(1).setHitPoints(256);
        units.getUnit(1).setShieldPoints(123);

        TriggerContext context = createContext(createResource(257), units);

        runFrame(context);

        Assertions.assertEquals(123, units.getUnit(1).getShieldPoints());
        Assertions.assertEquals(0, context.getPlayerRecord(0, 0)[FLAGS_OFFSET]);
    }

    @Test
    void memory_trigger_keeps_earlier_writes_when_a_later_address_is_unsupported() {
        UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        byte[] resource = createResource(0);
        ByteBuffer input = ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN);

        input.putInt(SECOND_ACTION + ACTION_PLAYER_FIELD, getPlayerOperand(ClassicUnitTableRegion.BASE + UNSUPPORTED_FIELD));
        input.putInt(SECOND_ACTION + ACTION_AMOUNT_FIELD, -1);
        input.put(SECOND_ACTION + ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_DEATHS);
        input.put(SECOND_ACTION + ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);

        TriggerContext context = createContext(resource, units);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> runFrame(context));
        Assertions.assertEquals(WRITTEN_SHIELD_POINTS, units.getUnit(1).getShieldPoints());
        Assertions.assertEquals(0, units.getUnit(1).getEnergy());
        Assertions.assertEquals(RUNNING_FLAGS, context.getPlayerRecord(0, 0)[FLAGS_OFFSET]);
        Assertions.assertEquals(1, context.getPlayerRecord(0, 0)[ACTION_INDEX_OFFSET]);
    }

    @Test
    void preserved_trigger_clears_completion_bits_and_runs_again() {
        UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        byte[] resource = createResource(0);
        ByteBuffer input = ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN);

        input.putInt(FLAGS_OFFSET, TriggerFlags.EXECUTION_IGNORE_INTERFACE_ACTIONS | TriggerFlags.EXECUTION_WAIT_SKIPPING_DISABLED);
        input.put(SECOND_ACTION + ACTION_TYPE_FIELD, (byte) TriggerActionIds.PRESERVE_TRIGGER);

        TriggerContext context = createContext(resource, units);

        runFrame(context);

        ByteBuffer record = ByteBuffer.wrap(context.getPlayerRecord(0, 0)).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(PRESERVE_FLAGS, record.getInt(FLAGS_OFFSET));
        Assertions.assertEquals(0, record.get(ACTION_INDEX_OFFSET));

        units.getUnit(1).setShieldPoints(123);

        for (int frame = 0; frame < LATER_FRAME_COUNT; frame++) {
            runFrame(context);
        }

        Assertions.assertEquals(WRITTEN_SHIELD_POINTS, units.getUnit(1).getShieldPoints());
    }

    @Test
    void memory_trigger_skips_disabled_entries_and_stops_at_the_first_empty_action() {
        UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        byte[] resource = createResource(0);
        ByteBuffer input = ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN);

        input.put(CONDITION_TYPE_FIELD, (byte) INVALID_ENTRY_TYPE);
        input.put(CONDITION_FLAGS_FIELD, (byte) TriggerFlags.CONDITION_DISABLED);
        System.arraycopy(resource, ACTIONS_OFFSET, resource, SECOND_ACTION, ACTION_SIZE);
        System.arraycopy(resource, ACTIONS_OFFSET, resource, FOURTH_ACTION, ACTION_SIZE);
        input.put(ACTIONS_OFFSET + ACTION_TYPE_FIELD, (byte) INVALID_ENTRY_TYPE);
        input.put(ACTIONS_OFFSET + ACTION_FLAGS_FIELD, (byte) TriggerFlags.ACTION_DISABLED);
        input.putInt(FOURTH_ACTION + ACTION_AMOUNT_FIELD, 999);

        TriggerContext context = createContext(resource, units);

        runFrame(context);

        Assertions.assertEquals(WRITTEN_SHIELD_POINTS, units.getUnit(1).getShieldPoints());
        Assertions.assertEquals(64, context.getPlayerRecord(0, 0)[ACTION_INDEX_OFFSET]);
    }

    @Test
    void current_player_deaths_route_to_the_death_table_outside_the_unit_profile() {
        UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        byte[] resource = createResource(0);

        ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN).putInt(CONDITION_PLAYER_FIELD, TriggerPlayerSelectors.CURRENT_PLAYER);

        TriggerContext context = createContext(resource, units);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> runFrame(context));
        Assertions.assertEquals(0, units.getUnit(1).getShieldPoints());
    }

    @Test
    void memory_trigger_applies_condition_mask_and_ignores_marker_outside_action_mask() {
        for (int variant = 0; variant < 2; variant++) {
            UnitPool units = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);

            units.getUnit(1).setHitPoints(0x1234);

            byte[] resource = createResource(0x0034);
            ByteBuffer input = ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN);

            if (variant == 0) {
                input.putInt(CONDITION_MASK_FIELD, 0xFF);
                input.putShort(CONDITION_EXTENSION_FIELD, (short) MASK_MARKER);
            } else {
                input.putInt(CONDITION_AMOUNT_FIELD, 0x1234);
                input.putShort(ACTIONS_OFFSET + ACTION_EXTENSION_FIELD, (short) MASK_MARKER);
            }

            TriggerContext context = createContext(resource, units);

            runFrame(context);

            Assertions.assertEquals(WRITTEN_SHIELD_POINTS, units.getUnit(1).getShieldPoints());
        }
    }
}
