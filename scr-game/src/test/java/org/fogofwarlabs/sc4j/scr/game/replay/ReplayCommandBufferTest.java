package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayWriter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests which commands the replay command buffer runs for frames that fit it, overflow it, and run in or after their loading call.
 */
final class ReplayCommandBufferTest {
    private static final int EARLIER_FRAME = 1;
    private static final int LATER_FRAME = 2;
    private static final int OVERFLOW_FRAME = 10;

    private static final int RECORDED_ISSUER = 1;
    private static final int OVERFLOW_ISSUER = 3;

    /**
     * Six chat commands fill 492 of the 512 data bytes, so a third block of the frame grows the buffer at entry six.
     */
    private static final int CHATS_BEFORE_GROWTH = 6;
    private static final int CHATS_IN_OVERFLOW = 3;
    /**
     * The Alliance commands of the earlier frame whose stale entries the deferred frame runs.
     */
    private static final int STALE_ALLIANCES = 2;

    private static final int ALLIANCE_PAYLOAD = 4;
    /**
     * The command id of the Alliance command.
     */
    private static final int ALLIANCE_COMMAND = 0x0E;
    /**
     * A select whose 88 handle bytes end at data byte 500 after five chat commands; its last 8 bytes lie at the growth entry's offset.
     */
    private static final int COVERING_SELECT_HANDLES = 22;
    private static final int COVERING_SELECT_STALE_OFFSET = 81;

    private static @NotNull ReplayCommand createChat(int frame, int issuer, int marker) {
        byte[] payload = new byte[1 + ReplayFile.CHAT_MESSAGE_SIZE];

        payload[0] = (byte) marker;

        return new ReplayCommand(frame, issuer, ReplayCommandIds.CHAT, payload);
    }

    private static void addChats(@NotNull List<ReplayCommand> commands, int frame, int issuer, int count) {
        for (int index = 0; index < count; index++) {
            commands.add(createChat(frame, issuer, commands.size()));
        }
    }

    /**
     * Adds a frame of six chat commands in two blocks, then three chat commands of the overflow issuer in a third block that grows the buffer.
     *
     * @param commands the commands to add the frame to
     */
    private static void addOverflowFrame(@NotNull List<ReplayCommand> commands) {
        addChats(commands, OVERFLOW_FRAME, RECORDED_ISSUER, CHATS_BEFORE_GROWTH);
        addChats(commands, OVERFLOW_FRAME, OVERFLOW_ISSUER, CHATS_IN_OVERFLOW);
    }

    private static @NotNull ReplayCommandBuffer createBuffer(@NotNull List<ReplayCommand> commands) {
        return new ReplayCommandBuffer(commands, ReplayWriter.splitBlocks(commands));
    }

    /**
     * Loads and runs one frame: in its loading call, or after the call released the grown copy.
     *
     * @param buffer   the command buffer
     * @param deferred whether the frame runs after its loading call released the grown copy
     * @return the commands the frame ran, in order
     */
    private static @NotNull List<ReplayCommand> loadAndRun(@NotNull ReplayCommandBuffer buffer, boolean deferred) {
        List<ReplayCommand> run = new ArrayList<>();
        int frame = buffer.load();

        if (deferred) {
            buffer.release();
        }

        buffer.run(frame, run);
        buffer.release();

        return run;
    }

    private static void assertCommand(int issuer, @NotNull ReplayCommand expected, @NotNull ReplayCommand actual) {
        Assertions.assertEquals(issuer, actual.getPlayerId());
        Assertions.assertEquals(expected.getTypeId(), actual.getTypeId());
        Assertions.assertArrayEquals(expected.getPayload(), actual.getPayload());
    }

    private static void assertRecorded(@NotNull List<ReplayCommand> expected, @NotNull List<ReplayCommand> actual) {
        Assertions.assertEquals(expected.size(), actual.size());

        for (int index = 0; index < expected.size(); index++) {
            assertCommand(expected.get(index).getPlayerId(), expected.get(index), actual.get(index));
        }
    }


    @Test
    void run_should_execute_every_recorded_command_of_a_frame_that_fits_the_buffer() {
        List<ReplayCommand> commands = new ArrayList<>();

        addChats(commands, EARLIER_FRAME, RECORDED_ISSUER, CHATS_BEFORE_GROWTH);

        ReplayCommandBuffer buffer = createBuffer(commands);

        assertRecorded(commands, loadAndRun(buffer, true));
        Assertions.assertEquals(0, buffer.getCount());
    }

