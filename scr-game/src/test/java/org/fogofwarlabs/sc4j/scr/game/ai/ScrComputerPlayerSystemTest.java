package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.engine.computer.ComputerController;
import org.fogofwarlabs.sc4j.engine.controller.ControllerSource;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.frame.ScrFramePhase;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the SC:R computer player system: it is the game's engine computer player system, its step is the computer phase of the world
 * phases, and a playable slot whose player is a computer has the controller of the computer-player phase.
 */
final class ScrComputerPlayerSystemTest {
    private static final int SCHEDULER_COUNTER_COUNT = 8;
    private static final int REGION_COUNT = 1;

    private static final int HUMAN_SLOT = 0;
    private static final int COMPUTER_SLOT = 1;
    /**
     * The world phase list position of the computer step: after the creep phase.
     */
    private static final int COMPUTER_PHASE_POSITION = 1;

    @Test
    void computer_player_system_controls_the_computer_slots_of_the_computer_player_phase() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        ScrComputerPlayerSystem system = game.getComputerPlayers();
        org.fogofwarlabs.sc4j.engine.game.Game engineGame = game.getEngineGame();

        Assertions.assertSame(system, engineGame.getComputerPlayers());
        Assertions.assertSame(ScrFramePhase.COMPUTER_PLAYERS, game.getFramePhases().get(COMPUTER_PHASE_POSITION));
        Assertions.assertFalse(system.isReady());
        Assertions.assertNull(system.getController(engineGame.getPlayer(COMPUTER_SLOT)));

        game.getPlayerTable().setPlayer(HUMAN_SLOT, new Player(HUMAN_SLOT, HUMAN_SLOT, PlayerTypes.HUMAN, 0, HUMAN_SLOT));
        game.getPlayerTable().setPlayer(COMPUTER_SLOT, new Player(COMPUTER_SLOT, COMPUTER_SLOT, PlayerTypes.COMPUTER, 0, COMPUTER_SLOT));
        game.setComputerPlayerPhase(new ComputerPlayerPhase(game, REGION_COUNT, new int[SCHEDULER_COUNTER_COUNT],
                                                            new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]));

        ComputerController controller = system.getController(engineGame.getPlayer(COMPUTER_SLOT));

        Assertions.assertTrue(system.isReady());
        Assertions.assertNotNull(controller);
        Assertions.assertSame(engineGame.getPlayer(COMPUTER_SLOT), controller.getPlayer());
        Assertions.assertEquals(ControllerSource.COMPUTER, controller.getSource());
        Assertions.assertNull(system.getController(engineGame.getPlayer(HUMAN_SLOT)));
        Assertions.assertNull(system.getController(engineGame.getPlayer(PlayerTable.PLAYABLE_PLAYER_SLOTS)));
    }
}
