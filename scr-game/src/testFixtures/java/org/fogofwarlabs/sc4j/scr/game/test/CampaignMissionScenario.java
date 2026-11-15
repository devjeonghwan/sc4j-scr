package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.CampaignMissions;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts a campaign mission of the installation as a live single-player game at the Fastest speed, exchanges its turns frame by frame and creates
 * completed units in it, for the tests that check a routine in a running mission.
 */
public final class CampaignMissionScenario {
    /**
     * The local player name of the started missions.
     */
    private static final @NotNull String PLAYER_NAME = "tester";
    /**
     * The game seed of the started missions.
     */
    private static final int GAME_SEED = 1790573170;

    /**
     * The test body that runs on a started mission.
     */
    public interface MissionBody {
        /**
         * Runs the test body on a started mission.
         *
         * @param game the live game of the mission
         * @param live the started runtime of the mission
         */
        void run(@NotNull LiveGame game, @NotNull ReplayRuntime live);
    }

    /**
     * Starts a campaign mission and runs a test body on it. The mission's live game uses the shared viewport and instruction limit of
     * {@link RunningGameScenario}; the installed data stay open until the body returns.
     *
     * @param gamePath  the installed game
     * @param mission   the campaign mission name, for example {@code bw-protoss-1}
     * @param expansion whether the mission is played as Brood War rather than StarCraft
     * @param body      the test body
     * @throws IOException if the installed data or the mission scenario cannot be read
     */
    public static void run(@NotNull Path gamePath, @NotNull String mission, boolean expansion, @NotNull MissionBody body) throws IOException {
        try (CascStorage storage = CascStorage.open(gamePath); GameData gameData = GameData.load(storage)) {
            CampaignMissions missions = CampaignMissions.load(storage);
            int missionIndex = missions.resolve(mission);
            byte[] mapData = missions.readScenario(storage, missionIndex, null);
            LiveGame game = new LiveGame(MeleeGameSetup.createCampaign(mapData, PLAYER_NAME, missionIndex, GameSpeed.FASTEST, expansion), GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(),
                                                              ChkFile.parseTolerant(mapData),
                                                              gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            body.run(game, live);
        }
    }

    /**
     * Exchanges the next turns of a started mission and steps one frame after each exchange.
     *
     * @param game  the live game of the mission
     * @param live  the started runtime of the mission
     * @param turns the number of turns
     */
    public static void exchangeTurnsAndStep(@NotNull LiveGame game, @NotNull ReplayRuntime live, int turns) {
        Game state = live.getGame();

        for (int turn = 0; turn < turns; turn++) {
            game.exchangeTurn(state.getCurrentFrame());

            Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, state.getCurrentFrame() * GameSpeed.FASTEST_DURATION),
                                  () -> String.valueOf(live.getFailure()));
        }
    }

    /**
     * Creates a {@link Unit} instance as a completed unit at the exact position, without the map clamping of
     * {@link RunningGameScenario#createUnit}.
     *
     * @param state     the running game of the mission
     * @param unitType  the unit type
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param owner     the owning player
     * @return the created {@link Unit} instance
     */
    public static @NotNull Unit createUnit(@NotNull Game state, int unitType, int positionX, int positionY, int owner) {
        UnitCreationContext creation = state.getUnitSystem().getUnitCreationContext();

        Assertions.assertNotNull(creation);

        UnitStorageConstructor constructor = state.getUnitStorageConstructor();
        UnitAccounting accounting = state.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        Unit unit = constructor.create(creation,
                                       accounting,
                                       unitType,
                                       positionX, positionY,
                                       owner,
                                       owner | owner << Byte.SIZE,
                                       RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);

        UnitStorageConstructor completion = state.getUnitStorageConstructor();
        Player completionOwner = state.getPlayerTable().getPlayer(owner);

        Assertions.assertNotNull(completionOwner);

        Assertions.assertTrue(completion.tryComplete(creation, unit, completionOwner.getType(), RunningGameScenario.INSTRUCTION_LIMIT));

        return unit;
    }


    /**
     * Creates a {@link CampaignMissionScenario} instance.
     */
    private CampaignMissionScenario() {
    }
}
