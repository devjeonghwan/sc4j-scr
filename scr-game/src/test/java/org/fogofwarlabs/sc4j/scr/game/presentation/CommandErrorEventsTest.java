package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

/**
 * Checks command-error text and sound routing for cost, supply, unit, energy and build-site failures, nuclear launch detection, gas depletion and the
 * pending unit creation failure message.
 */
@RequiresInstallation
final class CommandErrorEventsTest {
    /**
     * The frame the replay is advanced to before every test, past its initial unit setup.
     */
    private static final int INITIAL_FRAME_COUNT = 10;

    /**
     * The minerals and gas a test gives the player so that the cost test passes.
     */
    private static final int FIXTURE_RESOURCES = 10000;
    /**
     * The player notified of the unit creation failure.
     */
    private static final int CREATION_FAILURE_PLAYER = 3;
    /**
     * The creation list index that ends the list.
     */
    private static final int NO_UNIT_INDEX = 0;

    /**
     * The EUD address of the game data.
     */
    private static final int GAME_DATA_ADDRESS = 0x57F0F0;
    /**
     * The game data offset of the Terran supply limits, one DWORD per player.
     */
    private static final int TERRAN_SUPPLY_LIMIT_OFFSET = 0x3144;

    /**
     * The race of a command error shown with the console race.
     */
    private static final int CONSOLE_RACE = -1;
    /**
     * The race of a command error that uses no race.
     */
    private static final int NO_RACE = -2;
    /**
     * The Terran race.
     */
    private static final int TERRAN_RACE = 1;
    /**
     * The player of a command error shown to every player.
     */
    private static final int EVERY_PLAYER = -1;
    /**
     * The {@code stat_txt} id of the Zerg supply limit text; the Terran and Protoss texts follow.
     */
    private static final int SUPPLY_LIMIT_TEXT = 0x34F;
    /**
     * The {@code stat_txt} id of the insufficient minerals text.
     */
    private static final int INSUFFICIENT_MINERALS_TEXT = 0x352;
    /**
     * The {@code stat_txt} id of the insufficient gas text.
     */
    private static final int INSUFFICIENT_GAS_TEXT = 0x353;
    /**
     * The {@code stat_txt} id of the text of a blocked build site.
     */
    private static final int CANNOT_BUILD_THERE_TEXT = 0x358;
    /**
     * The {@code stat_txt} id of the Zerg not enough energy text; the Terran and Protoss texts follow.
     */
    private static final int NOT_ENOUGH_ENERGY_TEXT = 0x360;
    /**
     * The {@code stat_txt} id of the depleted geyser text.
     */
    private static final int GAS_DEPLETED_TEXT = 0x36B;

    /**
     * The secondary order state of a queue head's first training attempt.
     */
    private static final int FIRST_ATTEMPT_STATE = 0;
    /**
     * The secondary order state of a queue head that waits for supply.
     */
    private static final int WAITING_STATE = 1;

    /**
     * The event count after one recorded event.
     */
    private static final int ONE_EVENT = 1;
    /**
     * The index of the first recorded event.
     */
    private static final int FIRST_EVENT = 0;
    /**
     * The index of the second recorded event.
     */
    private static final int SECOND_EVENT = 1;
    /**
     * The index of the third recorded event.
     */
    private static final int THIRD_EVENT = 2;

    /**
     * Starts the configured replay and advances through its initial unit setup.
     *
     * @param gameData the installed game data
     * @return the started game
     * @throws Exception if the replay cannot be read
     */
    private static @NotNull Game startTerranGame(@NotNull GameData gameData) throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        int hostTicks = 0;

        while (runtime.getGame().getCurrentFrame() < INITIAL_FRAME_COUNT) {
            Assertions.assertTrue(runtime.step(GameSpeed.FASTEST_DURATION, hostTicks += GameSpeed.FASTEST_DURATION));
        }

