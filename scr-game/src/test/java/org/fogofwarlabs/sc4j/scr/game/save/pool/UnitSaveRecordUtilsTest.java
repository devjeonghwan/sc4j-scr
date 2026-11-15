package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitTraits;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ArrayDatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.save.SaveReferences;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks saved resource and powerup union bytes beyond units.dat; their classic layout is the save-file contract.
 */
final class UnitSaveRecordUtilsTest {
    private static final int RESOURCE_AMOUNT_OFFSET = 0xD0;
    private static final int RESOURCE_AREA_OFFSET = 0xD8;

    private static final int RESOURCE_AMOUNT = 1234;
    private static final int RESOURCE_ANIMATION = 5;
    private static final int RESOURCE_BUSY_BYTE = 7;
    private static final int POWERUP_VERTICAL_ORIGIN = 4321;
    private static final int RESOURCE_AREA = 9;
    private static final int RESOURCE_CLAIM = 11;

    /**
     * The pool index of the unit that the source unit links to; the pool holds the source and this unit.
     */
    private static final int LINKED_UNIT_INDEX = 2;
    private static final int UNIT_POOL_CAPACITY = LINKED_UNIT_INDEX;

    @ParameterizedTest
    @ValueSource(ints = {UnitTypeFlags.POWERUP, UnitTypeFlags.RESOURCE_CONTAINER})
    void write_and_read_should_preserve_resource_and_powerup_union_past_units_dat(int flag) {
        int[] flags = new int[UnitsDat.UNIT_COUNT];
        ScrUnitTypeView units = new ScrUnitTypeView(column -> new ArrayDatColumn(flags), ScrUnitTraits.createColumns(), UnitsDat.REQUIREMENT_INDEX,
                                                    (column, index) -> flag | UnitTypeFlags.WORKER | UnitTypeFlags.BUILDING);
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeSpriteEffectPool(1);
        game.initializeOrderPool(1);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit source = pool.getUnit(1);
        Unit linked = pool.getUnit(LINKED_UNIT_INDEX);

        source.setUnitId(ScrUnitIds.NONE);
        source.setResources(RESOURCE_AMOUNT);
        source.setSecondLarvaSideCount(POWERUP_VERTICAL_ORIGIN);

        if (flag == UnitTypeFlags.RESOURCE_CONTAINER) {
            source.setResourceAnimation(RESOURCE_ANIMATION);
            source.setResourceBeingGatheredByte(RESOURCE_BUSY_BYTE);
        }

        source.setResourceAreaIndex(RESOURCE_AREA);
        source.setResourceClaimByte(RESOURCE_CLAIM);
        source.setPowerupCarrier(linked);
        source.setFirstGatheringWorker(linked);

        ByteBuffer record = ByteBuffer.allocate(UnitSaveRecordUtils.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        SaveReferences references = new SaveReferences(game);

        UnitSaveRecordUtils.write(source, record, units, references);

        Assertions.assertEquals(RESOURCE_AMOUNT, record.getShort(RESOURCE_AMOUNT_OFFSET));

        Unit restored = UnitTestUtils.createUnit(1);

        UnitSaveRecordUtils.read(restored, record, units, null, references);

        Assertions.assertEquals(RESOURCE_AMOUNT, restored.getResources());

        if (flag == UnitTypeFlags.POWERUP) {
            Assertions.assertEquals(POWERUP_VERTICAL_ORIGIN, restored.getSecondLarvaSideCount());
            Assertions.assertSame(linked, restored.getPowerupCarrier());
        } else {
            Assertions.assertEquals(RESOURCE_ANIMATION, restored.getResourceAnimation());
            Assertions.assertEquals(RESOURCE_BUSY_BYTE, restored.getResourceBeingGatheredByte());
            Assertions.assertEquals(RESOURCE_AREA, record.get(RESOURCE_AREA_OFFSET));
            Assertions.assertEquals(RESOURCE_AREA, restored.getResourceAreaIndex());
            Assertions.assertEquals(RESOURCE_CLAIM, restored.getResourceClaimByte());
            Assertions.assertSame(linked, restored.getFirstGatheringWorker());
        }
    }
}
