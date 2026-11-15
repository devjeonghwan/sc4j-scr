package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks leaderboard resource ordering, tie behavior and goal distances.
 */
final class LeaderboardCalculationTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;

    /**
     * The classic address of the game-data block that holds the leaderboard fields.
     */
    private static final int GAME_DATA = 0x57F0F0;
    private static final int LEADERBOARD_COMPUTERS_OFFSET = 0xE618;
    private static final int LEADERBOARD_TYPE_OFFSET = 0xE61C;
    private static final int LEADERBOARD_GOAL_OFFSET = 0xE620;

    /**
     * The trigger update calls of one periodic tick.
     */
    private static final int PERIODIC_TICK_CALLS = 16;

    /**
     * Creates a {@link TriggerContext} instance with the classic player records of the supplied players.
     *
     * @param players the initialized player slots
     * @return the created {@link TriggerContext} instance
     */
    private static @NotNull TriggerContext createContext(@NotNull Player @NotNull [] players) {
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);

        return context;
    }

    /**
     * Advances trigger evaluation through one periodic leaderboard tick.
     *
     * @param context the trigger evaluation context
     */
    private static void runPeriodicTick(@NotNull TriggerContext context) {
        for (int frame = 0; frame < PERIODIC_TICK_CALLS; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }
    }


    @Test
    void resources_rank_descending_with_ties_in_shortsort_order() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.COMPUTER, PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE};
        Player[] players = TriggerPlayerScenario.createPlayers(types, new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        players[0].setMinerals(50);
        players[1].setMinerals(900);
        players[2].setMinerals(300);
        players[3].setMinerals(300);

        TriggerContext context = createContext(players);
        ClassicAddressSpace space = ClassicAddressSpace.create(context, PlayerProgressTestUtils.createPlayerScores(), null, players, new MessageLines());

        space.writeDword(GAME_DATA + LEADERBOARD_TYPE_OFFSET, TriggerActionIds.LEADERBOARD_RESOURCES);

        runPeriodicTick(context);

        Assertions.assertEquals(1, context.getLeaderboardPlayerIndex(0));
        Assertions.assertEquals(900, context.getLeaderboardValue(0));
        // Equal values: each pass moves the first greatest entry of the prefix to its end.
        Assertions.assertEquals(2, context.getLeaderboardPlayerIndex(1));
        Assertions.assertEquals(3, context.getLeaderboardPlayerIndex(2));
        Assertions.assertEquals(0, context.getLeaderboardPlayerIndex(3));
        Assertions.assertEquals(-1, context.getLeaderboardPlayerIndex(4));
    }

    @Test
    void goals_rank_ascending_by_distance_and_hide_computers_when_asked() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.COMPUTER, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE};
        Player[] players = TriggerPlayerScenario.createPlayers(types, new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        players[0].setMinerals(100);
        players[0].setGas(20);
        players[2].setMinerals(700);

        TriggerContext context = createContext(players);
        ClassicAddressSpace space = ClassicAddressSpace.create(context, PlayerProgressTestUtils.createPlayerScores(), null, players, new MessageLines());

        // Greed (40) with goal 500: minerals and gas each add their distance below the goal.
        space.writeDword(GAME_DATA + LEADERBOARD_TYPE_OFFSET, TriggerActionIds.LEADERBOARD_GREED);
        space.writeDword(GAME_DATA + LEADERBOARD_GOAL_OFFSET, 500);
        space.writeDword(GAME_DATA + LEADERBOARD_COMPUTERS_OFFSET, 0);

        runPeriodicTick(context);

        Assertions.assertEquals(2, context.getLeaderboardPlayerIndex(0));
        Assertions.assertEquals(500, context.getLeaderboardValue(0), "700 minerals reach the goal; 0 gas is 500 below it.");
        Assertions.assertEquals(0, context.getLeaderboardPlayerIndex(1));
        Assertions.assertEquals(400 + 480, context.getLeaderboardValue(1));
        Assertions.assertEquals(-1, context.getLeaderboardPlayerIndex(2), "The computer is hidden.");
    }
}
