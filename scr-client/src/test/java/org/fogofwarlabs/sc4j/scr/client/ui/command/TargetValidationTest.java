package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.console.ConsoleModel;
import org.fogofwarlabs.sc4j.scr.client.ui.input.WorldInterface;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Checks that a ground click of a technology targeting mode in a live melee game passes the target test and sends the order.
 */
@RequiresInstallation
final class TargetValidationTest {
    private static final int CENTER_DIVISOR = 2;
    private static final @NotNull String MAP = "(2)Bottleneck.scm";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final @NotNull String PLAYER_RACE = "terran";
    private static final @NotNull String COMPUTER_RACE = "zerg";
    private static final int START_SLOT = 0;
    private static final int GAME_SPEED_SETTING = 4;
    private static final int REPLAY_SPEED = 1;
    private static final int SETTLE_FRAMES = 2;
    private static final int COMMAND_FRAMES = 3;

    /**
     * The positions of the created caster and of the ground click, from the local Command Center toward the map center.
     */
    private static final int CASTER_OFFSET_X = 160;
    private static final int CASTER_OFFSET_Y = 96;
    private static final int CLICK_OFFSET_X = 160;
    private static final int CLICK_OFFSET_Y = 200;

    private static final int ENERGY_FRACTION_BITS = 8;
    private static final int COMSAT_ENERGY = 200;
    private static final int SCANNER_SWEEP_ENERGY = 50;
    private static final int PLACE_MINE_ORDER = 132;

    /**
     * Starts a live melee game of a Terran local player on Bottleneck and runs its first frames.
     *
     * @return the started client session
     * @throws IOException if the game cannot be started
     */
    private static @NotNull ClientSession startMelee() throws IOException {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationDirectory);

