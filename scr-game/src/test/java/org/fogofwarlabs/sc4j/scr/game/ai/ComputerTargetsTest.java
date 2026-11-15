package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitType;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the computer player handles a unit of an added unit type as a unit of its base type: its targets, detectors, workers, unit counts
 * and strengths, and a whole fight against copies of a type.
 */
@RequiresInstallation
final class ComputerTargetsTest {
    /**
     * The offset of the created units below the human player's Command Center.
     */
    private static final int UNIT_OFFSET_Y = 96;
    /**
     * The scripted fight: a wave of units of the human player created below its Command Center attack-moves to the computer's town center and
     * the game runs on for a fixed number of frames.
     */
    private static final int WAVE_SIZE = 8;
    private static final int WAVE_SPACING = 24;
    private static final int FIGHT_FRAMES = 2400;

    private static @Nullable GameData gameData;
    private static int addedMarine;
    private static int addedVessel;
    private static int addedWorker;
    private static int addedFirebat;

    @BeforeAll
    static void loadGameData() throws IOException {
        GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());

        gameData     = installedData;
        addedMarine  = addType(installedData, "Marine copy", ScrUnits.TERRAN_MARINE);
        addedVessel  = addType(installedData, "Science Vessel copy", ScrUnits.TERRAN_SCIENCE_VESSEL);
        addedWorker  = addType(installedData, "SCV copy", ScrUnits.TERRAN_SCV);
        addedFirebat = addType(installedData, "Firebat copy", ScrUnits.TERRAN_FIREBAT);
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        gameData = null;

