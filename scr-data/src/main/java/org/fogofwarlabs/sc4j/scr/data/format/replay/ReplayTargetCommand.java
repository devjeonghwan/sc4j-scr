package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents a decoded right-click or targeted-order request without executing it.
 * <p>
 * Coordinates, target handles, unit types and queue flags preserve their encoded unsigned values. A target handle is not a CHK serial
 * number or a Java unit index. Decoding does not resolve targets, validate map bounds or choose the resulting order.
 */
public final class ReplayTargetCommand {
    /**
     * The payload bytes besides the handle: x, y and unit type WORDs and the queue byte, plus the order byte.
     */
    private static final int RIGHT_CLICK_FIELDS_SIZE = 7;
    private static final int TARGETED_ORDER_FIELDS_SIZE = 8;

    /**
     * Identifies whether the request supplies an explicit order identifier.
     */
    public enum Operation {
        /**
         * Requests context-dependent right-click behavior.
         */
        RIGHT_CLICK,
        /**
         * Supplies an explicit targeted order.
         */
        TARGETED_ORDER
    }

    /**
     * Checks whether the command type carries a supported target payload.
     *
     * @param typeIdentifier the command type identifier
     * @return {@code true} if the type is one of the four target command types, {@code false} otherwise
     */
    public static boolean supports(int typeIdentifier) {
        return switch (typeIdentifier) {
            case ReplayCommandIds.RIGHT_CLICK, ReplayCommandIds.TARGETED_ORDER,
                 ReplayCommandIds.RIGHT_CLICK_121, ReplayCommandIds.TARGETED_ORDER_121 -> true;
            default -> false;
        };
    }

    /**
     * Creates a {@link ReplayTargetCommand} instance from the command payload.
     * <p>
     * Payloads exclude the opcode and use one of the four supported target layouts. Targeted orders later sign-extend coordinate words
     * after checking unsigned map bounds; this decoder retains the raw words for the caller to interpret.
     *
     * @param command the source command
     * @return the created {@link ReplayTargetCommand} instance
     * @throws IOException if the type is unsupported or the payload has an incorrect length
     */
    public static @NotNull ReplayTargetCommand parse(@NotNull ReplayCommand command) throws IOException {
        return read(command.getTypeId(), new CommandInput(command.getPayload()));
    }

    /**
     * Creates the codec of one target command type: a read is {@link #read}, a write {@link #write}.
     *
     * @param typeIdentifier the command type byte, a target command type ({@link #supports})
     * @return the created codec
     */
    public static @NotNull ReplayCommandCodec<ReplayTargetCommand> createCodec(int typeIdentifier) {
        return new ReplayCommandCodec<>() {
            @Override
            public @NotNull ReplayTargetCommand read(@NotNull CommandInput input) throws IOException {
                return ReplayTargetCommand.read(typeIdentifier, input);
            }

            @Override
            public void write(@NotNull ReplayTargetCommand command, @NotNull CommandOutput output) {
                command.write(output);
            }
        };
    }

    /**
     * Creates a right-click command.
     *
     * @param x                  the target x coordinate
     * @param y                  the target y coordinate
     * @param targetHandle       the target unit handle, {@code 0} for none
     * @param handleWidthBytes   the handle width of the command type in bytes
     * @param unitTypeIdentifier the unit type of the remembered building under the click
     * @param queueFlags         the queue flags BYTE
     * @return the created {@link ReplayTargetCommand} instance
     */
    public static @NotNull ReplayTargetCommand createRightClick(int x, int y,
                                                                long targetHandle, int handleWidthBytes,
                                                                int unitTypeIdentifier, int queueFlags) {
        return new ReplayTargetCommand(Operation.RIGHT_CLICK, x, y, targetHandle, handleWidthBytes, unitTypeIdentifier, -1, queueFlags);
    }

    /**
     * Creates a targeted order command.
     *
     * @param x                  the target x coordinate
     * @param y                  the target y coordinate
     * @param targetHandle       the target unit handle, {@code 0} for none
     * @param handleWidthBytes   the handle width of the command type in bytes
     * @param unitTypeIdentifier the unit type of the remembered building under the click
     * @param orderIdentifier    the order BYTE
     * @param queueFlags         the queue flags BYTE
     * @return the created {@link ReplayTargetCommand} instance
     */
    public static @NotNull ReplayTargetCommand createTargetedOrder(int x, int y,
                                                                   long targetHandle, int handleWidthBytes,
                                                                   int unitTypeIdentifier, int orderIdentifier, int queueFlags) {
        return new ReplayTargetCommand(Operation.TARGETED_ORDER, x, y, targetHandle, handleWidthBytes, unitTypeIdentifier, orderIdentifier,
                                       queueFlags);
    }

