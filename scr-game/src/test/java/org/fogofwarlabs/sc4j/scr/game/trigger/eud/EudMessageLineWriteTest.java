package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace.MessageCursorRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks message-line byte stores and the next-line cursor.
 */
final class EudMessageLineWriteTest {
    private static final int MESSAGE_LINES = 0x640B60;
    private static final int LINE_LENGTH = 218;
    private static final int WRITTEN_OFFSET = 2 * LINE_LENGTH + 4;

    @Test
    void message_line_writes_store_line_bytes_and_the_next_line() {
        MessageLines lines = new MessageLines();
        ClassicAddressSpace space = ClassicAddressSpace.create(PlayerProgressTestUtils.createPlayerScores(), null, null, lines);

        space.writeDword(MESSAGE_LINES + WRITTEN_OFFSET, 0x00434241);

        Assertions.assertEquals('A', lines.readByte(WRITTEN_OFFSET));
        Assertions.assertEquals('C', lines.readByte(WRITTEN_OFFSET + 2));

        space.writeDword(MESSAGE_LINES + WRITTEN_OFFSET, 0x44434241);

        Assertions.assertEquals(0x44434241, space.readDword(MESSAGE_LINES + WRITTEN_OFFSET));

        new MessageCursorRegion(lines).writeByte(0, 7);

        Assertions.assertEquals(7, lines.getNextLine());
    }
}
