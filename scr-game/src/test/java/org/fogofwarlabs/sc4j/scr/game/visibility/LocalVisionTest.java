package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the vision of the local view: the start and the restart of a replay, OK of the vision dialog, a removed player, the observer slots and the
 * tiles the view hides.
 */
final class LocalVisionTest {
    /**
     * The slot of the second player.
     */
    private static final int SECOND_PLAYER_SLOT = 1;
    /**
     * The slot of the third player.
     */
    private static final int THIRD_PLAYER_SLOT = 2;
    /**
     * The slot of the fourth player.
     */
    private static final int FOURTH_PLAYER_SLOT = 3;
    /**
     * The slot of the fifth player.
     */
    private static final int FIFTH_PLAYER_SLOT = 4;
    /**
     * The mask of the first player.
     */
    private static final int FIRST_PLAYER = 0x01;
    /**
     * The mask of the second player.
     */
    private static final int SECOND_PLAYER = 1 << SECOND_PLAYER_SLOT;
    /**
     * The mask of the third player.
     */
    private static final int THIRD_PLAYER = 1 << THIRD_PLAYER_SLOT;
    /**
     * The mask of the fourth player.
     */
    private static final int FOURTH_PLAYER = 1 << FOURTH_PLAYER_SLOT;
    /**
     * The mask of the fifth player.
     */
    private static final int FIFTH_PLAYER = 1 << FIFTH_PLAYER_SLOT;
    /**
     * The mask of the first two players.
     */
    private static final int FIRST_TWO_PLAYERS = FIRST_PLAYER | SECOND_PLAYER;
    /**
     * The mask of the eight playable players.
     */
    private static final int EVERY_PLAYER = 0xFF;
    /**
     * A tile no player sees: the low byte has a bit for each player that does not see the tile.
     */
    private static final int UNSEEN_TILE = 0xFF;
    /**
     * A tile only the second player sees.
     */
    private static final int SECOND_PLAYER_TILE = UNSEEN_TILE & ~SECOND_PLAYER;
    /**
     * The horizontal pixel position of the created unit.
     */
    private static final int UNIT_X = 3560;
    /**
     * The vertical pixel position of the created unit.
     */
    private static final int UNIT_Y = 2160;
    /**
     * The mask of a player slot that sees no tile of the running game's map.
     */
    private static final int UNSEEING_PLAYER_MASK = 0x80;
    /**
     * The race of the players out of the game.
     */
    private static final int PLAYER_RACE = 0;
    /**
     * The team of the players out of the game.
     */
    private static final int PLAYER_TEAM = 0;
    /**
     * The capacity of the unit pool behind the observer's selection.
     */
    private static final int UNIT_POOL_CAPACITY = 1;
    /**
     * The team size of the observer's selection, which is not in a team game.
     */
    private static final int NO_TEAM_SIZE = 0;
    /**
     * The sprite flag of a shown solid selection circle.
     */
    private static final int SELECTION_CIRCLE_FLAG = 0x01;

    private static @NotNull Game createGame(boolean replay) {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.setReplayPlayback(replay);

        return game;
    }

    /**
     * Fills the second, third and fourth slots with players out of the game: one who left, a defeated human and a dropped computer.
     *
     * @param game the game whose player table receives the players
     */
    private static void addFinishedPlayers(@NotNull Game game) {
        Player left = new Player(SECOND_PLAYER_SLOT, SECOND_PLAYER_SLOT, PlayerTypes.PLAYER_LEFT, PLAYER_RACE, PLAYER_TEAM);
        Player defeated = new Player(THIRD_PLAYER_SLOT, THIRD_PLAYER_SLOT, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM);
        Player dropped = new Player(FOURTH_PLAYER_SLOT, FOURTH_PLAYER_SLOT, PlayerTypes.COMPUTER, PLAYER_RACE, PLAYER_TEAM);

        defeated.setVictoryState(Player.VICTORY_STATE_DEFEATED);
        dropped.setVictoryState(Player.VICTORY_STATE_DROPPED);
        game.getPlayerTable().setPlayer(SECOND_PLAYER_SLOT, left);
        game.getPlayerTable().setPlayer(THIRD_PLAYER_SLOT, defeated);
        game.getPlayerTable().setPlayer(FOURTH_PLAYER_SLOT, dropped);
    }

