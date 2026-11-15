package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponHitTypes;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.order.UnitOrderEligibility;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies spell target validity, status timers, effects and the rejection of unhandled explosion types.
 */
@RequiresInstallation
final class UnitSpellsTest {
    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int TEXT_MIND_CONTROL_OWN_UNIT = 0x52F;
    private static final int TEXT_INVALID_CONSUME_TARGET = 0x381;
    private static final int TEXT_MUST_TARGET_UNIT = 0x36E;
    private static final int TEXT_CANNOT_TARGET_BUILDING = 0x36D;
    private static final int NO_ERROR_TEXT = -1;
    private static final int PLAGUE_DURATION = 75;
    private static final int PLAGUE_DAMAGE_DIVISOR = 76;
    private static final int STASIS_DURATION = 131;
    private static final int DEFENSIVE_MATRIX_HEALTH = 250;
    private static final int DEFENSIVE_MATRIX_DURATION = 168;
    private static final int WEAK_DEFENSIVE_MATRIX_HEALTH = 2;
    private static final int GAUSS_RIFLE_DAMAGE = 6;
    private static final int FULL_DAMAGE_DIVISOR = 1;
    private static final int MINIMUM_DAMAGE = 1 << (FIXED_POINT_FRACTION_BITS - 1);

    /**
     * The transport position and the position of a passenger moved farther from it than the Irradiate range.
     */
    private static final int TRANSPORT_X = 512;
    private static final int TRANSPORT_Y = 512;
    private static final int DISTANT_PASSENGER_X = 768;

    private static final int SCENARIO_X = 3560;
    private static final int SCENARIO_Y = 2160;
    private static final int DISTANT_TARGET_OFFSET = 96;
    private static final int ADDON_OFFSET = 200;
    private static final int MIND_CONTROL_ENERGY = 200 << FIXED_POINT_FRACTION_BITS;

