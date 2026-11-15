package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A command sink for input tests: it records every submitted command in submission order instead of executing it.
 */
public final class RecordingCommandSink implements CommandInput.CommandSink {
    /**
     * One submitted command.
     */
    public static final class Command {
        private final int player;
        private final int opcode;
        private final byte @NotNull [] payload;

        /**
         * Creates a {@link Command} instance.
         *
         * @param player  the submitting player
         * @param opcode  the command opcode
         * @param payload the command payload without the opcode
         */
        private Command(int player, int opcode, byte @NotNull [] payload) {
            this.player  = player;
            this.opcode  = opcode;
            this.payload = payload;
        }

        /**
         * @return the submitting player
         */
        public int getPlayer() {
            return this.player;
        }

        /**
         * @return the command opcode
         */
        public int getOpcode() {
            return this.opcode;
        }

        /**
         * @return the command payload without the opcode
         */
        public byte @NotNull [] getPayload() {
            return this.payload;
        }
    }

    private final @NotNull List<Command> commands;

    /**
     * Creates a {@link RecordingCommandSink} instance that has recorded nothing.
     */
    public RecordingCommandSink() {
        this.commands = new ArrayList<>();
    }

    /**
     * @return the submitted commands, in submission order
     */
    public @NotNull List<Command> getCommands() {
        return this.commands;
    }

    /**
     * Records a command of the local player.
     *
     * @param player  the local player slot
     * @param opcode  the command opcode
     * @param payload the command payload without the opcode
     */
    @Override
    public void submit(int player, int opcode, byte @NotNull [] payload) {
        this.commands.add(new Command(player, opcode, payload));
    }
}
