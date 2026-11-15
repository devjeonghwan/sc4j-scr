package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.engine.replay.ReplaySummary;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tests the SC:R mod's replay format: a {@code .rep} file is recognized and summarized from its header, and content of another kind, an
 * engine replay's start included, is not taken for one.
 */
final class ScrReplayFormatTest {
    private static final int TRUNCATED_LENGTH = 10;

    private final @NotNull ScrReplayFormat format;

    ScrReplayFormatTest() {
        this.format = new ScrReplayFormat();
    }

    @Test
    void read_summary_should_return_the_map_length_and_named_players_of_the_header() throws IOException {
        byte[] contents = GameTestResources.readAllBytes(GameTestResources.REPLAY_PVZ_JUDGEMENT_DAY);
        ReplayHeader header = ReplayFile.parse(contents).getHeader();
        ReplaySummary summary = this.format.readSummary(contents);

        Assertions.assertTrue(this.format.isReplay(contents));
        Assertions.assertEquals("rep", this.format.getFileExtension());
        Assertions.assertEquals(header.getMap(), summary.getMapName());
        Assertions.assertEquals(header.getFrames(), summary.getFrameCount());
        Assertions.assertEquals(2, summary.getPlayerCount());
    }

    @Test
    void is_replay_should_refuse_content_of_another_kind() throws IOException {
        byte[] contents = GameTestResources.readAllBytes(GameTestResources.REPLAY_PVZ_JUDGEMENT_DAY);

        Assertions.assertFalse(this.format.isReplay("SC4R engine replay".getBytes(StandardCharsets.US_ASCII)));
        Assertions.assertFalse(this.format.isReplay(new byte[0]));
        Assertions.assertFalse(this.format.isReplay(Arrays.copyOf(contents, TRUNCATED_LENGTH)));
        Assertions.assertThrows(IOException.class, () -> this.format.readSummary("not a replay".getBytes(StandardCharsets.US_ASCII)));
    }
}
