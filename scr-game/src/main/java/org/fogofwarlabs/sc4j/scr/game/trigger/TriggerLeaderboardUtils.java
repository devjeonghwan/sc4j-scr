package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Computes the leaderboard of the trigger cycle.
 */
final class TriggerLeaderboardUtils {
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The ordered score parts that Goal Points of the total score adds.
     */
    private static final int @NotNull [] TOTAL_SCORE_PARTS = {
            TriggerScoreActionsUtils.SCORE_CUSTOM,
            TriggerScoreActionsUtils.SCORE_UNITS,
            TriggerScoreActionsUtils.SCORE_BUILDINGS,
            TriggerScoreActionsUtils.SCORE_KILLS,
            TriggerScoreActionsUtils.SCORE_RAZINGS
    };
    /**
     * The score parts that Goal Points of the units and buildings score adds.
     */
    private static final int @NotNull [] UNITS_AND_BUILDINGS_SCORE_PARTS = {
            TriggerScoreActionsUtils.SCORE_UNITS,
            TriggerScoreActionsUtils.SCORE_BUILDINGS
    };
    /**
     * The score parts that Goal Points of the kills and razings score adds.
     */
    private static final int @NotNull [] KILLS_AND_RAZINGS_SCORE_PARTS = {
            TriggerScoreActionsUtils.SCORE_KILLS,
            TriggerScoreActionsUtils.SCORE_RAZINGS
    };

    /**
     * The counter a leaderboard ranks by.
     */
    private enum LeaderboardValue {
        /**
         * The completed unit count after the pass or location counts.
         */
        CONTROL,
        /**
         * The resource amount of a resource type.
         */
        RESOURCES,
        /**
         * The kill count of a unit type.
         */
        KILLS,
        /**
         * The score of a score type.
         */
        POINTS
    }

    /**
     * Computes the leaderboard, run when the periodic countdown reloads.
     * <p>
     * Clears the player/value pairs, then fills each shown active player's completed unit count, resources, kills or
     * score. Goals add the unsigned distance below the goal; combined resource and score counters are added in order.
     * Each selection pass moves the first greatest pair of the unsorted prefix to its end. Values sort descending,
     * goals ascending, and unused pairs last. An unsupported leaderboard type leaves the cleared pairs unsorted.
     *
     * @param context the trigger context
     */
    static void computeLeaderboard(@NotNull TriggerContext context) {
        Arrays.fill(context.leaderboardPlayerIndices, -1);
        Arrays.fill(context.leaderboardValues, 0);

        int unit = context.leaderboardUnitType & WORD_MASK;
        boolean ascending = true;

        switch (context.leaderboardType & BYTE_MASK) {
            case TriggerActionIds.LEADERBOARD_CONTROL -> {
                TriggerConditions.prepareCommandCounts(context);
                fillLeaderboard(context, LeaderboardValue.CONTROL, unit, false);

                ascending = false;
            }
            case TriggerActionIds.LEADERBOARD_CONTROL_AT_LOCATION -> {
                TriggerConditions.prepareLocationCounts(context, context.leaderboardLocation & BYTE_MASK);
                fillLeaderboard(context, LeaderboardValue.CONTROL, unit, false);

                ascending = false;
            }
            case TriggerActionIds.LEADERBOARD_RESOURCES -> {
                fillLeaderboard(context, LeaderboardValue.RESOURCES, unit, false);

                ascending = false;
            }
            case TriggerActionIds.LEADERBOARD_KILLS -> {
                fillLeaderboard(context, LeaderboardValue.KILLS, unit, false);

                ascending = false;
            }
            case TriggerActionIds.LEADERBOARD_POINTS -> {
                fillLeaderboard(context, LeaderboardValue.POINTS, unit, false);

                ascending = false;
            }
            case TriggerActionIds.LEADERBOARD_GOAL_CONTROL -> {
                TriggerConditions.prepareCommandCounts(context);
                fillLeaderboard(context, LeaderboardValue.CONTROL, unit, true);
            }
            case TriggerActionIds.LEADERBOARD_GOAL_CONTROL_AT_LOCATION -> {
                TriggerConditions.prepareLocationCounts(context, context.leaderboardLocation & BYTE_MASK);
                fillLeaderboard(context, LeaderboardValue.CONTROL, unit, true);
            }
            case TriggerActionIds.LEADERBOARD_GOAL_RESOURCES -> {
                if (unit == TriggerScoreActionsUtils.RESOURCE_BOTH) {
                    fillLeaderboard(context, LeaderboardValue.RESOURCES, TriggerScoreActionsUtils.RESOURCE_MINERALS, true);
                    fillLeaderboard(context, LeaderboardValue.RESOURCES, TriggerScoreActionsUtils.RESOURCE_GAS, true);
                } else {
                    fillLeaderboard(context, LeaderboardValue.RESOURCES, unit, true);
                }
            }
            case TriggerActionIds.LEADERBOARD_GOAL_KILLS -> fillLeaderboard(context, LeaderboardValue.KILLS, unit, true);
            case TriggerActionIds.LEADERBOARD_GOAL_POINTS -> {
                int[] parts = switch (unit) {
                    case TriggerScoreActionsUtils.SCORE_TOTAL -> TOTAL_SCORE_PARTS;
                    case TriggerScoreActionsUtils.SCORE_UNITS_AND_BUILDINGS -> UNITS_AND_BUILDINGS_SCORE_PARTS;
                    case TriggerScoreActionsUtils.SCORE_KILLS_AND_RAZINGS -> KILLS_AND_RAZINGS_SCORE_PARTS;
                    default -> new int[]{unit};
                };

                for (int part : parts) {
                    fillLeaderboard(context, LeaderboardValue.POINTS, part, true);
                }
            }
            case TriggerActionIds.LEADERBOARD_GREED -> {
                fillLeaderboard(context, LeaderboardValue.RESOURCES, TriggerScoreActionsUtils.RESOURCE_MINERALS, true);
                fillLeaderboard(context, LeaderboardValue.RESOURCES, TriggerScoreActionsUtils.RESOURCE_GAS, true);
            }
            default -> {
                return;
            }
        }

        for (int last = context.leaderboardPlayerIndices.length - 1; last > 0; last--) {
            int greatest = 0;

            for (int candidate = 1; candidate <= last; candidate++) {
                if (compareLeaderboardPairs(context, candidate, greatest, ascending) > 0) {
                    greatest = candidate;
                }
            }

            int player = context.leaderboardPlayerIndices[greatest];
            int value = context.leaderboardValues[greatest];

            context.leaderboardPlayerIndices[greatest] = context.leaderboardPlayerIndices[last];
            context.leaderboardValues[greatest]        = context.leaderboardValues[last];
            context.leaderboardPlayerIndices[last]     = player;
            context.leaderboardValues[last]            = value;
        }
    }

