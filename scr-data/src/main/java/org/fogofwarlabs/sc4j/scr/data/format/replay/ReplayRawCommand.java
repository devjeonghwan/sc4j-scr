package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents a native command whose payload the simulation does not interpret: none (Keep Alive, Pause, Stop of a Carrier and the other commands
 * of the current selection without arguments), or bytes kept as recorded (Sync, Latency, Save Game and the other session commands).
 */
public final class ReplayRawCommand {
    /**
     * The command without payload bytes.
     */
    public static final @NotNull ReplayRawCommand EMPTY = new ReplayRawCommand(new byte[0]);

    /**
     * The codec of a payload kept as recorded: a read takes every byte left.
     */
    public static final @NotNull ReplayCommandCodec<ReplayRawCommand> CODEC = new ReplayCommandCodec<>() {
        @Override
        public @NotNull ReplayRawCommand read(@NotNull CommandInput input) throws IOException {
            int length = input.getRemaining();

            return length == 0 ? EMPTY : new ReplayRawCommand(input.readBytes(length));
        }

        @Override
        public void write(@NotNull ReplayRawCommand command, @NotNull CommandOutput output) {
            output.writeBytes(command.payload);
        }
    };

    private final byte @NotNull [] payload;

    private ReplayRawCommand(byte @NotNull [] payload) {
        this.payload = payload;
    }

    /**
     * @return the number of payload bytes
     */
    public int getLength() {
        return this.payload.length;
    }
}
