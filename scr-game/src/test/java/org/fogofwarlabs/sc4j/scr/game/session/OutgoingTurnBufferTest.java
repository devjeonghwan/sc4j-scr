package org.fogofwarlabs.sc4j.scr.game.session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks command-buffer transmission, retained bytes and the access to the queued Sync command.
 */
final class OutgoingTurnBufferTest {
    /**
     * The command types of the Sync, Keep Alive, Select and Stop commands.
     */
    private static final int SYNC_COMMAND_TYPE = 0x37;
    private static final int KEEP_ALIVE_COMMAND_TYPE = 0x05;
    private static final int SELECT_COMMAND_TYPE = 0x09;
    private static final int STOP_COMMAND_TYPE = 0x1A;
    /**
     * The length of the Sync command that starts every turn.
     */
    private static final int SYNC_COMMAND_LENGTH = 7;

    @Test
    void send_should_include_commands_written_at_the_length_and_reset_to_sync() {
        OutgoingTurnBuffer buffer = new OutgoingTurnBuffer(LiveGame.MAXIMUM_TURN_BYTES);

        Assertions.assertEquals(SYNC_COMMAND_LENGTH, buffer.getLength());

        byte[] select = {0x09, 0x01, (byte) 0x94, 0x06};

        for (int index = 0; index < select.length; index++) {
            buffer.writeByte(buffer.getLength() + index, select[index]);
        }

        buffer.setLength(buffer.getLength() + select.length);
        buffer.queue(new byte[]{0x1A, 0x00});

        byte[] turn = buffer.send();

        Assertions.assertArrayEquals(new byte[]{0x37, 0, 0, 0, 0, 0, 0, 0x09, 0x01, (byte) 0x94, 0x06, 0x1A, 0x00}, turn);
        Assertions.assertEquals(SYNC_COMMAND_LENGTH, buffer.getLength());
        Assertions.assertEquals(SELECT_COMMAND_TYPE, buffer.readByte(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH));
        Assertions.assertEquals(STOP_COMMAND_TYPE, buffer.readByte(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH + select.length));
    }

    @Test
    void send_should_send_keep_alive_for_an_empty_turn() {
        OutgoingTurnBuffer buffer = new OutgoingTurnBuffer(LiveGame.MAXIMUM_TURN_BYTES);

        // A map clears the length as an EUD write does.
        buffer.setLength(0);

        Assertions.assertArrayEquals(new byte[]{KEEP_ALIVE_COMMAND_TYPE}, buffer.send());
        Assertions.assertEquals(SYNC_COMMAND_TYPE, buffer.readByte(0), "The next turn's Sync command is queued over it.");
        Assertions.assertEquals(SYNC_COMMAND_LENGTH, buffer.getLength());
    }

    @Test
    void sync_command_bytes_are_readable_and_reject_changing_writes() {
        OutgoingTurnBuffer buffer = new OutgoingTurnBuffer(LiveGame.MAXIMUM_TURN_BYTES);

        // Before the game starts its Sync records, the queued Sync command carries zero record bytes.
        Assertions.assertEquals(SYNC_COMMAND_TYPE, buffer.readByte(0));
        Assertions.assertEquals(0, buffer.readByte(1));
        Assertions.assertEquals(0, buffer.readByte(4));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> buffer.writeByte(2, 1));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> buffer.writeByte(5, 1));

        buffer.writeByte(2, 0);
        buffer.writeByte(5, 0);
    }

    @Test
    void queue_should_reject_commands_that_overflow_the_turn() {
        OutgoingTurnBuffer buffer = new OutgoingTurnBuffer(LiveGame.MAXIMUM_TURN_BYTES);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> buffer.queue(new byte[LiveGame.MAXIMUM_TURN_BYTES]));
    }
}
