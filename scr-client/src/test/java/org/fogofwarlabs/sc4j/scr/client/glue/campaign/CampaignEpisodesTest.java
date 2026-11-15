package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.scr.client.glue.CharacterFile;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.session.setup.CampaignMissions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the campaign episode entries: episode endings, next entries and the scenario lookup by file name.
 */
final class CampaignEpisodesTest {
    private static final int LAST_ORIGINAL_PROTOSS_MISSION = 31;
    private static final int LAST_ORIGINAL_PROTOSS_CINEMATIC = 23;
    private static final int LAST_EXPANSION_TERRAN_MISSION = 48;
    private static final int TERMINATING_MISSION = 0;
    private static final int EXPANSION_ZERG_BRANCH = 53;
    private static final int EXPANSION_ZERG_FIFTH = 58;
    /**
     * The missions whose file names start with {@code zerg04}: the original campaign's first, the Brood War one later.
     */
    private static final int ORIGINAL_ZERG_FOURTH = 15;
    private static final int EXPANSION_ZERG_FOURTH = 52;
    private static final int AFTER_ORIGINAL_ZERG_FOURTH = 16;

    @Test
    void last_episodes_end_with_terminator_and_only_original_campaign_shows_ending() {
        CampaignEpisodes.Entry protoss = CampaignEpisodes.findMissionEntry(false, LAST_ORIGINAL_PROTOSS_MISSION);

        Assertions.assertNotNull(protoss);

        CampaignEpisodes.Entry cinematic = protoss.createNextEntry();

        Assertions.assertNotNull(cinematic);
        Assertions.assertEquals(LAST_ORIGINAL_PROTOSS_CINEMATIC, cinematic.getCinematic());

        CampaignEpisodes.Entry end = cinematic.createNextEntry();

        Assertions.assertNotNull(end);
        Assertions.assertTrue(end.isTerminator());
        Assertions.assertTrue(end.isEnding(), "the original Protoss episode ends the original campaign");
        Assertions.assertNull(end.createNextEntry());

        CampaignEpisodes.Entry terran = CampaignEpisodes.findMissionEntry(true, LAST_EXPANSION_TERRAN_MISSION);

        Assertions.assertNotNull(terran);

        CampaignEpisodes.Entry terranEnd;

        if (terran.createNextEntry() == null) {
            terranEnd = null;
        } else {
            CampaignEpisodes.Entry nextEntry = terran.createNextEntry();

            Assertions.assertNotNull(nextEntry);

            terranEnd = nextEntry.createNextEntry();
        }

        Assertions.assertNotNull(terranEnd);
        Assertions.assertTrue(terranEnd.isTerminator());
        Assertions.assertFalse(terranEnd.isEnding(), "the Brood War Terran episode returns to the campaign screen");
        Assertions.assertNull(CampaignEpisodes.findMissionEntry(true, TERMINATING_MISSION), "the terminating entry is no mission");
    }

    @RequiresInstallation
    @Test
    void find_scenario_should_find_named_mission_case_insensitively() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            CampaignMissions missions = CampaignMissions.load(storage);
            CampaignEpisodes.Entry branch = CampaignEpisodes.findScenario("zerg04b.scx", missions);

            Assertions.assertNotNull(branch);
            Assertions.assertEquals(EXPANSION_ZERG_BRANCH, branch.getMission());
            Assertions.assertEquals(CharacterFile.Race.ZERG, branch.getProgressRace());

            CampaignEpisodes.Entry upper = CampaignEpisodes.findScenario("ZERG05.SCX", missions);

            Assertions.assertNotNull(upper);
            Assertions.assertEquals(EXPANSION_ZERG_FIFTH, upper.getMission(), "the name compares case-insensitively");
            Assertions.assertNull(CampaignEpisodes.findScenario("zerg04b.scm", missions), ".SCM searches the original episodes only");
            Assertions.assertNull(CampaignEpisodes.findScenario("zerg04b", missions), "a name without an extension keeps the cursor");
            Assertions.assertEquals(ORIGINAL_ZERG_FOURTH, missions.findFileName("zerg04", 0), "the original campaign's zerg04 comes first");
            Assertions.assertEquals(EXPANSION_ZERG_FOURTH, missions.findFileName("zerg04", AFTER_ORIGINAL_ZERG_FOURTH),
                                    "the stem is a prefix of the file name");
        }
    }
}