    @Test
    void start_replay_or_observer_should_show_every_player_without_revealing_the_map() {
        Game game = createGame(true);
        LocalVision vision = game.getLocalVision();

        vision.acceptDialog(FIRST_PLAYER, true);
        vision.startReplayOrObserver();

        Assertions.assertEquals(EVERY_PLAYER, vision.getReplayMask());
        Assertions.assertEquals(EVERY_PLAYER, vision.getComparisonMask());
        Assertions.assertFalse(vision.isRevealed());
    }

    @Test
    void accept_dialog_should_reveal_the_map_with_every_player_in_a_replay() {
        LocalVision vision = createGame(true).getLocalVision();

        vision.acceptDialog(FIRST_PLAYER, true);

        Assertions.assertTrue(vision.isRevealed());
        Assertions.assertEquals(EVERY_PLAYER, vision.getReplayMask());
        Assertions.assertEquals(EVERY_PLAYER, vision.getComparisonMask());

        vision.acceptDialog(FIRST_TWO_PLAYERS, false);

        Assertions.assertFalse(vision.isRevealed(), "An unchecked box ends the reveal.");
        Assertions.assertEquals(FIRST_TWO_PLAYERS, vision.getReplayMask());
        Assertions.assertEquals(FIRST_TWO_PLAYERS, vision.getComparisonMask());
    }

    @Test
    void accept_dialog_should_not_reveal_the_map_outside_a_replay() {
        LocalVision vision = createGame(false).getLocalVision();

        vision.acceptDialog(FIRST_PLAYER, true);

        Assertions.assertFalse(vision.isRevealed());
        Assertions.assertEquals(FIRST_PLAYER, vision.getReplayMask());
        Assertions.assertEquals(FIRST_PLAYER, vision.getComparisonMask());
    }

    @Test
    void accept_dialog_should_take_the_players_out_of_the_game_out_of_a_replay_vision() {
        Game replay = createGame(true);
        Game game = createGame(false);
        int remaining = EVERY_PLAYER & ~(SECOND_PLAYER | THIRD_PLAYER | FOURTH_PLAYER);

        addFinishedPlayers(replay);
        addFinishedPlayers(game);
        replay.getLocalVision().acceptDialog(LocalVision.ALL_PLAYERS, false);
        game.getLocalVision().acceptDialog(LocalVision.ALL_PLAYERS, false);

        Assertions.assertEquals(remaining, replay.getLocalVision().getReplayMask());
        Assertions.assertEquals(remaining, replay.getLocalVision().getComparisonMask());
        Assertions.assertEquals(EVERY_PLAYER, game.getLocalVision().getComparisonMask(), "A game keeps the checked players.");

        replay.getLocalVision().acceptDialog(FIRST_PLAYER, true);

        Assertions.assertEquals(remaining, replay.getLocalVision().getReplayMask(), "A revealed map shows no player out of the game.");
        Assertions.assertTrue(replay.getLocalVision().isRevealed());
    }

