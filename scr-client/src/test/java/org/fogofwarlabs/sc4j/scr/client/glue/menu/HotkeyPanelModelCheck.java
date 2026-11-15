package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * Looks up the hotkey panel page-table entries that the hotkey tests require.
 */
final class HotkeyPanelModelCheck {
    /**
     * Finds the required unit entry in the page tables.
     *
     * @param unitType the unit type identifier
     * @return the matching unit entry
     * @throws AssertionError if no unit entry matches
     */
    static HotkeyPanelModel.@NotNull Unit requireUnitByType(int unitType) {
        for (HotkeyPanelModel.Unit unit : HotkeyPanelModel.getUnits()) {
            if (unit.getUnitType() == unitType) {
                return unit;
            }
        }

        throw Exceptions.newAssertionError("No unit {0}", unitType);
    }

    /**
     * Finds the required action entry in the action table.
     *
     * @param identifier the action identifier
     * @return the matching action entry
     * @throws AssertionError if no action entry exists
     */
    static HotkeyPanelModel.@NotNull Action requireAction(int identifier) {
        HotkeyPanelModel.Action action = HotkeyPanelModel.getActions().get(identifier);

        Assertions.assertNotNull(action);

        return action;
    }

    private HotkeyPanelModelCheck() {
    }
}
