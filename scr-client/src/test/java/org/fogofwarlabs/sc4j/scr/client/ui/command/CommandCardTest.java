package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.console.ConsoleModel;
import org.fogofwarlabs.sc4j.scr.client.ui.input.HotkeyProfile;
import org.fogofwarlabs.sc4j.scr.client.ui.input.WorldInterface;
import org.fogofwarlabs.sc4j.scr.client.ui.status.ButtonTooltip;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.fogofwarlabs.sc4j.scr.game.presentation.HostButtonSets;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.AddedGraphicsScenario;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Tests the command card in replay and live game states: the game's button set table, a chosen button set, the active
 * order highlight, a Drone build submenu kept through colony construction and the production buttons of added unit types with their texts.
 */
@RequiresInstallation
final class CommandCardTest {
    private static final @NotNull String REPLAY = GameTestResources.REPLAY_ZVZ_PYTHON;
    private static final long FRAME = 1500;
    private static final int FASTEST_SPEED = 6;
    private static final int GAME_SPEED_SETTING = 4;
    private static final int REPLAY_SPEED = 1;
    private static final int NO_CONNECTED_SET = 0xFFFF;
    private static final int STOP_POSITION = 1;
    private static final int STOP_ICON = 229;
    private static final int STOP_STRING = 665;
    private static final int MOVE_POSITION = 2;
    private static final int MOVE_ICON = 228;
    private static final int MOVE_STRING = 664;
    private static final int REPLACED_BUTTON_COUNT = 2;
    /**
     * The state of a button that the player can press.
     */
    private static final int ENABLED_STATE = 1;
    /**
     * A button set the test chooses through the card's Change Buttons action.
     */
    private static final int CHOSEN_SET = 238;
    private static final int HIGHLIGHT_IDENTIFIER_MASK = 0xFFFF;
    private static final int SETTLE_FRAMES = 60;
    private static final int COMMAND_FRAMES = 3;
    private static final int BUILD_FRAMES = 3000;
    private static final int SUNKEN_COLONY_ICON = 146;
    private static final int BASIC_STRUCTURES_SET = 238;
    /**
     * The interface set of a card without a chosen set.
     */
    private static final int NO_SET = 228;
    private static final int CREEP_COLONY_SET = 143;
    private static final int TILE_SIZE = 32;
    private static final int MINIMUM_PLACEMENT_DISTANCE = 6;
    private static final int MAXIMUM_PLACEMENT_DISTANCE = 12;
    /**
     * The building morph button of an added unit type on the Hatchery card, at a position the Hatchery's own buttons leave free.
     */
    private static final int MORPH_BUTTON_POSITION = 8;
    private static final char MORPH_BUTTON_HOTKEY = 'u';
    private static final @NotNull String MORPH_RESULT_NAME = "Test Titan";
    private static final @NotNull String MORPH_BUTTON_TEXT = "Mutate into " + MORPH_RESULT_NAME;
    /**
     * A second building morph button whose requirement script asks for an Ultralisk Cavern, which the player does not have, with its texts in
     * English and Korean.
     */
    private static final int LOCKED_BUTTON_POSITION = 6;
    private static final char LOCKED_BUTTON_HOTKEY = 'k';
    private static final @NotNull String LOCKED_RESULT_NAME = "Locked Titan";
    private static final @NotNull String LOCKED_BUTTON_TEXT = "Mutate into " + LOCKED_RESULT_NAME;
    private static final @NotNull String LOCKED_BUTTON_DISABLED_TEXT = LOCKED_RESULT_NAME + " Requires:\n   Ultralisk Cavern";
    private static final @NotNull String KOREAN_LOCKED_BUTTON_TEXT = "잠긴 타이탄으로 변이";
    private static final @NotNull String KOREAN_LOCKED_BUTTON_DISABLED_TEXT = "잠긴 타이탄 요구 사항:\n   울트라리스크 캐번";
    private static final @NotNull String ENGLISH_LOCALE = "enUS";
    private static final @NotNull String KOREAN_LOCALE = "koKR";

