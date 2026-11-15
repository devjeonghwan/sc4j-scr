package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplaySettings;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Checks that replay startup allocates the starting units without initializing the world, which options start the melee scripts, and runs original
 * replays of the game modes: their startup gives the preplaced units, the team alliances, the map tables, triggers and shared vision of the mode, and
 * the replays run to their end.
 */
final class ReplayStartupTest {
    /**
     * The units the replay's map and melee startup allocate.
     */
    private static final int STARTING_UNIT_COUNT = 135;

    /**
     * The image drawing functions of a hallucination and of a shadow.
     */
    private static final int HALLUCINATION_IMAGE_MODIFIER = 16;
    private static final int SHADOW_IMAGE_MODIFIER = 10;
    /**
     * The neutral player that owns the preplaced critters.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The secondary order and the button set of a preplaced hallucinated critter.
     */
    private static final int HALLUCINATION_SECONDARY_ORDER = 149;
    private static final int HALLUCINATED_CRITTER_BUTTON_SET = 244;
    /**
     * The removal timer of a preplaced hallucination, and the remaining lifetime that the startup frames leave it above.
     */
    private static final int HALLUCINATION_LIFETIME = 1350;
    private static final int STARTED_HALLUCINATION_LIFETIME_BOUND = 1340;

    /**
     * The shared vision bits of players {@code 0}, {@code 1} and {@code 2}.
     */
    private static final int PLAYER_0_VISION = 1;
    private static final int PLAYER_1_VISION = 1 << 1;
    private static final int PLAYER_2_VISION = 1 << 2;
    /**
     * The frame by which the map triggers have shared the observer vision.
     */
    private static final int VISION_SHARED_FRAME = 5;
    /**
     * The players whose shared vision the vision test reads.
     */
    private static final int PLAYER_0 = 0;
    private static final int PLAYER_1 = 1;
    private static final int PLAYER_2 = 2;
    /**
     * A player of the top versus bottom replay and a player of the other team.
     */
    private static final int TEAM_PLAYER = 1;
    private static final int OTHER_TEAM_PLAYER = 3;
    /**
     * The game type of a top versus bottom game.
     */
    private static final int TOP_VS_BOTTOM_GAME_TYPE = 15;
    /**
     * The alliance of enemies and the allied victory alliance of a player with itself.
     */
    private static final int ENEMY_ALLIANCE = 0;
    private static final int ALLIED_VICTORY_ALLIANCE = 2;
    /**
     * The final frames of the top versus bottom, Lost Temple, Baekdudaegan, Defiler Perversion and Team Fighting Spirit replays.
     */
    private static final int TOP_VS_BOTTOM_END_FRAME = 16402;
    private static final int LOST_TEMPLE_END_FRAME = 13080;
    private static final int BAEKDUDAEGAN_END_FRAME = 25308;
    private static final int DEFILER_PERVERSION_END_FRAME = 5071;
    private static final int TEAM_FIGHTING_SPIRIT_END_FRAME = 3458;

