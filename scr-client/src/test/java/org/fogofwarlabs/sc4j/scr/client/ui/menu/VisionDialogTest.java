package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks that the vision dialog edits the game's replay vision: opening shows it, OK applies it, Cancel keeps it.
 */
final class VisionDialogTest {
    private static final int FIRST_PLAYER = 0;
    private static final int SECOND_PLAYER = 1;
    private static final int SECOND_PLAYER_VISION = 1 << SECOND_PLAYER;
    /**
     * The vision mask of every player slot.
     */
    private static final int ALL_PLAYERS = 0xFF;
    /**
     * The row of the first player in {@link #ROWS}.
     */
    private static final int FIRST_ROW = 0;
    /**
     * The row of the second player in {@link #ROWS}.
     */
    private static final int SECOND_ROW = 1;
    private static final @NotNull List<VisionDialog.Row> ROWS = List.of(new VisionDialog.Row(FIRST_PLAYER, "First"),
                                                                        new VisionDialog.Row(SECOND_PLAYER, "Second"));

    private static @NotNull LocalVision createReplayVision() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.setReplayPlayback(true);
        game.getLocalVision().startReplayOrObserver();

        return game.getLocalVision();
    }

    @Test
    void accept_should_apply_the_checked_players_to_the_game_vision() {
        LocalVision vision = createReplayVision();
        VisionDialog dialog = new VisionDialog();

        dialog.open(ROWS, vision);

        Assertions.assertTrue(dialog.isChecked(FIRST_ROW), "The dialog opens with the replay vision mask.");

        dialog.toggle(FIRST_ROW);
        dialog.accept();

        Assertions.assertEquals(ALL_PLAYERS & ~(1 << FIRST_PLAYER), vision.getReplayMask());
        Assertions.assertEquals(ALL_PLAYERS & ~(1 << FIRST_PLAYER), vision.getComparisonMask());
        Assertions.assertFalse(dialog.isOpen());
    }

    @Test
    void accept_should_reveal_the_map_of_the_replay() {
        LocalVision vision = createReplayVision();
        VisionDialog dialog = new VisionDialog();

        vision.acceptDialog(SECOND_PLAYER_VISION, false);
        dialog.open(ROWS, vision);

        Assertions.assertFalse(dialog.isChecked(FIRST_ROW));
        Assertions.assertFalse(dialog.isRevealChecked());

        dialog.toggleReveal();
        dialog.accept();

        Assertions.assertTrue(vision.isRevealed());
        Assertions.assertEquals(ALL_PLAYERS, vision.getReplayMask());
    }

    @Test
    void cancel_should_keep_the_game_vision() {
        LocalVision vision = createReplayVision();
        VisionDialog dialog = new VisionDialog();

        dialog.open(ROWS, vision);
        dialog.toggle(SECOND_ROW);
        dialog.toggleReveal();
        dialog.cancel();

        Assertions.assertEquals(ALL_PLAYERS, vision.getReplayMask());
        Assertions.assertFalse(vision.isRevealed());
    }
}
