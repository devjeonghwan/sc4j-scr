package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.order.UnitCommands;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
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

/**
 * Checks the attack orders of {@link UnitAttack}: Attack Unit waits for the facing and then starts a randomized weapon cooldown, and Hold Position
 * stops once and marks the unit ready to attack.
 */
@RequiresInstallation
final class UnitAttackTest {
    /**
     * The image and order instruction budget of the created units, the commands and the primary order updates.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * An order position near both units of the duel world, used for both coordinates.
     */
    private static final int NEAR_POSITION = 300;
    /**
     * The heading from the first unit to the second unit.
     */
    private static final int EAST_HEADING = 64;
    /**
     * The starting-attack movement flag that the attack sets once the unit faces its target.
     */
    private static final int STARTING_ATTACK_FLAG = 0x8;
    /**
     * The shifts of the ground and air weapon cooldown bytes in the cooldown timers.
     */
    private static final int GROUND_COOLDOWN_SHIFT = 8;
    private static final int AIR_COOLDOWN_SHIFT = 16;
    /**
     * The mask of one cooldown byte of the cooldown timers.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The frames by which the randomized weapon cooldown may lie below and above the installed weapon cooldown.
     */
    private static final int COOLDOWN_JITTER_BELOW = 1;
    private static final int COOLDOWN_JITTER_ABOVE = 2;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    private static @NotNull Game createDuelGame(int firstUnitType, int secondUnitType) {
        Assertions.assertNotNull(gameData);

        return CombatGameScenario.createDuelGame(gameData, firstUnitType, secondUnitType);
    }


    @Test
    void attack_unit_waits_for_facing_then_starts_randomized_cooldown() {
        Assertions.assertNotNull(gameData);

        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit attacker = unitPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);
        Unit target = unitPool.getUnit(CombatGameScenario.SECOND_UNIT_INDEX);

        Assertions.assertTrue(commands.order(0,
                                             ScrOrderIds.ATTACK_DEFAULT,
                                             NEAR_POSITION, NEAR_POSITION,
                                             target,
                                             ScrUnitIds.NONE,
                                             false,
                                             INSTRUCTION_LIMIT));

        attacker.setHeading(0);
        attacker.setPrimaryOrderCountdown(0);

        int randomCount = game.getRandom().getTotalRandomCounts();

        game.getUnitOrders().updatePrimary(attacker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(1, attacker.getPrimaryOrderState());
        Assertions.assertEquals(0, attacker.getPrimaryOrderCountdown());
        Assertions.assertEquals(0, attacker.getMovementFlags() & MovementFlags.STARTING_ATTACK);
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());

        attacker.setHeading(EAST_HEADING);
        game.getUnitOrders().updatePrimary(attacker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(STARTING_ATTACK_FLAG, attacker.getMovementFlags() & MovementFlags.STARTING_ATTACK);

        int cooldown = attacker.getCooldownTimers() >>> GROUND_COOLDOWN_SHIFT & BYTE_MASK;
        int weaponCooldown = gameData.getWeapons().get("Weapon Cooldown", ScrWeaponIds.PARTICLE_BEAM);

        Assertions.assertTrue(cooldown >= weaponCooldown - COOLDOWN_JITTER_BELOW && cooldown <= weaponCooldown + COOLDOWN_JITTER_ABOVE,
                              "cooldown=" + cooldown);
        Assertions.assertEquals(cooldown, attacker.getCooldownTimers() >>> AIR_COOLDOWN_SHIFT & BYTE_MASK);
        Assertions.assertTrue(game.getRandom().getTotalRandomCounts() > randomCount);
    }

    @Test
    void hold_position_stops_once_and_marks_unit_ready_to_attack() {
        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);

        Assertions.assertTrue(game.getUnitCommands().holdPosition(0, false, INSTRUCTION_LIMIT));

        unit.setPrimaryOrderCountdown(0);
        game.getUnitOrders().updatePrimary(unit, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(1, unit.getPrimaryOrderState());
        Assertions.assertNotEquals(0, unit.getStatus() & UnitStatusFlags.READY_TO_ATTACK);
        Assertions.assertEquals(unit.getPositionX(), unit.getMovementDestinationX());
    }
}
