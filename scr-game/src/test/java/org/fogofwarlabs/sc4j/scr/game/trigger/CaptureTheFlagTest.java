package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.resource.CarryingFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.VictoryCondition;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.util.stream.IntStream;

/**
 * Checks unsupported Flag notice tracking through beacon scoring and worker pickup in an initialized game.
 */
@RequiresInstallation
final class CaptureTheFlagTest {
    private static final int INITIAL_FRAME_COUNT = 100;
    private static final int HOST_TICK_INTERVAL = 42;
    private static final int FLAG_BEACON_SCAN_DELAY = 15;
    private static final int CREATION_TAG_SHIFT = 8;
    private static final int UNKNOWN_VICTORY_CONDITION = 2;
    private static final int OUT_OF_RANGE_VICTORY_CONDITION = 260;
    private static final @NotNull String MISSING_SETTINGS_MESSAGE = "The flag message requires the established game options.";

    /**
     * The Die order of a scored Flag.
     */
    private static final int DIE_ORDER = 0;
    /**
     * The Nothing order of a carried Flag.
     */
    private static final int NOTHING_ORDER = 23;
    /**
     * The carrying flags of a worker that carries a powerup.
     */
    private static final int POWERUP_CARRYING_FLAGS = 0x4;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws Exception {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    private static @NotNull Game startGame() throws Exception {
        Assertions.assertNotNull(gameData);

        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_UMS_CAPTURE_THE_FLAG)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        Game game = runtime.getGame();
        int hostTick = 0;

        while (game.getCurrentFrame() < INITIAL_FRAME_COUNT) {
            hostTick += HOST_TICK_INTERVAL;

            Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, hostTick));
            Assertions.assertNull(runtime.getFailure());
        }

        game.getInterfaceLink().getMessageLines().requireObservable();

        return game;
    }

    private static @NotNull Unit findBeacon(@NotNull Game game) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getPrimaryOrder() == ScrOrderIds.CTFCOP_INIT && unit.getOwnerIndex() < PlayerTable.PLAYABLE_PLAYER_SLOTS) {
                return unit;
            }
        }

        return Assertions.fail("The initialized map must contain a Flag beacon.");
    }

    private static @NotNull Unit createWorker(@NotNull Game game, @NotNull Unit beacon) {
        Unit worker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SCV, beacon.getPositionX(), beacon.getPositionY(), beacon.getOwnerIndex());

        Assertions.assertEquals(beacon.getOwnerIndex(), worker.getOwnerIndex());
        Assertions.assertNotEquals(0, game.requireGameData().getUnitTypes().getFlags(worker.getUnitId()) & UnitTypeFlags.WORKER);
        Assertions.assertNotEquals(0, worker.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(0, worker.getCarryingFlags());
        Assertions.assertNull(worker.getCarriedPowerup());

        return worker;
    }

    private static @NotNull Unit createFlag(@NotNull Game game, @NotNull Unit beacon) {
        UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();
        int flagOwner = PlayerTable.NEUTRAL_PLAYER_ID;
        Player player = game.getPlayerTable().getPlayer(flagOwner);

        Assertions.assertNotNull(creation);
        Assertions.assertNotNull(accounting);
        Assertions.assertNotNull(player);
        Assertions.assertNotEquals(flagOwner, beacon.getOwnerIndex());

        Unit flag = game.getUnitStorageConstructor().create(creation, accounting, ScrUnitIds.POWERUP_FLAG,
                                                            beacon.getPositionX(), beacon.getPositionY(), flagOwner,
                                                            flagOwner | flagOwner << CREATION_TAG_SHIFT, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertNotNull(flag);

        game.getUnitVisibility().show(flag, creation.isFoliageEnabled());
        game.getUnitCompletion().initializeBeforePlacement(creation, flag, RunningGameScenario.INSTRUCTION_LIMIT);
        game.getUnitCompletion().completeAfterPlacement(creation, flag, player.getType(), RunningGameScenario.INSTRUCTION_LIMIT);

        return flag;
    }

    private static void setVictoryCondition(@NotNull Game game, int victoryCondition) {
        GameSettings settings = game.getGameSettings();

        Assertions.assertNotNull(settings);

        boolean[] createMeleeUnitsForPlayer = new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int playerSlot = 0; playerSlot < createMeleeUnitsForPlayer.length; playerSlot++) {
            createMeleeUnitsForPlayer[playerSlot] = settings.createsMeleeUnitsForPlayer(playerSlot);
        }

        game.setGameSettings(new GameSettings(victoryCondition, settings.getResourceType(), settings.getStartingMinerals(),
                                              settings.getCreateInitialUnits(), createMeleeUnitsForPlayer, settings.getTournamentMode()));
    }

    private static @NotNull IntStream nonFlagVictoryConditions() {
        return IntStream.of(VictoryCondition.NONE.getValue(), VictoryCondition.MELEE.getValue(), UNKNOWN_VICTORY_CONDITION,
                            VictoryCondition.GREED.getValue(), VictoryCondition.SUDDEN_DEATH.getValue(),
                            VictoryCondition.SLAUGHTER.getValue(), VictoryCondition.ONE_ON_ONE.getValue(), OUT_OF_RANGE_VICTORY_CONDITION);
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void enemy_flag_score_marks_the_actual_flag_owner_and_destroys_the_flag() throws Exception {
        Game game = startGame();
        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            Unit beacon = findBeacon(game);
            Unit flag = createFlag(game, beacon);
            int flagOwner = flag.getOwnerIndex();
            MessageLines lines = game.getInterfaceLink().getMessageLines();

            setVictoryCondition(game, VictoryCondition.CAPTURE_THE_FLAG.getValue());
            lines.requireObservable();
            game.getDoodadOrders().updateFlagBeacon(beacon, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(DIE_ORDER, flag.getPrimaryOrder());
            Assertions.assertNotEquals(0, flag.getUserActionFlags() & Unit.USER_ACTION_HIDE_ON_DEATH);
            Assertions.assertEquals(FLAG_BEACON_SCAN_DELAY, beacon.getPrimaryOrderCountdown());

            UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class, lines::requireObservable);

            Assertions.assertTrue(failure.getMessage().contains("strCTF_CAPTURED_s for Flag owner " + flagOwner));
        } finally {
            scope.endCommandDispatch();
        }
    }

    @Test
    void own_carrier_score_uses_the_carried_flag_owner_and_releases_the_carrier() throws Exception {
        Game game = startGame();
        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            Unit beacon = findBeacon(game);
            Unit worker = createWorker(game, beacon);
            Unit flag = createFlag(game, beacon);
            int flagOwner = flag.getOwnerIndex();
            MessageLines lines = game.getInterfaceLink().getMessageLines();

            game.getUnitPositioning().move(worker, beacon.getPositionX(), beacon.getPositionY());
            game.getUnitLifecycle().hide(flag, true);
            game.getOrders().issueUntargeted(flag, ScrOrderIds.NOTHING, RunningGameScenario.INSTRUCTION_LIMIT);
            flag.setPowerupCarrier(worker);
            worker.setCarryingFlags(CarryingFlags.POWERUP);
            worker.setCarriedPowerup(flag);
            setVictoryCondition(game, VictoryCondition.CAPTURE_THE_FLAG.getValue());
            lines.requireObservable();
            game.getDoodadOrders().updateFlagBeacon(beacon, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(worker.getCarriedPowerup());
            Assertions.assertEquals(0, worker.getCarryingFlags());
            Assertions.assertEquals(DIE_ORDER, flag.getPrimaryOrder());
            Assertions.assertEquals(FLAG_BEACON_SCAN_DELAY, beacon.getPrimaryOrderCountdown());

            UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class, lines::requireObservable);

            Assertions.assertTrue(failure.getMessage().contains("strCTF_CAPTURED_s for Flag owner " + flagOwner));
        } finally {
            scope.endCommandDispatch();
        }
    }

    @Test
    void flag_pickup_marks_the_actual_flag_owner_and_preserves_the_carry_relationship() throws Exception {
        Game game = startGame();
        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            Unit beacon = findBeacon(game);
            Unit worker = createWorker(game, beacon);
            Unit flag = createFlag(game, beacon);
            int flagOwner = flag.getOwnerIndex();
            MessageLines lines = game.getInterfaceLink().getMessageLines();

            game.getUnitPositioning().move(worker, flag.getPositionX(), flag.getPositionY());
            setVictoryCondition(game, VictoryCondition.CAPTURE_THE_FLAG.getValue());
            lines.requireObservable();
            game.getUnitPowerups().updateIdle(flag, false, RunningGameScenario.INSTRUCTION_LIMIT);

            Unit carrier = flag.getPowerupCarrier();

            Assertions.assertNotNull(carrier);
            Assertions.assertNotEquals(flagOwner, carrier.getOwnerIndex());
            Assertions.assertSame(flag, carrier.getCarriedPowerup());
            Assertions.assertEquals(POWERUP_CARRYING_FLAGS, carrier.getCarryingFlags());
            Assertions.assertEquals(NOTHING_ORDER, flag.getPrimaryOrder());

            Sprite flagSprite = flag.getSpriteOrNull();
            Sprite carrierSprite = carrier.getSpriteOrNull();

            Assertions.assertNotNull(flagSprite);
            Assertions.assertNotNull(carrierSprite);
            Assertions.assertNotEquals(0, flagSprite.getFlags() & Sprite.FLAG_HIDDEN);

            boolean hasFlagImage = false;

            for (SpriteImage image : carrierSprite.getImages()) {
                hasFlagImage |= image.getImageTypeId() == ImageIds.FLAG;
            }

            Assertions.assertTrue(hasFlagImage);

            UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class, lines::requireObservable);

            Assertions.assertTrue(failure.getMessage().contains("strCTF_TAKEN_s for Flag owner " + flagOwner));
        } finally {
            scope.endCommandDispatch();
        }
    }

    @ParameterizedTest
    @MethodSource("nonFlagVictoryConditions")
    void flag_notices_leave_message_lines_known_for_other_victory_values(int victoryCondition) throws Exception {
        Game game = startGame();
        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            Unit beacon = findBeacon(game);
            Unit flag = createFlag(game, beacon);
            MessageLines lines = game.getInterfaceLink().getMessageLines();

            setVictoryCondition(game, victoryCondition);
            game.getDoodadOrders().updateFlagBeacon(beacon, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(DIE_ORDER, flag.getPrimaryOrder());
            Assertions.assertEquals(FLAG_BEACON_SCAN_DELAY, beacon.getPrimaryOrderCountdown());
            Assertions.assertDoesNotThrow(lines::requireObservable);

            Unit worker = createWorker(game, beacon);
            Unit takenFlag = createFlag(game, beacon);

            game.getUnitPositioning().move(worker, takenFlag.getPositionX(), takenFlag.getPositionY());
            game.getUnitPowerups().updateIdle(takenFlag, false, RunningGameScenario.INSTRUCTION_LIMIT);

            Unit carrier = takenFlag.getPowerupCarrier();

            Assertions.assertNotNull(carrier);
            Assertions.assertSame(takenFlag, carrier.getCarriedPowerup());
            Assertions.assertDoesNotThrow(lines::requireObservable);

            game.getUnitPositioning().move(carrier, beacon.getPositionX(), beacon.getPositionY());

            Assertions.assertEquals(beacon.getOwnerIndex(), carrier.getOwnerIndex());

            game.getDoodadOrders().updateFlagBeacon(beacon, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(carrier.getCarriedPowerup());
            Assertions.assertEquals(0, carrier.getCarryingFlags());
            Assertions.assertEquals(DIE_ORDER, takenFlag.getPrimaryOrder());
            Assertions.assertDoesNotThrow(lines::requireObservable);
        } finally {
            scope.endCommandDispatch();
        }
    }

    @Test
    void flag_notices_reject_a_game_without_established_settings() {
        GameData installedData = gameData;

        Assertions.assertNotNull(installedData);

        Game game = Game.create(installedData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        CaptureTheFlag flags = game.getCaptureTheFlag();
        int flagOwner = PlayerTable.NEUTRAL_PLAYER_ID;

        Assertions.assertNull(game.getGameSettings());

        UnsupportedOperationException scored = Assertions.assertThrows(UnsupportedOperationException.class,
                                                                       () -> flags.markFlagScoredUnrepresented(flagOwner));
        UnsupportedOperationException taken = Assertions.assertThrows(UnsupportedOperationException.class,
                                                                      () -> flags.markFlagTakenUnrepresented(flagOwner));

        Assertions.assertEquals(MISSING_SETTINGS_MESSAGE, scored.getMessage());
        Assertions.assertEquals(MISSING_SETTINGS_MESSAGE, taken.getMessage());
        Assertions.assertDoesNotThrow(game.getInterfaceLink().getMessageLines()::requireObservable);
    }
}
