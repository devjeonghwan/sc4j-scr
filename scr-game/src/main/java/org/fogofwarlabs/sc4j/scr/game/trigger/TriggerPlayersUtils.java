package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the trigger player selectors: single players, the current player, forces and the player groups, and lists
 * the players a selector selects in the order of the active-player walk.
 */
final class TriggerPlayersUtils {
    /**
     * The mask of the low BYTE.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Force selectors are {@code FORCE_BASE + force}.
     */
    static final int FORCE_BASE = TriggerPlayerSelectors.FORCE_1 - 1;

    /**
     * Returns the next player of a selector's walk: a single-player selector ({@link #resolveSinglePlayer}) selects only
     * that player; a group selector selects the players of the active-player walk ({@link TriggerContext#getNextActivePlayer})
     * that {@link #selectsPlayer} accepts at the time of the step, then, for {@link TriggerPlayerSelectors#ALL_PLAYERS},
     * players {@code 8}-{@code 11}.
     *
     * @param context  the trigger context
     * @param selector the player selector
     * @param player   the player the walk returned last, or {@link TriggerContext#WALK_START}
     * @return the next selected player, or {@link PlayerTable#PLAYER_SLOTS} at the end of the walk
     */
    static int getNextSelectedPlayer(@NotNull TriggerContext context, int selector, int player) {
        int single = resolveSinglePlayer(context, selector);

        if (single >= 0) {
            return player == TriggerContext.WALK_START ? single : PlayerTable.PLAYER_SLOTS;
        }

        if (player >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            return selector == TriggerPlayerSelectors.ALL_PLAYERS ? player + 1 : PlayerTable.PLAYER_SLOTS;
        }

        int next = context.getNextActivePlayer(player);

        while (next < PlayerTable.PLAYABLE_PLAYER_SLOTS && !selectsPlayer(context, selector, next)) {
            next = context.getNextActivePlayer(next);
        }

        // At the end of the active walk the next player is 8, the first of the players All Players adds.
        return next < PlayerTable.PLAYABLE_PLAYER_SLOTS || selector == TriggerPlayerSelectors.ALL_PLAYERS ? next : PlayerTable.PLAYER_SLOTS;
    }

    /**
     * Tests whether a selector selects the owner of a unit: the owner must be one of the players of the selector's walk
     * ({@link #getNextSelectedPlayer}), so a group selector of a team network game selects only the force leaders and,
     * for {@link TriggerPlayerSelectors#ALL_PLAYERS}, players {@code 8}-{@code 11}.
     *
     * @param context  the trigger context
     * @param selector the player selector
     * @param owner    the owner
     * @return {@code true} if the selector's walk visits the owner, {@code false} otherwise
     */
    static boolean isOwnerSelected(@NotNull TriggerContext context, int selector, int owner) {
        for (int selected = getNextSelectedPlayer(context, selector, TriggerContext.WALK_START); selected < PlayerTable.PLAYER_SLOTS;
             selected = getNextSelectedPlayer(context, selector, selected)) {
            if (selected == owner) {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves direct player selectors: {@link TriggerPlayerSelectors#CURRENT_PLAYER} is the current player and
     * {@code 0}-{@code 11} name one player.
     *
     * @param context  the trigger context
     * @param selector the player selector
     * @return the selected player, or {@code -1} for a group selector
     */
    static int resolveSinglePlayer(@NotNull TriggerContext context, int selector) {
        if (selector == TriggerPlayerSelectors.CURRENT_PLAYER) {
            return context.currentPlayer;
        }

        if (Integer.compareUnsigned(selector, PlayerTable.PLAYER_SLOTS) < 0) {
            return selector;
        }

        return -1;
    }

    /**
     * Applies group player selectors: {@link TriggerPlayerSelectors#ALL_PLAYERS} selects the active players and players
     * {@code 8}-{@code 11}, a force selector the active players of its force, and
     * {@link TriggerPlayerSelectors#NON_ALLIED_VICTORY_PLAYERS} the active players other than the current player without
     * mutual allied victory. Foes, allies and neutral players compare the current player's alliance with the player and
     * skip the current player; the remaining selectors select nobody.
     *
     * @param context  the trigger context
     * @param selector the group selector
     * @param player   the player
     * @return {@code true} if the selector selects the player, {@code false} otherwise
     */
    private static boolean selectsPlayer(@NotNull TriggerContext context, int selector, int player) {
        if (player >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            return selector == TriggerPlayerSelectors.ALL_PLAYERS;
        }

        if (!context.requirePlayer(player).isActive()) {
            return false;
        }

        return switch (selector) {
            case TriggerPlayerSelectors.ALL_PLAYERS -> true;
            case TriggerPlayerSelectors.FOES -> player != context.currentPlayer && context.requireRelations().getAlliance(context.currentPlayer, player) == PlayerRelations.ENEMY;
            case TriggerPlayerSelectors.ALLIES -> player != context.currentPlayer && context.requireRelations().getAlliance(context.currentPlayer, player) == PlayerRelations.ALLIED_VICTORY;
            case TriggerPlayerSelectors.NEUTRAL_PLAYERS -> player != context.currentPlayer && context.requireRelations().getAlliance(context.currentPlayer, player) == PlayerRelations.ALLIED;
            case TriggerPlayerSelectors.FORCE_1,
                 TriggerPlayerSelectors.FORCE_2,
                 TriggerPlayerSelectors.FORCE_3,
                 TriggerPlayerSelectors.FORCE_4 -> (context.requirePlayer(player).getTeam() & BYTE_MASK) == selector - FORCE_BASE;
            case TriggerPlayerSelectors.NON_ALLIED_VICTORY_PLAYERS -> player != context.currentPlayer &&
                                                                      (context.requireRelations().getAlliance(player, context.currentPlayer) != PlayerRelations.ALLIED_VICTORY ||
                                                                       context.requireRelations().getAlliance(context.currentPlayer, player) != PlayerRelations.ALLIED_VICTORY);
            default -> false;
        };
    }


    private TriggerPlayersUtils() {
    }
}
