package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks leaderboard labels and row visibility using the installed data.
 */
final class LeaderboardTest {
    private static final int FASTEST_SPEED = 6;
    private static final int POINTS_BOARD = 0x15;
    private static final int KILLS_BOARD = 0x14;
    private static final int CONTROL_BOARD = 0x11;
    private static final int POINTS_GOAL_BOARD = 0x25;
    private static final int BOARD_LABEL_OFFSET = 0x330;
    private static final int GOAL_LABEL_OFFSET = 0x325;

    @RequiresInstallation
    @Test
    void default_labels_are_stat_text_names_and_board_stays_hidden_until_action() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 "bw-terran-1",
                                                                 "Tester",
                                                                 FASTEST_SPEED,
                                                                 ClientSessionScenario.GAME_SEED,
                                                                 "enUS")) {
            Game game = session.getGame();
            StatText text = StatText.load(session.getStorage(), "enUS");

            // Types 17..21 (Control, Control At Location, Resources, Kills, Points): stat_txt(type + 0x330).
            Assertions.assertEquals("Score", StatText.stripColors(text.getString(new ScrGameView(game), POINTS_BOARD + BOARD_LABEL_OFFSET)).trim());
            Assertions.assertEquals("Kills", StatText.stripColors(text.getString(new ScrGameView(game), KILLS_BOARD + BOARD_LABEL_OFFSET)).trim());
            // Goal types 33..37: stat_txt(type + 0x325).
            Assertions.assertEquals("Score to go", StatText.stripColors(text.getString(new ScrGameView(game), POINTS_GOAL_BOARD + GOAL_LABEL_OFFSET)).trim());
            Assertions.assertEquals("Units", StatText.stripColors(text.getString(new ScrGameView(game), CONTROL_BOARD + BOARD_LABEL_OFFSET)).trim());
            // A single player game always has the dialog; nothing is shown until a leaderboard action ran.
            Assertions.assertTrue(Leaderboard.isCreated(new ScrGameView(game), false));

            Leaderboard leaderboard = new Leaderboard();

            leaderboard.tick();
            leaderboard.update(new ScrGameView(game), new ScrPresentation(game), text);

            Assertions.assertEquals(Leaderboard.Mode.NONE, leaderboard.getMode());
        }
    }

    @Test
    void toggle_all_players_shown_should_alternate_from_all_shown() {
        // Ctrl+R toggles every row; all players are initially shown.
        Leaderboard leaderboard = new Leaderboard();

        Assertions.assertTrue(leaderboard.isAllPlayersShown());

        leaderboard.toggleAllPlayersShown();

        Assertions.assertFalse(leaderboard.isAllPlayersShown());

        leaderboard.toggleAllPlayersShown();

        Assertions.assertTrue(leaderboard.isAllPlayersShown());
    }
}