    @Test
    void run_should_execute_an_overflowing_frame_completely_in_its_loading_call() {
        List<ReplayCommand> commands = new ArrayList<>();

        addOverflowFrame(commands);

        ReplayCommandBuffer buffer = createBuffer(commands);

        Assertions.assertEquals(OVERFLOW_FRAME, buffer.load());
        Assertions.assertEquals(CHATS_BEFORE_GROWTH + CHATS_IN_OVERFLOW, buffer.getCount());

        List<ReplayCommand> run = new ArrayList<>();

        buffer.run(OVERFLOW_FRAME, run);
        buffer.release();

        assertRecorded(commands, run);
    }

    @Test
    void run_should_drop_the_overflow_commands_of_a_deferred_frame_with_empty_stale_entries() {
        List<ReplayCommand> commands = new ArrayList<>();

        addOverflowFrame(commands);

        List<ReplayCommand> run = loadAndRun(createBuffer(commands), true);

        assertRecorded(commands.subList(0, CHATS_BEFORE_GROWTH), run);
    }

    @Test
    void run_should_execute_stale_entries_of_earlier_frames_with_the_deferred_frames_issuers() {
        List<ReplayCommand> commands = new ArrayList<>();
        ReplayCommand firstAlliance = new ReplayCommand(EARLIER_FRAME, RECORDED_ISSUER, ReplayCommandIds.ALLIANCE, new byte[]{1, 2, 3, 4});
        ReplayCommand secondAlliance = new ReplayCommand(EARLIER_FRAME, RECORDED_ISSUER, ReplayCommandIds.ALLIANCE, new byte[]{5, 6, 7, 8});

        addChats(commands, EARLIER_FRAME, RECORDED_ISSUER, CHATS_BEFORE_GROWTH);
        commands.add(firstAlliance);
        commands.add(secondAlliance);
        addOverflowFrame(commands);

        ReplayCommandBuffer buffer = createBuffer(commands);

        Assertions.assertEquals(CHATS_BEFORE_GROWTH + STALE_ALLIANCES, loadAndRun(buffer, false).size());

        List<ReplayCommand> run = loadAndRun(buffer, true);
        int overflowStart = CHATS_BEFORE_GROWTH + STALE_ALLIANCES;

        Assertions.assertEquals(CHATS_BEFORE_GROWTH + STALE_ALLIANCES, run.size());
        assertRecorded(commands.subList(overflowStart, overflowStart + CHATS_BEFORE_GROWTH), run.subList(0, CHATS_BEFORE_GROWTH));
        assertCommand(OVERFLOW_ISSUER, firstAlliance, run.get(CHATS_BEFORE_GROWTH));
        assertCommand(OVERFLOW_ISSUER, secondAlliance, run.get(CHATS_BEFORE_GROWTH + 1));
    }

    @Test
    void run_should_not_execute_a_stale_command_that_does_not_fit_its_entry() {
        List<ReplayCommand> commands = new ArrayList<>();
        byte[] coveringHandles = new byte[1 + COVERING_SELECT_HANDLES * Integer.BYTES];
        byte[] stale = {ReplayCommandIds.ALLIANCE, 1, 2, 3, 4, ReplayCommandIds.SELECT_121, 1, 0};

        coveringHandles[0] = COVERING_SELECT_HANDLES;
        System.arraycopy(stale, 0, coveringHandles, COVERING_SELECT_STALE_OFFSET, stale.length);

        // The earlier frame leaves an 8-byte size at the growth entry; the later frame covers its data with the stale bytes.
        addChats(commands, EARLIER_FRAME, RECORDED_ISSUER, CHATS_BEFORE_GROWTH);
        commands.add(new ReplayCommand(EARLIER_FRAME, RECORDED_ISSUER, ReplayCommandIds.BUILD, new byte[ReplayFile.BUILD_PAYLOAD]));
        addChats(commands, LATER_FRAME, RECORDED_ISSUER, CHATS_BEFORE_GROWTH - 1);
        commands.add(new ReplayCommand(LATER_FRAME, RECORDED_ISSUER, ReplayCommandIds.SELECT_121, coveringHandles));
        addOverflowFrame(commands);

        ReplayCommandBuffer buffer = createBuffer(commands);

        loadAndRun(buffer, false);
        loadAndRun(buffer, false);

        List<ReplayCommand> run = loadAndRun(buffer, true);
        ReplayCommand alliance = run.get(run.size() - 1);

        Assertions.assertEquals(CHATS_BEFORE_GROWTH + 1, run.size(), "The truncated select after the Alliance does not run.");
        Assertions.assertEquals(OVERFLOW_ISSUER, alliance.getPlayerId());
        Assertions.assertEquals(ALLIANCE_COMMAND, alliance.getTypeId());
        Assertions.assertArrayEquals(Arrays.copyOfRange(stale, 1, 1 + ALLIANCE_PAYLOAD), alliance.getPayload());
    }
}
