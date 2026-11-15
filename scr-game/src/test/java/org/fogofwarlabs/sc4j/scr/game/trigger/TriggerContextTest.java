package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicDynamicRegionsUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Checks the active-player walk of the trigger context and the classic trigger lists it fills in walk order, and the
 * game-context memory after the all-units type counts of the trigger count cache, which unit type 228 addresses.
 * <p>
 * The layout of that memory is the contract: it is EUD-visible game-context state, where the count-cache state packs the
 * preparation flag and the prepared location ({@code flag | location << 8}), so the tests set and read those fields
 * directly. The list heads are EUD-visible as well, so the tests read their link DWORDs.
 */
final class TriggerContextTest {
    private static final int EMPTY_TYPE = ScrUnitIds.NONE;

    /**
     * The player whose all-units count of type {@code 228} is the count-cache state.
     */
    private static final int STATE_PLAYER = 0;
    /**
     * The player whose all-units count of type {@code 228} is the gap bytes after the count-cache state.
     */
    private static final int GAP_PLAYER = 2;
    /**
     * The player whose all-units count of type {@code 228} is the all-units count of the Marine.
     */
    private static final int MARINE_PLAYER = 1;
    /**
     * The units of type {@code 228} the test adds for {@link #GAP_PLAYER}.
     */
    private static final int GAP_UNIT_COUNT = 2;

    private static final int PREPARATION_FLAG = 2;
    private static final int PREPARED_LOCATION = 5;
    /**
     * The shift of the prepared location in the count-cache state, the BYTE after the preparation flag.
     */
    private static final int PREPARED_LOCATION_SHIFT = Byte.SIZE;

    private static final int COMPLETED_COUNT = 4;

    /*
     * The active-player walk fixtures: eight humans; a team network game names leaders for forces 1, 2 and 4.
     */
    private static final int FIRST_LEADER = 5;
    private static final int SECOND_LEADER = 1;
    private static final int FOURTH_LEADER = 3;
    private static final int NO_LEADER = -1;
    private static final int DEFEATED_PLAYER = SECOND_LEADER;
    /**
     * The walk result after the last active player: the number of playable slots.
     */
    private static final int WALK_END = 8;
    /**
     * The active players of the slot walk when {@link #DEFEATED_PLAYER} is out of the game.
     */
    private static final int @NotNull [] SLOTS_WITHOUT_DEFEATED_PLAYER = {0, 2, 3, 4, 5, 6, 7};

    /**
     * The offset of the first node's link address in a classic list head.
     */
    private static final int FIRST_LINK_OFFSET = 2 * Integer.BYTES;

