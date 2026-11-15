package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.base.text.TerminatedStringUtils;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Stores the campaign progress of the run's player in a single-player ID file, encoded in replay sections: a record section, whose
 * unrecognized bytes are kept, and a UTF-8 name section. The file is named after the player's folder name ({@link
 * org.fogofwarlabs.sc4j.scr.client.ClientPlayer#getFolderName()}) and holds the player's name. Files from before {@code 1.18} using the
 * PKWare codec are unsupported.
 */
public final class CharacterFile {
    /**
     * The extension of single player IDs ({@code .mpc} for multiplayer ones).
     */
    public static final @NotNull String EXTENSION = ".spc";
    /**
     * The size of the name section: the longest name in bytes and its terminator.
     */
    public static final int NAME_SIZE = 0x60;

    private static final @NotNull Logger LOGGER = Logger.of(CharacterFile.class);
    private static final int MILLISECONDS_PER_SECOND = 1000;

    private static final int RECORD_SECTION_INDEX = 0;
    private static final int NAME_SECTION_INDEX = 1;
    private static final int RECORD_SIZE = 0x70;
    private static final int MAGIC = 0x00112233;
    private static final int MAGIC_OFFSET = 0x00;
    private static final int MULTIPLAYER_OFFSET = 0x04;
    private static final int CREATION_TIME_OFFSET = 0x08;
    private static final int LEGACY_NAME_OFFSET = 0x0c;
    private static final int LEGACY_NAME_SIZE = 25;
    private static final int PROGRESS_OFFSET = 0x28;
    private static final int EXPANSION_PROGRESS_OFFSET = 0x34;

    /**
     * The races of the campaign progress fields, in field order.
     */
    public enum Race {
        /**
         * Zerg campaign progress.
         */
        ZERG,
        /**
         * Terran campaign progress.
         */
        TERRAN,
        /**
         * Protoss campaign progress.
         */
        PROTOSS
    }

    /**
     * Opens the character file of a player: reads the file when it is a valid single-player ID, which keeps its progress, and creates and
     * writes a new one otherwise. The ID holds the given name; a read file takes it at its next write.
     *
     * @param directory the character folder
     * @param fileName  the file name without {@link #EXTENSION}
     * @param name      the player's name; more than the name section holds is cut ({@link TerminatedStringUtils#cutText(String, int)})
     * @return the opened character file; when a new file cannot be written, the ID lives in memory until a write succeeds
     */
    public static @NotNull CharacterFile open(@NotNull Path directory, @NotNull String fileName, @NotNull String name) {
        Path file = directory.resolve(fileName + EXTENSION);
        byte[] nameBytes = TerminatedStringUtils.cutText(name, NAME_SIZE);
        CharacterFile read = Files.isRegularFile(file) ? read(file) : null;

        if (read != null && !read.isMultiplayer()) {
            return new CharacterFile(file, nameBytes, read.record);
        }

        try {
            return create(directory, fileName, nameBytes);
        } catch (IOException exception) {
            LOGGER.warning("CHARACTER CREATE FAILED: " + file + ": " + exception.getMessage());

            return new CharacterFile(file, nameBytes, createRecord(nameBytes));
        }
    }

    /**
     * Reads one ID file.
     *
     * @param file the {@code .spc} file
     * @return the ID with the name its name section holds, or {@code null} if the file is not a valid ID
     */
    public static @Nullable CharacterFile read(@NotNull Path file) {
        try {
            List<byte[]> sections = ReplayFile.readSections(Files.readAllBytes(file), false, RECORD_SIZE, NAME_SIZE);
            byte[] record = sections.get(RECORD_SECTION_INDEX);

            if (createRecordBuffer(record).getInt(MAGIC_OFFSET) != MAGIC) {
                return null;
            }

            byte[] section = sections.get(NAME_SECTION_INDEX);

            return new CharacterFile(file, Arrays.copyOf(section, TerminatedStringUtils.getTextLength(section)), record);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warning("CHARACTER UNREADABLE: " + file + ": " + exception.getMessage());

            return null;
        }
    }