    @Test
    @RequiresInstallation
    void accept_dialog_should_update_the_sprite_visibility_of_every_unit_in_a_replay() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(gameData);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, UNIT_X, UNIT_Y, RunningGameScenario.COMPUTER);
                Sprite sprite = marine.getSpriteOrNull();

                Assertions.assertNotNull(sprite);

                sprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
                sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTION_CIRCLE);
                game.getLocalVision().acceptDialog(UNSEEING_PLAYER_MASK, false);

                Assertions.assertEquals(0, sprite.getVisibilityFlags() & UNSEEING_PLAYER_MASK, "OK updates the visibility of the unit at once.");
                Assertions.assertEquals(0, sprite.getFlags() & SELECTION_CIRCLE_FLAG, "A unit the new vision loses hides its circle.");
            });
        }
    }

    @Test
    void restart_replay_should_hide_again_only_the_players_the_dialog_unchecked() {
        Game previousGame = createGame(true);
        LocalVision previous = previousGame.getLocalVision();
        LocalVision restarted = createGame(true).getLocalVision();

        addFinishedPlayers(previousGame);
        previous.startReplayOrObserver();
        previous.acceptDialog(LocalVision.ALL_PLAYERS & ~FIRST_PLAYER, false);
        previous.acceptDialog(LocalVision.ALL_PLAYERS & ~FIFTH_PLAYER, false);
        restarted.startReplayOrObserver();
        restarted.restartReplay(previous);

        Assertions.assertEquals(EVERY_PLAYER & ~FIFTH_PLAYER, restarted.getReplayMask(),
                                "A checked player and the players out of the game come back, the unchecked player stays hidden.");
        Assertions.assertEquals(EVERY_PLAYER & ~FIFTH_PLAYER, restarted.getComparisonMask());
        Assertions.assertFalse(restarted.isRevealed());
    }

    @Test
    void restart_replay_should_keep_the_reveal() {
        LocalVision previous = createGame(true).getLocalVision();
        LocalVision restarted = createGame(true).getLocalVision();

        previous.startReplayOrObserver();
        previous.acceptDialog(FIRST_PLAYER, false);
        previous.acceptDialog(FIRST_PLAYER, true);
        restarted.startReplayOrObserver();
        restarted.restartReplay(previous);

        Assertions.assertTrue(restarted.isRevealed());
        Assertions.assertEquals(EVERY_PLAYER, restarted.getReplayMask(), "The reveal forgets the unchecked players.");
        Assertions.assertEquals(EVERY_PLAYER, restarted.getComparisonMask());
    }

    @Test
    void remove_player_should_clear_its_bit_only_in_a_replay_or_observer_view() {
        LocalVision replay = createGame(true).getLocalVision();
        LocalVision game = createGame(false).getLocalVision();

        replay.startReplayOrObserver();
        replay.removePlayer(SECOND_PLAYER_SLOT);
        game.setComparisonMask(SECOND_PLAYER);
        game.removePlayer(SECOND_PLAYER_SLOT);

        Assertions.assertEquals(EVERY_PLAYER & ~SECOND_PLAYER, replay.getReplayMask());
        Assertions.assertEquals(EVERY_PLAYER & ~SECOND_PLAYER, replay.getComparisonMask());
        Assertions.assertEquals(SECOND_PLAYER, game.getComparisonMask());
    }

    @Test
    void an_observer_slot_sees_by_the_replay_rule() {
        Game game = createGame(false);
        UnitPool unitPool = new UnitPool(UNIT_POOL_CAPACITY);

        game.setSelection(new UnitSelection(unitPool, true, NO_TEAM_SIZE, PlayerTable.FIRST_OBSERVER_SLOT));

        Assertions.assertTrue(game.getLocalVision().isObserver());
        Assertions.assertTrue(game.getLocalVision().isReplayOrObserver());
        Assertions.assertFalse(createGame(false).getLocalVision().isReplayOrObserver());
    }

    @Test
    void is_tile_hidden_should_follow_the_replay_vision_and_the_reveal_in_a_replay() {
        LocalVision vision = createGame(true).getLocalVision();

        vision.acceptDialog(FIRST_PLAYER, false);

        Assertions.assertTrue(vision.isTileHidden(SECOND_PLAYER_TILE), "Only a player outside the replay vision sees the tile.");

        vision.acceptDialog(SECOND_PLAYER, false);

        Assertions.assertFalse(vision.isTileHidden(SECOND_PLAYER_TILE));
        Assertions.assertEquals(SECOND_PLAYER, vision.getViewMask());

        vision.acceptDialog(FIRST_PLAYER, true);

        Assertions.assertFalse(vision.isTileHidden(UNSEEN_TILE), "A revealed map hides no tile.");
        Assertions.assertTrue(vision.isTileUnseen(UNSEEN_TILE), "The reveal does not make a tile seen.");
    }

    @Test
    void is_tile_hidden_should_follow_the_comparison_mask_in_a_game() {
        LocalVision vision = createGame(false).getLocalVision();

        vision.setComparisonMask(SECOND_PLAYER);

        Assertions.assertFalse(vision.isTileHidden(SECOND_PLAYER_TILE));
        Assertions.assertTrue(vision.isTileHidden(UNSEEN_TILE));
        Assertions.assertEquals(SECOND_PLAYER, vision.getViewMask());
    }
}
