package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks computer adoption after an attacked neutral owner changes behavior.
 */
@RequiresInstallation
final class ComputerNeutralOrderTest {
    /**
     * The campaign mission of the mission test, played as Brood War.
     */
    private static final @NotNull String CAMPAIGN_MISSION = "bw-protoss-1";
    private static final int NEUTRAL_OWNER = 7;
    /**
     * The frames of the mission that run before the neutral units are searched.
     */
    private static final int START_FRAMES = 10;
    /**
     * The frame before which the neutral owner has to switch to computer behavior.
     */
    private static final int SWITCH_FRAME_LIMIT = 400;
    /**
     * The distance above the Dragoon at which the attacking Zealot is created.
     */
    private static final int ZEALOT_DISTANCE = 48;

    private static final int NEUTRAL_PLAYER_TYPE = 7;
    private static final int COMPUTER_PLAYER_TYPE = 1;
    private static final int NEUTRAL_ORDER = 162;
    private static final int ENEMY_RELATION = 0;
    private static final int GUARD_KIND = 1;

    private static @NotNull Unit findNeutral(@NotNull Game simulation, int unitTypeIdentifier) {
        UnitPool pool = simulation.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getUnitId() == unitTypeIdentifier &&
                pool.getUnit(index).getOwnerIndex() == NEUTRAL_OWNER) {
                return pool.getUnit(index);
            }
        }

        return Assertions.fail("No unit of type " + unitTypeIdentifier + " owned by player 8");
    }


    @Test
    void attacked_neutral_unit_switches_its_owner_to_computer_behavior() throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), CAMPAIGN_MISSION, true, (game, live) -> {
            Game simulation = live.getGame();
            int frame = 0;

            for (; frame < START_FRAMES; frame++) {
                game.exchangeTurn(simulation.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            Player neutralPlayerBeforeHit = simulation.getPlayerTable().getPlayer(NEUTRAL_OWNER);

            Assertions.assertNotNull(neutralPlayerBeforeHit);

            Assertions.assertEquals(NEUTRAL_PLAYER_TYPE, neutralPlayerBeforeHit.getType());

            Unit dragoon = findNeutral(simulation, ScrUnitIds.PROTOSS_DRAGOON);
            Unit other = findNeutral(simulation, ScrUnitIds.PROTOSS_ZEALOT);

            Assertions.assertEquals(NEUTRAL_ORDER, dragoon.getPrimaryOrder());
            Assertions.assertNull(dragoon.getComputerRecord());

            PlayerRelations relations = simulation.getPlayerTable().getPlayerRelations();

            Assertions.assertNotNull(relations);

            int attacker = game.getLocalPlayer();

            relations.setAlliance(NEUTRAL_OWNER, attacker, PlayerRelations.ENEMY);

            Assertions.assertNotEquals(ENEMY_RELATION, relations.getAlliance(attacker, NEUTRAL_OWNER));

            int unitTypeIdentifier = ScrUnitIds.PROTOSS_ZEALOT;

            int positionX = dragoon.getPositionX();
            int positionY = dragoon.getPositionY() - ZEALOT_DISTANCE;

            Unit zealot = CampaignMissionScenario.createUnit(simulation, unitTypeIdentifier, positionX, positionY, attacker);

            simulation.getOrders().issue(zealot, ScrOrderIds.ATTACK_UNIT, dragoon, 0, 0, ScrUnitIds.NONE, false,
                                         RunningGameScenario.INSTRUCTION_LIMIT);

            boolean switched = false;

            for (; frame < SWITCH_FRAME_LIMIT && !switched; frame++) {
                game.exchangeTurn(simulation.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                Player playerAfterStep = simulation.getPlayerTable().getPlayer(NEUTRAL_OWNER);

                Assertions.assertNotNull(playerAfterStep);

                switched = playerAfterStep.getType() == COMPUTER_PLAYER_TYPE;
            }

            Assertions.assertTrue(switched, "player 8 never switched to computer behavior");
            Assertions.assertEquals(ENEMY_RELATION, relations.getAlliance(NEUTRAL_OWNER, attacker));
            Assertions.assertEquals(ENEMY_RELATION, relations.getAlliance(attacker, NEUTRAL_OWNER));
            Assertions.assertNull(dragoon.getAutomaticTargetUnit());
            Assertions.assertEquals(zealot, dragoon.getOrderTargetUnit());

            GameData unitData = simulation.getGameData();

            Assertions.assertNotNull(unitData);

            Assertions.assertEquals(unitData.getUnits().get("Attack Unit", dragoon.getUnitId()), dragoon.getPrimaryOrder());
            Assertions.assertNotNull(dragoon.getComputerRecord());
            Assertions.assertEquals(GUARD_KIND, dragoon.getComputerRecord().getKind());
            Assertions.assertNotNull(other.getComputerRecord(), "the other units of player 8 were not adopted");
            Assertions.assertNotEquals(NEUTRAL_ORDER, other.getPrimaryOrder());
        });
    }
}
