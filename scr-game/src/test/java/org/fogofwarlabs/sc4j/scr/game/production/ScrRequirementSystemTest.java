package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the requirement script words and their indexes, and order, technology and production requirements in a running game.
 */
final class ScrRequirementSystemTest {
    private static final int HUMAN = RunningGameScenario.HUMAN;
    private static final int OTHER_OWNER = RunningGameScenario.COMPUTER;
    private static final int START_FRAME = 100;

    private static final int UNIT_POSITION_X = 3600;
    private static final int UNIT_POSITION_Y = 2200;
    private static final int ZERGLING_POSITION_X = 3640;
    private static final int VULTURE_POSITION_Y = 2260;
    private static final int CARRIER_POSITION_X = 3700;
    private static final int CANAL_POSITION_X = 3400;
    private static final int CANAL_POSITION_Y = 2400;
    private static final int NYDUS_EXIT_POSITION_X = 3600;
    private static final int NYDUS_EXIT_POSITION_Y = 2400;
    private static final int SILO_POSITION_X = 3400;
    private static final int SILO_POSITION_Y = 2500;
    private static final int MISSILE_POSITION_X = 3500;
    private static final int MISSILE_POSITION_Y = 2500;

    private static final int SPIDER_MINE_COUNT = 3;
    private static final int STORED_INTERCEPTOR_COUNT = 3;
    private static final int LAUNCHED_INTERCEPTOR_COUNT = 1;

    /**
     * The script words that require the current unit type, that end a script or a section, that require the expansion and that require a researched
     * technology.
     */
    private static final int CURRENT_UNIT_WORD = 0xFF02;
    private static final int END_WORD = 0xFFFF;
    private static final int EXPANSION_WORD = 0xFF24;
    private static final int TECHNOLOGY_RESEARCHED_WORD = 0xFF25;
    /**
     * The unit types of the Sunken Colony script: the Creep Colony it morphs from and the required Spawning Pool.
     */
    private static final int CREEP_COLONY_TYPE = 143;
    private static final int SPAWNING_POOL_TYPE = 142;
    /**
     * The unit type and the technology of the Lurker script: the Hydralisk it morphs from and Lurker Aspect.
     */
    private static final int HYDRALISK_TYPE = 38;
    private static final int LURKER_ASPECT_TECHNOLOGY = 32;
    /**
     * The words of the Terran Infantry Armor upgrade script with its level sections.
     */
    private static final int @NotNull [] INFANTRY_ARMOR_SCRIPT = {0xFF02, 0x7A, 0xFF05, 0xFF07, 0xFF1F, 0xFFFF,
                                                                  0xFF20, 0x74, 0xFFFF,
                                                                  0xFF21, 0x74, 0xFFFF};
    /**
     * The words of the Burrowing research script with its alternative producers.
     */
    private static final int @NotNull [] BURROWING_SCRIPT = {0xFF02, 0x83, 0xFF01, 0xFF02, 0x84, 0xFF01, 0xFF02, 0x85, 0xFF07, 0xFFFF};
    /**
     * The producer of the expansion-gated Restoration research, the Academy.
     */
    private static final int ACADEMY_TYPE = 0x70;

