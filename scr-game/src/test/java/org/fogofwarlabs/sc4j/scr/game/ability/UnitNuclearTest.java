package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayGameVersion;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameRulesVersion;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.io.IOException;

/**
 * Verifies Nuclear Silo storage, the {@link ScrOrderIds#NUKE_LAUNCH} arrival timing gate ({@link GameRulesVersion#detonatesOnArrival}) in a running
 * game and the automatic cloaking of computer units approaching a nuclear strike target.
 */
@RequiresInstallation
final class UnitNuclearTest {
    private static final int UNIT_POSITION_X = 1200;
    private static final int UNIT_POSITION_Y = 1200;
    private static final int ENERGY_FRACTION_BITS = 8;
    private static final int INITIAL_ENERGY = 200 << ENERGY_FRACTION_BITS;
    private static final int APPROACH_SIGHT_RANGES = 2;
    private static final int OUTSIDE_CLOAK_SIGHT_RANGES = 4;
    private static final int CLOAK_ORDER = 109;
    private static final int GUARD_KIND = 1;

    private static final int GHOST_X = 400;
    private static final int LEGACY_GHOST_X = 700;
    private static final int GHOST_Y = 400;
    private static final int MISSILE_Y = 440;
    private static final int FIXED_POINT_SHIFT = 8;
    private static final int LAUNCH_STATE_DESCENDING = 5;
    private static final int LAUNCH_STATE_DETONATING = 6;
    private static final int DIE_ORDER = 0;
    private static final int NUKE_LAUNCH_ORDER = 125;

