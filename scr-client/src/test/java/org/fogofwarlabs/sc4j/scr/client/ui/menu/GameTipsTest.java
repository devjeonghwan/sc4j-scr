package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks tip numbering, mission-specific tips and wrapping.
 */
final class GameTipsTest {
    private static final int SECOND_TERRAN_MISSION = 2;
    private static final int FIRST_EXPANSION_MISSION = 32;
    private static final int MISSION_WITHOUT_TIP = 47;
    private static final int STORED_TIP = 5;
    private static final int TIP_AFTER_STORED_TIP = 6;
    /**
     * The tip after the first tip, and the tip of {@link #SECOND_TERRAN_MISSION}.
     */
    private static final int SECOND_TIP = 2;
    private static final int OUT_OF_RANGE_TIP = 0;
    private static final int STORED_MISSION_TIP = 40;
    private static final int TIP_AFTER_STORED_MISSION_TIP = 41;
    private static final int FIRST_EXPANSION_MISSION_TIP = 80;
    /**
     * The tip numbers: the first tip, the last tip of the original game, the second and the last Brood War tip.
     */
    private static final int FIRST_TIP = 1;
    private static final int LAST_ORIGINAL_TIP = 79;
    private static final int SECOND_EXPANSION_TIP = 81;
    private static final int LAST_EXPANSION_TIP = 102;
    private static final int NO_MISSION = 0;
    private static final int NEXT = 1;
    private static final int PREVIOUS = -1;

    @Test
    void open_should_show_tip_after_stored_one_from_help() {
        GameTips tips = new GameTips(false, STORED_TIP, true);

        Assertions.assertEquals(TIP_AFTER_STORED_TIP, tips.open(false, SECOND_TERRAN_MISSION), "the Help page steps by one whatever the mission");
        Assertions.assertEquals(TIP_AFTER_STORED_TIP, tips.getNumber());
    }

    @Test
    void open_should_restart_at_first_tip_when_stored_number_is_out_of_range() {
        Assertions.assertEquals(SECOND_TIP, new GameTips(false, OUT_OF_RANGE_TIP, true).open(false, NO_MISSION), "0 becomes 1, then the step");
        Assertions.assertEquals(SECOND_TIP,
                                new GameTips(false, GameTips.LAST_ORIGINAL_TIP + 1, true).open(false, NO_MISSION),
                                "the original game has 79 tips");
        Assertions.assertEquals(SECOND_EXPANSION_TIP,
                                new GameTips(true, GameTips.LAST_ORIGINAL_TIP + 1, true).open(false, NO_MISSION),
                                "Brood War has 102 tips");
    }

    @Test
    void open_should_show_mission_tip_at_mission_start() {
        Assertions.assertEquals(SECOND_TIP, new GameTips(false, STORED_MISSION_TIP, true).open(true, SECOND_TERRAN_MISSION));
        Assertions.assertEquals(FIRST_EXPANSION_MISSION_TIP, new GameTips(true, STORED_MISSION_TIP, true).open(true, FIRST_EXPANSION_MISSION));
        Assertions.assertEquals(FIRST_TIP,
                                new GameTips(true, STORED_MISSION_TIP, true).open(true, MISSION_WITHOUT_TIP),
                                "0xffff is outside the tips");
        Assertions.assertEquals(TIP_AFTER_STORED_MISSION_TIP,
                                new GameTips(false, STORED_MISSION_TIP, true).open(true, NO_MISSION),
                                "a non-campaign game steps from the stored tip");
    }

    @Test
    void step_should_wrap_around_tip_range() {
        GameTips original = new GameTips(false, GameTips.LAST_ORIGINAL_TIP, true);

        Assertions.assertEquals(FIRST_TIP, original.step(NEXT));
        Assertions.assertEquals(LAST_ORIGINAL_TIP, original.step(PREVIOUS));

        GameTips expansion = new GameTips(true, GameTips.FIRST_TIP, true);

        Assertions.assertEquals(LAST_EXPANSION_TIP, expansion.step(PREVIOUS));
        Assertions.assertEquals(FIRST_TIP, expansion.step(NEXT));
    }
}
