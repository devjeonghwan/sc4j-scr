package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents a decoded selection request without applying it to game state.
 * <p>
 * Raw handles retain their packet order and duplicates. They are not CHK serial numbers, and decoding does not validate unit identity or
 * selection rules.
 */
public final class ReplaySelectionCommand extends SelectionRequest {
    /**
     * The count byte that precedes the handles of the payload.
     */
    private static final int COUNT_SIZE = Byte.BYTES;

    /**
     * Checks whether the command type carries a supported selection payload.
     *
     * @param typeIdentifier the command type identifier
     * @return {@code true} if the type is one of the six selection command types, {@code false} otherwise
     */
    public static boolean supports(int typeIdentifier) {
        return switch (typeIdentifier) {
            case ReplayCommandIds.SELECT, ReplayCommandIds.SELECT_ADD, ReplayCommandIds.SELECT_REMOVE, ReplayCommandIds.SELECT_121,
                 ReplayCommandIds.SELECT_ADD_121, ReplayCommandIds.SELECT_REMOVE_121 -> true;
            default -> false;
        };
    }

    /**
     * Creates a {@link ReplaySelectionCommand} instance from the command payload.
     * <p>
     * The payload excludes the opcode and must contain one count byte followed by exactly that many little-endian handles. Counts above
     * {@code 12} are retained because this method does not execute the request.
     *
     * @param command the source command
     * @return the created {@link ReplaySelectionCommand} instance
     * @throws IOException if the type is unsupported or the payload length does not match its count
     */
    public static @NotNull ReplaySelectionCommand parse(@NotNull ReplayCommand command) throws IOException {
        return read(command.getTypeId(), new CommandInput(command.getPayload()));
    }

    /**
     * Creates the codec of one selection command type: a read is {@link #read}, a write {@link #write}.
     *
     * @param typeIdentifier the command type byte, a selection command type ({@link #supports})
     * @return the created codec
     */
    public static @NotNull ReplayCommandCodec<ReplaySelectionCommand> createCodec(int typeIdentifier) {
        return new ReplayCommandCodec<>() {
            @Override
            public @NotNull ReplaySelectionCommand read(@NotNull CommandInput input) throws IOException {
                return ReplaySelectionCommand.read(typeIdentifier, input);
            }

            @Override
            public void write(@NotNull ReplaySelectionCommand command, @NotNull CommandOutput output) {
                command.write(output);
            }
        };
    }

    /**
     * Creates a {@link ReplaySelectionCommand} instance from the payload of a selection command, as {@link #parse} does.
     *
     * @param typeIdentifier the command type byte
     * @param input          the payload, of which every byte left belongs to the command
     * @return the created {@link ReplaySelectionCommand} instance
     * @throws IOException if the type is unsupported or the payload length does not match its handle count
     */
    public static @NotNull ReplaySelectionCommand read(int typeIdentifier, @NotNull CommandInput input) throws IOException {
        Operation operation = switch (typeIdentifier) {
            case ReplayCommandIds.SELECT, ReplayCommandIds.SELECT_121 -> Operation.REPLACE;
            case ReplayCommandIds.SELECT_ADD, ReplayCommandIds.SELECT_ADD_121 -> Operation.ADD;
            case ReplayCommandIds.SELECT_REMOVE, ReplayCommandIds.SELECT_REMOVE_121 -> Operation.REMOVE;
            default -> throw Exceptions.newIOException("The replay command is not a selection command: {0}", typeIdentifier);
        };

        int handleWidthBytes = typeIdentifier < ReplayCommandIds.SELECT_121 ? ReplayCommandUtils.LEGACY_HANDLE_WIDTH
                                                                            : ReplayCommandUtils.HANDLE_WIDTH_121;
        int payloadLength = input.getRemaining();

        if (payloadLength == 0) {
            throw Exceptions.newIOException("The selection command payload is missing its count byte.");
        }

        int handleCount = input.readUnsignedByte();
        int expectedLength = COUNT_SIZE + handleCount * handleWidthBytes;

        if (payloadLength != expectedLength) {
            throw Exceptions.newIOException("The selection command payload length does not match its handle count: expected {0}, got {1}.",
                                            expectedLength, payloadLength);
        }

        long[] handles = new long[handleCount];

        for (int handleIndex = 0; handleIndex < handleCount; handleIndex++) {
            handles[handleIndex] = input.readUnsigned(handleWidthBytes);
        }

        return new ReplaySelectionCommand(operation, handleWidthBytes, handles);
    }


    /**
     * Creates a selection command.
     *
     * @param operation        the selection operation
     * @param handleWidthBytes the handle width of the command type in bytes
     * @param handles          the unit handles, owned by the command
     * @return the created {@link ReplaySelectionCommand} instance
     */
    public static @NotNull ReplaySelectionCommand create(@NotNull Operation operation, int handleWidthBytes, long @NotNull [] handles) {
        return new ReplaySelectionCommand(operation, handleWidthBytes, handles);
    }


    private ReplaySelectionCommand(@NotNull Operation operation, int handleWidthBytes, long @NotNull [] handles) {
        super(operation, handleWidthBytes, handles);
    }

    /**
     * Writes the payload of the command: the bytes {@link #read} reads it from.
     *
     * @param output the output the payload is appended to
     */
    public void write(@NotNull CommandOutput output) {
        output.writeByte(this.getHandleCount());

        for (int handleIndex = 0; handleIndex < this.getHandleCount(); handleIndex++) {
            output.writeUnsigned(this.getHandle(handleIndex), this.getHandleWidthBytes());
        }
    }
}
