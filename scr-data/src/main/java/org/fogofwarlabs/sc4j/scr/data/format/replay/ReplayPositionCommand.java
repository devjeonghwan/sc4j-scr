package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents a native command whose payload is one map position, two little-endian WORDs: Lift Off and Minimap Ping.
 */
public final class ReplayPositionCommand {
    /**
     * The codec of the command.
     */
    public static final @NotNull ReplayCommandCodec<ReplayPositionCommand> CODEC = new ReplayCommandCodec<>() {
        @Override
        public @NotNull ReplayPositionCommand read(@NotNull CommandInput input) throws IOException {
            int x = input.readUnsignedShort();
            int y = input.readUnsignedShort();

            return new ReplayPositionCommand(x, y);
        }

        @Override
        public void write(@NotNull ReplayPositionCommand command, @NotNull CommandOutput output) {
            output.writeShort(command.getX()).writeShort(command.getY());
        }
    };

    private final int x;
    private final int y;

    /**
     * Creates a {@link ReplayPositionCommand} instance.
     *
     * @param x the x coordinate WORD
     * @param y the y coordinate WORD
     */
    public ReplayPositionCommand(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * @return the x coordinate WORD
     */
    public int getX() {
        return this.x;
    }

    /**
     * @return the y coordinate WORD
     */
    public int getY() {
        return this.y;
    }
}