    /**
     * Creates a {@link ReplayTargetCommand} instance from the payload of a target command, as {@link #parse} does.
     *
     * @param typeIdentifier the command type byte
     * @param input          the payload, of which every byte left belongs to the command
     * @return the created {@link ReplayTargetCommand} instance
     * @throws IOException if the type is unsupported or the payload has an incorrect length
     */
    public static @NotNull ReplayTargetCommand read(int typeIdentifier, @NotNull CommandInput input) throws IOException {
        Operation operation = switch (typeIdentifier) {
            case ReplayCommandIds.RIGHT_CLICK, ReplayCommandIds.RIGHT_CLICK_121 -> Operation.RIGHT_CLICK;
            case ReplayCommandIds.TARGETED_ORDER, ReplayCommandIds.TARGETED_ORDER_121 -> Operation.TARGETED_ORDER;
            default -> throw Exceptions.newIOException("The replay command is not a target command: {0}", typeIdentifier);
        };

        int handleWidthBytes = typeIdentifier < ReplayCommandIds.RIGHT_CLICK_121 ? ReplayCommandUtils.LEGACY_HANDLE_WIDTH
                                                                                 : ReplayCommandUtils.HANDLE_WIDTH_121;
        boolean targetedOrder = operation == Operation.TARGETED_ORDER;
        int expectedLength = handleWidthBytes + (targetedOrder ? TARGETED_ORDER_FIELDS_SIZE : RIGHT_CLICK_FIELDS_SIZE);
        if (input.getRemaining() != expectedLength) {
            throw Exceptions.newIOException("The target command payload has an incorrect length: expected {0}, got {1}.",
                                            expectedLength, input.getRemaining());
        }

        int x = input.readUnsignedShort();
        int y = input.readUnsignedShort();
        long targetHandle = input.readUnsigned(handleWidthBytes);
        int unitTypeIdentifier = input.readUnsignedShort();
        int orderIdentifier = targetedOrder ? input.readUnsignedByte() : -1;
        int queueFlags = input.readUnsignedByte();

        return new ReplayTargetCommand(operation, x, y, targetHandle, handleWidthBytes, unitTypeIdentifier, orderIdentifier, queueFlags);
    }


    private final @NotNull Operation operation;

    private final int x;
    private final int y;

    private final long targetHandle;
    private final int handleWidthBytes;

    private final int unitTypeIdentifier;
    private final int orderIdentifier;
    private final int queueFlags;

    private ReplayTargetCommand(@NotNull Operation operation,
                                int x, int y,
                                long targetHandle, int handleWidthBytes,
                                int unitTypeIdentifier, int orderIdentifier, int queueFlags) {
        this.operation = operation;

        this.x = x;
        this.y = y;

        this.targetHandle     = targetHandle;
        this.handleWidthBytes = handleWidthBytes;

        this.unitTypeIdentifier = unitTypeIdentifier;
        this.orderIdentifier    = orderIdentifier;
        this.queueFlags         = queueFlags;
    }

    /**
     * @return the requested target operation
     */
    public @NotNull Operation getOperation() {
        return this.operation;
    }

    /**
     * @return the raw unsigned {@code 16}-bit horizontal coordinate
     */
    public int getX() {
        return this.x;
    }

    /**
     * @return the raw unsigned {@code 16}-bit vertical coordinate
     */
    public int getY() {
        return this.y;
    }

    /**
     * @return the raw unsigned target handle, including zero without resolving its meaning
     */
    public long getTargetHandle() {
        return this.targetHandle;
    }

    /**
     * @return the encoded target handle width in bytes, either {@code 2} or {@code 4}
     */
    public int getHandleWidthBytes() {
        return this.handleWidthBytes;
    }

    /**
     * @return the raw unsigned {@code 16}-bit target unit type identifier
     */
    public int getUnitTypeIdentifier() {
        return this.unitTypeIdentifier;
    }

    /**
     * @return the unsigned {@code 8}-bit explicit order identifier, or {@code -1} for a right-click request
     */
    public int getOrderIdentifier() {
        return this.orderIdentifier;
    }

    /**
     * @return the raw unsigned {@code 8}-bit queue flags without reducing them to a boolean
     */
    public int getQueueFlags() {
        return this.queueFlags;
    }


    /**
     * Writes the payload of the command: the bytes {@link #read} reads it from.
     *
     * @param output the output the payload is appended to
     */
    public void write(@NotNull CommandOutput output) {
        output.writeShort(this.x).writeShort(this.y).writeUnsigned(this.targetHandle, this.handleWidthBytes).writeShort(this.unitTypeIdentifier);

        if (this.operation == Operation.TARGETED_ORDER) {
            output.writeByte(this.orderIdentifier);
        }

        output.writeByte(this.queueFlags);
    }
}
