package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.fogofwarlabs.sc4j.engine.command.CommandSink;
import org.fogofwarlabs.sc4j.engine.command.CommandType;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Receives the engine commands of the SC:R map input as an engine {@link CommandSink}: each command is written with its type's native codec
 * and submitted to the input's raw sink ({@link CommandInput.CommandSink}) as the type id and payload, the same form the command card's own
 * commands take (the live turn buffer of a live game, the replay viewer's refusal otherwise).
 */
public final class EncodedCommandSink implements CommandSink {
    private final @NotNull CommandInput.CommandSink output;

    private final @NotNull CommandOutput encoder;

    /**
     * Creates an {@link EncodedCommandSink} instance.
     *
     * @param output the raw sink of the map input
     */
    public EncodedCommandSink(@NotNull CommandInput.CommandSink output) {
        this.output = output;

        this.encoder = new CommandOutput();
    }

    /**
     * Encodes one command of a player and submits it to the raw sink.
     *
     * @param <C>     the command class
     * @param player  the issuing player, whose slot is the submitting player
     * @param type    the command type
     * @param command the command
     */
    @Override
    public <C> void submit(@NotNull Player player, @NotNull CommandType<C> type, @NotNull C command) {
        this.encoder.clear();
        type.write(command, this.encoder);
        this.output.submit(player.getIndex(), type.getId(), this.encoder.toByteArray());
    }
}