    /**
     * Creates a {@link CharacterFile} instance for a new single-player ID and writes its file.
     * <p>
     * The ID keeps the name bytes as its name section holds them: more than the name section's {@value #NAME_SIZE} bytes less the
     * terminator are cut ({@link TerminatedStringUtils#cutText(byte[], int)}).
     *
     * @param directory the character folder
     * @param fileName  the file name without {@link #EXTENSION}
     * @param name      the name bytes without terminator
     * @return the created {@link CharacterFile} instance
     * @throws IOException if the file cannot be written
     */
    public static @NotNull CharacterFile create(@NotNull Path directory, @NotNull String fileName, byte @NotNull [] name) throws IOException {
        byte[] sectionName = TerminatedStringUtils.cutText(name, NAME_SIZE);

        Files.createDirectories(directory);

        Path file = directory.resolve(fileName + EXTENSION);
        CharacterFile character = new CharacterFile(file, sectionName, createRecord(sectionName));

        Files.write(file, character.encode());

        return character;
    }

    /**
     * Creates the record of a new single-player ID: the magic number, the creation time and the legacy name field, which holds the name
     * cut to its size followed by a zero byte.
     */
    private static byte @NotNull [] createRecord(byte @NotNull [] sectionName) {
        byte[] record = new byte[RECORD_SIZE];
        ByteBuffer fields = createRecordBuffer(record);

        fields.putInt(MAGIC_OFFSET, MAGIC);
        record[MULTIPLAYER_OFFSET] = 0;
        fields.putInt(CREATION_TIME_OFFSET, (int) (System.currentTimeMillis() / MILLISECONDS_PER_SECOND));

        byte[] legacyName = TerminatedStringUtils.cutText(sectionName, LEGACY_NAME_SIZE);

        System.arraycopy(legacyName, 0, record, LEGACY_NAME_OFFSET, legacyName.length);

        return record;
    }

    private static @NotNull ByteBuffer createRecordBuffer(byte @NotNull [] record) {
        return ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Finds the record offset of a campaign's progress field.
     *
     * @param expansion {@code true} for the Brood War campaigns
     * @param race      the campaign race
     * @return the offset of the race's next mission index
     */
    private static int findProgressOffset(boolean expansion, @NotNull Race race) {
        return (expansion ? EXPANSION_PROGRESS_OFFSET : PROGRESS_OFFSET) + race.ordinal() * Integer.BYTES;
    }

    private final @NotNull Path path;
    /**
     * The name section's bytes before the terminator.
     */
    private final byte @NotNull [] name;

    private final byte @NotNull [] record;

    private CharacterFile(@NotNull Path path, byte @NotNull [] name, byte @NotNull [] record) {
        this.path = path;
        this.name = name;

        this.record = record;
    }

    /**
     * @return the player's name the name section holds, read from the name bytes ({@link TerminatedStringUtils#readText})
     */
    public @NotNull String getName() {
        return TerminatedStringUtils.readText(this.name);
    }

    /**
     * @return {@code true} if this is a multiplayer ID, {@code false} otherwise
     */
    public boolean isMultiplayer() {
        return this.record[MULTIPLAYER_OFFSET] != 0;
    }

    /**
     * @param expansion {@code true} for the Brood War campaigns
     * @param race      the campaign race
     * @return the campaign's next mission index, {@code 0} if not started
     */
    public int getCampaignProgress(boolean expansion, @NotNull Race race) {
        return createRecordBuffer(this.record).getInt(findProgressOffset(expansion, race));
    }

    /**
     * Sets a campaign's next mission index and writes the character file.
     *
     * @param expansion {@code true} for the Brood War campaigns
     * @param race      the campaign race
     * @param mission   the mission index
     * @throws IOException if the file cannot be written
     */
    public void setCampaignProgress(boolean expansion, @NotNull Race race, int mission) throws IOException {
        createRecordBuffer(this.record).putInt(findProgressOffset(expansion, race), mission);
        Files.write(this.path, this.encode());
    }

    private byte @NotNull [] encode() {
        byte[] nameSection = Arrays.copyOf(this.name, NAME_SIZE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.writeBytes(ReplayWriter.encodeSection(this.record));
        output.writeBytes(ReplayWriter.encodeSection(nameSection));

        return output.toByteArray();
    }
}
