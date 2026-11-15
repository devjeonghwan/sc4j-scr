package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Reads and writes the payload of one native command type: the bytes after the type byte, as replays, LAN turns and recordings store them
 * ({@link ReplayCommand#getPayload()}). A write of a read command gives back the read bytes.
 *
 * @param <C> the command class
 */
public interface ReplayCommandCodec<C> {
    /**
     * Reads a command from its payload.
     *
     * @param input the payload, positioned at its first byte
     * @return the command
     * @throws IOException if the payload is not a command of the type
     */
    @NotNull C read(@NotNull CommandInput input) throws IOException;

    /**
     * Writes the payload of a command.
     *
     * @param command the command
     * @param output  the output the payload is appended to
     */
    void write(@NotNull C command, @NotNull CommandOutput output);
}
