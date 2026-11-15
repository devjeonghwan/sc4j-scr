package org.fogofwarlabs.screcorder.sample.camera;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Defines camera and input payloads and bounded {@code 64}-bit mouse pointer sampling.
 */
public final class CameraSampleDefinition extends SampleDefinition<CameraSample> {
    private static final int SAMPLE_IDENTIFIER = 13;
    private static final int PAYLOAD_BYTES = 56;

    private static final int MAXIMUM_UNSIGNED_BYTE = 0xFF;
    private static final int MAXIMUM_UNSIGNED_SHORT = 0xFFFF;

    private static final long EUD_MOUSE_POINTER_OFFSET = 0xE7E4A8L;
    private static final int EUD_MOUSE_COORDINATE_OFFSET = 0x140;
    private static final int EUD_MOUSE_COORDINATES_BYTES = Integer.BYTES * 2;

    private static final long SCREEN_X_OFFSET = 0xED3BC0L;
    private static final long SCREEN_Y_OFFSET = 0xED3BC4L;
    private static final long MAXIMUM_X_OFFSET = 0xED3B74L;
    private static final long MAXIMUM_Y_OFFSET = 0xED3B78L;
    private static final long PREVIOUS_X_OFFSET = 0xED3BB8L;
    private static final long PREVIOUS_Y_OFFSET = 0xED3BBCL;
    private static final long CENTER_VIEW_X_OFFSET = 0x10A1A80L;
    private static final long CENTER_VIEW_Y_OFFSET = 0x10A1A84L;
    private static final long CENTER_VIEW_ACTIVE_OFFSET = 0x10A1944L;
    private static final long CURSOR_X_OFFSET = 0x10B6DC8L;
    private static final long CURSOR_Y_OFFSET = 0x10B6DCCL;
    private static final long BUTTONS_OFFSET = 0x1090610L;

    private static final @NotNull SampleReader<CameraSample> READER = CameraSampleDefinition::read;
    private static final @NotNull SampleWriter<CameraSample> WRITER = CameraSampleDefinition::write;

    private static @NotNull CameraSample read(@NotNull ByteBuffer input) throws IOException {
        if (input.remaining() != PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid camera payload size: {0}", input.remaining());
        }

        input.order(ByteOrder.LITTLE_ENDIAN);

        int screenX = input.getInt();
        int screenY = input.getInt();
        int maximumX = input.getInt();
        int maximumY = input.getInt();
        int previousX = input.getInt();
        int previousY = input.getInt();
        int centerViewX = input.getInt();
        int centerViewY = input.getInt();
        int centerViewActive = Byte.toUnsignedInt(input.get());
        int eudMouseAvailableCode = Byte.toUnsignedInt(input.get());
        int eudMouseX = input.getInt();
        int eudMouseY = input.getInt();
        int cursorX = input.getInt();
        int cursorY = input.getInt();
        int buttons = Short.toUnsignedInt(input.getShort());
        int tileX = Short.toUnsignedInt(input.getShort());
        int tileY = Short.toUnsignedInt(input.getShort());

        if (eudMouseAvailableCode > 1 || (eudMouseAvailableCode == 0 && (eudMouseX != 0 || eudMouseY != 0))) {
            throw Exceptions.newIOException("Invalid EUD mouse availability or absent coordinates");
        }

        return new CameraSample(screenX, screenY, maximumX, maximumY, previousX, previousY,
                                centerViewX, centerViewY, centerViewActive,
                                eudMouseAvailableCode != 0, eudMouseX, eudMouseY,
                                cursorX, cursorY, buttons, tileX, tileY);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull CameraSample sample) throws IOException {
        validate(sample);

        if (output.remaining() < PAYLOAD_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable camera buffer");
        }

        output.order(ByteOrder.LITTLE_ENDIAN);

        output.putInt(sample.getScreenX());
        output.putInt(sample.getScreenY());
        output.putInt(sample.getMaximumX());
        output.putInt(sample.getMaximumY());
        output.putInt(sample.getPreviousX());
        output.putInt(sample.getPreviousY());
        output.putInt(sample.getCenterViewX());
        output.putInt(sample.getCenterViewY());
        output.put((byte) sample.getCenterViewActive());
        output.put((byte) (sample.isEudMouseAvailable() ? 1 : 0));
        output.putInt(sample.getEudMouseX());
        output.putInt(sample.getEudMouseY());
        output.putInt(sample.getCursorX());
        output.putInt(sample.getCursorY());
        output.putShort((short) sample.getButtons());
        output.putShort((short) sample.getTileX());
        output.putShort((short) sample.getTileY());
    }

