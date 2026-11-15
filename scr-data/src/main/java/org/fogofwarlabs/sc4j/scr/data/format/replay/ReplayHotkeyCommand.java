package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents the native Hotkey command: the control group operation BYTE and the group BYTE.
 */
public final class ReplayHotkeyCommand {
    /**
     * The codec of the command.
     */
    public static final @NotNull ReplayCommandCodec<ReplayHotkeyCommand> CODEC = new ReplayCommandCodec<>() {
        @Override
        public @NotNull ReplayHotkeyCommand read(@NotNull CommandInput input) throws IOException {
            int operation = input.readUnsignedByte();
            int group = input.readUnsignedByte();

            return new ReplayHotkeyCommand(operation, group);
        }

        @Override
        public void write(@NotNull ReplayHotkeyCommand command, @NotNull CommandOutput output) {
            output.writeByte(command.getOperation()).writeByte(command.getGroup());
        }
    };

    private final int operation;
    private final int group;

    /**
     * Creates a {@link ReplayHotkeyCommand} instance.
     *
     * @param operation the control group operation BYTE
     * @param group     the group BYTE
     */
    public ReplayHotkeyCommand(int operation, int group) {
        this.operation = operation;
        this.group     = group;
    }

    /**
     * @return the control group operation BYTE
     */
    public int getOperation() {
        return this.operation;
    }

    /**
     * @return the group BYTE
     */
    public int getGroup() {
        return this.group;
    }
}
