package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.game.save.section.GameInformationSection;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks saved-file startup headers and the layout reader's malformed-header boundary.
 */
final class SaveGameFileTest {
    private static final int SAVING_PLAYER_SLOT = 3;
    private static final @NotNull String LAYOUT_FAILURE_PREFIX = "Cannot split the saved game header: ";
    private static final @NotNull String MISSING_BANNER_MESSAGE = "The file has no saved game banner.";
    private static final @NotNull String TRUNCATED_MESSAGE_PREFIX = "The saved game is truncated at ";
    /**
     * The format and version WORDs of the game information part.
     */
    private static final int INFORMATION_FORMAT = 109;
    private static final int INFORMATION_VERSION = 4;
    /**
     * The sizes of the player records and of the game structure.
     */
    private static final int PLAYERS_SIZE = 432;
    private static final int GAME_STRUCTURE_SIZE = 0x17700;

    /**
     * Creates a startup header without game parts.
     *
     * @param formatMark              the plain format DWORD
     * @param version                 the information section version WORD
     * @param trailerOffsetAdjustment the displacement from the end of the header
     * @return the encoded saved-game header
     */
    private static byte @NotNull [] createHeader(int formatMark, int version, int trailerOffsetAdjustment) {
        SaveGameDescription description = SaveGameRoundTripScenario.createDescription(false, "header");
        ByteBuffer information = ByteBuffer.allocate(GameInformationSection.SIZE).order(ByteOrder.LITTLE_ENDIAN);

        information.putShort(0, (short) GameInformationSection.FORMAT);
        information.putShort(Short.BYTES, (short) version);

        SaveFileWriter output = new SaveFileWriter();

        output.writeBytes(description.toBannerBytes());
        output.writeBytes(new byte[]{(byte) SaveGameDescription.END_OF_BANNER});
        output.writeDword(formatMark);
        output.writeSection(information.array());

        int trailerOffsetPosition = output.getPosition();

        output.writeDword(0);
        output.writeSection(new byte[SaveGameFile.PLAYERS_SIZE]);
        output.writeSection(new byte[GameContext.CONTEXT_SIZE]);
        output.writeDword(SAVING_PLAYER_SLOT);
        output.patchDword(trailerOffsetPosition, output.getPosition() + trailerOffsetAdjustment);

        return output.toByteArray();
    }


    @Test
    void layout_parse_should_reject_a_missing_banner_with_a_retained_cause() {
        for (byte[] contents : new byte[][]{new byte[0], {1, 2, 3}}) {
            IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> SaveGameLayoutCheck.parse(contents));

            Assertions.assertInstanceOf(IOException.class, exception.getCause());
            Assertions.assertEquals(LAYOUT_FAILURE_PREFIX + MISSING_BANNER_MESSAGE, exception.getMessage());
            Assertions.assertEquals(MISSING_BANNER_MESSAGE, exception.getCause().getMessage());
        }
    }

    @Test
    void layout_parse_should_reject_a_truncated_startup_header() {
        for (int formatLength : new int[]{0, Integer.BYTES}) {
            ByteBuffer contents = ByteBuffer.allocate(Byte.BYTES + formatLength).order(ByteOrder.LITTLE_ENDIAN);

            contents.put((byte) SaveGameDescription.END_OF_BANNER);

            if (formatLength != 0) {
                contents.putInt(SaveGameFile.FORMAT_MARK);
            }

            IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                         () -> SaveGameLayoutCheck.parse(contents.array()));

            Assertions.assertInstanceOf(IOException.class, exception.getCause());
            Assertions.assertEquals(TRUNCATED_MESSAGE_PREFIX + contents.capacity() + ".", exception.getCause().getMessage());
        }
    }

    @Test
    void layout_parse_should_reject_an_unsupported_format_mark() {
        byte[] contents = createHeader(SaveGameFile.FORMAT_MARK ^ 1, GameInformationSection.VERSION, 0);
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> SaveGameLayoutCheck.parse(contents));

        Assertions.assertInstanceOf(IOException.class, exception.getCause());
        Assertions.assertTrue(exception.getCause().getMessage().startsWith("The file is no native saved game of format 109 version 4"));
    }

    @Test
    void layout_parse_should_reject_an_unsupported_information_version() {
        byte[] contents = createHeader(SaveGameFile.FORMAT_MARK, GameInformationSection.VERSION + 1, 0);
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> SaveGameLayoutCheck.parse(contents));

        Assertions.assertInstanceOf(IOException.class, exception.getCause());
        Assertions.assertEquals("The saved game has format " + INFORMATION_FORMAT + " version " + (INFORMATION_VERSION + 1) +
                                "; only " + INFORMATION_FORMAT + " version " + INFORMATION_VERSION + " is read.",
                                exception.getCause().getMessage());
    }

    @Test
    void layout_parse_should_reject_a_trailer_before_or_after_the_body_start() {
        for (int adjustment : new int[]{-1, 1}) {
            byte[] contents = createHeader(SaveGameFile.FORMAT_MARK, GameInformationSection.VERSION, adjustment);
            IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> SaveGameLayoutCheck.parse(contents));

            Assertions.assertInstanceOf(IOException.class, exception.getCause());
            Assertions.assertEquals("The saved game has the trailer offset " + (contents.length + adjustment) + " outside its game parts.",
                                    exception.getCause().getMessage());
        }
    }

    @Test
    void parse_should_read_a_valid_header_without_game_parts() throws IOException {
        byte[] contents = createHeader(SaveGameFile.FORMAT_MARK, GameInformationSection.VERSION, 0);
        SaveGameFile file = SaveGameFile.parse(contents);
        ByteBuffer information = ByteBuffer.wrap(file.getInformation()).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertTrue(SaveGameFile.isNativeFile(contents));
        Assertions.assertEquals(INFORMATION_FORMAT, Short.toUnsignedInt(information.getShort(0)));
        Assertions.assertEquals(INFORMATION_VERSION, Short.toUnsignedInt(information.getShort(Short.BYTES)));
        Assertions.assertEquals(PLAYERS_SIZE, file.getPlayers().length);
        Assertions.assertEquals(GAME_STRUCTURE_SIZE, file.getGameStructure().length);
        Assertions.assertEquals(SAVING_PLAYER_SLOT, file.getRecordSlot());
        Assertions.assertEquals(contents.length, file.openBody().getPosition());
        Assertions.assertEquals(contents.length, file.openTrailer().getPosition());
        Assertions.assertArrayEquals(contents, file.toByteArray());
    }

    @Test
    void layout_parse_should_preserve_the_first_missing_body_part_failure() {
        byte[] contents = createHeader(SaveGameFile.FORMAT_MARK, GameInformationSection.VERSION, 0);
        UncheckedIOException exception = Assertions.assertThrows(UncheckedIOException.class, () -> SaveGameLayoutCheck.parse(contents));

        Assertions.assertEquals(TRUNCATED_MESSAGE_PREFIX + contents.length + ".", exception.getCause().getMessage());
    }
}
