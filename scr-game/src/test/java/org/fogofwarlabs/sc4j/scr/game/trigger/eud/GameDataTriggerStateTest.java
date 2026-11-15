package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace.GameDataRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks trigger locations, switches, relations, counters and clocks.
 */
final class GameDataTriggerStateTest {
    private static final int GAME_DATA = 0x57F0F0;

    private static final int SHARED_VISION = 0xFC;
    private static final int UNIT_COUNTS = 0x2CF4;
    private static final int UNITS_LOST = 0x2D84;
    private static final int TERRAN_SUPPLY_USED = 0x3114;
    private static final int TYPE_COUNTS = 0x3234;
    private static final int ALLIANCES = 0xE544;
    private static final int MISSION_OBJECTIVES = 0xE5D4;
    private static final int COUNTDOWN = 0xE604;
    private static final int VICTORY_STATES = 0xE610;
    private static final int LEADERBOARD_COMPUTERS = 0xE618;
    private static final int LEADERBOARD_TYPE = 0xE61C;
    private static final int SWITCHES = 0xEB50;
    private static final int LOCATIONS = 0xEB70;
    private static final int COUNTDOWN_PAUSED = 0xFF5C;

    private static final int LOCATION_COUNT = 255;
    private static final int LOCATION_SIZE = 20;
    private static final int BYTE_MASK = 0xFF;

    @Test
    void locations_are_twenty_byte_records_with_their_string_word() {
        TriggerContext context = new TriggerContext();
        byte[] payload = new byte[LOCATION_COUNT * LOCATION_SIZE];
        int record = 3 * LOCATION_SIZE;

        payload[record]      = 32;
        payload[record + 4]  = 64;
        payload[record + 8]  = (byte) 128;
        payload[record + 12] = (byte) 160;
        payload[record + 16] = 7;
        payload[record + 18] = 0x0F;
        context.loadLocations(payload);

        ClassicAddressSpace space = ClassicAddressSpace.create(context, PlayerProgressTestUtils.createPlayerScores(), null, null, new MessageLines());

        Assertions.assertEquals(128, space.readDword(GAME_DATA + LOCATIONS + record + 8));
        Assertions.assertEquals(7 | 0x0F << 16, space.readDword(GAME_DATA + LOCATIONS + record + 16));

        space.writeDword(GAME_DATA + LOCATIONS + record, 1000);

        Assertions.assertEquals(1000, context.getLocation(3).getLeft());

        space.writeDword(GAME_DATA + LOCATIONS + record + 16, 9 | 0x3F << 16);

        Assertions.assertEquals(9, context.getLocation(3).getStringIndex());
        Assertions.assertEquals(0x3F, context.getLocation(3).getElevationFlags());
    }

    @Test
    void switches_are_eight_dwords_of_bits() {
        TriggerContext context = new TriggerContext();
        ClassicAddressSpace space = ClassicAddressSpace.create(context, PlayerProgressTestUtils.createPlayerScores(), null, null, new MessageLines());

        space.writeDword(GAME_DATA + SWITCHES + Integer.BYTES, 1 << 3);

        Assertions.assertTrue(context.isSwitchSet(Integer.SIZE + 3));
        Assertions.assertFalse(context.isSwitchSet(3));
    }

    @Test
    void mission_objectives_are_string_dwords_of_twelve_players() {
        TriggerContext context = new TriggerContext();
        ClassicAddressSpace space = ClassicAddressSpace.create(context, PlayerProgressTestUtils.createPlayerScores(), null, null, new MessageLines());

        space.writeDword(GAME_DATA + MISSION_OBJECTIVES + 2 * Integer.BYTES, 17);

        Assertions.assertEquals(17, context.getMissionObjectivesStringIndex(2));

        space.writeDword(GAME_DATA + MISSION_OBJECTIVES + 10 * Integer.BYTES, 5);

        Assertions.assertEquals(5, space.readDword(GAME_DATA + MISSION_OBJECTIVES + 10 * Integer.BYTES));
    }

    @Test
    void alliances_shared_vision_and_victory_states_are_read_and_written() {
        PlayerRelations relations = new PlayerRelations(new int[PlayerTable.PLAYER_SLOTS], false, false, false);
        Player[] players = new Player[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, PlayerTypes.HUMAN, ScrRaceIds.ZERG, 0);
        }

        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new GameDataRegion(null, PlayerProgressTestUtils.createPlayerScores(), null, players, null, null, relations, null));

        space.writeDword(GAME_DATA + ALLIANCES + PlayerTable.PLAYER_SLOTS, 0x00020000);

        Assertions.assertEquals(2, relations.getAlliance(1, 2));

        space.writeDword(GAME_DATA + SHARED_VISION + 3 * Integer.BYTES, 0x81);

        Assertions.assertEquals(0x81, relations.getSharedVision(3));

        players[5].setVictoryState(3);

        Assertions.assertEquals(3, space.readDword(GAME_DATA + VICTORY_STATES + 4) >>> Byte.SIZE & BYTE_MASK);

        space.writeDword(GAME_DATA + VICTORY_STATES, 0x0200);

        Assertions.assertEquals(2, players[1].getVictoryState());
    }

    @Test
    void accounting_counters_follow_the_capture_layout() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        ClassicAddressSpace space = ClassicAddressSpace.create(null, accounting.getScores(), accounting, null, new MessageLines());

        space.writeDword(GAME_DATA + TERRAN_SUPPLY_USED + 2 * Integer.BYTES, 18);

        Assertions.assertEquals(18, UnitAccountingAssert.readCounter(accounting, TERRAN_SUPPLY_USED + 2 * Integer.BYTES));

        space.writeDword(GAME_DATA + TYPE_COUNTS + (7 * PlayerTable.PLAYER_SLOTS + 3) * Integer.BYTES, 5);

        Assertions.assertEquals(5, UnitAccountingAssert.readCounter(accounting, TYPE_COUNTS + (7 * PlayerTable.PLAYER_SLOTS + 3) * Integer.BYTES));

        space.writeDword(GAME_DATA + UNIT_COUNTS + 4, 9);

        Assertions.assertEquals(9, space.readDword(GAME_DATA + UNIT_COUNTS + 4));

        space.writeDword(GAME_DATA + UNITS_LOST + 4, 3);

        int[] scoreCounters = accounting.getScores().getPlayerCounters(UNITS_LOST);

        Assertions.assertNotNull(scoreCounters);

        Assertions.assertEquals(3, scoreCounters[1], "Score ranges stay with PlayerScores.");
    }

    @Test
    void clocks_and_leaderboard_start_at_reset_values_and_store_writes() {
        TriggerContext context = new TriggerContext();
        ClassicAddressSpace space = ClassicAddressSpace.create(context, PlayerProgressTestUtils.createPlayerScores(), null, null, new MessageLines());

        Assertions.assertEquals(1, space.readDword(GAME_DATA + LEADERBOARD_COMPUTERS), "Leaderboards include computers.");

        space.writeDword(GAME_DATA + COUNTDOWN, 90);
        space.writeDword(GAME_DATA + COUNTDOWN_PAUSED, 1);
        space.writeDword(GAME_DATA + LEADERBOARD_TYPE, 0x0005_02_10);

        Assertions.assertEquals(90, space.readDword(GAME_DATA + COUNTDOWN));
        Assertions.assertEquals(1, space.readDword(GAME_DATA + COUNTDOWN_PAUSED));
        Assertions.assertEquals(0x0005_02_10, space.readDword(GAME_DATA + LEADERBOARD_TYPE), "Type byte, location byte and unit WORD.");
    }
}
