package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitSystem;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the unit query buffer of the computer rectangle and region queries.
 */
final class ComputerTerrainTest {
    /**
     * The unit capacity of the extended limits.
     */
    private static final int EXTENDED_UNIT_CAPACITY = 3400;
    /**
     * The units inside the query rectangle, two more than the classic unit capacity.
     */
    private static final int QUERIED_UNIT_COUNT = 1702;
    /**
     * The extents of one unit type: left, up, right and down.
     */
    private static final int UNIT_EXTENT_COUNT = 4;
    /**
     * The bytes of one unit type in the units.dat columns of the fixture: the special ability flags and the extents.
     */
    private static final int UNIT_TYPE_RECORD_SIZE = Integer.BYTES + UNIT_EXTENT_COUNT * Short.BYTES;
    /**
     * The horizontal and vertical position of every queried unit.
     */
    private static final int UNIT_POSITION = 50;
    /**
     * The right and lower bound of the query rectangle, whose left and upper bound are {@code 0}.
     */
    private static final int RECTANGLE_BOUND = 100;

    @Test
    void collect_rectangle_units_should_hold_more_units_than_the_classic_unit_capacity() throws IOException {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        UnitSystem unitSystem = game.getUnitSystem();
        ScrUnitTypeView units = UnitsDat.createTypeTable(new DatSchema().fieldU32("Special Ability Flags", UnitsDat.UNIT_COUNT)
                                                                        .fieldU16x4("Unit Size Left", "Unit Size Up", "Unit Size Right",
                                                                                    "Unit Size Down", UnitsDat.UNIT_COUNT)
                                                                        .parse(new byte[UnitsDat.UNIT_COUNT * UNIT_TYPE_RECORD_SIZE]));

        unitSystem.initializeUnitPool(EXTENDED_UNIT_CAPACITY);
        unitSystem.initializeUnitSpatialIndex(units);

        UnitPool pool = unitSystem.getUnitPool();
        UnitSpatialIndex index = unitSystem.getUnitSpatialIndex();

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(index);

        for (int poolIndex = 1; poolIndex <= QUERIED_UNIT_COUNT; poolIndex++) {
            Unit unit = pool.getUnit(poolIndex);

            unit.setUnitId(ScrUnitIds.TERRAN_MARINE);
            unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
            index.insert(unit, units);
        }

        ComputerTerrain terrain = game.getComputerTerrain();
        int count = terrain.collectRectangleUnits(0, 0, RECTANGLE_BOUND, RECTANGLE_BOUND);
        boolean[] found = new boolean[QUERIED_UNIT_COUNT + 1];

        Assertions.assertEquals(QUERIED_UNIT_COUNT, count);

        for (int resultIndex = 0; resultIndex < count; resultIndex++) {
            found[terrain.getRegionUnit(resultIndex)] = true;
        }

        for (int poolIndex = 1; poolIndex <= QUERIED_UNIT_COUNT; poolIndex++) {
            Assertions.assertTrue(found[poolIndex], "Unit " + poolIndex);
        }
    }
}