        Path map = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP);

        MeleeGameSetup setup = MeleeGameSetup.load(map, PLAYER_NAME, PLAYER_RACE, COMPUTER_RACE, START_SLOT, GameSpeed.FASTEST);
        ClientSession session = ClientSession.startMelee(installationDirectory, map, setup, ClientSessionScenario.GAME_SEED, null);

        session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
        session.startReplay();
        stepFrames(session, SETTLE_FRAMES);

        return session;
    }

    /**
     * Creates a completed unit of the local player beside its Command Center, inside a command dispatch.
     *
     * @param session  the client session
     * @param unitType the unit type
     * @return the created unit
     */
    private static @NotNull Unit createCaster(@NotNull ClientSession session, int unitType) {
        Game game = session.getGame();
        Unit commandCenter = findUnit(game, session.getLocalPlayer(), ScrUnitIds.TERRAN_COMMAND_CENTER);
        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            return RunningGameScenario.createUnit(game,
                                                  unitType,
                                                  offsetTowardCenter(commandCenter.getPositionX(), CASTER_OFFSET_X, game.getMap().getWidth()),
                                                  offsetTowardCenter(commandCenter.getPositionY(), CASTER_OFFSET_Y, game.getMap().getHeight()),
                                                  session.getLocalPlayer());
        } finally {
            scope.endCommandDispatch();
        }
    }

    /**
     * Selects the caster, presses its technology button and left-clicks the ground at the given point, as the client does,
     * then runs the command frames.
     *
     * @param session    the client session
     * @param caster     the selected caster
     * @param technology the technology of the pressed button
     * @param clickX     the map x of the click
     * @param clickY     the map y of the click
     * @return the effect of the click
     * @throws IOException if a unit image cannot be read
     */
    private static @NotNull CommandInput.Result clickTechnologyTarget(@NotNull ClientSession session,
                                                                      @NotNull Unit caster,
                                                                      int technology,
                                                                      int clickX, int clickY) throws IOException {
        Game game = session.getGame();
        int player = session.getLocalPlayer();
        ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));
        WorldInterface world = new WorldInterface(console, (owner, opcode, payload) -> session.submitLocalCommand(owner, opcode, payload), true);

        game.getInterfaceLink().setLocalSelection(world);
        console.setOwnLocalSelection(player);
        console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

        world.getCommandInput().select(caster);
        stepFrames(session, SETTLE_FRAMES);
        console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

        int slot = findTechnologySlot(console.getCommandCard(), technology);

        Assertions.assertEquals(CommandInput.Result.INTERFACE, world.getCommandInput().pressSlot(slot, false));

        CommandInput.Result result = world.handleLeftPress(clickX, clickY, false);

        stepFrames(session, COMMAND_FRAMES);

        return result;
    }

    /**
     * Moves a coordinate by an offset toward the center of the map.
     *
     * @param position  the coordinate in pixels
     * @param offset    the offset in pixels
     * @param tileCount the map size in tiles along the axis
     * @return the moved coordinate
     */
    private static int offsetTowardCenter(int position, int offset, int tileCount) {
        return position < tileCount * TerrainTileset.TILE_SIZE / CENTER_DIVISOR ? position + offset : position - offset;
    }

    private static int findTechnologySlot(@NotNull CommandCard card, int technology) {
        for (int slot = 0; slot < CommandCard.SLOT_COUNT; slot++) {
            ButtonDefinition button = card.getButton(slot);

            if (button != null && button.getAction() == ButtonAction.USE_TECHNOLOGY && button.getActionValue() == technology &&
                card.getState(slot) == CommandCard.ENABLED) {
                return slot;
            }
        }

        return Assertions.fail("An enabled " + technology + " button");
    }

    private static @NotNull Unit findUnit(@NotNull Game game, int player, int unitType) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getOwnerIndex() == player && pool.getUnit(index).getUnitId() == unitType) {
                return pool.getUnit(index);
            }
        }

        return Assertions.fail("A unit " + unitType + " of player " + player);
    }

    private static void stepFrames(@NotNull ClientSession session, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            session.stepFrame(false);
        }
    }


    @Test
    void spider_mines_ground_click_sends_the_place_mine_order() throws IOException {
        try (ClientSession session = startMelee()) {
            Game game = session.getGame();
            int player = session.getLocalPlayer();
            Unit commandCenter = findUnit(game, player, ScrUnitIds.TERRAN_COMMAND_CENTER);
            int clickX = offsetTowardCenter(commandCenter.getPositionX(), CLICK_OFFSET_X, game.getMap().getWidth());
            int clickY = offsetTowardCenter(commandCenter.getPositionY(), CLICK_OFFSET_Y, game.getMap().getHeight());

            game.getPlayerTable().requirePlayerTechnology().setResearched(player, ScrTechIds.SPIDER_MINES, true);

            Unit vulture = createCaster(session, ScrUnitIds.TERRAN_VULTURE);

            Assertions.assertEquals(CommandInput.Result.COMMAND, clickTechnologyTarget(session, vulture, ScrTechIds.SPIDER_MINES, clickX, clickY));

            Assertions.assertEquals(PLACE_MINE_ORDER, vulture.getPrimaryOrder());
            Assertions.assertEquals(clickX, vulture.getOrderTargetX());
            Assertions.assertEquals(clickY, vulture.getOrderTargetY());
        }
    }

    @Test
    void scanner_sweep_ground_click_spends_energy_and_sweeps_the_point() throws IOException {
        try (ClientSession session = startMelee()) {
            Game game = session.getGame();
            int player = session.getLocalPlayer();
            Unit commandCenter = findUnit(game, player, ScrUnitIds.TERRAN_COMMAND_CENTER);
            int clickX = offsetTowardCenter(commandCenter.getPositionX(), CLICK_OFFSET_X, game.getMap().getWidth());
            int clickY = offsetTowardCenter(commandCenter.getPositionY(), CLICK_OFFSET_Y, game.getMap().getHeight());
            Unit comsat = createCaster(session, ScrUnitIds.TERRAN_COMSAT_STATION);

            comsat.setEnergy(COMSAT_ENERGY << ENERGY_FRACTION_BITS);

            Assertions.assertEquals(CommandInput.Result.COMMAND, clickTechnologyTarget(session, comsat, ScrTechIds.SCANNER_SWEEP, clickX, clickY));

            Unit sweep = findUnit(game, player, ScrUnitIds.SPELL_SCANNER_SWEEP);

            Assertions.assertEquals(COMSAT_ENERGY - SCANNER_SWEEP_ENERGY, comsat.getEnergy() >> ENERGY_FRACTION_BITS);
            Assertions.assertEquals(clickX, sweep.getPositionX());
            Assertions.assertEquals(clickY, sweep.getPositionY());
        }
    }
}
