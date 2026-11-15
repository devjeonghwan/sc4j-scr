package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Gives the units of the computer tests a computer unit record.
 */
final class ComputerUnitRecordScenario {
    /**
     * Sets a military {@link ComputerUnitRecord} of the unit's pool index on a unit that has no computer record.
     *
     * @param unit the unit
     */
    static void ensureMilitaryRecord(@NotNull Unit unit) {
        if (unit.getComputerRecord() == null) {
            unit.setComputerRecord(new ComputerUnitRecord(ComputerUnitRecord.KIND_MILITARY, unit.getIndex()));
        }
    }


    /**
     * Creates a {@link ComputerUnitRecordScenario} instance.
     */
    private ComputerUnitRecordScenario() {
    }
}