    private static void validate(@NotNull CameraSample sample) throws IOException {
        if (sample.getCenterViewActive() < 0 || sample.getCenterViewActive() > MAXIMUM_UNSIGNED_BYTE ||
            sample.getButtons() < 0 || sample.getButtons() > MAXIMUM_UNSIGNED_SHORT ||
            sample.getTileX() < 0 || sample.getTileX() > MAXIMUM_UNSIGNED_SHORT ||
            sample.getTileY() < 0 || sample.getTileY() > MAXIMUM_UNSIGNED_SHORT ||
            (!sample.isEudMouseAvailable() && (sample.getEudMouseX() != 0 || sample.getEudMouseY() != 0))) {
            throw Exceptions.newIOException("Invalid camera narrow values or unavailable EUD coordinates");
        }
    }


    /**
     * Creates a {@link CameraSampleDefinition} instance.
     */
    public CameraSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "camera", CameraSample.class, PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<CameraSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<CameraSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    @Override
    public int getEncodedSize(@NotNull CameraSample sample) throws IOException {
        validate(sample);

        return PAYLOAD_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<CameraSample> createSampler(@NotNull CaptureContext context) {
        byte[] scalarStorage = new byte[Long.BYTES];
        ByteBuffer scalars = ByteBuffer.wrap(scalarStorage).order(ByteOrder.LITTLE_ENDIAN);
        byte[] mouseStorage = new byte[EUD_MOUSE_COORDINATES_BYTES];
        ByteBuffer mouse = ByteBuffer.wrap(mouseStorage).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            context.getMemory().read(context.getModuleBase() + SCREEN_X_OFFSET, scalarStorage, 0, Integer.BYTES);
            int screenX = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + SCREEN_Y_OFFSET, scalarStorage, 0, Integer.BYTES);
            int screenY = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + MAXIMUM_X_OFFSET, scalarStorage, 0, Integer.BYTES);
            int maximumX = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + MAXIMUM_Y_OFFSET, scalarStorage, 0, Integer.BYTES);
            int maximumY = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + PREVIOUS_X_OFFSET, scalarStorage, 0, Integer.BYTES);
            int previousX = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + PREVIOUS_Y_OFFSET, scalarStorage, 0, Integer.BYTES);
            int previousY = scalars.getInt(0);

            context.getMemory().read(context.getModuleBase() + CENTER_VIEW_X_OFFSET, scalarStorage, 0, Integer.BYTES);
            int centerViewX = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + CENTER_VIEW_Y_OFFSET, scalarStorage, 0, Integer.BYTES);
            int centerViewY = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + CENTER_VIEW_ACTIVE_OFFSET, scalarStorage, 0, Byte.BYTES);
            int centerViewActive = Byte.toUnsignedInt(scalars.get(0));

            context.getMemory().read(context.getModuleBase() + CURSOR_X_OFFSET, scalarStorage, 0, Integer.BYTES);
            int cursorX = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + CURSOR_Y_OFFSET, scalarStorage, 0, Integer.BYTES);
            int cursorY = scalars.getInt(0);
            context.getMemory().read(context.getModuleBase() + BUTTONS_OFFSET, scalarStorage, 0, Short.BYTES);
            int buttons = Short.toUnsignedInt(scalars.getShort(0));

            context.getMemory().read(context.getContextAddress() + StarCraftLayout.SCREEN_TILE_X_OFFSET, scalarStorage, 0, Short.BYTES);
            int tileX = Short.toUnsignedInt(scalars.getShort(0));
            context.getMemory().read(context.getContextAddress() + StarCraftLayout.SCREEN_TILE_Y_OFFSET, scalarStorage, 0, Short.BYTES);
            int tileY = Short.toUnsignedInt(scalars.getShort(0));

            context.getMemory().read(context.getModuleBase() + EUD_MOUSE_POINTER_OFFSET, scalarStorage, 0, Long.BYTES);
            long mouseAddress = scalars.getLong(0);

            if (mouseAddress < 0 || mouseAddress > Long.MAX_VALUE - EUD_MOUSE_COORDINATE_OFFSET - mouseStorage.length) {
                throw Exceptions.newIOException("Invalid EUD mouse address");
            }

            boolean eudMouseAvailable = mouseAddress != 0;
            int eudMouseX = 0;
            int eudMouseY = 0;

            if (eudMouseAvailable) {
                context.getMemory().read(mouseAddress + EUD_MOUSE_COORDINATE_OFFSET, mouseStorage, 0, mouseStorage.length);
                eudMouseX = mouse.getInt(0);
                eudMouseY = mouse.getInt(Integer.BYTES);
            }

            return new CameraSample(screenX, screenY, maximumX, maximumY, previousX, previousY,
                                    centerViewX, centerViewY, centerViewActive,
                                    eudMouseAvailable, eudMouseX, eudMouseY,
                                    cursorX, cursorY, buttons, tileX, tileY);
        };
    }
}
