package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the routines that use a unit's weapon or turret type unchecked: the {@code attackwith} script command of a unit without that weapon,
 * the attack-angle test of a Lurker and the morph of a unit with a turret into a type without one.
 */
@RequiresInstallation
final class UncheckedTypeDataTest {
    private static final int POSITION_X = 3300;
    private static final int POSITION_Y = 2600;
    /**
     * The vertical distance of the target below the attacking unit.
     */
    private static final int TARGET_DISTANCE = 64;
    /**
     * The {@code attackwith} operand of the air weapon.
     */
    private static final int AIR_WEAPON_NUMBER = 2;
    /**
     * The {@code attackwith} operand of the ground weapon.
     */
    private static final int GROUND_WEAPON_NUMBER = 1;
    /**
     * The launch offset that selects the weapon's own offset.
     */
    private static final int WEAPON_FORWARD_OFFSET = -1;
    /**
     * A heading away from the target below the unit.
     */
    private static final int NORTH_HEADING = 0;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void startGame() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void close() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }

        gameData = null;
    }

    /**
     * @param game the running game
     * @return the number of active bullets of the current frame
     */
    private static int countBullets(@NotNull Game game) {
        GameFrame frame = game.getFrame();

        Assertions.assertNotNull(frame);

        return frame.getProjectiles().getActiveCount();
    }

    /**
     * Creates a unit and an enemy target below it, the unit's order target.
     *
     * @param game     the running game
     * @param unitType the unit type
     * @return the created unit
     */
    private static @NotNull Unit createAttacker(@NotNull Game game, int unitType) {
        Unit unit = RunningGameScenario.createUnit(game, unitType, POSITION_X, POSITION_Y, RunningGameScenario.COMPUTER);
        Unit target = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, POSITION_X, POSITION_Y + TARGET_DISTANCE,
                                                     RunningGameScenario.HUMAN);

        unit.setOrderTargetUnit(target);

        return unit;
    }


    @Test
    void attack_with_a_weapon_the_unit_lacks_should_launch_no_bullet() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit zealot = createAttacker(game, ScrUnitIds.PROTOSS_ZEALOT);
            int bullets = countBullets(game);

            Assertions.assertEquals(ScrWeaponIds.NONE, game.getUnitTargeting().getAirWeapon(zealot));
            Assertions.assertFalse(game.getProjectilePhase().launchesBullets(ScrWeaponIds.NONE));

            game.getUnitAttack().attackWith(zealot, AIR_WEAPON_NUMBER, WEAPON_FORWARD_OFFSET, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(bullets, countBullets(game));

            Unit marine = createAttacker(game, ScrUnitIds.TERRAN_MARINE);

            game.getUnitAttack().attackWith(marine, GROUND_WEAPON_NUMBER, WEAPON_FORWARD_OFFSET, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(bullets + 1, countBullets(game));
        });
    }

    @Test
    void attack_angle_test_of_a_lurker_should_turn_it_to_the_position_and_pass() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit lurker = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_LURKER, POSITION_X, POSITION_Y, RunningGameScenario.COMPUTER);
            Sprite sprite = lurker.requireSprite();
            int targetY = sprite.getY() + TARGET_DISTANCE;

            lurker.setHeading(NORTH_HEADING);

            Assertions.assertTrue(game.getUnitTargeting().isInAttackAngle(lurker, sprite.getX(), targetY, ScrWeaponIds.NONE));
            Assertions.assertEquals(FlingyMotionUtils.calculateDirection(0, TARGET_DISTANCE), lurker.getHeading());
        });
    }

    @Test
    void morph_into_a_type_without_a_turret_should_reinitialize_the_turret_as_that_subunit() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            GameData data = game.getGameData();

            Assertions.assertNotNull(data);

            DatTable units = data.getUnits();
            int siegeTurret = units.get(UnitsDat.FIRST_SUBUNIT, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE);
            Unit tank = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, POSITION_X, POSITION_Y,
                                                       RunningGameScenario.COMPUTER);
            Unit turret = tank.getSubunit();

            Assertions.assertNotNull(turret);

            units.set(UnitsDat.FIRST_SUBUNIT, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, ScrUnitIds.NONE);

            try {
                game.getUnitMorphing().morph(tank, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, RunningGameScenario.INSTRUCTION_LIMIT);
            } finally {
                units.set(UnitsDat.FIRST_SUBUNIT, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, siegeTurret);
            }

            Assertions.assertEquals(ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, tank.getUnitId());
            Assertions.assertSame(turret, tank.getSubunit());
            Assertions.assertEquals(ScrUnitIds.NONE, turret.getUnitId());
        });
    }
}
