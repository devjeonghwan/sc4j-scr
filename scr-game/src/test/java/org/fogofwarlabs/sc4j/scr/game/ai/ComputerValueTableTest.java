package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks weapon strength and worker adjustments against hand-evaluated values.
 */
@RequiresInstallation
final class ComputerValueTableTest {
    @Test
    void unit_strength_table_evaluates_marine_and_worker_adjustment() throws Exception {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData data = GameData.load(storage)) {
            UnitStrengthTable values = new UnitStrengthTable(data.getUnitTypes(), data.getWeaponTypes());

            Assertions.assertEquals(100, values.getStrength(ScrUnitIds.TERRAN_MARINE, 1));
            Assertions.assertEquals(23, values.getStrength(ScrUnitIds.TERRAN_SCV, 1));
            Assertions.assertEquals(0, values.getStrength(ScrUnitIds.TERRAN_SCV, 0));
        }
    }
}
