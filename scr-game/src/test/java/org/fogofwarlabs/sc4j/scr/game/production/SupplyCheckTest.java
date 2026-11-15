package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the supply that a unit type requires and the supply check of the unit types that ignore it.
 */
@RequiresInstallation
final class SupplyCheckTest {
    /**
     * The Zerglings that hatch from an egg.
     */
    private static final int ZERGLINGS_PER_EGG = 2;
    /**
     * The supply check result of a type that fits.
     */
    private static final int PASSED = 0;
    /**
     * The player of the supply check.
     */
    private static final int PLAYER = 0;
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;


    @Test
    void supply_check_charges_lurker_difference_and_zergling_pair_and_passes_exempt_types() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data.createGameCopy(), new GameMap(1, 1, TILESET, new int[1]), new LcgRandom(0));
            DatTable units = data.getUnits();

            Assertions.assertEquals(units.get("Supply Required", ScrUnitIds.ZERG_LURKER) -
                                    units.get("Supply Required", ScrUnitIds.ZERG_HYDRALISK),
                                    game.getSupplyCheck().getRequiredSupply(ScrUnitIds.ZERG_LURKER));
            Assertions.assertEquals(units.get("Supply Required", ScrUnitIds.ZERG_ZERGLING) * ZERGLINGS_PER_EGG,
                                    game.getSupplyCheck().getRequiredSupply(ScrUnitIds.ZERG_ZERGLING),
                                    "Zerglings hatch two per egg.");
            Assertions.assertEquals(units.get("Supply Required", ScrUnitIds.ZERG_HYDRALISK),
                                    game.getSupplyCheck().getRequiredSupply(ScrUnitIds.ZERG_HYDRALISK));
            Assertions.assertEquals(0, game.getSupplyCheck().getRequiredSupply(ScrUnitIds.ZERG_OVERLORD));

            for (int type = 0; type < UnitsDat.UNIT_COUNT; type++) {
                if ((units.get("Special Ability Flags", type) & UnitTypeFlags.IGNORE_SUPPLY_CHECK) != 0) {
                    Assertions.assertEquals(PASSED, game.getSupplyCheck().check(type, PLAYER), "Type " + type + " ignores the supply check.");
                }
            }
        }
    }
}