    /**
     * Steps the client session for the specified number of silent frames.
     *
     * @param session the client session
     * @param frames  the number of frames
     */
    private static void stepFrames(@NotNull ClientSession session, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            session.stepFrame(false);
        }
    }

    /**
     * Finds the first allocated Hatchery in the initialized replay world.
     *
     * @param game the initialized world
     * @return the existing Hatchery
     * @throws AssertionError if the replay contains no Hatchery
     */
    private static @NotNull Unit findHatchery(@NotNull Game game) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getUnitId() == ScrUnitIds.ZERG_HATCHERY) {
                return pool.getUnit(index);
            }
        }

        throw Exceptions.newAssertionError("The replay has no Hatchery.");
    }

    /**
     * Finds the first allocated unit matching the requested owner and type.
     *
     * @param game     the initialized world
     * @param player   the required owner
     * @param unitType the required unit type
     * @return the matching unit, or {@code null} if none is present
     */
    private static @Nullable Unit findUnit(@NotNull Game game, int player, int unitType) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getOwnerIndex() == player && pool.getUnit(index).getUnitId() == unitType) {
                return pool.getUnit(index);
            }
        }

        return null;
    }

    /**
     * Finds the first enabled command slot for the required action.
     *
     * @param card   the current command card
     * @param action the required action
     * @return the matching slot, or {@code -1} if no enabled slot matches
     */
    private static int findSlot(@NotNull CommandCard card, @NotNull ButtonAction action) {
        for (int slot = 0; slot < CommandCard.SLOT_COUNT; slot++) {
            ButtonDefinition button = card.getButton(slot);

            if (button != null && button.getAction() == action && card.getState(slot) == CommandCard.ENABLED) {
                return slot;
            }
        }

        return -1;
    }

    /**
     * Finds the Zerg build slot of the creep colony.
     *
     * @param card the current command card
     * @return the matching build slot
     * @throws AssertionError if the card has no matching build button
     */
    private static int findCreepColonySlot(@NotNull CommandCard card) {
        for (int slot = 0; slot < CommandCard.SLOT_COUNT; slot++) {
            ButtonDefinition button = card.getButton(slot);

            if (button != null && button.getAction() == ButtonAction.ZERG_BUILD && button.getActionValue() == ScrUnitIds.ZERG_CREEP_COLONY) {
                return slot;
            }
        }

        throw Exceptions.newAssertionError("no build button of type {0}", ScrUnitIds.ZERG_CREEP_COLONY);
    }

    /**
     * Places the building at the first accepted tile of a ring {@code 6..12} tiles from the Hatchery.
     *
     * @param input    the active placement input
     * @param hatchery the center unit
     * @return {@code true} if a tile accepts the command, {@code false} otherwise
     */
    private static boolean placeBuildingNear(@NotNull CommandInput input, @NotNull Unit hatchery) {
        int centerX = hatchery.getPositionX() / TILE_SIZE;
        int centerY = hatchery.getPositionY() / TILE_SIZE;

        for (int distance = MINIMUM_PLACEMENT_DISTANCE; distance <= MAXIMUM_PLACEMENT_DISTANCE; distance++) {
            for (int verticalOffset = -distance; verticalOffset <= distance; verticalOffset++) {
                for (int horizontalOffset = -distance; horizontalOffset <= distance; horizontalOffset++) {
                    if (Math.max(Math.abs(horizontalOffset), Math.abs(verticalOffset)) != distance ||
                        centerX + horizontalOffset < 0 || centerY + verticalOffset < 0) {
                        continue;
                    }

                    if (input.clickPlacement(centerX + horizontalOffset, centerY + verticalOffset) == CommandInput.Result.COMMAND) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Test
    void replaced_host_button_sets_drive_command_card_until_defaults_restore() throws IOException {
        try (ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(), GameTestResources.getPath(REPLAY))) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();

            while (session.getLastCompletedFrame() < FRAME && session.stepFrame(false)) {
            }

            Game game = session.getGame();
            Unit hatchery = findHatchery(game);
            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));

            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
            console.showLocalSelection(hatchery);
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            CommandCard card = console.getCommandCard();
            int set = card.getDisplayedSet();
            ButtonDefinition larva = card.getButton(0);

            Assertions.assertNotNull(larva);
            Assertions.assertSame(ButtonAction.SELECT_LARVA, larva.getAction());

            HostButtonSets host = game.getGameContext().getHostButtonSets();

            try {
                for (int entry = 0; entry < HostButtonSets.SET_COUNT; entry++) {
                    host.replace(entry, 0, null, NO_CONNECTED_SET);
                }

                HostButtonSets.Button stop = new HostButtonSets.Button(STOP_POSITION,
                                                                       STOP_ICON,
                                                                       ButtonCondition.ALWAYS.getNativeAddress(), 0,
                                                                       ButtonAction.STOP.getNativeAddress(), 0,
                                                                       STOP_STRING, 0);
                // An action function in the condition slot is not a condition; the card does not show that button.
                HostButtonSets.Button swapped = new HostButtonSets.Button(MOVE_POSITION,
                                                                          MOVE_ICON,
                                                                          ButtonAction.MOVE.getNativeAddress(), 0,
                                                                          ButtonCondition.ALWAYS.getNativeAddress(), 0,
                                                                          MOVE_STRING, 0);

                host.replace(set, REPLACED_BUTTON_COUNT, new HostButtonSets.Button[]{stop, swapped}, NO_CONNECTED_SET);
                console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

                ButtonDefinition first = card.getButton(0);

                Assertions.assertNotNull(first);
                Assertions.assertSame(ButtonAction.STOP, first.getAction());
                Assertions.assertEquals(STOP_ICON, first.getIcon());
                Assertions.assertEquals(ENABLED_STATE, card.getState(0));
                Assertions.assertNull(card.getButton(1));
            } finally {
                host.restoreDefaults();
            }

            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            ButtonDefinition restored = card.getButton(0);

            Assertions.assertNotNull(restored);
            Assertions.assertSame(ButtonAction.SELECT_LARVA, restored.getAction());
        }
    }

    @Test
    void selecting_same_unit_again_closes_chosen_button_set() throws IOException {
        try (ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(), GameTestResources.getPath(REPLAY))) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();

            while (session.getLastCompletedFrame() < FRAME && session.stepFrame(false)) {
            }

            Game game = session.getGame();
            Unit hatchery = findHatchery(game);
            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));

            console.showLocalSelection(hatchery);
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            CommandCard card = console.getCommandCard();
            int own = card.getDisplayedSet();

            card.changeButtons(CHOSEN_SET);
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(CHOSEN_SET, card.getDisplayedSet());

            // Refreshes keep the chosen set while the selection stays.
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(CHOSEN_SET, card.getDisplayedSet());

            // Selecting the same unit again recomputes the card.
            console.showLocalSelection(hatchery);
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(own, card.getDisplayedSet());
        }
    }

    @Test
    void hold_position_button_is_highlighted_while_holding() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 "bw-terran-1", "Tester", FASTEST_SPEED, ClientSessionScenario.GAME_SEED,
                                                                 "enUS")) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();
            stepFrames(session, SETTLE_FRAMES);

            Game game = session.getGame();
            int player = session.getLocalPlayer();
            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));
            WorldInterface world = new WorldInterface(console, (owner, opcode, payload) -> session.submitLocalCommand(owner, opcode, payload), true);

            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            ButtonAction requiredAction = ButtonAction.HOLD_POSITION;
            UnitPool selectionPool = game.getUnitSystem().getUnitPool();
            Unit unit = null;

            Assertions.assertNotNull(selectionPool);

            for (int index = 1; index <= selectionPool.getCapacity(); index++) {
                if (!selectionPool.isAllocated(index) || selectionPool.getUnit(index).getOwnerIndex() != player) {
                    continue;
                }

                Unit candidate = selectionPool.getUnit(index);

                world.getCommandInput().select(candidate);
                console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

                if (findSlot(console.getCommandCard(), requiredAction) >= 0) {
                    unit = candidate;

                    break;
                }
            }

            Assertions.assertNotNull(unit, "a local unit with a Hold Position button");

            int holdSlot = findSlot(console.getCommandCard(), ButtonAction.HOLD_POSITION);
            ButtonDefinition holdButton = console.getCommandCard().getButton(holdSlot);

            Assertions.assertNotNull(holdButton);

            int holdIcon = holdButton.getIcon();

            Assertions.assertNotEquals(holdIcon, console.getCommandCard().getHighlightedIcon());

            Assertions.assertEquals(CommandInput.Result.COMMAND, world.getCommandInput().pressSlot(holdSlot, false));
            stepFrames(session, COMMAND_FRAMES);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            // The hold order highlight is the Hold Position icon.
            GameData orderData = game.getGameData();

            Assertions.assertNotNull(orderData);

            Assertions.assertEquals(holdIcon, orderData.getOrders().get("Highlight", unit.getPrimaryOrder()) & HIGHLIGHT_IDENTIFIER_MASK);
            Assertions.assertEquals(holdIcon, console.getCommandCard().getHighlightedIcon());

            int stopSlot = findSlot(console.getCommandCard(), ButtonAction.STOP);

            Assertions.assertEquals(CommandInput.Result.COMMAND, world.getCommandInput().pressSlot(stopSlot, false));
            stepFrames(session, COMMAND_FRAMES);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertNotEquals(holdIcon, console.getCommandCard().getHighlightedIcon());
        }
    }

    /**
     * Checks that a single selected Drone keeps its chosen build submenu while becoming a colony until reselection.
     */
    @Test
    void drone_build_submenu_survives_colony_morph_until_reselected() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 "zerg-2", "Tester", FASTEST_SPEED, ClientSessionScenario.GAME_SEED,
                                                                 "enUS")) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();
            stepFrames(session, SETTLE_FRAMES);

            Game game = session.getGame();
            int player = session.getLocalPlayer();
            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));
            WorldInterface world = new WorldInterface(console, (owner, opcode, payload) -> session.submitLocalCommand(owner, opcode, payload), true);
            Unit drone = findUnit(game, player, ScrUnitIds.ZERG_DRONE);
            Unit hatchery = findUnit(game, player, ScrUnitIds.ZERG_HATCHERY);

            Assertions.assertNotNull(drone);
            Assertions.assertNotNull(hatchery);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
            world.getCommandInput().select(drone);
            stepFrames(session, COMMAND_FRAMES);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            int basic = findSlot(console.getCommandCard(), ButtonAction.CHANGE_BUTTONS);

            Assertions.assertEquals(CommandInput.Result.INTERFACE, world.getCommandInput().pressSlot(basic, false));
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(BASIC_STRUCTURES_SET, console.getCommandCard().getDisplayedSet());

            int colonySlot = findCreepColonySlot(console.getCommandCard());

            Assertions.assertEquals(CommandInput.Result.INTERFACE, world.getCommandInput().pressSlot(colonySlot, false));
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertTrue(placeBuildingNear(world.getCommandInput(), hatchery), "a Creep Colony site on the creep");
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(NO_SET, console.getCommandCard().getInterfaceSet());

            Assertions.assertEquals(CommandInput.Result.INTERFACE, world.getCommandInput().pressSlot(basic, false));

            boolean morphed = false;
            boolean completed = false;

            for (int frame = 0; frame < BUILD_FRAMES && !completed; frame++) {
                session.stepFrame(false);
                console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
                morphed |= drone.getUnitId() == ScrUnitIds.ZERG_CREEP_COLONY;
                completed = morphed && (drone.getStatus() & UnitStatusFlags.COMPLETED) != 0;
            }

            Assertions.assertTrue(completed, "the Drone became a completed Creep Colony");

            CommandCard card = console.getCommandCard();

            Assertions.assertEquals(drone, console.getSelection().getActiveUnit());
            Assertions.assertEquals(BASIC_STRUCTURES_SET, card.getInterfaceSet());
            Assertions.assertEquals(BASIC_STRUCTURES_SET, card.getDisplayedSet());

            for (int slot = 0; slot < CommandCard.SLOT_COUNT; slot++) {
                ButtonDefinition button = card.getButton(slot);

                Assertions.assertTrue(button == null || button.getIcon() != SUNKEN_COLONY_ICON);
            }

            // Selecting the colony again runs the select routine: the colony's own card with the Sunken Colony morph.
            world.getCommandInput().select(drone);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(CREEP_COLONY_SET, card.getDisplayedSet());
        }
    }

    @Test
    void train_button_of_an_added_unit_type_follows_the_producer_buttons_of_its_position() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            AddedGraphicsScenario added = AddedGraphicsScenario.register(gameData);
            Game game = RunningGameScenario.start(gameData).getGame();
            Unit commandCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);

            Assertions.assertNotNull(commandCenter);

            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));

            console.showLocalSelection(commandCenter);
            console.update(commandCenter.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            CommandCard card = console.getCommandCard();
            int slot = AddedGraphicsScenario.TRAIN_BUTTON_POSITION - 1;
            ButtonDefinition train = card.getButton(slot);
            ButtonDefinition scv = card.getButton(0);

            // The grounded Command Center hides Stop, so the added train button of the same position shows.
            Assertions.assertNotNull(train);
            Assertions.assertSame(ButtonAction.TRAIN, train.getAction());
            Assertions.assertEquals(added.getUnitTypeId(), train.getActionValue());
            Assertions.assertEquals(ScrUnitIds.TERRAN_MARINE, train.getIcon());
            Assertions.assertEquals(String.valueOf(AddedGraphicsScenario.TRAIN_BUTTON_HOTKEY) + ButtonDefinition.UNIT_PRODUCTION_KIND +
                                    AddedGraphicsScenario.TRAIN_BUTTON_TEXT, train.getEnabledText());
            Assertions.assertEquals(CommandCard.ENABLED, card.getState(slot));
            Assertions.assertNotNull(scv);
            Assertions.assertEquals(ScrUnitIds.TERRAN_SCV, scv.getActionValue());
        }
    }

    @Test
    void train_button_of_an_added_unit_type_shows_its_own_command_icon() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            AddedGraphicsScenario added = AddedGraphicsScenario.registerWithInterfaceArt(gameData);
            Game game = RunningGameScenario.start(gameData).getGame();
            Unit commandCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);

            Assertions.assertNotNull(commandCenter);

            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));

            console.showLocalSelection(commandCenter);
            console.update(commandCenter.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            ButtonDefinition train = console.getCommandCard().getButton(AddedGraphicsScenario.TRAIN_BUTTON_POSITION - 1);

            Assertions.assertNotNull(train);
            Assertions.assertEquals(added.getUnitTypeId(), train.getActionValue());
            Assertions.assertEquals(ScrAddedGraphics.FIRST_INTERFACE_FRAME, train.getIcon());
            Assertions.assertEquals(ScrAddedGraphics.FIRST_INTERFACE_FRAME, gameData.getWireframe(added.getUnitTypeId()));
            Assertions.assertEquals(ScrAddedGraphics.FIRST_PORTRAIT_ID,
                                    game.requireGameData().getUnitTypes().getPortrait(added.getUnitTypeId()));
            Assertions.assertEquals(ScrUnitIds.TERRAN_MARINE, gameData.getWireframeType(added.getUnitTypeId()));
            Assertions.assertEquals(ScrUnitIds.TERRAN_SCV, gameData.getCommandIcon(ScrUnitIds.TERRAN_SCV));
            Assertions.assertEquals(ScrUnitIds.TERRAN_SCV, gameData.getWireframe(ScrUnitIds.TERRAN_SCV));
        }
    }

    @Test
    void morph_button_of_an_added_unit_type_issues_a_building_morph() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ScrAddedUnitType.TrainButton button = new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HATCHERY, MORPH_BUTTON_POSITION,
                                                                                   ScrAddedUnitType.ProductionAction.BUILDING_MORPH,
                                                                                   MORPH_BUTTON_HOTKEY, MORPH_BUTTON_TEXT);
            int added = gameData.getAddedUnitTypes().add(
                    new ScrAddedUnitType.Builder(new UnitType(MORPH_RESULT_NAME), MORPH_RESULT_NAME, ScrUnits.ZERG_ULTRALISK, gameData.getUnits())
                            .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                            .setRequirementScript(new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY).build())
                            .setTrainButton(button)
                            .build()
            );
            ReplayRuntime runtime = RunningGameScenario.createRuntime(ReplayFile.parse(GameTestResources.readAllBytes(REPLAY)), gameData);

            runtime.start(0, 0);

            Game game = runtime.getGame();

            while (game.getCurrentFrame() < SETTLE_FRAMES) {
                Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
            }

            Unit hatchery = findHatchery(game);
            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));

            // The card shows the Hatchery owner's perspective first, so the local selection is that player's.
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
            console.showLocalSelection(hatchery);
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            CommandCard card = console.getCommandCard();
            ButtonDefinition morph = card.getButton(MORPH_BUTTON_POSITION - 1);

            Assertions.assertNotNull(morph);
            Assertions.assertSame(ButtonAction.BUILDING_MORPH, morph.getAction());
            Assertions.assertSame(ButtonCondition.UNIT_REQUIREMENT, morph.getCondition());
            Assertions.assertEquals(added, morph.getActionValue());
            Assertions.assertEquals(String.valueOf(MORPH_BUTTON_HOTKEY) + ButtonDefinition.UNIT_PRODUCTION_KIND + MORPH_BUTTON_TEXT,
                                    morph.getEnabledText());
            Assertions.assertEquals(CommandCard.ENABLED, card.getState(MORPH_BUTTON_POSITION - 1));
        }
    }

    @Test
    void button_of_an_added_unit_type_shows_its_texts_in_the_client_language() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
             CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            ScrAddedUnitType.TrainButton.Text open = new ScrAddedUnitType.TrainButton.Text(MORPH_BUTTON_TEXT, null);
            ScrAddedUnitType.TrainButton.Text locked = new ScrAddedUnitType.TrainButton.Text(LOCKED_BUTTON_TEXT, LOCKED_BUTTON_DISABLED_TEXT);
            ScrAddedUnitType.TrainButton.Text koreanLocked = new ScrAddedUnitType.TrainButton.Text(KOREAN_LOCKED_BUTTON_TEXT,
                                                                                                   KOREAN_LOCKED_BUTTON_DISABLED_TEXT);
            int openType = gameData.getAddedUnitTypes().add(
                    new ScrAddedUnitType.Builder(new UnitType(MORPH_RESULT_NAME), MORPH_RESULT_NAME, ScrUnits.ZERG_ULTRALISK, gameData.getUnits())
                            .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                            .setRequirementScript(new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY).build())
                            .setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HATCHERY, MORPH_BUTTON_POSITION,
                                                                             ScrAddedUnitType.ProductionAction.BUILDING_MORPH,
                                                                             MORPH_BUTTON_HOTKEY, open, Map.of()))
                            .build()
            );
            gameData.getAddedUnitTypes().add(
                    new ScrAddedUnitType.Builder(new UnitType(LOCKED_RESULT_NAME), LOCKED_RESULT_NAME, ScrUnits.ZERG_ULTRALISK, gameData.getUnits())
                            .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                            .setRequirementScript(new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY)
                                                                                    .requireCompleted(ScrUnits.ZERG_ULTRALISK_CAVERN)
                                                                                    .build())
                            .setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HATCHERY, LOCKED_BUTTON_POSITION,
                                                                             ScrAddedUnitType.ProductionAction.BUILDING_MORPH,
                                                                             LOCKED_BUTTON_HOTKEY, locked, Map.of(KOREAN_LOCALE, koreanLocked)))
                            .build()
            );
            ReplayRuntime runtime = RunningGameScenario.createRuntime(ReplayFile.parse(GameTestResources.readAllBytes(REPLAY)), gameData);

            runtime.start(0, 0);

            Game game = runtime.getGame();

            while (game.getCurrentFrame() < SETTLE_FRAMES) {
                Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
            }

            Unit hatchery = findHatchery(game);
            ScrGameView view = new ScrGameView(game);
            ConsoleModel console = new ConsoleModel(view, new ScrPresentation(game));

            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
            console.showLocalSelection(hatchery);
            console.update(hatchery.getOwnerIndex(), CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            CommandCard card = console.getCommandCard();
            StatText english = StatText.load(storage, ENGLISH_LOCALE);
            StatText korean = StatText.load(storage, KOREAN_LOCALE);
            int openSlot = MORPH_BUTTON_POSITION - 1;
            int lockedSlot = LOCKED_BUTTON_POSITION - 1;

            // A missing requirement: the disabled button names it, in the client's language or the default one.
            Assertions.assertEquals(CommandCard.DISABLED, card.getState(lockedSlot));
            Assertions.assertEquals(LOCKED_BUTTON_DISABLED_TEXT, buildTooltip(view, english, card, lockedSlot, hatchery).getText());
            Assertions.assertEquals(KOREAN_LOCKED_BUTTON_DISABLED_TEXT, buildTooltip(view, korean, card, lockedSlot, hatchery).getText());
            Assertions.assertTrue(buildTooltip(view, korean, card, lockedSlot, hatchery).getCosts().isEmpty());

            // Every requirement met: the enabled text with the cost; no translation shows the default language.
            ButtonTooltip enabled = buildTooltip(view, korean, card, openSlot, hatchery);

            Assertions.assertEquals(CommandCard.ENABLED, card.getState(openSlot));
            Assertions.assertEquals(MORPH_BUTTON_TEXT, enabled.getText());
            Assertions.assertEquals(game.requireGameData().getUnitTypes().getMineralCost(openType), enabled.getCosts().getFirst().getAmount());
        }
    }

    private static @NotNull ButtonTooltip buildTooltip(@NotNull ScrGameView view, @NotNull StatText text, @NotNull CommandCard card, int slot,
                                                       @NotNull Unit active) {
        ButtonDefinition button = card.getButton(slot);

        Assertions.assertNotNull(button);

        ButtonTooltip tooltip = ButtonTooltip.build(view, text, new HotkeyProfile(text, HotkeyProfile.DEVICE, Map.of()), button,
                                                    card.getState(slot) == CommandCard.DISABLED, card.getDisabledString(slot), active,
                                                    active.getOwnerIndex(), ScrRaceIds.ZERG);

        Assertions.assertNotNull(tooltip);

        return tooltip;
    }
}
