package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayWriter;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

/**
 * Writes saved-game parts in their established order.
 * <p>
 * Mixes plain values (little-endian WORDs and DWORDs, raw byte blocks) with sections of the replay section codec
 * ({@link ReplayWriter#encodeSection}) and one bare zlib stream (the foliage). {@link SaveFileReader} reads the parts back
 * in the same order.
 */
public final class SaveFileWriter {
    private static final int STREAM_BUFFER_SIZE = 0x10000;

    private final @NotNull ByteArrayOutputStream output;

    /**
     * Creates a {@link SaveFileWriter} instance with an empty output.
     */
    public SaveFileWriter() {
        this.output = new ByteArrayOutputStream();
    }

    /**
     * @return the number of bytes written so far, the file offset of the next part
     */
    public int getPosition() {
        return this.output.size();
    }

    /**
     * Writes a plain WORD.
     *
     * @param value the value in the low 16 bits
     */
    public void writeWord(int value) {
        this.output.write(value);
        this.output.write(value >>> Byte.SIZE);
    }

    /**
     * Writes a plain DWORD.
     *
     * @param value the value
     */
    public void writeDword(int value) {
        this.writeWord(value);
        this.writeWord(value >>> Short.SIZE);
    }

    /**
     * Writes plain bytes.
     *
     * @param bytes the bytes
     */
    public void writeBytes(byte @NotNull [] bytes) {
        this.output.writeBytes(bytes);
    }

    /**
     * Writes one section of the replay section codec.
     *
     * @param payload the decoded section bytes
     */
    public void writeSection(byte @NotNull [] payload) {
        this.output.writeBytes(ReplayWriter.encodeSection(payload));
    }

    /**
     * Writes one bare zlib stream at the default compression level, without a length prefix.
     *
     * @param payload the uncompressed bytes
     */
    public void writeStream(byte @NotNull [] payload) {
        try (Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];

            deflater.setInput(payload);
            deflater.finish();

            while (!deflater.finished()) {
                int length = deflater.deflate(buffer);

                this.output.write(buffer, 0, length);
            }
        }
    }

    /**
     * Replaces a previously written DWORD, including a reserved trailer offset.
     *
     * @param position the file offset of the DWORD
     * @param value    the value
     * @throws IndexOutOfBoundsException if the four-byte range is outside the written bytes; the output is then unchanged
     */
    public void patchDword(int position, int value) {
        byte[] bytes = this.output.toByteArray();

        for (int index = 0; index < Integer.BYTES; index++) {
            bytes[position + index] = (byte) (value >>> index * Byte.SIZE);
        }

        this.output.reset();
        this.output.writeBytes(bytes);
    }

    /**
     * @return the written file
     */
    public byte @NotNull [] toByteArray() {
        return this.output.toByteArray();
    }
}