        return runtime.getGame();
    }

    /**
     * Finds the first visible created unit of the requested type, failing if none exists.
     *
     * @param game     the fixture game
     * @param unitType the requested unit type
     * @return the first matching created unit
     */
    private static @NotNull Unit findUnit(@NotNull Game game, int unitType) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = pool.getFirstCreatedIndex(true); index != NO_UNIT_INDEX; index = pool.getNextCreatedIndex(index)) {
            if (pool.getUnit(index).getUnitId() == unitType) {
                return pool.getUnit(index);
            }
        }

        return Assertions.fail("No unit of type " + unitType + ".");
    }


    @Test
    void failing_cost_tests_record_mineral_then_gas_error_text_and_sound() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);

            Unit commandCenter = findUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);
            int owner = commandCenter.getOwnerIndex();
            Player player = game.getPlayerTable().getPlayer(owner);
            CommandErrorEvents events = game.getInterfaceLink().getCommandErrorEvents();

            Assertions.assertNotNull(player);

            player.setMinerals(0);
            player.setGas(0);

            events.clear();

            Assertions.assertEquals(-1, game.getUnitProduction().pushBuildQueue(commandCenter, ScrUnitIds.TERRAN_SCV));
            Assertions.assertEquals(ONE_EVENT, events.getCount(), "The failing cost test shows its error.");

            CommandErrorEvents.Event event = events.get(FIRST_EVENT);

            Assertions.assertEquals(owner, event.getPlayer());
            Assertions.assertEquals(INSUFFICIENT_MINERALS_TEXT, event.getTextIdentifier());
            Assertions.assertEquals(CommandErrorEvents.Sound.INSUFFICIENT_MINERALS, event.getSound());
            Assertions.assertEquals(CONSOLE_RACE, event.getRace());

            player.setMinerals(FIXTURE_RESOURCES);

            events.clear();

            Assertions.assertFalse(game.getCostTests().testUnit(game, owner, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, false, true));
            Assertions.assertEquals(INSUFFICIENT_GAS_TEXT, events.get(FIRST_EVENT).getTextIdentifier(), "Gas is tested after minerals.");

            player.setGas(FIXTURE_RESOURCES);

            events.clear();

            Assertions.assertTrue(game.getCostTests().testUnit(game, owner, ScrUnitIds.TERRAN_SCV, true, true));
            Assertions.assertEquals(0, events.getCount(), "A passing test records nothing.");
        }
    }

    @Test
    void supply_blocked_queue_head_shows_error_only_on_first_attempt() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);

            Unit commandCenter = findUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);
            int owner = commandCenter.getOwnerIndex();
            Player player = game.getPlayerTable().getPlayer(owner);

            Assertions.assertNotNull(player);

            player.setMinerals(FIXTURE_RESOURCES);

            CommandErrorEvents events = game.getInterfaceLink().getCommandErrorEvents();

            Assertions.assertNotEquals(-1, game.getUnitProduction().pushBuildQueue(commandCenter, ScrUnitIds.TERRAN_SCV));

            // A zero supply cap prevents the queued worker from starting.
            ClassicAddressSpace.create(game).writeDword(GAME_DATA_ADDRESS + TERRAN_SUPPLY_LIMIT_OFFSET + Integer.BYTES * owner, 0);
            commandCenter.setCurrentBuildUnit(null);
            commandCenter.setSecondaryOrderState(FIRST_ATTEMPT_STATE);

            events.clear();

            game.getUnitTraining().update(commandCenter, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(ONE_EVENT, events.getCount(), "The supply error shows in state 0.");
            Assertions.assertEquals(CommandErrorEvents.Sound.SUPPLY_LIMIT, events.get(FIRST_EVENT).getSound());
            Assertions.assertEquals(SUPPLY_LIMIT_TEXT + TERRAN_RACE, events.get(FIRST_EVENT).getTextIdentifier());
            Assertions.assertEquals(WAITING_STATE, commandCenter.getSecondaryOrderState());

            events.clear();

            game.getUnitTraining().update(commandCenter, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, events.getCount(), "The waiting retries of state 1 are silent.");
        }
    }

    @Test
    void unit_error_energy_and_nuclear_launch_events_record_race_sound_and_audience() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);

            Unit commandCenter = findUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);
            CommandErrorEvents events = game.getInterfaceLink().getCommandErrorEvents();

            events.clear();

            events.raiseUnitError(game, commandCenter, CommandErrorEvents.TEXT_INVALID_SPELL_TARGET, commandCenter.getOwnerIndex());

            Assertions.assertEquals(CommandErrorEvents.Sound.ERROR, events.get(FIRST_EVENT).getSound());
            Assertions.assertEquals(TERRAN_RACE, events.get(FIRST_EVENT).getRace(), "A unit error uses the unit's race.");

            events.raiseNotEnoughEnergy(game, commandCenter);

            Assertions.assertEquals(NOT_ENOUGH_ENERGY_TEXT + TERRAN_RACE, events.get(SECOND_EVENT).getTextIdentifier(),
                                    "0x360 plus the Terran race.");
            Assertions.assertEquals(CommandErrorEvents.Sound.NONE, events.get(SECOND_EVENT).getSound());

            events.raiseNuclearLaunchDetected();

            Assertions.assertEquals(EVERY_PLAYER, events.get(THIRD_EVENT).getPlayer());
        }
    }

    @Test
    void blocked_sites_and_gas_depletion_raise_their_own_texts_and_sounds() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);

            Unit commandCenter = findUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);
            CommandErrorEvents events = game.getInterfaceLink().getCommandErrorEvents();

            events.clear();

            events.raiseBlockedBuildLocation(game, commandCenter);

            CommandErrorEvents.Event blocked = events.get(FIRST_EVENT);

            Assertions.assertEquals(CANNOT_BUILD_THERE_TEXT, blocked.getTextIdentifier());
            Assertions.assertEquals(CommandErrorEvents.Sound.BLOCKED_BUILD_LOCATION, blocked.getSound());
            Assertions.assertEquals(TERRAN_RACE, blocked.getRace(), "Terran is race 1.");
            Assertions.assertEquals(commandCenter.getIndex(), blocked.getPoolIndex());

            events.raiseInvalidBuildLocation(game, commandCenter, PlacementResult.BLOCKED);

            Assertions.assertEquals(CANNOT_BUILD_THERE_TEXT, events.get(SECOND_EVENT).getTextIdentifier());

            events.raiseGasDepleted(commandCenter);

            Assertions.assertEquals(GAS_DEPLETED_TEXT, events.get(THIRD_EVENT).getTextIdentifier());
            Assertions.assertEquals(NO_RACE, events.get(THIRD_EVENT).getRace());
        }
    }

    @Test
    void pending_creation_failure_message_is_shown_once_for_its_player() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);

            CommandErrorEvents events = game.getInterfaceLink().getCommandErrorEvents();

            events.clear();

            game.getUnitStorageConstructor().recordEarlyCreationFailure(true);

            game.getUnitStorageConstructor().notifyCreationFailure(CREATION_FAILURE_PLAYER);

            Assertions.assertEquals(ONE_EVENT, events.getCount());
            Assertions.assertEquals("strMAXUNITS", events.get(FIRST_EVENT).getTextKey(), "A full unit table leaves strMAXUNITS.");
            Assertions.assertEquals(CREATION_FAILURE_PLAYER, events.get(FIRST_EVENT).getPlayer());
            Assertions.assertEquals(CommandErrorEvents.Sound.NONE, events.get(FIRST_EVENT).getSound());

            game.getUnitStorageConstructor().notifyCreationFailure(CREATION_FAILURE_PLAYER);

            Assertions.assertEquals(ONE_EVENT, events.getCount(), "The message is emptied after it is shown.");

            game.getUnitStorageConstructor().recordEarlyCreationFailure(false);

            game.getUnitStorageConstructor().notifyCreationFailure(CREATION_FAILURE_PLAYER);

            Assertions.assertEquals(ONE_EVENT, events.getCount(), "A position failure leaves no message.");
        }
    }
}