    /**
     * Stores the value or, for goals, adds the value's distance below the goal for every shown active player.
     *
     * @param context the trigger context
     * @param kind    the counter the leaderboard ranks by
     * @param operand the unit type, resource type or score type
     * @param goal    {@code true} to add the distance below the goal, {@code false} to store the value
     */
    private static void fillLeaderboard(@NotNull TriggerContext context, @NotNull LeaderboardValue kind, int operand, boolean goal) {
        for (int player = context.getNextActivePlayer(TriggerContext.WALK_START); player < PlayerTable.PLAYABLE_PLAYER_SLOTS;
             player = context.getNextActivePlayer(player)) {
            Player record = context.requirePlayer(player);

            if ((record.getType() & BYTE_MASK) != PlayerTypes.HUMAN && context.leaderboardComputers == 0) {
                continue;
            }

            int value = switch (kind) {
                case CONTROL -> TriggerConditions.countPlayerUnits(context, player, operand, true);
                case RESOURCES -> TriggerConditions.readResources(record, operand);
                case KILLS -> TriggerConditions.readPlayerCounter(context, player, operand, TriggerConditions.COUNTER_KILLS);
                case POINTS -> TriggerConditions.readPlayerCounter(context, player, operand, TriggerConditions.COUNTER_SCORE);
            };

            context.leaderboardPlayerIndices[player] = player;

            if (goal) {
                context.leaderboardValues[player] += Integer.compareUnsigned(value, context.leaderboardGoal) >= 0
                                                     ? 0
                                                     : context.leaderboardGoal - value;
            } else {
                context.leaderboardValues[player] = value;
            }
        }
    }

    /**
     * Compares two leaderboard pairs: an unused pair (player {@code -1}) sorts after a used one; used pairs compare
     * their unsigned values, descending or ascending.
     *
     * @param context   the trigger context
     * @param first     the first pair position
     * @param second    the second pair position
     * @param ascending {@code true} for ascending values (goals), {@code false} for descending values
     * @return a positive value if the first pair sorts after the second, a negative value if before, {@code 0} if equal
     */
    private static int compareLeaderboardPairs(@NotNull TriggerContext context, int first, int second, boolean ascending) {
        boolean firstUnused = context.leaderboardPlayerIndices[first] == -1;
        boolean secondUnused = context.leaderboardPlayerIndices[second] == -1;

        if (firstUnused) {
            return secondUnused ? 0 : 1;
        }

        if (secondUnused) {
            return -1;
        }

        int order = Integer.compareUnsigned(context.leaderboardValues[first], context.leaderboardValues[second]);

        return Integer.signum(ascending ? order : -order);
    }


    private TriggerLeaderboardUtils() {
    }
}
