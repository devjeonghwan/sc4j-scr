package org.fogofwarlabs.sc4j.scr.game.player;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the type, race and team bytes a player keeps, and which players are out of the game and which take part in it.
 */
final class PlayerTest {
    /**
     * The player slot of the created players.
     */
    private static final int SLOT = 2;
    /**
     * The race of the created players.
     */
    private static final int RACE = 0;
    /**
     * The team of the created players.
     */
    private static final int TEAM = 0;
    /**
     * The victory state of a player that is neither dropped nor defeated.
     */
    private static final int PLAYING = 0;
    /**
     * A type, race and team value with bits above the low byte, and the low bytes the player keeps of them.
     */
    private static final int WIDE_TYPE = 0x102;
    private static final int WIDE_RACE = 0x201;
    private static final int WIDE_TEAM = 0x303;
    private static final int TYPE_LOW_BYTE = 2;
    private static final int RACE_LOW_BYTE = 1;
    private static final int TEAM_LOW_BYTE = 3;

    private static @NotNull Player createPlayer(int type, int victoryState) {
        Player player = new Player(SLOT, SLOT, type, RACE, TEAM);

        player.setVictoryState(victoryState);

        return player;
    }

    @Test
    void is_finished_should_accept_players_who_left_and_dropped_or_defeated_humans_and_computers() {
        Assertions.assertTrue(createPlayer(PlayerTypes.PLAYER_LEFT, PLAYING).isFinished());
        Assertions.assertTrue(createPlayer(PlayerTypes.COMPUTER_LEFT, PLAYING).isFinished());
        Assertions.assertTrue(createPlayer(PlayerTypes.HUMAN, Player.VICTORY_STATE_DROPPED).isFinished());
        Assertions.assertTrue(createPlayer(PlayerTypes.HUMAN, Player.VICTORY_STATE_DEFEATED).isFinished());
        Assertions.assertTrue(createPlayer(PlayerTypes.COMPUTER, Player.VICTORY_STATE_DEFEATED).isFinished());
        Assertions.assertFalse(createPlayer(PlayerTypes.HUMAN, PLAYING).isFinished());
        Assertions.assertFalse(createPlayer(PlayerTypes.RESCUE_PASSIVE, Player.VICTORY_STATE_DEFEATED).isFinished(),
                               "Only human and computer players finish by their victory state.");
        Assertions.assertFalse(createPlayer(PlayerTypes.NEUTRAL, Player.VICTORY_STATE_DROPPED).isFinished());
    }

    @Test
    void is_active_should_accept_computers_through_rescuable_and_neutral_players_still_in_the_game() {
        Assertions.assertTrue(createPlayer(PlayerTypes.COMPUTER, PLAYING).isActive());
        Assertions.assertTrue(createPlayer(PlayerTypes.HUMAN, PLAYING).isActive());
        Assertions.assertTrue(createPlayer(PlayerTypes.RESCUE_PASSIVE, Player.VICTORY_STATE_DEFEATED).isActive());
        Assertions.assertTrue(createPlayer(PlayerTypes.RESCUE_ACTIVE, PLAYING).isActive());
        Assertions.assertTrue(createPlayer(PlayerTypes.NEUTRAL, PLAYING).isActive());
        Assertions.assertFalse(createPlayer(PlayerTypes.NONE, PLAYING).isActive());
        Assertions.assertFalse(createPlayer(PlayerTypes.EITHER_PREFER_HUMAN, PLAYING).isActive(), "Open lobby slots take no part.");
        Assertions.assertFalse(createPlayer(PlayerTypes.CLOSED, PLAYING).isActive());
        Assertions.assertFalse(createPlayer(PlayerTypes.PLAYER_LEFT, PLAYING).isActive());
        Assertions.assertFalse(createPlayer(PlayerTypes.COMPUTER, Player.VICTORY_STATE_DEFEATED).isActive(),
                               "A defeated computer is out of the game.");
        Assertions.assertFalse(createPlayer(PlayerTypes.HUMAN, Player.VICTORY_STATE_DROPPED).isActive());
    }

    @Test
    void player_keeps_the_low_bytes_of_type_race_and_team() {
        Player player = new Player(SLOT, SLOT, WIDE_TYPE, WIDE_RACE, WIDE_TEAM);

        Assertions.assertEquals(TYPE_LOW_BYTE, player.getType());
        Assertions.assertEquals(RACE_LOW_BYTE, player.getRace());
        Assertions.assertEquals(TEAM_LOW_BYTE, player.getTeam());

        player.setType(WIDE_TYPE);

        Assertions.assertEquals(TYPE_LOW_BYTE, player.getType());
    }
}
