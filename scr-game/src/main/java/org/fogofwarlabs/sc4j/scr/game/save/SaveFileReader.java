package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads saved-game parts in the order {@link SaveFileWriter} wrote them.
 * <p>
 * A truncated or malformed part fails with an {@link IOException}-backed {@link UncheckedIOException}, so the section
 * readers can read without declaring checked exceptions.
 */
public final class SaveFileReader {
    private static final int SECTION_HEADER_SIZE = 2 * Integer.BYTES;

    /**
     * Creates an {@link UncheckedIOException} instance for a malformed saved game.
     *
     * @param message   the message pattern with {@code {0}} placeholders
     * @param arguments the pattern arguments
     * @return the created {@link UncheckedIOException} instance
     */
    public static @NotNull UncheckedIOException createFailure(@NotNull String message, @Nullable Object @NotNull ... arguments) {
        return Exceptions.newUncheckedIOException(Exceptions.newIOException(message, arguments));
    }


    private final byte @NotNull [] data;

    private int position;

    /**
     * Creates a {@link SaveFileReader} instance over a file.
     *
     * @param data     the file contents
     * @param position the file offset of the first part to read
     */
    public SaveFileReader(byte @NotNull [] data, int position) {
        this.data = data;

        this.position = position;
    }

    /**
     * @return the file offset of the next part
     */
    public int getPosition() {
        return this.position;
    }

    /**
     * @return the bytes after the next part's offset
     */
    public int getRemaining() {
        return this.data.length - this.position;
    }


    /**
     * Moves to a file offset, as the version 4 reader seeks to the trailer.
     *
     * @param position the file offset
     * @throws UncheckedIOException if the requested offset is outside the file
     */
    public void seek(int position) {
        if (position < 0 || position > this.data.length) {
            throw createFailure("The saved game has no offset {0}.", position);
        }

        this.position = position;
    }

    /**
     * Reads a plain little-endian WORD.
     *
     * @return the read WORD, zero-extended
     * @throws UncheckedIOException if fewer than two bytes remain
     */
    public int readWord() {
        this.require(Short.BYTES);

        int value = LittleEndianUtils.readUnsigned16(this.data, this.position);

        this.position += Short.BYTES;

        return value;
    }

    /**
     * Reads a plain little-endian DWORD.
     *
     * @return the read DWORD
     * @throws UncheckedIOException if fewer than four bytes remain
     */
    public int readDword() {
        return this.readWord() | this.readWord() << Short.SIZE;
    }

    /**
     * Reads plain bytes.
     *
     * @param count the number of bytes
     * @return the read bytes
     * @throws UncheckedIOException if the count is negative or the requested bytes are truncated
     */
    public byte @NotNull [] readBytes(int count) {
        if (count < 0) {
            throw createFailure("Invalid saved byte count: {0}", count);
        }

        this.require(count);

        byte[] bytes = Arrays.copyOfRange(this.data, this.position, this.position + count);

        this.position += count;

        return bytes;
    }

    /**
     * Reads one section of the replay section codec.
     *
     * @param size the decoded size the reader expects
     * @return the decoded section
     * @throws UncheckedIOException if the size is negative or a section is truncated or malformed; the position is then unchanged
     */
    public byte @NotNull [] readSection(int size) {
        int start = this.position;

        if (size < 0) {
            throw createFailure("Invalid saved section size: {0}", size);
        }

        this.require(SECTION_HEADER_SIZE);

        int blockCount = LittleEndianUtils.readInt32(this.data, start + Integer.BYTES);
        int end = start + SECTION_HEADER_SIZE;

        for (int block = 0; block < blockCount; block++) {
            if (end + Integer.BYTES > this.data.length) {
                throw createFailure("The saved section at {0} is truncated.", start);
            }

            int length = LittleEndianUtils.readInt32(this.data, end);

            if (length < 0 || length > this.data.length - end - Integer.BYTES) {
                throw createFailure("The saved section at {0} has an invalid block length.", start);
            }

            end += Integer.BYTES + length;
        }

        try {
            byte[] section = ReplayFile.readSections(Arrays.copyOfRange(this.data, start, end), false, size).getFirst();

            this.position = end;

            return section;
        } catch (IOException exception) {
            throw createFailure("The saved section at {0} is malformed: {1}", start, exception.getMessage());
        }
    }

    /**
     * Reads one bare zlib stream ({@link SaveFileWriter#writeStream}).
     *
     * @param size the uncompressed size
     * @return the uncompressed bytes
     * @throws NegativeArraySizeException if the decoded size is negative
     * @throws UncheckedIOException       if the stream is malformed or does not decode to its size; the position is then unchanged
     */
    public byte @NotNull [] readStream(int size) {
        try (Inflater inflater = new Inflater()) {
            byte[] payload = new byte[size];

            try {
                inflater.setInput(this.data, this.position, this.data.length - this.position);

                int length = inflater.inflate(payload);

                if (length != size || !inflater.finished()) {
                    throw createFailure("The saved stream at {0} does not hold {1} bytes.", this.position, size);
                }

                this.position += (int) inflater.getBytesRead();

                return payload;
            } catch (DataFormatException exception) {
                throw createFailure("The saved stream at {0} is malformed: {1}", this.position, exception.getMessage());
            }
        }
    }

    private void require(int count) {
        if (count > this.data.length - this.position) {
            throw createFailure("The saved game is truncated at {0}.", this.position);
        }
    }
}
