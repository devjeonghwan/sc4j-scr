package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.faction.FactionDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks the SC:R race definitions and the native unit types the race branches read from them.
 */
final class ScrRacesTest {
    @Test
    void race_definitions_name_the_native_worker_town_hall_and_refinery() {
        Assertions.assertEquals(ScrUnitIds.ZERG_DRONE, ScrRaces.getWorkerId(ScrRaceIds.ZERG));
        Assertions.assertEquals(ScrUnitIds.TERRAN_SCV, ScrRaces.getWorkerId(ScrRaceIds.TERRAN));
        Assertions.assertEquals(ScrUnitIds.PROTOSS_PROBE, ScrRaces.getWorkerId(ScrRaceIds.PROTOSS));
        Assertions.assertEquals(ScrUnitIds.ZERG_HATCHERY, ScrRaces.getTownHallId(ScrRaceIds.ZERG));
        Assertions.assertEquals(ScrUnitIds.TERRAN_COMMAND_CENTER, ScrRaces.getTownHallId(ScrRaceIds.TERRAN));
        Assertions.assertEquals(ScrUnitIds.PROTOSS_NEXUS, ScrRaces.getTownHallId(ScrRaceIds.PROTOSS));
        Assertions.assertEquals(ScrUnitIds.ZERG_EXTRACTOR, ScrRaces.getRefineryId(ScrRaceIds.ZERG));
        Assertions.assertEquals(ScrUnitIds.TERRAN_REFINERY, ScrRaces.getRefineryId(ScrRaceIds.TERRAN));
        Assertions.assertEquals(ScrUnitIds.PROTOSS_ASSIMILATOR, ScrRaces.getRefineryId(ScrRaceIds.PROTOSS));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ScrRaces.getWorkerId(ScrRaceIds.INDEPENDENT));
    }

    @Test
    void race_definitions_start_with_the_town_hall_four_workers_and_the_zerg_overlord() {
        FactionDefinition zerg = ScrRaces.getDefinition(ScrRaceIds.ZERG);
        FactionDefinition terran = ScrRaces.getDefinition(ScrRaceIds.TERRAN);

        Assertions.assertEquals(List.of(ScrUnits.ZERG_HATCHERY, ScrUnits.ZERG_DRONE, ScrUnits.ZERG_DRONE, ScrUnits.ZERG_DRONE, ScrUnits.ZERG_DRONE,
                                        ScrUnits.ZERG_OVERLORD), zerg.getStartUnits());
        Assertions.assertEquals(List.of(ScrUnits.TERRAN_COMMAND_CENTER, ScrUnits.TERRAN_SCV, ScrUnits.TERRAN_SCV, ScrUnits.TERRAN_SCV,
                                        ScrUnits.TERRAN_SCV), terran.getStartUnits());
        Assertions.assertSame(ScrUnits.TERRAN_SUPPLY_DEPOT, terran.getSupplyProvider());
        Assertions.assertEquals(List.of(ScrResources.MINERALS, ScrResources.GAS), terran.getResourceKinds());
    }
}