    /**
     * The offset of the first Broodling candidate from the target on both axes, the expected positions of the two Broodlings at the map edge
     * (the first moved by the placement search, the second four pixels on from it on both axes), and the number of Broodlings.
     */
    private static final int BROODLING_OFFSET = 2;
    private static final @NotNull List<Integer> FIRST_BROODLING_POSITION = List.of(24, 440);
    private static final @NotNull List<Integer> SECOND_BROODLING_POSITION = List.of(28, 444);
    private static final int BROODLING_COUNT = 2;
    /**
     * The search for a walkable stretch at the left map edge: its first and last row distance from the map edges, the step between the
     * tested rows, the width of the stretch and the grid of the tested points in it.
     */
    private static final int EDGE_SEARCH_MARGIN = 192;
    private static final int EDGE_SEARCH_STEP = 32;
    private static final int EDGE_STRETCH_WIDTH = 64;
    private static final int EDGE_STRETCH_GRID = 4;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        runtime = null;

        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }

        gameData = null;
    }

    private static int findWalkableEdgeRow(@NotNull Game game) {
        int height = game.getMap().getHeight() * TerrainTileset.TILE_SIZE;

        for (int row = EDGE_SEARCH_MARGIN; row < height - EDGE_SEARCH_MARGIN; row += EDGE_SEARCH_STEP) {
            if (isWalkableEdgeStretch(game, row)) {
                return row;
            }
        }

        return Assertions.fail("The scenario map has no walkable stretch at its left edge.");
    }

    private static boolean isWalkableEdgeStretch(@NotNull Game game, int row) {
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        int broodling = ScrUnitIds.ZERG_BROODLING;

        for (int offsetY = -EDGE_STRETCH_GRID; offsetY <= EDGE_STRETCH_GRID; offsetY += EDGE_STRETCH_GRID) {
            for (int positionX = units.getDimensionLeft(broodling); positionX <= EDGE_STRETCH_WIDTH; positionX += EDGE_STRETCH_GRID) {
                if (!game.getUnitPositioning().isTerrainPlacementWalkable(broodling, positionX, row + offsetY)) {
                    return false;
                }
            }
        }

        return true;
    }


    @Test
    void technology_target_checks_follow_stasis_owner_and_fogged_type_rules() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.ZERG_SCOURGE, ScrUnitIds.ZERG_ZERGLING);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit caster = pool.getUnit(TwoUnitGameScenario.CASTER_INDEX);
        Unit zergling = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);

        Assertions.assertTrue(game.getUnitSpells().isTechnologyTargetValid(caster, ScrTechIds.CONSUME, zergling));

        zergling.setStasisTimer(1);

        Assertions.assertFalse(game.getUnitSpells().isTechnologyTargetValid(caster, ScrTechIds.CONSUME, zergling));

        zergling.setStasisTimer(0);
        zergling.setOwnerIndex(TwoUnitGameScenario.OTHER_PLAYER);

        Assertions.assertFalse(game.getUnitSpells().isTechnologyTargetValid(caster, ScrTechIds.CONSUME, zergling));
        Assertions.assertTrue(game.getUnitSpells().isTechnologyTargetValid(caster, ScrTechIds.MIND_CONTROL, zergling));
        Assertions.assertTrue(game.getUnitSpells().isTechnologyTargetValid(caster, ScrTechIds.PSIONIC_STORM, zergling),
                              "Psionic Storm is always valid");
        Assertions.assertEquals(TEXT_MIND_CONTROL_OWN_UNIT,
                                game.getUnitSpells().getTechnologyTargetError(caster,
                                                                              ScrTechIds.MIND_CONTROL,
                                                                              caster, ScrUnitIds.NONE,
                                                                              0, 0),
                                "own unit");
        Assertions.assertEquals(TEXT_INVALID_CONSUME_TARGET,
                                game.getUnitSpells().getTechnologyTargetError(caster,
                                                                              ScrTechIds.CONSUME,
                                                                              zergling, ScrUnitIds.NONE,
                                                                              0, 0),
                                "enemy Consume");
        Assertions.assertEquals(TEXT_MUST_TARGET_UNIT,
                                game.getUnitSpells().getTechnologyTargetError(caster, ScrTechIds.CONSUME, null, ScrUnitIds.NONE, 0, 0),
                                "no target");
        Assertions.assertEquals(NO_ERROR_TEXT,
                                game.getUnitSpells().getTechnologyTargetError(caster,
                                                                              ScrTechIds.INFESTATION,
                                                                              null, ScrUnitIds.TERRAN_COMMAND_CENTER,
                                                                              0, 0),
                                "Infestation of a fogged Command Center");
        Assertions.assertEquals(TEXT_CANNOT_TARGET_BUILDING,
                                game.getUnitSpells().getTechnologyTargetError(caster,
                                                                              ScrTechIds.DEFENSIVE_MATRIX,
                                                                              null, ScrUnitIds.TERRAN_MARINE,
                                                                              0, 0),
                                "a fog type rejects Defensive Matrix");
    }

    @Test
    void plague_deals_per_tick_damage_without_killing_target() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.ZERG_SCOURGE, ScrUnitIds.TERRAN_MARINE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit marine = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);
        int hitPoints = marine.getHitPoints();

        game.getUnitSpells().applyPlague(marine, INSTRUCTION_LIMIT);

        Assertions.assertEquals(PLAGUE_DURATION, marine.getPlagueTimer());

        game.getUnitSpells().updateStatusTimers(marine, INSTRUCTION_LIMIT);

        Assertions.assertNotNull(gameData);

        int damage = (gameData.getWeapons().get("Damage Amount", ScrWeaponIds.PLAGUE) << FIXED_POINT_FRACTION_BITS) / PLAGUE_DAMAGE_DIVISOR;

        Assertions.assertEquals(hitPoints - damage, marine.getHitPoints());
        Assertions.assertEquals(PLAGUE_DURATION - 1, marine.getPlagueTimer());

        marine.setHitPoints(damage);
        game.getUnitSpells().updateStatusTimers(marine, INSTRUCTION_LIMIT);

        Assertions.assertEquals(damage, marine.getHitPoints());
    }

    @Test
    void irradiate_of_a_loaded_unit_damages_every_unit_loaded_with_it_and_not_the_container() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);
            Assertions.assertNotNull(gameData);

            Game game = runtime.getGame();
            Unit overlord = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_OVERLORD, TRANSPORT_X, TRANSPORT_Y, RunningGameScenario.COMPUTER);
            Unit zergling = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, TRANSPORT_X, TRANSPORT_Y, RunningGameScenario.COMPUTER);
            Unit hydralisk = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_HYDRALISK, TRANSPORT_X, TRANSPORT_Y, RunningGameScenario.COMPUTER);

            game.getUnitPositioning().move(hydralisk, DISTANT_PASSENGER_X, TRANSPORT_Y);

            game.getUnitTransport().load(overlord, zergling, RunningGameScenario.INSTRUCTION_LIMIT);
            game.getUnitTransport().load(overlord, hydralisk, RunningGameScenario.INSTRUCTION_LIMIT);
            game.getUnitSpells().applyIrradiation(null, RunningGameScenario.HUMAN, zergling, RunningGameScenario.INSTRUCTION_LIMIT);

            int overlordHitPoints = overlord.getHitPoints();
            int zerglingHitPoints = zergling.getHitPoints();
            int hydraliskHitPoints = hydralisk.getHitPoints();
            int damage = (gameData.getWeaponTypes().getDamageAmount(ScrWeaponIds.IRRADIATE) << FIXED_POINT_FRACTION_BITS) /
                         gameData.getWeaponTypes().getCooldown(ScrWeaponIds.IRRADIATE);

            game.getUnitSpells().updateStatusTimers(zergling, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(zerglingHitPoints - damage, zergling.getHitPoints(), "the irradiated passenger");
            Assertions.assertEquals(hydraliskHitPoints - damage, hydralisk.getHitPoints(), "the passenger out of range");
            Assertions.assertEquals(overlordHitPoints, overlord.getHitPoints(), "the container");
        });
    }

    @Test
    void irradiate_of_an_unloaded_unit_checks_the_range_of_a_candidate_with_loaded_status() throws IOException {
        Assertions.assertNotNull(gameData);

        ReplayRuntime scenario = RunningGameScenario.start(gameData);

        RunningGameScenario.runInCommandDispatch(scenario, () -> {
            Game game = scenario.getGame();
            Unit source = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, SCENARIO_X, SCENARIO_Y, RunningGameScenario.COMPUTER);
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.ZERG_HYDRALISK,
                                                         SCENARIO_X + DISTANT_TARGET_OFFSET, SCENARIO_Y,
                                                         RunningGameScenario.COMPUTER);

            game.getUnitPositioning().move(target, source.getPositionX() + DISTANT_TARGET_OFFSET, source.getPositionY());
            target.setStatus(target.getStatus() | UnitStatusFlags.LOADED);
            game.getUnitSpells().applyIrradiation(null, RunningGameScenario.HUMAN, source, RunningGameScenario.INSTRUCTION_LIMIT);

            int targetHitPoints = target.getHitPoints();

            game.getUnitSpells().updateStatusTimers(source, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(targetHitPoints, target.getHitPoints());
        });
    }

    @Test
    void update_mind_control_should_transfer_the_addon_of_a_target_with_grounded_building_status() throws IOException {
        Assertions.assertNotNull(gameData);

        ReplayRuntime scenario = RunningGameScenario.start(gameData);

        RunningGameScenario.runInCommandDispatch(scenario, () -> {
            Game game = scenario.getGame();
            Unit caster = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_DARK_ARCHON, SCENARIO_X, SCENARIO_Y, RunningGameScenario.HUMAN);
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_MARINE,
                                                         SCENARIO_X + DISTANT_TARGET_OFFSET, SCENARIO_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit addon = RunningGameScenario.createUnit(game,
                                                        ScrUnitIds.TERRAN_MACHINE_SHOP,
                                                        SCENARIO_X + ADDON_OFFSET, SCENARIO_Y,
                                                        RunningGameScenario.COMPUTER);

            target.setStatus(target.getStatus() | UnitStatusFlags.GROUNDED_BUILDING);
            target.setAddon(addon);
            target.setResearchType(ScrTechIds.NONE);
            target.setUpgradeType(ScrUpgradeIds.NONE);
            caster.setEnergy(MIND_CONTROL_ENERGY);
            caster.setHeading(FlingyMotionUtils.calculateDirection(target.getPositionX() - caster.getPositionX(),
                                                                   target.getPositionY() - caster.getPositionY()));
            target.requireSprite().setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
            caster.setPrimaryOrder(ScrOrderIds.CAST_MIND_CONTROL);
            caster.setOrderTargetUnit(target);

            game.getUnitSpells().updateMindControl(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(RunningGameScenario.HUMAN, target.getOwnerIndex());
            Assertions.assertEquals(RunningGameScenario.HUMAN, addon.getOwnerIndex());
        });
    }

    @Test
    void spawn_broodlings_steps_the_second_broodling_from_the_position_found_for_the_first() throws IOException {
        Assertions.assertNotNull(gameData);

        ReplayRuntime scenario = RunningGameScenario.start(gameData);

        RunningGameScenario.runInCommandDispatch(scenario, () -> {
            Game game = scenario.getGame();
            int row = findWalkableEdgeRow(game);
            Unit queen = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_QUEEN, SCENARIO_X, SCENARIO_Y, RunningGameScenario.HUMAN);
            Unit target = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, 0, row, RunningGameScenario.COMPUTER);

            // The first candidate lies too close to the map edge for a Broodling and for the caster, so the search moves the Broodling.
            int candidateX = target.getPositionX() - BROODLING_OFFSET;
            int candidateY = target.getPositionY() - BROODLING_OFFSET;

            Assertions.assertFalse(game.getUnitPositioning().isTerrainPlacementWalkable(ScrUnitIds.ZERG_BROODLING, candidateX, candidateY));

            game.getUnitSpells().hit(WeaponHitTypes.BROODLINGS,
                                     RunningGameScenario.HUMAN,
                                     queen,
                                     target,
                                     0, 0,
                                     0, 0,
                                     RunningGameScenario.INSTRUCTION_LIMIT);

            UnitPool pool = game.getUnitSystem().getUnitPool();
            Set<List<Integer>> positions = new HashSet<>();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstOwnerIndex(RunningGameScenario.HUMAN); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == ScrUnitIds.ZERG_BROODLING) {
                    positions.add(List.of(unit.getPositionX(), unit.getPositionY()));
                }
            }

            Assertions.assertEquals(BROODLING_COUNT, positions.size());
            Assertions.assertEquals(Set.of(FIRST_BROODLING_POSITION, SECOND_BROODLING_POSITION), positions);
        });
    }

    @Test
    void stasis_makes_unit_invincible_and_disabled_until_timer_ends() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.ZERG_SCOURGE, ScrUnitIds.TERRAN_MARINE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit marine = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);

        game.getUnitSpells().applyStasis(marine, INSTRUCTION_LIMIT);

        Assertions.assertEquals(STASIS_DURATION, marine.getStasisTimer());
        Assertions.assertNotEquals(0, marine.getStatus() & UnitStatusFlags.INVINCIBLE);
        Assertions.assertTrue(UnitOrderEligibility.isDisabled(marine));

        marine.setStasisTimer(1);
        game.getUnitSpells().updateStatusTimers(marine, INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, marine.getStasisTimer());
        Assertions.assertEquals(0, marine.getStatus() & UnitStatusFlags.INVINCIBLE);
    }

    @Test
    void defensive_matrix_absorbs_damage_before_hit_points() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.ZERG_SCOURGE, ScrUnitIds.TERRAN_MARINE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit marine = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);
        int hitPoints = marine.getHitPoints();

        marine.setDefensiveMatrixHealth(DEFENSIVE_MATRIX_HEALTH << FIXED_POINT_FRACTION_BITS);
        marine.setDefensiveMatrixTimer(DEFENSIVE_MATRIX_DURATION);

        game.getUnitDamage().dealWeaponDamage(ScrWeaponIds.GAUSS_RIFLE,
                                              GAUSS_RIFLE_DAMAGE << FIXED_POINT_FRACTION_BITS,
                                              FULL_DAMAGE_DIVISOR,
                                              null, TwoUnitGameScenario.OTHER_PLAYER,
                                              marine, 0,
                                              INSTRUCTION_LIMIT);

        // Damage still removes at least 1/2 hit point when the matrix absorbs the weapon hit.
        Assertions.assertEquals(hitPoints - MINIMUM_DAMAGE, marine.getHitPoints());
        Assertions.assertEquals((DEFENSIVE_MATRIX_HEALTH - GAUSS_RIFLE_DAMAGE) << FIXED_POINT_FRACTION_BITS, marine.getDefensiveMatrixHealth());

        marine.setDefensiveMatrixHealth(WEAK_DEFENSIVE_MATRIX_HEALTH << FIXED_POINT_FRACTION_BITS);
        game.getUnitDamage().dealWeaponDamage(ScrWeaponIds.GAUSS_RIFLE,
                                              GAUSS_RIFLE_DAMAGE << FIXED_POINT_FRACTION_BITS,
                                              FULL_DAMAGE_DIVISOR,
                                              null, TwoUnitGameScenario.OTHER_PLAYER,
                                              marine, 0,
                                              INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, marine.getDefensiveMatrixHealth());
        Assertions.assertEquals(0, marine.getDefensiveMatrixTimer());
        Assertions.assertTrue(marine.getHitPoints() < hitPoints - MINIMUM_DAMAGE);
    }

    @Test
    void hit_should_reject_unhandled_explosion_types() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();

            Assertions.assertThrows(UnsupportedOperationException.class,
                                    () -> game.getUnitSpells().hit(WeaponHitTypes.FEEDBACK,
                                                                   RunningGameScenario.COMPUTER,
                                                                   null,
                                                                   null,
                                                                   0, 0,
                                                                   0, 0,
                                                                   RunningGameScenario.INSTRUCTION_LIMIT));
        });
    }
}
