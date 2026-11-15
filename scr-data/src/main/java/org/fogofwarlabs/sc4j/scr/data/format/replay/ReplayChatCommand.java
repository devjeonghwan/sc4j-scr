package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * Represents the native Chat command: the sender BYTE and the message field ({@link ReplayFile#CHAT_MESSAGE_SIZE} bytes as recorded), whose text
 * ends at its first zero byte.
 */
public final class ReplayChatCommand {
    /**
     * The codec of the command: a read takes the sender and every byte left as the message field.
     */
    public static final @NotNull ReplayCommandCodec<ReplayChatCommand> CODEC = new ReplayCommandCodec<>() {
        @Override
        public @NotNull ReplayChatCommand read(@NotNull CommandInput input) throws IOException {
            int sender = input.readUnsignedByte();

            return new ReplayChatCommand(sender, input.readBytes(input.getRemaining()));
        }

        @Override
        public void write(@NotNull ReplayChatCommand command, @NotNull CommandOutput output) {
            output.writeByte(command.getSender()).writeBytes(command.field);
        }
    };

    private final int sender;
    private final byte @NotNull [] field;

    /**
     * Creates a {@link ReplayChatCommand} instance.
     *
     * @param sender the sender BYTE
     * @param field  the message field as recorded, owned by the command
     */
    public ReplayChatCommand(int sender, byte @NotNull [] field) {
        this.sender = sender;
        this.field  = field;
    }

    /**
     * @return the sender BYTE
     */
    public int getSender() {
        return this.sender;
    }

    /**
     * Copies the message text: the bytes of the field before its first zero byte, or the whole field.
     *
     * @return the text bytes
     */
    public byte @NotNull [] copyText() {
        int textEnd = 0;

        while (textEnd < this.field.length && this.field[textEnd] != 0) {
            textEnd++;
        }

        return Arrays.copyOf(this.field, textEnd);
    }
}
