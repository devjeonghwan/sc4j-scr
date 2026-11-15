package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.engine.save.SaveSummary;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tests the SC:R mod's save format: a native saved game is recognized and summarized from its banner and game structure, the kind names the
 * file name extension, and content of another kind, an engine saved game's start included, is not taken for one.
 */
final class ScrSaveFormatTest {
    private static final int TRUNCATED_LENGTH = 10;

    private final @NotNull ScrSaveFormat format;

    ScrSaveFormatTest() {
        this.format = new ScrSaveFormat(SaveGameKind.SINGLE_PLAYER);
    }

    @Test
    void read_summary_should_return_the_title_frame_and_players_of_the_saved_game() throws IOException {
        byte[] contents = GameTestResources.readAllBytes(GameTestResources.SAVE_SINGLE_PLAYER_WITH_COMPUTER);
        SaveGameDescription description = SaveGameFile.parse(contents).getDescription();
        SaveSummary summary = this.format.readSummary(contents);

        Assertions.assertTrue(this.format.isSave(contents));
        Assertions.assertEquals("snx", this.format.getFileExtension());
        Assertions.assertEquals("mlx", new ScrSaveFormat(SaveGameKind.MULTIPLAYER).getFileExtension());
        Assertions.assertEquals(description.getScenarioTitle(), summary.getMapName());
        Assertions.assertEquals(description.getPlayers().size(), summary.getPlayerCount());
        Assertions.assertTrue(summary.getFrame() > 0, () -> "frame " + summary.getFrame());
    }

    @Test
    void is_save_should_refuse_content_of_another_kind() throws IOException {
        byte[] contents = GameTestResources.readAllBytes(GameTestResources.SAVE_SINGLE_PLAYER_WITH_COMPUTER);

        Assertions.assertFalse(this.format.isSave("SC4S engine save".getBytes(StandardCharsets.US_ASCII)));
        Assertions.assertFalse(this.format.isSave(new byte[0]));
        Assertions.assertFalse(this.format.isSave(Arrays.copyOf(contents, TRUNCATED_LENGTH)));
        Assertions.assertThrows(IOException.class, () -> this.format.readSummary("not a saved game".getBytes(StandardCharsets.US_ASCII)));
    }
}
