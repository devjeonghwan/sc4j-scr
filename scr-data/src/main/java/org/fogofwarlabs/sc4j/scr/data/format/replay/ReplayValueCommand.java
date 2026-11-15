package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents a native command whose payload is one little-endian value of one, two or four bytes: a flag or a type, slot, mask, handle or bit
 * field.
 */
public final class ReplayValueCommand {
    /**
     * The codec of a one-byte value.
     */
    public static final @NotNull ReplayCommandCodec<ReplayValueCommand> BYTE = createCodec(Byte.BYTES);
    /**
     * The codec of a two-byte value.
     */
    public static final @NotNull ReplayCommandCodec<ReplayValueCommand> WORD = createCodec(Short.BYTES);
    /**
     * The codec of a four-byte value.
     */
    public static final @NotNull ReplayCommandCodec<ReplayValueCommand> DWORD = createCodec(Integer.BYTES);

    private static @NotNull ReplayCommandCodec<ReplayValueCommand> createCodec(int width) {
        return new ReplayCommandCodec<>() {
            @Override
            public @NotNull ReplayValueCommand read(@NotNull CommandInput input) throws IOException {
                return new ReplayValueCommand((int) input.readUnsigned(width), width);
            }

            @Override
            public void write(@NotNull ReplayValueCommand command, @NotNull CommandOutput output) {
                output.writeUnsigned(command.getValue(), command.getWidth());
            }
        };
    }


    private final int value;
    private final int width;

    /**
     * Creates a {@link ReplayValueCommand} instance.
     *
     * @param value the value: unsigned for one and two bytes, any {@code int} for four
     * @param width the payload width in bytes: {@code 1}, {@code 2} or {@code 4}
     * @throws IllegalArgumentException if the width is not one of these
     */
    public ReplayValueCommand(int value, int width) {
        if (width != Byte.BYTES && width != Short.BYTES && width != Integer.BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid command value width: {0}", width);
        }

        this.value = value;
        this.width = width;
    }

    /**
     * @return the value: unsigned for one and two bytes, any {@code int} for four
     */
    public int getValue() {
        return this.value;
    }

    /**
     * @return the payload width in bytes
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return {@code true} if the value is not {@code 0}, {@code false} otherwise
     */
    public boolean isSet() {
        return this.value != 0;
    }
}
