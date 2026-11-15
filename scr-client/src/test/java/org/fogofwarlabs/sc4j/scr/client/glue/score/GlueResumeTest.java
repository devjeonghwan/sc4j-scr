package org.fogofwarlabs.sc4j.scr.client.glue.score;

import org.fogofwarlabs.sc4j.scr.client.glue.GlueGameRequest;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueNavigator;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Checks the glue continuation after a game: the next scenario of a custom game, the campaign-screen fallback and the
 * main menu after a game that a fatal notice ended.
 */
final class GlueResumeTest {
    private static final @NotNull GameTemplate USE_MAP_SETTINGS = GameTemplate.USE_MAP_SETTINGS;
    private static final @NotNull GameTemplate MELEE = GameTemplate.MELEE;
    private static final int TERRAN = 1;
    private static final int CAMPAIGN_MISSION = 0;
    private static final @NotNull String PLAYER_NAME = "player";
    private static final @NotNull Path MAP = Path.of("maps", "custom", "chain1.scx");
    private static final @NotNull Path FOLDERLESS_MAP = Path.of("solo.scm");
    private static final @NotNull String NEXT_MAP_NAME = "chain2.scx";
    private static final @NotNull String OTHER_MAP_NAME = "x.scm";
    private static final @NotNull Path REPLAY = Path.of("replays", "LastReplay.rep");

    @Test
    void create_after_fatal_notice_should_show_the_main_menu_after_a_game_without_network() {
        GlueGameRequest custom = GlueGameRequest.createCustom(MAP, PLAYER_NAME, true, USE_MAP_SETTINGS, TERRAN, new int[0]);
        GlueResume afterCustom = GlueResume.createAfterFatalNotice(custom);
        GlueResume afterReplay = GlueResume.createAfterFatalNotice(GlueGameRequest.createReplay(REPLAY, PLAYER_NAME, true));
        GlueResume afterLaunch = GlueResume.createAfterFatalNotice(null);

        Assertions.assertEquals(GlueNavigator.Destination.MAIN_MENU, afterCustom.getDestination());
        Assertions.assertEquals(GlueNavigator.Destination.MAIN_MENU, afterReplay.getDestination());
        Assertions.assertEquals(GlueNavigator.Destination.MAIN_MENU, afterLaunch.getDestination());
        Assertions.assertNull(afterCustom.getScore(), "no score screen");
    }

    @Test
    void get_next_scenario_should_load_named_file_next_to_map_with_same_setup() {
        GlueGameRequest played = GlueGameRequest.createCustom(MAP, PLAYER_NAME, true, USE_MAP_SETTINGS, TERRAN, new int[0]);
        GlueResume resume = GlueResume.createAfterGame(played, true).withNextScenario(NEXT_MAP_NAME);
        GlueGameRequest next = resume.getNextScenario();

        Assertions.assertNotNull(next);
        Assertions.assertEquals(MAP.resolveSibling(NEXT_MAP_NAME), next.getPath());
        Assertions.assertEquals(GlueGameRequest.Kind.USE_MAP_SETTINGS, next.getKind());
        Assertions.assertEquals(TERRAN, next.getRace());
        Assertions.assertSame(USE_MAP_SETTINGS, next.getTemplate());
    }

    @Test
    void get_next_scenario_should_continue_won_or_lost_custom_games_only() {
        GlueGameRequest played = GlueGameRequest.createCustom(MAP, PLAYER_NAME, true, MELEE, TERRAN, new int[0]);

        Assertions.assertNotNull(GlueResume.createAfterGame(played, false).withNextScenario(NEXT_MAP_NAME).getNextScenario(),
                                 "A custom game continues with its next scenario whatever the outcome.");
        Assertions.assertNull(GlueResume.createAfterGame(played, true).withNextScenario("").getNextScenario());
        Assertions.assertNull(GlueResume.createAfterGame(played, true).withNextScenario(null).getNextScenario());

        GlueGameRequest campaign = GlueGameRequest.createCampaign(PLAYER_NAME, false, CAMPAIGN_MISSION);

        Assertions.assertNull(GlueResume.createAfterGame(campaign, true).withNextScenario(OTHER_MAP_NAME).getNextScenario());

        GlueGameRequest folderless = GlueGameRequest.createCustom(FOLDERLESS_MAP, PLAYER_NAME, false, MELEE, TERRAN, new int[0]);

        Assertions.assertNull(GlueResume.createAfterGame(folderless, true).withNextScenario(OTHER_MAP_NAME).getNextScenario(),
                              "A map path without a folder has no sibling.");
    }

    @Test
    void custom_game_without_next_scenario_returns_to_campaign_screen() {
        GlueGameRequest played = GlueGameRequest.createCustom(MAP, PLAYER_NAME, true, USE_MAP_SETTINGS, TERRAN, new int[0]);

        Assertions.assertEquals(GlueNavigator.Destination.CAMPAIGN, GlueResume.createAfterGame(played, true).getDestination());
    }
}