    private static @NotNull ReplayFile readReplay(@NotNull String name) throws IOException {
        return ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(name)));
    }

    private static void runToEnd(@NotNull ReplayRuntime runtime) {
        int tick = 0;

        while (runtime.step(GameSpeed.FASTEST_DURATION, tick += GameSpeed.FASTEST_DURATION)) {
            Assertions.assertNull(runtime.getFailure());
        }

        ReplaySession session = runtime.getSession();

        Assertions.assertNotNull(session);
        Assertions.assertTrue(session.isFinished());
    }

    /**
     * Creates a {@link ReplaySettings} instance for the tested melee-script startup predicate.
     *
     * @param victory       the victory option
     * @param startingUnits the starting-unit option
     * @param players       the player option
     * @param tournament    the tournament option
     * @return the created {@link ReplaySettings} instance
     */
    private static @NotNull ReplaySettings createStartupSettings(int victory, int startingUnits, int players, int tournament) {
        return new ReplaySettings(0,
                                  victory,
                                  0,
                                  0,
                                  0,
                                  startingUnits,
                                  0,
                                  players,
                                  1, 0,
                                  0,
                                  tournament,
                                  0,
                                  0, 0,
                                  new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS]);
    }


    @RequiresInstallation
    @Test
    void replay_startup_allocates_starting_units_without_initializing_world() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayInitialization initialization = ReplayInitialization.createFreshReplay(replay.getHeader());
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayStartup startup = new ReplayStartup(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      initialization,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertFalse(startup.getGame().hasInitializedWorldState());

            UnitPool unitPool = startup.getGame().getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);
            Assertions.assertEquals(STARTING_UNIT_COUNT, unitPool.getAllocatedCount());
            Assertions.assertNotNull(startup.getGame().getSelection());
        }
    }

    @RequiresInstallation
    @Test
    void preplaced_critters_start_as_neutral_hallucinations() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_ZVP_PYTHON);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            int hallucinations = 0;

            for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
                Unit unit = pool.getUnit(poolIndex);

                if ((unit.getStatus() & UnitStatusFlags.HALLUCINATION) == 0) {
                    continue;
                }

                hallucinations++;

                Assertions.assertEquals(NEUTRAL_PLAYER, unit.getOwnerIndex());
                Assertions.assertEquals(HALLUCINATION_SECONDARY_ORDER, unit.getSecondaryOrder());
                Assertions.assertEquals(HALLUCINATED_CRITTER_BUTTON_SET, unit.getCurrentButtonSet());
                Assertions.assertTrue(unit.getRemainingLifetime() > STARTED_HALLUCINATION_LIFETIME_BOUND &&
                                      unit.getRemainingLifetime() <= HALLUCINATION_LIFETIME, "The removal timer is 1350.");

                Sprite sprite = unit.getSpriteOrNull();

                Assertions.assertNotNull(sprite);

                for (SpriteImage image : sprite.getImages()) {
                    Assertions.assertTrue(image.getModifier() == HALLUCINATION_IMAGE_MODIFIER || image.getModifier() == SHADOW_IMAGE_MODIFIER,
                                          "Replays draw every hallucination.");
                }

                UnitAccounting completedAccounting = game.getPlayerTable().getUnitAccounting();

                Assertions.assertNotNull(completedAccounting);
                Assertions.assertEquals(0, completedAccounting.getCompletedTypeCount(unit.getUnitId(), PlayerTable.NEUTRAL_PLAYER_ID));

                UnitAccounting typeAccounting = game.getPlayerTable().getUnitAccounting();

                Assertions.assertNotNull(typeAccounting);
                Assertions.assertEquals(0, typeAccounting.getTypeCount(unit.getUnitId(), PlayerTable.NEUTRAL_PLAYER_ID));
            }

            Assertions.assertTrue(hallucinations > 0, "The map's critters carry the valid and state hallucination bits.");
        }
    }

    @RequiresInstallation
    @Test
    void top_vs_bottom_replay_sets_team_alliances_and_runs_to_end() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_TVZ_LUNA);

        Assertions.assertEquals(TOP_VS_BOTTOM_GAME_TYPE, replay.getHeader().getType());

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            PlayerRelations relations = runtime.getGame().getPlayerTable().getPlayerRelations();

            Assertions.assertNotNull(relations);
            Assertions.assertEquals(ENEMY_ALLIANCE, relations.getAlliance(TEAM_PLAYER, OTHER_TEAM_PLAYER), "Players of different teams are enemies.");
            Assertions.assertEquals(ALLIED_VICTORY_ALLIANCE, relations.getAlliance(TEAM_PLAYER, TEAM_PLAYER),
                                    "An active team player has alliance 2 with itself.");

            runToEnd(runtime);

            Assertions.assertEquals(TOP_VS_BOTTOM_END_FRAME, runtime.getGame().getCurrentFrame());
        }
    }

    @RequiresInstallation
    @Test
    void use_map_settings_replay_copies_game_tables_registers_triggers_and_runs_to_end() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_UMS_LOST_TEMPLE);

        Assertions.assertTrue(replay.getHeader().getSettings().usesMapSettings());

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int installedHitPoints = gameData.getUnits().get("Hit Points", 0);
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            Game game = runtime.getGame();

            Assertions.assertNotSame(gameData, game.getGameData(), "Map settings use per-game tables.");

            // Every game has its own DAT memory (GameData.createGameCopy), which starts as the installed images table.
            DatTable installedImages = gameData.getImages();
            GameData copiedImageData = game.getGameData();

            Assertions.assertNotNull(copiedImageData);
            Assertions.assertNotSame(installedImages, copiedImageData.getImages());

            int installedScript = gameData.getImages().get("Iscript", 0);
            GameData copiedScriptData = game.getGameData();

            Assertions.assertNotNull(copiedScriptData);
            Assertions.assertEquals(installedScript, copiedScriptData.getImages().get("Iscript", 0));
            Assertions.assertEquals(installedHitPoints, gameData.getUnits().get("Hit Points", 0), "The installed tables are unchanged.");
            Assertions.assertNotNull(game.getPlayerTable().getPlayerUnitAvailability());

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            TriggerContext triggers = game.getTriggerContext();

            Assertions.assertNotNull(triggers);
            Assertions.assertTrue(triggers.getPlayerRecordCount(0) > 0, "The map TRIG records are registered.");

            runToEnd(runtime);

            Assertions.assertEquals(LOST_TEMPLE_END_FRAME, game.getCurrentFrame());
        }
    }

    @RequiresInstallation
    @Test
    void map_triggers_share_observer_vision_and_replay_runs_to_end() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_UMS_BAEKDUDAEGAN);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure(), "Use Map Settings startup completes.");

            PlayerRelations relations = runtime.getGame().getPlayerTable().getPlayerRelations();

            Assertions.assertNotNull(relations);
            Assertions.assertEquals(PLAYER_0_VISION, relations.getSharedVision(PLAYER_0));

            int tick = 0;

            while (runtime.getGame().getCurrentFrame() < VISION_SHARED_FRAME &&
                   runtime.step(GameSpeed.FASTEST_DURATION, tick += GameSpeed.FASTEST_DURATION)) {
                Assertions.assertNull(runtime.getFailure());
            }

            Assertions.assertEquals(PLAYER_0_VISION | PLAYER_2_VISION, relations.getSharedVision(PLAYER_0));
            Assertions.assertEquals(PLAYER_1_VISION | PLAYER_2_VISION, relations.getSharedVision(PLAYER_1));
            Assertions.assertEquals(PLAYER_2_VISION, relations.getSharedVision(PLAYER_2));

            runToEnd(runtime);

            Assertions.assertEquals(BAEKDUDAEGAN_END_FRAME, runtime.getGame().getCurrentFrame());
        }
    }

    @RequiresInstallation
    @Test
    void replay_with_set_alliance_status_triggers_runs_to_end() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_UMS_DEFILER_PERVERSION);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            runToEnd(runtime);

            Assertions.assertEquals(DEFILER_PERVERSION_END_FRAME, runtime.getGame().getCurrentFrame(),
                                    "Set Alliance Status no longer stops the replay.");
        }
    }

    @RequiresInstallation
    @Test
    void replay_with_bring_trigger_conditions_runs_to_end() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_UMS_TEAM_FIGHTING_SPIRIT);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            runToEnd(runtime);

            Assertions.assertEquals(TEAM_FIGHTING_SPIRIT_END_FRAME, runtime.getGame().getCurrentFrame(), "Bring counts units in map locations.");
        }
    }

    @Test
    void starts_melee_scripts_should_need_option_one_or_three_and_victory_units_or_tournament() {
        Assertions.assertTrue(ReplayStartup.startsMeleeScripts(createStartupSettings(1, 0, 3, 0)), "melee victory");
        Assertions.assertTrue(ReplayStartup.startsMeleeScripts(createStartupSettings(0, 2, 1, 0)), "starting units");
        Assertions.assertTrue(ReplayStartup.startsMeleeScripts(createStartupSettings(0, 0, 3, 1)), "tournament");
        Assertions.assertFalse(ReplayStartup.startsMeleeScripts(createStartupSettings(0, 0, 3, 0)), "use map settings");
        Assertions.assertFalse(ReplayStartup.startsMeleeScripts(createStartupSettings(1, 2, 2, 0)), "player option 2");
    }
}
