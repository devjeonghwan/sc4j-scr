package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.scr.client.glue.CharacterFile;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.CampaignMissions;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Verifies that {@link CampaignScreen#advanceProgress} advances the ID's campaign progress after a campaign mission won with
 * {@code there is no cow level}, as after a trigger victory.
 */
@RequiresInstallation
final class CampaignScreenTest {
    private static final @NotNull String MISSION_NAME = "bw-terran-1";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int FASTEST_SPEED = 6;
    private static final int GAME_SEED = 1790573170;
    private static final int INSTRUCTION_LIMIT = 100000;
    private static final int MAXIMUM_FRAMES = 400;
    private static final int CHEAT_FRAME = 8;
    /**
     * The Unix time of a typed line.
     */
    private static final int TYPED_TIME = 0x6543210F;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int RUNNING_OUTCOME = 0;
    private static final int VICTORY_OUTCOME = 3;
    private static final @NotNull String VICTORY_CHEAT = "there is no cow level";
    /**
     * The result of submitting a cheat text that queues the Cheat command.
     */
    private static final int CHEAT_QUEUED = 1;

    @TempDir
    @Nullable Path directory;

    @Test
    void cow_level_victory_advances_campaign_progress() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            CampaignMissions missions = CampaignMissions.load(storage);
            int mission = missions.resolve(MISSION_NAME);
            byte[] mapData = missions.readScenario(storage, mission, null);
            LiveGame game = new LiveGame(MeleeGameSetup.createCampaign(mapData, PLAYER_NAME, mission, FASTEST_SPEED, true), GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(),
                                                              ChkFile.parseTolerant(mapData),
                                                              gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();

            for (int frame = 0; frame < MAXIMUM_FRAMES && game.updateOutcome(state) == RUNNING_OUTCOME; frame++) {
                if (frame == CHEAT_FRAME) {
                    GameFrame victoryFrame = state.getFrame();

                    Assertions.assertNotNull(victoryFrame);
                    Assertions.assertEquals(CHEAT_QUEUED,
                                            game.submitText(VICTORY_CHEAT, victoryFrame.getVisibility().getCheatFlags(), state.areCheatsEnabled(),
                                                            state.getRuntimeRandom(), TYPED_TIME));
                }

                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(FRAME_MILLISECONDS, frame * FRAME_MILLISECONDS));
            }

            Assertions.assertEquals(VICTORY_OUTCOME, game.getLocalOutcome());

            Path characterDirectory = this.directory;

            Assertions.assertNotNull(characterDirectory);

            CharacterFile character = CharacterFile.create(characterDirectory, PLAYER_NAME, PLAYER_NAME.getBytes(StandardCharsets.UTF_8));
            int before = character.getCampaignProgress(true, CharacterFile.Race.TERRAN);
            CampaignEpisodes.Entry won = CampaignEpisodes.findMissionEntry(missions.isExpansion(mission),
                                                                           game.getReplay().getHeader().getCampaignMission());

            Assertions.assertNotNull(won);

            CampaignEpisodes.Entry next = won.createNextEntry();

            Assertions.assertNotNull(next);

            CampaignScreen.advanceProgress(character, missions.isExpansion(mission), next);

            Path destinationDirectory = this.directory;
            Path listedDirectory = this.directory;

            Assertions.assertNotNull(listedDirectory);

            String[] characterFiles = listedDirectory.toFile().list();

            Assertions.assertNotNull(characterFiles);
            Assertions.assertNotNull(destinationDirectory);

            CharacterFile reread = CharacterFile.read(destinationDirectory.resolve(characterFiles[0]));

            Assertions.assertNotNull(reread);
            Assertions.assertTrue(reread.getCampaignProgress(true, CharacterFile.Race.TERRAN) > before,
                                  "progress " + before + " -> " + reread.getCampaignProgress(true, CharacterFile.Race.TERRAN));
        }
    }
}
