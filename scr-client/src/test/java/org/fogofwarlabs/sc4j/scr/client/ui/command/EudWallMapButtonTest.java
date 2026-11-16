package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.console.ConsoleModel;
import org.fogofwarlabs.sc4j.scr.client.ui.input.WorldInterface;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Presses the enhancement button the EUD map "Wall and block a million zerglings 3" gives the Marine, through the client's command card and
 * command input: the button sends Carrier Stop, the map's triggers see the unit's Carrier order and replace it with the unit of the next level
 * (the Marine's attempt always succeeds, a Ghost's attempt costs minerals and may fail).
 * <p>
 * The map is a user map: it is read from the client's maps folder ({@link ExternalInput#MAPS}) under its original name.
 */
@CorpusTest({ExternalInput.INSTALLATION, ExternalInput.MAPS})
final class EudWallMapButtonTest {
    private static final @NotNull String MAP_NAME = "eud-wall-million-zergling-3-v0.04.scx";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int HUMAN_LOBBY_SLOT = 0;

    /**
     * The frame the map's start triggers have replaced the button sets by.
     */
    private static final int START_FRAME = 20;
    /**
     * The frames a selection or a command takes from its submission to its effect, and the frames the test waits for the map's reaction.
     */
    private static final int COMMAND_FRAMES = 4;
    private static final int REACTION_FRAMES = 24;
    /**
     * The command card inputs: the fastest speed setting and no replay multiplier.
     */
    private static final int GAME_SPEED_SETTING = 4;
    private static final int REPLAY_SPEED = 1;
    /**
     * The distance in pixels from the SCV at which the Marine is created.
     */
    private static final int MARINE_OFFSET = 64;
    /**
     * The minerals the player holds before the Ghost's enhancement, more than any attempt of the map costs.
     */
    private static final int MINERALS = 5000;

    /**
     * A live Use Map Settings game of the human player on the map with the client's console and world interface.
     */
    private static final class ButtonGame implements AutoCloseable {
        private final @NotNull GameData gameData;
        private final @NotNull LiveGame live;
        private final @NotNull ReplayRuntime runtime;
        private final @NotNull Game game;
        private final @NotNull ConsoleModel console;
        private final @NotNull WorldInterface world;
        private final int player;

        private ButtonGame() throws IOException {
            Path mapPath = ExternalInput.MAPS.resolveFile(MAP_NAME);
            byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
            MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, HUMAN_LOBBY_SLOT, GameSpeed.FASTEST);

            this.gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
            this.live     = new LiveGame(setup, ClientSessionScenario.GAME_SEED);
            this.runtime  = LiveMapGameScenario.startLive(this.gameData, this.live, mapPath);
            this.game     = this.runtime.getGame();
            this.player   = this.live.getLocalPlayer();
            this.console  = new ConsoleModel(new ScrGameView(this.game), new ScrPresentation(this.game));
            this.world    = new WorldInterface(this.console, (owner, opcode, payload) -> this.live.submit(opcode, payload), true);

            this.game.getInterfaceLink().setLocalSelection(this.world);
            this.console.setOwnLocalSelection(this.player);
            this.runFrames(START_FRAME);
        }

        @Override
        public void close() {
            this.gameData.close();
        }

        private void runFrames(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                LiveMapGameScenario.stepFrame(this.runtime, this.live);

                Assertions.assertNull(this.runtime.getFailure());
            }

            this.console.update(this.player, CommandCard.Perspective.PLAYER, false, GAME_SPEED_SETTING, REPLAY_SPEED);
        }

        private int countUnits(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();
            int count = 0;

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && unit.getOwnerIndex() == this.player) {
                    count++;
                }
            }

            return count;
        }

        private @NotNull Unit requireUnit(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && unit.getOwnerIndex() == this.player) {
                    return unit;
                }
            }

            return Assertions.fail("The player has no unit of type " + unitType + ".");
        }

        private @NotNull Unit createMarine() {
            Unit scv = this.requireUnit(ScrUnitIds.TERRAN_SCV);
            GameExecutionScope scope = this.game.getExecutionScope();

            Assertions.assertNotNull(scope);

            scope.beginCommandDispatch();

            try {
                return RunningGameScenario.createUnit(this.game, ScrUnitIds.TERRAN_MARINE, scv.getPositionX() + MARINE_OFFSET, scv.getPositionY(),
                                                      this.player);
            } finally {
                scope.endCommandDispatch();
            }
        }

        /**
         * Selects the unit through the world interface and presses the enabled Carrier Stop button of its command card.
         *
         * @param unit the unit
         * @return the action value of the pressed button
         */
        private int pressEnhancement(@NotNull Unit unit) {
            this.world.getCommandInput().select(unit);
            this.runFrames(COMMAND_FRAMES);

            CommandCard card = this.console.getCommandCard();

            for (int slot = 0; slot < CommandCard.SLOT_COUNT; slot++) {
                ButtonDefinition button = card.getButton(slot);

                if (button != null && button.getAction() == ButtonAction.CARRIER_STOP && card.getState(slot) == CommandCard.ENABLED) {
                    Assertions.assertEquals(CommandInput.Result.COMMAND, this.world.getCommandInput().pressSlot(slot, false));

                    return button.getActionValue();
                }
            }

            return Assertions.fail("The command card of type " + unit.getUnitId() + " has no enabled enhancement button.");
        }
    }

    @Test
    void enhancement_button_replaces_the_marine_with_the_unit_of_the_next_level() throws IOException {
        try (ButtonGame button = new ButtonGame()) {
            Unit marine = button.createMarine();
            int ghosts = button.countUnits(ScrUnitIds.TERRAN_GHOST);

            Assertions.assertEquals(ScrUnitIds.TERRAN_GHOST, button.pressEnhancement(marine), "the button names the next level");

            button.runFrames(REACTION_FRAMES);

            Assertions.assertTrue(marine.isDestroyed(), "the enhanced Marine is removed");
            Assertions.assertEquals(ghosts + 1, button.countUnits(ScrUnitIds.TERRAN_GHOST), "a Ghost takes the Marine's place");

            Player owner = button.game.getPlayerTable().getPlayer(button.player);

            owner.setMinerals(MINERALS);

            Assertions.assertEquals(ScrUnitIds.TERRAN_VULTURE, button.pressEnhancement(button.requireUnit(ScrUnitIds.TERRAN_GHOST)),
                                    "the Ghost's button names the next level");

            button.runFrames(REACTION_FRAMES);

            Assertions.assertTrue(owner.getMinerals() < MINERALS, "the map charges the Ghost's enhancement attempt");
        }
    }
}
