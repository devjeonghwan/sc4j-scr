package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;

/**
 * Seeds a unit spatial index with a captured native unit-finder table through {@link UnitSpatialIndex#insert}.
 */
public final class UnitSpatialIndexScenario {
    private static final int AXIS_COUNT = 2;
    private static final int EDGES_PER_UNIT = 2;
    private static final int HORIZONTAL_AXIS = 0;
    private static final int VERTICAL_AXIS = 1;

    /**
     * Reads the captured edges of both axes, horizontal first, as pairs of one-based unit index and coordinate DWORDs. Each
     * unit is inserted with a unit type whose extents span its captured box and a sprite at the matching position, and the
     * index must then hold the captured edges in order. Afterwards every unit has its former unit type and sprite again.
     *
     * @param index     the empty spatial index
     * @param pool      the pool whose first {@code unitCount} slots are allocated
     * @param units     the unit type table
     * @param records   the captured table, little-endian, at the first horizontal edge; it advances past the vertical edges
     * @param unitCount the number of indexed units
     */
    public static void seed(@NotNull UnitSpatialIndex index, @NotNull UnitPool pool, @NotNull ScrUnitTypeView units,
                            @NotNull ByteBuffer records, int unitCount) {
        int edgeCount = unitCount * EDGES_PER_UNIT;
        int[][] unitIndices = new int[AXIS_COUNT][edgeCount];
        int[][] coordinates = new int[AXIS_COUNT][edgeCount];
        int[][] minimums = new int[AXIS_COUNT][unitCount + 1];
        int[][] maximums = new int[AXIS_COUNT][unitCount + 1];

        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            boolean[] encountered = new boolean[unitCount + 1];

            for (int edge = 0; edge < edgeCount; edge++) {
                int unitIndex = records.getInt();
                int coordinate = records.getInt();

                unitIndices[axis][edge] = unitIndex;
                coordinates[axis][edge] = coordinate;

                if (encountered[unitIndex]) {
                    maximums[axis][unitIndex] = coordinate;
                } else {
                    minimums[axis][unitIndex] = coordinate;
                }

                encountered[unitIndex] = true;
            }
        }

        for (int unitIndex = 1; unitIndex <= unitCount; unitIndex++) {
            Unit unit = pool.getUnit(unitIndex);
            int unitType = findSpanningType(units, maximums[HORIZONTAL_AXIS][unitIndex] - minimums[HORIZONTAL_AXIS][unitIndex],
                                            maximums[VERTICAL_AXIS][unitIndex] - minimums[VERTICAL_AXIS][unitIndex]);
            int formerType = unit.getUnitId();
            Sprite formerSprite = unit.getSpriteOrNull();

            unit.setUnitId(unitType);
            unit.setSprite(SpriteScenario.createSprite(0,
                                                       minimums[HORIZONTAL_AXIS][unitIndex] + (short) units.getDimensionLeft(unitType),
                                                       minimums[VERTICAL_AXIS][unitIndex] + (short) units.getDimensionUp(unitType),
                                                       0));
            index.insert(unit, units);
            unit.setUnitId(formerType);
            unit.setSprite(formerSprite);
        }

        Assertions.assertEquals(edgeCount, index.getCount());

        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            for (int edge = 0; edge < edgeCount; edge++) {
                Assertions.assertEquals(unitIndices[axis][edge], index.getUnitIndex(axis == HORIZONTAL_AXIS, edge),
                                        "unit of edge " + axis + ":" + edge);
                Assertions.assertEquals(coordinates[axis][edge], index.getCoordinate(axis == HORIZONTAL_AXIS, edge),
                                        "coordinate of edge " + axis + ":" + edge);
            }
        }
    }

    private static int findSpanningType(@NotNull ScrUnitTypeView units, int width, int height) {
        for (int unitType = 0; unitType < ScrUnitIds.NONE; unitType++) {
            if ((units.getFlags(unitType) & UnitTypeFlags.SUBUNIT) == 0 &&
                (short) units.getDimensionLeft(unitType) + (short) units.getDimensionRight(unitType) == width &&
                (short) units.getDimensionUp(unitType) + (short) units.getDimensionDown(unitType) == height) {
                return unitType;
            }
        }

        throw Exceptions.newIllegalArgumentException("No unit type spans a captured box of {0} by {1}.", width, height);
    }


    private UnitSpatialIndexScenario() {
    }
}
