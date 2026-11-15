package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the transcribed page tables of the hotkey panel: the actions, the units per race and the command icons.
 */
final class HotkeyPanelModelTest {
    private static final int MARINE = 0;
    private static final int INFESTED_COMMAND_CENTER = 130;
    private static final int STIM = 334;
    private static final int ACTION_COUNT = 220;
    private static final int UNIT_COUNT = 88;
    private static final int TERRAN_UNIT_COUNT = 29;
    private static final int PROTOSS_UNIT_COUNT = 28;
    private static final int ZERG_UNIT_COUNT = 31;

    /**
     * Counts the units of a race in the page tables.
     *
     * @param race the race
     * @return the number of units of the race
     */
    private static int countUnits(HotkeyPanelModel.@NotNull Race race) {
        return (int) HotkeyPanelModel.getUnits().stream().filter(unit -> unit.getRace() == race).count();
    }

    @Test
    void panel_model_holds_page_tables() {
        Assertions.assertEquals(ACTION_COUNT, HotkeyPanelModel.getActions().size());
        Assertions.assertEquals(UNIT_COUNT, HotkeyPanelModel.getUnits().size());
        Assertions.assertEquals(TERRAN_UNIT_COUNT, countUnits(HotkeyPanelModel.Race.TERRAN));
        Assertions.assertEquals(PROTOSS_UNIT_COUNT, countUnits(HotkeyPanelModel.Race.PROTOSS));
        Assertions.assertEquals(ZERG_UNIT_COUNT, countUnits(HotkeyPanelModel.Race.ZERG));

        HotkeyPanelModel.Unit last = HotkeyPanelModel.getUnits().getLast();

        Assertions.assertEquals(INFESTED_COMMAND_CENTER, last.getUnitType());
        Assertions.assertEquals("cmdicons.237.png",
                                HotkeyPanelModelCheck.requireAction(STIM).getIcon(HotkeyPanelModelCheck.requireUnitByType(MARINE)));
    }
}
