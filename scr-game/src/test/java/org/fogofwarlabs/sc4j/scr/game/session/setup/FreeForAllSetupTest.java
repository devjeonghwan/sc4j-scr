package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks free-for-all template headers and computer alliances.
 */
@RequiresInstallation
final class FreeForAllSetupTest {
    private static final int TEMPLATE_OPTIONS_START = 0x40;
    private static final int TEMPLATE_OPTIONS_END = 0x61;
    private static final int HEADER_TEMPLATE_OFFSET = 0x40;
    /**
     * The game types of a free-for-all and of a Use Map Settings game.
     */
    private static final int FREE_FOR_ALL_GAME_TYPE = 3;
    private static final int USE_MAP_SETTINGS_GAME_TYPE = 10;

    private static final @NotNull String TWO_PLAYER_MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String FOUR_PLAYER_MAP_NAME = "(4)Lost Civilization.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String FREE_FOR_ALL_TEMPLATE = "locales/enUS/Assets/Templates/Free For All(1).got";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 0x1234;

    @Test
    void free_for_all_header_matches_the_installed_template() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, TWO_PLAYER_MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            byte[] template = storage.readFile(FREE_FOR_ALL_TEMPLATE);
            MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);

            setup.setTemplate(GameTemplate.FREE_FOR_ALL);

            Assertions.assertEquals(FREE_FOR_ALL_GAME_TYPE, setup.getGameType());

            byte[] header = ReplayFile.readHeaderSection(new LiveGame(setup, 1).writeReplay(0, 0));

            for (int offset = TEMPLATE_OPTIONS_START; offset < TEMPLATE_OPTIONS_END; offset++) {
                Assertions.assertEquals(template[offset], header[HEADER_TEMPLATE_OFFSET + offset],
                                        "Free For All(1).got byte 0x" + Integer.toHexString(offset));
            }
        }
    }

    @Test
    void only_melee_allies_the_computers() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, FOUR_PLAYER_MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (GameTemplate template : new GameTemplate[]{GameTemplate.MELEE, GameTemplate.FREE_FOR_ALL}) {
                MeleeGameSetup setup = new MeleeGameSetup(mapData,
                                                          PLAYER_NAME, ScrRaceIds.TERRAN, 0,
                                                          new int[]{ScrRaceIds.ZERG, ScrRaceIds.PROTOSS},
                                                          GameSpeed.FASTEST);

                setup.setTemplate(template);

                LiveGame game = new LiveGame(setup, GAME_SEED);
                ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                                  game.getLocalPlayer(),
                                                                  RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                  RunningGameScenario.INSTRUCTION_LIMIT);

                live.start(0, 0);

                Assertions.assertNull(live.getFailure());

                Game state = live.getGame();
                List<Integer> computers = new ArrayList<>();

                for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                    Player resolvedPlayer = state.getPlayerTable().getPlayer(player);

                    Assertions.assertNotNull(resolvedPlayer);

                    if (resolvedPlayer.getType() == PlayerTypes.COMPUTER) {
                        computers.add(player);
                    }
                }

                Assertions.assertEquals(2, computers.size());

                PlayerRelations relations = state.getPlayerTable().getPlayerRelations();

                Assertions.assertNotNull(relations);

                int alliance = relations.getAlliance(computers.getFirst(), computers.get(1));

                if (template == GameTemplate.MELEE) {
                    Assertions.assertNotEquals(0, alliance, "Melee allies the computers.");
                } else {
                    Assertions.assertEquals(0, alliance, "Free For All leaves the computers unallied.");
                }

                Assertions.assertEquals(0, relations.getAlliance(game.getLocalPlayer(), computers.getFirst()));
            }
        }
    }

    @Test
    void use_map_settings_keeps_its_type_and_rejects_free_for_all_template() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, TWO_PLAYER_MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
        MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, GameSpeed.FASTEST);

        Assertions.assertEquals(USE_MAP_SETTINGS_GAME_TYPE, setup.getGameType());
        Assertions.assertThrows(IllegalArgumentException.class, () -> setup.setTemplate(GameTemplate.FREE_FOR_ALL));
    }
}