    private static final int FAILURE_NOT_OWNER = 1;
    private static final int FAILURE_FULL = 3;
    private static final int FAILURE_BUSY = 5;
    private static final int FAILURE_REQUIREMENT = 8;
    private static final int FAILURE_BURROWED = 0xB;
    private static final int FAILURE_NO_SCRIPT = 0x17;
    private static final int FAILURE_WRONG_UNIT = 0x19;
    private static final int DISABLED = 0x10000;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @AfterAll
    static void close() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }

        gameData = null;
    }

    /**
     * Starts the shared game of the installation tests on the first call and advances it to {@link #START_FRAME}; later calls keep the running game.
     *
     * @throws IOException if the replay or the installed game data cannot be read
     */
    private static void startGame() throws IOException {
        if (runtime != null) {
            return;
        }

        Path path = GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER);
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(path));

        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < START_FRAME) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }
    }


    @RequiresInstallation
    @Test
    void check_technology_use_should_reject_burrowed_caster_only_without_computer_record() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit defiler = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_DEFILER, UNIT_POSITION_X, UNIT_POSITION_Y, HUMAN);
            int darkSwarm = ScrTechIds.DARK_SWARM;

            Assertions.assertEquals(0, game.getRequirements().checkTechnologyUse(defiler, darkSwarm, HUMAN));

            defiler.setStatus(defiler.getStatus() | UnitStatusFlags.BURROWED);

            Assertions.assertEquals(FAILURE_BUSY,
                                    game.getRequirements().checkTechnologyUse(defiler, darkSwarm, HUMAN),
                                    "0xff11 without a record");

            defiler.setComputerRecord(new ComputerUnitRecord(ComputerUnitRecord.KIND_MILITARY, defiler.getIndex()));

            try {
                Assertions.assertEquals(0, game.getRequirements().checkTechnologyUse(defiler, darkSwarm, HUMAN), "0xff11 passes with a record");
            } finally {
                defiler.setComputerRecord(null);
            }
        });
    }

    @RequiresInstallation
    @Test
    void check_order_should_let_burrowed_unit_past_burrow_gate_only_for_unburrowing() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit zergling = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, ZERGLING_POSITION_X, UNIT_POSITION_Y, HUMAN);

            Assertions.assertEquals(0, game.getRequirements().checkOrder(zergling, ScrOrderIds.MOVE, HUMAN));

            zergling.setStatus(zergling.getStatus() | UnitStatusFlags.BURROWED);

            Assertions.assertEquals(FAILURE_BURROWED, game.getRequirements().checkOrder(zergling, ScrOrderIds.MOVE, HUMAN));
            Assertions.assertEquals(FAILURE_NO_SCRIPT,
                                    game.getRequirements().checkOrder(zergling, ScrOrderIds.UNBURROWING, HUMAN),
                                    "past the burrow gate; no order script");
            Assertions.assertEquals(FAILURE_NOT_OWNER,
                                    game.getRequirements().checkOrder(zergling, ScrOrderIds.MOVE, OTHER_OWNER),
                                    "another owner");
        });
    }

    @RequiresInstallation
    @Test
    void check_technology_use_should_need_spider_mine_research_and_a_mine_left() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit vulture = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_VULTURE, UNIT_POSITION_X, VULTURE_POSITION_Y, HUMAN);
            int mines = ScrTechIds.SPIDER_MINES;
            PlayerTechnology technology = game.getPlayerTable().getPlayerTechnology();

            Assertions.assertNotNull(technology);

            boolean researched = technology.isResearched(HUMAN, mines);

            technology.setResearched(HUMAN, mines, true);

            try {
                vulture.setSpiderMineCount(SPIDER_MINE_COUNT);

                Assertions.assertEquals(0, game.getRequirements().checkTechnologyUse(vulture, mines, HUMAN));

                vulture.setSpiderMineCount(0);

                Assertions.assertEquals(FAILURE_REQUIREMENT,
                                        game.getRequirements().checkTechnologyUse(vulture, mines, HUMAN),
                                        "0xff1b hides the button (native 0)");

                vulture.setSpiderMineCount(SPIDER_MINE_COUNT);
                technology.setResearched(HUMAN, mines, false);

                Assertions.assertEquals(DISABLED | FAILURE_REQUIREMENT,
                                        game.getRequirements().checkTechnologyUse(vulture, mines, HUMAN),
                                        "neither hero nor researched: the native returns -1, a disabled button");
            } finally {
                technology.setResearched(HUMAN, mines, researched);
            }
        });
    }

    @RequiresInstallation
    @Test
    void check_build_should_require_hangar_room_for_interceptor_and_reject_scarab_on_carrier() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_POSITION_X, UNIT_POSITION_Y, HUMAN);
            int interceptor = ScrUnitIds.PROTOSS_INTERCEPTOR;

            carrier.setHangarCount(false, STORED_INTERCEPTOR_COUNT);

            try {
                Assertions.assertEquals(0, game.getRequirements().checkBuild(carrier, interceptor, HUMAN));
                Assertions.assertEquals(DISABLED | FAILURE_WRONG_UNIT,
                                        game.getRequirements().checkBuild(carrier, ScrUnitIds.PROTOSS_SCARAB, HUMAN),
                                        "a Carrier is not a Reaver");

                carrier.setHangarCount(true, LAUNCHED_INTERCEPTOR_COUNT);

                Assertions.assertEquals(FAILURE_FULL,
                                        game.getRequirements().checkBuild(carrier, interceptor, HUMAN),
                                        "0xff0e: four of four without the upgrade");
            } finally {
                carrier.setHangarCount(true, 0);
                carrier.setHangarCount(false, 0);
            }
        });
    }

    @RequiresInstallation
    @Test
    void check_order_should_reject_nydus_exit_when_canal_is_linked() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit canal = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_NYDUS_CANAL, CANAL_POSITION_X, CANAL_POSITION_Y, HUMAN);
            Unit exit = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_NYDUS_CANAL, NYDUS_EXIT_POSITION_X, NYDUS_EXIT_POSITION_Y, HUMAN);

            canal.setNydusExit(exit);
            exit.setNydusExit(canal);

            try {
                Assertions.assertEquals(FAILURE_REQUIREMENT,
                                        game.getRequirements().checkOrder(canal, ScrOrderIds.BUILD_NYDUS_EXIT, HUMAN));
            } finally {
                exit.setNydusExit(null);
                canal.setNydusExit(null);
            }
        });
    }

    @RequiresInstallation
    @Test
    void check_order_should_accept_nydus_exit_when_canal_is_unlinked() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit canal = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_NYDUS_CANAL, CANAL_POSITION_X, CANAL_POSITION_Y, HUMAN);

            Assertions.assertEquals(0, game.getRequirements().checkOrder(canal, ScrOrderIds.BUILD_NYDUS_EXIT, HUMAN));
        });
    }

    @RequiresInstallation
    @Test
    void check_build_should_reject_nuclear_missile_when_silo_is_linked() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit silo = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_NUCLEAR_SILO, SILO_POSITION_X, SILO_POSITION_Y, HUMAN);
            Unit missile = RunningGameScenario.createUnit(game,
                                                          ScrUnitIds.TERRAN_NUCLEAR_MISSILE,
                                                          MISSILE_POSITION_X, MISSILE_POSITION_Y,
                                                          HUMAN);

            silo.setSiloNuke(missile);

            try {
                Assertions.assertEquals(FAILURE_FULL, game.getRequirements().checkBuild(silo, ScrUnitIds.TERRAN_NUCLEAR_MISSILE, HUMAN));
            } finally {
                silo.setSiloNuke(null);
            }
        });
    }

    @RequiresInstallation
    @Test
    void check_build_should_accept_nuclear_missile_when_silo_is_unlinked() throws IOException {
        startGame();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit silo = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_NUCLEAR_SILO, SILO_POSITION_X, SILO_POSITION_Y, HUMAN);

            Assertions.assertEquals(0, game.getRequirements().checkBuild(silo, ScrUnitIds.TERRAN_NUCLEAR_MISSILE, HUMAN));
        });
    }

    @Test
    void script_index_maps_build_entries_and_leaves_unused_upgrades_empty() {
        Assertions.assertNotEquals(0, ScrRequirementSystem.getBuildScriptStart(ScrUnitIds.PROTOSS_INTERCEPTOR));
        Assertions.assertEquals(CURRENT_UNIT_WORD,
                                ScrRequirementSystem.getBuildScriptWord(ScrRequirementSystem.getBuildScriptStart(ScrUnitIds.PROTOSS_INTERCEPTOR)));
        Assertions.assertEquals(0, ScrRequirementSystem.getUpgradeScriptStart(ScrUpgradeIds.UNKNOWN_18));
    }

    @Test
    void get_build_script_start_should_index_sunken_colony_and_lurker_scripts() {
        int sunken = ScrRequirementSystem.getBuildScriptStart(ScrUnitIds.ZERG_SUNKEN_COLONY);

        Assertions.assertEquals(CURRENT_UNIT_WORD, ScrRequirementSystem.getBuildScriptWord(sunken));
        Assertions.assertEquals(CREEP_COLONY_TYPE, ScrRequirementSystem.getBuildScriptWord(sunken + 1));
        Assertions.assertEquals(SPAWNING_POOL_TYPE, ScrRequirementSystem.getBuildScriptWord(sunken + 2));
        Assertions.assertEquals(END_WORD, ScrRequirementSystem.getBuildScriptWord(sunken + 3));

        int lurker = ScrRequirementSystem.getBuildScriptStart(ScrUnitIds.ZERG_LURKER);

        Assertions.assertEquals(EXPANSION_WORD, ScrRequirementSystem.getBuildScriptWord(lurker));
        Assertions.assertEquals(CURRENT_UNIT_WORD, ScrRequirementSystem.getBuildScriptWord(lurker + 1));
        Assertions.assertEquals(HYDRALISK_TYPE, ScrRequirementSystem.getBuildScriptWord(lurker + 2));
        Assertions.assertEquals(TECHNOLOGY_RESEARCHED_WORD, ScrRequirementSystem.getBuildScriptWord(lurker + 3));
        Assertions.assertEquals(LURKER_ASPECT_TECHNOLOGY, ScrRequirementSystem.getBuildScriptWord(lurker + 4));
    }

    @Test
    void get_build_script_start_should_return_zero_for_types_without_scripts() {
        Assertions.assertEquals(0, ScrRequirementSystem.getBuildScriptStart(ScrUnitIds.ZERG_LARVA));
        Assertions.assertEquals(0, ScrRequirementSystem.getBuildScriptStart(ScrUnitIds.RESOURCE_VESPENE_GEYSER));
    }

    @Test
    void get_upgrade_script_start_should_index_infantry_armor_with_level_sections() {
        int start = ScrRequirementSystem.getUpgradeScriptStart(ScrUpgradeIds.TERRAN_INFANTRY_ARMOR);

        for (int offset = 0; offset < INFANTRY_ARMOR_SCRIPT.length; offset++) {
            Assertions.assertEquals(INFANTRY_ARMOR_SCRIPT[offset], ScrRequirementSystem.getUpgradeScriptWord(start + offset), "word " + offset);
        }
    }

    @Test
    void get_research_script_start_should_index_alternative_burrowing_producers() {
        int start = ScrRequirementSystem.getResearchScriptStart(ScrTechIds.BURROWING);

        for (int offset = 0; offset < BURROWING_SCRIPT.length; offset++) {
            Assertions.assertEquals(BURROWING_SCRIPT[offset], ScrRequirementSystem.getResearchScriptWord(start + offset), "word " + offset);
        }
    }

    @Test
    void get_research_script_start_should_index_expansion_gated_research() {
        int start = ScrRequirementSystem.getResearchScriptStart(ScrTechIds.RESTORATION);

        Assertions.assertEquals(EXPANSION_WORD, ScrRequirementSystem.getResearchScriptWord(start));
        Assertions.assertEquals(CURRENT_UNIT_WORD, ScrRequirementSystem.getResearchScriptWord(start + 1));
        Assertions.assertEquals(ACADEMY_TYPE, ScrRequirementSystem.getResearchScriptWord(start + 2));
    }

    @Test
    void get_upgrade_script_start_should_return_zero_for_upgrades_without_scripts() {
        Assertions.assertEquals(0, ScrRequirementSystem.getUpgradeScriptStart(ScrUpgradeIds.UNKNOWN_18));
    }
}