    private static final int TRAIN_STATE_WAITING = 0;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }

        gameData = null;
    }

    /**
     * Creates a Ghost and its Nuclear Missile below it, the missile descending in its launch order at its movement destination with the action-done
     * signal set.
     *
     * @param game      the running game
     * @param positionX the horizontal position of both units
     * @return the created missile
     */
    private static @NotNull Unit createDescendingMissile(@NotNull Game game, int positionX) {
        Unit ghost = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_GHOST, positionX, GHOST_Y, RunningGameScenario.HUMAN);
        Unit missile = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_NUCLEAR_MISSILE, positionX, MISSILE_Y, RunningGameScenario.HUMAN);

        missile.setParentUnit(ghost);
        missile.setPrimaryOrder(ScrOrderIds.NUKE_LAUNCH);
        missile.setPrimaryOrderState(LAUNCH_STATE_DESCENDING);
        missile.setMovementDestinationX(missile.getFixedPositionX() >> FIXED_POINT_SHIFT);
        missile.setMovementDestinationY(missile.getFixedPositionY() >> FIXED_POINT_SHIFT);
        missile.setOrderSignal(missile.getOrderSignal() | Unit.ORDER_SIGNAL_ACTION_DONE);

        return missile;
    }


    private @Nullable ReplayRuntime runtime;

    @BeforeEach
    void startGame() throws IOException {
        Assertions.assertNotNull(gameData);

        this.runtime = RunningGameScenario.start(gameData);
    }

    @AfterEach
    void closeGame() {
        this.runtime = null;
    }

    private @NotNull Unit createApproachingUnit(@NotNull Game game, int type, boolean guard, int sightRanges) {
        Unit unit = RunningGameScenario.createUnit(game, type, UNIT_POSITION_X, UNIT_POSITION_Y, RunningGameScenario.COMPUTER);
        ComputerPlayerPhase phase = game.getComputerPlayerPhase();
        PlayerTechnology technology = game.getPlayerTable().getPlayerTechnology();

        Assertions.assertNotNull(phase);
        Assertions.assertNotNull(technology);
        Assertions.assertNull(unit.getComputerRecord());

        if (guard) {
            phase.registerPreplacedUnit(unit);

            ComputerUnitRecord record = unit.getComputerRecord();

            Assertions.assertNotNull(record);
            Assertions.assertEquals(GUARD_KIND, record.getKind());
        }

        technology.setResearched(RunningGameScenario.COMPUTER, ScrTechIds.CLOAKING_FIELD, true);
        technology.setResearched(RunningGameScenario.COMPUTER, ScrTechIds.PERSONNEL_CLOAKING, false);

        int sight = TerrainTileset.TILE_SIZE * game.getUnitVisibility().calculateSightRange(unit, false);
        int targetX = unit.getPositionX() + sightRanges * sight;

        Assertions.assertTrue(sight > 0);
        Assertions.assertTrue(targetX < game.getMap().getWidth() * TerrainTileset.TILE_SIZE);

        unit.setEnergy(INITIAL_ENERGY);
        unit.setPrimaryOrder(ScrOrderIds.CAST_NUCLEAR_STRIKE);
        unit.setOrderTargetX(targetX);
        unit.setOrderTargetY(unit.getPositionY());
        unit.setMovementDestinationCountdown(0);

        return unit;
    }

    @Test
    void update_train_should_adopt_missile_and_mark_silo_ready_when_missile_completes() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.TERRAN_NUCLEAR_SILO, ScrUnitIds.TERRAN_NUCLEAR_MISSILE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit silo = pool.getUnit(TwoUnitGameScenario.CASTER_INDEX);
        Unit missile = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);

        missile.setStatus(missile.getStatus() & ~UnitStatusFlags.COMPLETED);
        silo.setPrimaryOrderState(TRAIN_STATE_WAITING);
        silo.setCurrentBuildUnit(missile);

        game.getUnitNuclear().updateTrain(silo, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertSame(missile, silo.getSiloNuke());
        Assertions.assertSame(silo, missile.getParentUnit());
        Assertions.assertFalse(silo.isSiloReady());

        missile.setStatus(missile.getStatus() | UnitStatusFlags.COMPLETED);

        game.getUnitNuclear().updateTrain(silo, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertTrue(silo.isSiloReady());
        Assertions.assertEquals(TRAIN_STATE_WAITING, silo.getPrimaryOrderState());

        UnitNuclear.cancel(game.requireGameData().getUnitTypes(), missile);

        Assertions.assertEquals(TRAIN_STATE_WAITING, silo.getPrimaryOrderState());
    }

    @Test
    void update_launch_should_detonate_missile_on_arrival_frame_except_in_legacy_replays() {
        Assertions.assertNotNull(this.runtime);

        Game game = this.runtime.getGame();
        GameRulesVersion original = game.getRulesVersion();
        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            Assertions.assertTrue(original.detonatesOnArrival(), "re_vs_com is an SC:R replay.");

            Unit missile = createDescendingMissile(game, GHOST_X);

            game.getUnitNuclear().updateLaunch(missile, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(DIE_ORDER, missile.getPrimaryOrder(), "SC:R falls through into the detonation test.");

            game.setRulesVersion(GameRulesVersion.forReplay(ReplayGameVersion.absent(), true));

            Unit legacyMissile = createDescendingMissile(game, LEGACY_GHOST_X);

            game.getUnitNuclear().updateLaunch(legacyMissile, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(NUKE_LAUNCH_ORDER, legacyMissile.getPrimaryOrder(), "A legacy replay tests the signal on the next frame.");
            Assertions.assertEquals(LAUNCH_STATE_DETONATING, legacyMissile.getPrimaryOrderState());

            game.getUnitNuclear().updateLaunch(legacyMissile, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(DIE_ORDER, legacyMissile.getPrimaryOrder());
        } finally {
            game.setRulesVersion(original);
            scope.endCommandDispatch();
        }
    }

    @Test
    void update_cast_nuclear_strike_should_use_the_wraiths_cloaking_field() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = this.createApproachingUnit(game, ScrUnitIds.TERRAN_WRAITH, true, APPROACH_SIGHT_RANGES);

            Assertions.assertTrue(game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.CLOAKING_FIELD));
            Assertions.assertFalse(game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.PERSONNEL_CLOAKING));

            int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.CLOAKING_FIELD) << ENERGY_FRACTION_BITS;
            int targetX = unit.getOrderTargetX();
            int targetY = unit.getOrderTargetY();

            game.getUnitNuclear().updateCastNuclearStrike(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(CLOAK_ORDER, unit.getSecondaryOrder());
            Assertions.assertEquals(INITIAL_ENERGY - cost, unit.getEnergy());
            Assertions.assertEquals(targetX, unit.getDirectionTargetX());
            Assertions.assertEquals(targetY, unit.getDirectionTargetY());
            Assertions.assertEquals(targetX, unit.getMovementDestinationX());
            Assertions.assertEquals(targetY, unit.getMovementDestinationY());
        });
    }

    @Test
    void update_cast_nuclear_strike_should_keep_energy_without_a_computer_record() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = this.createApproachingUnit(game, ScrUnitIds.TERRAN_WRAITH, false, APPROACH_SIGHT_RANGES);

            Assertions.assertNull(unit.getComputerRecord());

            game.getUnitNuclear().updateCastNuclearStrike(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
            Assertions.assertEquals(unit.getOrderTargetX(), unit.getDirectionTargetX());
            Assertions.assertEquals(unit.getOrderTargetY(), unit.getDirectionTargetY());
        });
    }

    @Test
    void update_cast_nuclear_strike_should_keep_energy_when_a_detector_is_already_required() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = this.createApproachingUnit(game, ScrUnitIds.TERRAN_WRAITH, true, APPROACH_SIGHT_RANGES);

            unit.setStatus(unit.getStatus() | UnitStatusFlags.REQUIRES_DETECTOR);

            game.getUnitNuclear().updateCastNuclearStrike(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
        });
    }

    @Test
    void update_cast_nuclear_strike_should_keep_energy_outside_three_sight_ranges() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = this.createApproachingUnit(game, ScrUnitIds.TERRAN_WRAITH, true, OUTSIDE_CLOAK_SIGHT_RANGES);

            game.getUnitNuclear().updateCastNuclearStrike(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
            Assertions.assertEquals(unit.getOrderTargetX(), unit.getMovementDestinationX());
            Assertions.assertEquals(unit.getOrderTargetY(), unit.getMovementDestinationY());
        });
    }

    @Test
    void update_cast_nuclear_strike_should_skip_a_unit_without_cloaking_technology() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = this.createApproachingUnit(game, ScrUnitIds.TERRAN_MARINE, true, APPROACH_SIGHT_RANGES);

            game.getUnitNuclear().updateCastNuclearStrike(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
            Assertions.assertEquals(unit.getOrderTargetX(), unit.getDirectionTargetX());
            Assertions.assertEquals(unit.getOrderTargetY(), unit.getDirectionTargetY());
        });
    }
}
