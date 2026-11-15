package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPoolScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks occupied bunker space and health-scaled passenger strength.
 */
@RequiresInstallation
final class BunkerStrengthTest {
    /**
     * The unit capacities the scenario runs with: the classic capacity and an extended one.
     */
    private static final int @NotNull [] UNIT_CAPACITIES = {1700, 1750};
    /**
     * The loaded-unit positions of the Bunker slots that the Marine, the Ghost and the Firebat take.
     */
    private static final int @NotNull [] LOADED_POSITIONS = {0, 2, 5};
    /**
     * The shift from a unit handle to its extension bits.
     */
    private static final int HANDLE_EXTENSION_SHIFT = 16;
    /**
     * The rounding added to fixed-point hit points before they are shifted to whole hit points.
     */
    private static final int HIT_POINT_ROUNDING = 0xFF;
    /**
     * The shift from fixed-point hit points to whole hit points.
     */
    private static final int HIT_POINT_SHIFT = 8;
    private static final int WORD_MASK = 0xFFFF;

    private static @NotNull Unit createPassenger(@NotNull UnitPool pool, int unitType) {
        Unit passenger = pool.getUnit(UnitPoolScenario.allocate(pool));

        passenger.setSprite(SpriteScenario.createSprite(0, 0, 0, 0));
        passenger.setUnitId(unitType);
        passenger.setPrimaryOrder(ScrOrderIds.NOTHING);

        return passenger;
    }


    @Test
    void bunker_strength_multiplies_marine_strength_by_occupied_space() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            DatTable units = data.getUnits();
            UnitStrengthTable strengths = new UnitStrengthTable(UnitsDat.createTypeTable(units), data.getWeaponTypes());

            for (int capacity : UNIT_CAPACITIES) {
                Game game = Game.create(data, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

                game.getUnitSystem().initializeUnitPool(capacity);

                UnitPool pool = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(pool);

                Unit bunker = pool.getUnit(UnitPoolScenario.allocate(pool));

                bunker.setUnitId(ScrUnitIds.TERRAN_BUNKER);
                bunker.setHitPoints(units.get("Hit Points", ScrUnitIds.TERRAN_BUNKER));

                Assertions.assertEquals(0, strengths.calculate(game, bunker, 1, UnitsDat.createTypeTable(units)), "An empty Bunker has no strength.");
                Assertions.assertThrows(UnsupportedOperationException.class, () -> strengths.calculate(bunker, 1, UnitsDat.createTypeTable(units)));

                Unit marine = createPassenger(pool, ScrUnitIds.TERRAN_MARINE);
                Unit ghost = createPassenger(pool, ScrUnitIds.TERRAN_GHOST);
                Unit firebat = createPassenger(pool, ScrUnitIds.TERRAN_FIREBAT);
                int slot = 0;
                int extension = 0;

                for (Unit passenger : new Unit[]{marine, ghost, firebat}) {
                    int handle = pool.getHandle(passenger.getIndex());
                    int position = LOADED_POSITIONS[slot];

                    bunker.setLoadedUnitHandle(position, handle);
                    extension |= ((handle >>> HANDLE_EXTENSION_SHIFT) & Unit.LOADED_HANDLE_EXTENSION_MASK) <<
                                 (position * Unit.LOADED_HANDLE_EXTENSION_BITS);

                    slot++;
                }

                bunker.setLoadedUnitHandleExtensionBits(extension);

                int space = units.get("Space Required", ScrUnitIds.TERRAN_MARINE) +
                            units.get("Space Required", ScrUnitIds.TERRAN_GHOST) +
                            units.get("Space Required", ScrUnitIds.TERRAN_FIREBAT);

                Assertions.assertEquals(space, game.getUnitTransport().getOccupiedSpace(bunker));

                for (int direction = 0; direction < UnitStrengthTable.DIRECTION_COUNT; direction++) {
                    Assertions.assertEquals(strengths.getStrength(0, direction) * space,
                                            strengths.calculate(game, bunker, direction, UnitsDat.createTypeTable(units)));
                }

                bunker.setHitPoints(units.get("Hit Points", ScrUnitIds.TERRAN_BUNKER) / 2);

                int maximum = units.get("Hit Points", ScrUnitIds.TERRAN_BUNKER) >> HIT_POINT_SHIFT;
                int current = (units.get("Hit Points", ScrUnitIds.TERRAN_BUNKER) / 2 + HIT_POINT_ROUNDING) >> HIT_POINT_SHIFT;

                Assertions.assertEquals(Integer.divideUnsigned(current * strengths.getStrength(0, 1) * space, maximum),
                                        strengths.calculate(game, bunker, 1, UnitsDat.createTypeTable(units)),
                                        "The Bunker strength keeps the health scaling.");

                firebat.setPrimaryOrder(ScrOrderIds.DIE);
                firebat.setPrimaryOrderState(1);

                Assertions.assertEquals(space - units.get("Space Required", ScrUnitIds.TERRAN_FIREBAT),
                                        game.getUnitTransport().getOccupiedSpace(bunker),
                                        "A dead handle occupies no space.");

                strengths.update(game, bunker, UnitsDat.createTypeTable(units));

                Assertions.assertEquals(strengths.calculate(game, bunker, 0, UnitsDat.createTypeTable(units)) & WORD_MASK,
                                        bunker.getAirStrength() & WORD_MASK);
            }
        }
    }
}
