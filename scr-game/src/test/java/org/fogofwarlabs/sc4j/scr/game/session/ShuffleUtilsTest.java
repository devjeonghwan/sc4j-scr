package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayInitialization;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Checks the player-list shuffle and that it reseeds the game's C runtime generator, which the creep tile variants share.
 */
final class ShuffleUtilsTest {
    /**
     * The game seed, the generator state before a shuffle, and the order and state a shuffle of four entries leaves.
     */
    private static final int SEED = 0x1234;
    private static final int GAME_GENERATOR_STATE = 0x5EED1234;
    private static final @NotNull List<String> ENTRIES = List.of("first", "second", "third", "fourth");
    private static final @NotNull List<String> SHUFFLED_ENTRIES = List.of("first", "third", "fourth", "second");
    private static final int FOUR_ENTRY_SHUFFLE_STATE = 0x15072338;
    /**
     * The frame of the live game at which the list is shuffled, before the first creep growth, and the frame the game runs to.
     */
    private static final int SHUFFLE_FRAME = 24;
    private static final int END_FRAME = 12000;

    /**
     * Runs the Zerg-versus-Zerg replay as a live game of its first human, applies the action at {@link #SHUFFLE_FRAME} and
     * collects the tile identifiers, which hold the creep tile variants, at {@link #END_FRAME}.
     *
     * @param gameData       the installed game data
     * @param atShuffleFrame the action applied to the game at the shuffle frame
     * @param creepGrowth    receives the creep draws before and after the shuffle frame
     * @return the tile identifiers
     * @throws IOException if the replay cannot be read
     */
    private static int @NotNull [] runZergGame(@NotNull GameData gameData, @NotNull Consumer<Game> atShuffleFrame,
                                               int @NotNull [] creepGrowth) throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE)));
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        ReplayRuntime runtime = ReplayRuntime.createLiveGame(replay, ChkFile.parseTolerant(mapData), gameData,
                                                             ReplayInitialization.findNativeReplayLocalPlayer(replay.getHeader()),
                                                             RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                             RunningGameScenario.INSTRUCTION_LIMIT);

        runtime.start(0, 0);

        Game game = runtime.getGame();
        RuntimeRandom runtimeRandom = game.getRuntimeRandom();
        int startDraws = runtimeRandom.getDrawCount();

        while (game.getCurrentFrame() < SHUFFLE_FRAME) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        creepGrowth[0] = runtimeRandom.getDrawCount() - startDraws;

        atShuffleFrame.accept(game);

        int shuffleDraws = runtimeRandom.getDrawCount();

        while (game.getCurrentFrame() < END_FRAME && runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration())) {
            // The game runs past its creep growth.
        }

        creepGrowth[1] = runtimeRandom.getDrawCount() - shuffleDraws;

        GameMap map = game.getMap();
        int[] tiles = new int[map.getTileCount()];

        for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
            tiles[tileIndex] = map.getTileIdentifier(tileIndex);
        }

        return tiles;
    }

    @Test
    void shuffle_should_reseed_the_generator_with_the_seed_and_draw_once_per_entry() {
        RuntimeRandom runtimeRandom = new RuntimeRandom(GAME_GENERATOR_STATE);

        Assertions.assertEquals(SHUFFLED_ENTRIES, ShuffleUtils.shuffle(ENTRIES, SEED, runtimeRandom));
        Assertions.assertEquals(FOUR_ENTRY_SHUFFLE_STATE, runtimeRandom.getState());
        Assertions.assertEquals(ENTRIES.size(), runtimeRandom.getDrawCount());
    }

    @RequiresInstallation
    @Test
    void shuffle_before_creep_growth_changes_the_creep_variants_as_a_reseed_with_the_seed_does() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int[] plainGrowth = new int[2];
            int[] shuffledGrowth = new int[2];
            int[] reseededGrowth = new int[2];
            int[] plain = runZergGame(gameData, game -> {
            }, plainGrowth);
            int[] shuffled = runZergGame(gameData, game -> ShuffleUtils.shuffle(ENTRIES, SEED, game.getRuntimeRandom()), shuffledGrowth);
            int[] reseeded = runZergGame(gameData, game -> {
                RuntimeRandom runtimeRandom = game.getRuntimeRandom();

                runtimeRandom.seed(SEED);

                for (int draw = 0; draw < ENTRIES.size(); draw++) {
                    runtimeRandom.draw();
                }
            }, reseededGrowth);

            Assertions.assertEquals(0, plainGrowth[0], "no creep grows before the shuffle frame");
            Assertions.assertTrue(plainGrowth[1] > 0, "creep grows after the shuffle frame");
            Assertions.assertFalse(Arrays.equals(plain, shuffled), "the shuffle changes the creep variants drawn later");
            Assertions.assertArrayEquals(reseeded, shuffled, "the creep continues from the generator the shuffle reseeded");
        }
    }
}
