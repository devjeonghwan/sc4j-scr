package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the trap orders of {@link DoodadOrders}: a hidden Floor Gun Trap reveals itself, attacks and hides again.
 */
@RequiresInstallation
final class DoodadOrdersTest {
    /**
     * The campaign mission with the Floor Gun Traps, played as StarCraft.
     */
    private static final @NotNull String TRAP_MISSION = "terran-4";
    /**
     * The frames that run before the trap is searched.
     */
    private static final int START_FRAMES = 3;
    /**
     * The frame before which the trap has to reveal itself, attack and hide again.
     */
    private static final int FRAME_LIMIT = 400;

    private static final int TRAP_POSITION_X = 1952;
    private static final int TRAP_POSITION_Y = 1360;
    private static final int ATTACKER_POSITION_Y = 1300;

    /**
     * The no-collision, requires-detector and cloaked status flags of a hidden trap.
     */
    private static final int HIDDEN_TRAP_STATUS_FLAGS = 0x200300;
    /**
     * The order of a hidden trap.
     */
    private static final int HIDDEN_GUN_ORDER = 167;

    @Test
    void hidden_floor_gun_trap_reveals_attacks_and_hides_again() throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), TRAP_MISSION, false, (game, live) -> {
            Game state = live.getGame();
            int frame = 0;

            for (; frame < START_FRAMES; frame++) {
                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            UnitPool trapPool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(trapPool);

            Unit trap = null;

            for (int index = 1; index <= trapPool.getCapacity(); index++) {
                if (trapPool.isAllocated(index) && trapPool.getUnit(index).getUnitId() == ScrUnitIds.SPECIAL_FLOOR_GUN_TRAP &&
                    trapPool.getUnit(index).getPositionX() == TRAP_POSITION_X && trapPool.getUnit(index).getPositionY() == TRAP_POSITION_Y) {
                    trap = trapPool.getUnit(index);

                    break;
                }
            }

            if (trap == null) {
                Assertions.fail("No Floor Gun Trap at " + TRAP_POSITION_X + "," + TRAP_POSITION_Y);
            }

            // The hidden trap has no collision, requires detection and waits in state one.
            Assertions.assertEquals(HIDDEN_GUN_ORDER, trap.getPrimaryOrder());
            Assertions.assertEquals(1, trap.getPrimaryOrderState());

            int hidden = UnitStatusFlags.NO_COLLIDE | UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED;

            Assertions.assertEquals(HIDDEN_TRAP_STATUS_FLAGS, trap.getStatus() & hidden);
            Assertions.assertEquals(0, trap.requireSprite().getElevationLevel());

            int attackerType = ScrUnitIds.TERRAN_MARINE;
            int attackerOwner = game.getLocalPlayer();

            CampaignMissionScenario.createUnit(state, attackerType, TRAP_POSITION_X, ATTACKER_POSITION_Y, attackerOwner);

            GameData loadedData = state.getGameData();

            Assertions.assertNotNull(loadedData);

            DatTable units = loadedData.getUnits();
            boolean revealed = false;
            boolean attacked = false;

            for (; frame < FRAME_LIMIT && !(attacked && trap.getPrimaryOrder() == ScrOrderIds.HIDDEN_GUN); frame++) {
                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                if (trap.getPrimaryOrder() == ScrOrderIds.REVEAL_TRAP) {
                    revealed = true;

                    Assertions.assertEquals(0, trap.getStatus() & (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED));
                    Assertions.assertEquals(units.get("Elevation Level", trap.getUnitId()), trap.requireSprite().getElevationLevel());
                }

                if (revealed && trap.getPrimaryOrder() == units.get("Attack Unit", trap.getUnitId())) {
                    attacked = true;
                }
            }

            Assertions.assertTrue(revealed, "the trap never revealed itself");
            Assertions.assertTrue(attacked, "the trap never attacked");
            Assertions.assertEquals(HIDDEN_GUN_ORDER, trap.getPrimaryOrder(), "the trap did not hide again by loop " + frame);
        });
    }
}