        if (installedData != null) {
            IgnoreException.execute(
                    installedData::close
            );
        }
    }


    private static int addType(@NotNull GameData installedData, @NotNull String name, @NotNull UnitType baseType) {
        return installedData.getAddedUnitTypes().add(new ScrAddedUnitType.Builder(new UnitType(name), name, baseType, installedData.getUnits()).build());
    }

    private static @NotNull ReplayRuntime start() throws IOException {
        Assertions.assertNotNull(gameData);

        return RunningGameScenario.start(gameData);
    }

    private static @NotNull Unit createBelowCommandCenter(@NotNull Game game, int unitType, int owner) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();

        for (int index = pool.getFirstOwnerIndex(RunningGameScenario.HUMAN); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getUnitId() == ScrUnitIds.TERRAN_COMMAND_CENTER) {
                return RunningGameScenario.createUnit(game, unitType, unit.getPositionX(), unit.getPositionY() + UNIT_OFFSET_Y, owner);
            }
        }

        return Assertions.fail("The human player has a Command Center.");
    }

    private static @NotNull Unit findTownCenter(@NotNull Game game, int owner) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (!unit.isDestroyed() && (units.getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_DEPOT) != 0) {
                return unit;
            }
        }

        return Assertions.fail("The player has a town center.");
    }

    /**
     * Plays the scripted fight with a wave of one unit type and returns, for every frame, the units of every player without their types: pool
     * index, owner, position, hit points, primary order and order target. The computer player decides the same in two fights exactly when the
     * lists are equal.
     */
    private static @NotNull List<String> playFight(int unitType) throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        Unit humanCenter = findTownCenter(game, RunningGameScenario.HUMAN);
        Unit computerCenter = findTownCenter(game, RunningGameScenario.COMPUTER);
        List<String> frames = new ArrayList<>();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            for (int index = 0; index < WAVE_SIZE; index++) {
                Unit unit = RunningGameScenario.createUnit(game, unitType, humanCenter.getPositionX() + index * WAVE_SPACING,
                                                           humanCenter.getPositionY() + UNIT_OFFSET_Y, RunningGameScenario.HUMAN);

                game.getOrders().issue(unit, ScrOrderIds.ATTACK_MOVE, null, computerCenter.getPositionX(), computerCenter.getPositionY(),
                                       ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
            }
        });

        UnitPool pool = game.getUnitSystem().requireUnitPool();

        for (int frame = 0; frame < FIGHT_FRAMES; frame++) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
            Assertions.assertNull(runtime.getFailure());

            StringBuilder units = new StringBuilder();

            for (int owner = 0; owner < PlayerTable.PLAYER_SLOTS; owner++) {
                for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
                    Unit unit = pool.getUnit(index);
                    Unit target = unit.getOrderTargetUnit();

                    units.append(index).append(':').append(owner).append(':').append(unit.getPositionX()).append(',').append(unit.getPositionY())
                         .append(':').append(unit.getHitPoints()).append(':').append(unit.getPrimaryOrder()).append(':')
                         .append(target == null ? 0 : target.getIndex()).append(' ');
                }
            }

            frames.add(units.toString());
        }

        return frames;
    }


    @Test
    void get_base_type_should_return_the_base_type_of_an_added_type_and_every_other_type_itself() throws IOException {
        GameData data = start().getGame().requireGameData();

        Assertions.assertEquals(ScrUnitIds.TERRAN_MARINE, data.getBaseType(addedMarine));
        Assertions.assertEquals(ScrUnitIds.TERRAN_SCIENCE_VESSEL, data.getBaseType(addedVessel));
        Assertions.assertEquals(ScrUnitIds.TERRAN_GOLIATH, data.getBaseType(ScrUnitIds.TERRAN_GOLIATH));
        Assertions.assertEquals(ScrUnitIds.NONE, data.getBaseType(ScrUnitIds.NONE));
        Assertions.assertEquals(addedFirebat + 1, data.getBaseType(addedFirebat + 1));
    }

    @Test
    void select_region_target_should_score_an_enemy_unit_of_an_added_type() throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        Unit[] enemy = new Unit[1];

        RunningGameScenario.runInCommandDispatch(runtime, () -> enemy[0] = createBelowCommandCenter(game, addedMarine, RunningGameScenario.HUMAN));

        int region = game.getComputerTerrain().getUnitRegion(enemy[0]);
        Unit target = game.getComputerTargets().selectRegionTarget(game.requireComputerPlayerPhase(), RunningGameScenario.COMPUTER, region, true);

        Assertions.assertNotNull(target);
        Assertions.assertEquals(region, game.getComputerTerrain().getUnitRegion(target));
    }

    @Test
    void computer_unit_checks_should_read_the_flags_of_an_added_type() throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        Unit[] units = new Unit[2];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            units[0] = createBelowCommandCenter(game, addedVessel, RunningGameScenario.HUMAN);
            units[1] = createBelowCommandCenter(game, addedWorker, RunningGameScenario.HUMAN);
        });

        Assertions.assertTrue(game.getComputerTargets().isActiveDetector(units[0]), "A Science Vessel copy is a detector.");
        Assertions.assertFalse(game.getComputerTargets().isActiveDetector(units[1]));
        Assertions.assertTrue(game.getComputerUnits().isWorker(units[1]), "An SCV copy is a worker.");
        Assertions.assertFalse(game.getComputerUnits().isWorker(units[0]));
    }

    @Test
    void computer_player_counts_units_of_an_added_type_as_units_of_its_base_type() throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        ComputerUnits computerUnits = game.getComputerUnits();
        int marines = computerUnits.getCompletedTypeCount(ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);
        int units = computerUnits.getTypeCount(ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);

        RunningGameScenario.runInCommandDispatch(runtime, () -> createBelowCommandCenter(game, addedMarine, RunningGameScenario.HUMAN));

        Assertions.assertEquals(marines + 1, computerUnits.getCompletedTypeCount(ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN));
        Assertions.assertEquals(units + 1, computerUnits.getTypeCount(ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN));
        Assertions.assertEquals(0, computerUnits.getTypeCount(ScrUnitIds.TERRAN_FIREBAT, RunningGameScenario.HUMAN));
    }

    @Test
    void strength_table_applies_the_rules_of_the_base_type_to_an_added_type() throws IOException {
        UnitStrengthTable strengths = start().getGame().requireUnitStrengthTable();

        for (int direction = 0; direction < UnitStrengthTable.DIRECTION_COUNT; direction++) {
            Assertions.assertEquals(strengths.getStrength(ScrUnitIds.TERRAN_FIREBAT, direction), strengths.getStrength(addedFirebat, direction));
            Assertions.assertEquals(strengths.getStrength(ScrUnitIds.TERRAN_SCV, direction), strengths.getStrength(addedWorker, direction));
            Assertions.assertEquals(strengths.getStrength(ScrUnitIds.TERRAN_MARINE, direction), strengths.getStrength(addedMarine, direction));
        }
    }

    @Test
    void computer_player_fights_copies_of_the_marine_as_it_fights_marines() throws IOException {
        List<String> marines = playFight(ScrUnitIds.TERRAN_MARINE);
        List<String> copies = playFight(addedMarine);

        Assertions.assertEquals(FIGHT_FRAMES, copies.size());

        for (int frame = 0; frame < FIGHT_FRAMES; frame++) {
            Assertions.assertEquals(marines.get(frame), copies.get(frame), "Frame " + frame);
        }

        Assertions.assertNotEquals(marines.getFirst(), marines.getLast());
    }
}