    private static @NotNull Player @NotNull [] createHumans() {
        int[] types = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        Arrays.fill(types, PlayerTypes.HUMAN);

        return TriggerPlayerScenario.createPlayers(types, new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]);
    }


    @Test
    void get_next_active_player_should_walk_the_team_leaders_in_force_order() {
        TriggerContext context = new TriggerContext();

        context.setTeamLeaders(new int[]{FIRST_LEADER, SECOND_LEADER, NO_LEADER, FOURTH_LEADER});
        context.initializeClassicPlayerRecords(createHumans());

        Assertions.assertEquals(FIRST_LEADER, context.getNextActivePlayer(TriggerContext.WALK_START));
        Assertions.assertEquals(SECOND_LEADER, context.getNextActivePlayer(FIRST_LEADER));
        Assertions.assertEquals(FOURTH_LEADER, context.getNextActivePlayer(SECOND_LEADER), "A force without a leader is skipped.");
        Assertions.assertEquals(WALK_END, context.getNextActivePlayer(FOURTH_LEADER));
    }

    @Test
    void get_active_players_should_skip_players_out_of_the_game_with_and_without_team_leaders() {
        Player[] players = createHumans();
        TriggerContext slots = new TriggerContext();
        TriggerContext leaders = new TriggerContext();

        players[DEFEATED_PLAYER].setVictoryState(Player.VICTORY_STATE_DEFEATED);
        slots.initializeClassicPlayerRecords(players);
        leaders.setTeamLeaders(new int[]{FIRST_LEADER, SECOND_LEADER, NO_LEADER, FOURTH_LEADER});
        leaders.initializeClassicPlayerRecords(players);

        Assertions.assertArrayEquals(SLOTS_WITHOUT_DEFEATED_PLAYER, slots.getActivePlayers());
        Assertions.assertArrayEquals(new int[]{FIRST_LEADER, FOURTH_LEADER}, leaders.getActivePlayers());
    }

    @Test
    void set_team_leaders_should_keep_a_repeated_slot_only_for_its_first_force() {
        TriggerContext context = new TriggerContext();

        context.setTeamLeaders(new int[]{FIRST_LEADER, FIRST_LEADER, SECOND_LEADER, FIRST_LEADER});
        context.initializeClassicPlayerRecords(createHumans());

        Assertions.assertArrayEquals(new int[]{FIRST_LEADER, SECOND_LEADER}, context.getActivePlayers());
    }

    @Test
    void initialize_classic_player_records_should_take_the_initially_active_players_from_the_walk() {
        TriggerContext context = new TriggerContext();

        context.setTeamLeaders(new int[]{FIRST_LEADER, SECOND_LEADER, NO_LEADER, FOURTH_LEADER});
        context.initializeClassicPlayerRecords(createHumans());

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            boolean leader = player == FIRST_LEADER || player == SECOND_LEADER || player == FOURTH_LEADER;

            Assertions.assertEquals(leader, context.isInitiallyActive(player), "Only the team leaders are initially active.");
        }
    }

    @Test
    void get_classic_trigger_regions_should_allocate_the_nodes_in_walk_order() {
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        TriggerContext context = new TriggerContext();
        int firstNodeBase = ClassicDynamicRegionsUtils.getFirstTriggerNodeBase(0);

        records[TriggerRecordCodec.OWNERS_OFFSET + FIRST_LEADER]  = 1;
        records[TriggerRecordCodec.OWNERS_OFFSET + SECOND_LEADER] = 1;

        context.setTeamLeaders(new int[]{FIRST_LEADER, SECOND_LEADER, NO_LEADER, FOURTH_LEADER});
        context.initializeClassicPlayerRecords(createHumans());
        context.loadRecords(records);

        List<ClassicRegion> regions = context.getClassicTriggerRegions(firstNodeBase);
        ClassicRegion heads = regions.getFirst();

        Assertions.assertEquals(firstNodeBase, heads.readDword(FIRST_LEADER * ClassicTriggerMirror.HEAD_SIZE + FIRST_LINK_OFFSET),
                                "The leader of force 1 is walked first, so its node is allocated first.");
        Assertions.assertNotEquals(firstNodeBase, heads.readDword(SECOND_LEADER * ClassicTriggerMirror.HEAD_SIZE + FIRST_LINK_OFFSET));
    }

    @Test
    void location_counts_of_type_228_write_the_count_cache_state_and_its_gap_bytes() {
        TriggerContext context = new TriggerContext();
        UnitAccounting.TriggerSnapshot counts = context.createAccountingSnapshot();

        context.preparationFlag  = PREPARATION_FLAG;
        context.preparedLocation = PREPARED_LOCATION;

        counts.addLocationUnit(EMPTY_TYPE, STATE_PLAYER, 0, 0);
        counts.addLocationUnit(EMPTY_TYPE, GAP_PLAYER, 0, 0);
        counts.addLocationUnit(EMPTY_TYPE, GAP_PLAYER, 0, 0);

        Assertions.assertEquals(PREPARATION_FLAG + 1, context.preparationFlag);
        Assertions.assertEquals(PREPARED_LOCATION, context.preparedLocation);
        Assertions.assertEquals(PREPARATION_FLAG + 1 | PREPARED_LOCATION << PREPARED_LOCATION_SHIFT,
                                counts.getCommandCount(STATE_PLAYER, EMPTY_TYPE, false));
        Assertions.assertEquals(GAP_UNIT_COUNT, counts.getCommandCount(GAP_PLAYER, EMPTY_TYPE, false));

        counts.clear();

        Assertions.assertEquals(GAP_UNIT_COUNT, counts.getCommandCount(GAP_PLAYER, EMPTY_TYPE, false));
    }

    @Test
    void clear_should_clear_an_all_units_count_that_a_completed_count_of_type_228_wrote() {
        TriggerContext context = new TriggerContext();
        UnitAccounting.TriggerSnapshot counts = context.createAccountingSnapshot();

        counts.addCompletedType(EMPTY_TYPE, MARINE_PLAYER, COMPLETED_COUNT);

        Assertions.assertEquals(COMPLETED_COUNT, counts.getCommandCount(MARINE_PLAYER, ScrUnitIds.TERRAN_MARINE, false));

        counts.clear();

        Assertions.assertEquals(0, counts.getCommandCount(MARINE_PLAYER, ScrUnitIds.TERRAN_MARINE, false));
    }
}
