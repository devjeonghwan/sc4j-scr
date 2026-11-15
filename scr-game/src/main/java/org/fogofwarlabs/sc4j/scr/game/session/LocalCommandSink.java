package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.fogofwarlabs.sc4j.engine.command.CommandSink;
import org.fogofwarlabs.sc4j.engine.command.CommandType;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Receives the commands of the local player of a live game as an engine {@link CommandSink}: each command is encoded with the native codec of
 * its type (the type byte and the payload) and queued into the outgoing turn ({@link LiveGame#submit}), as the input layer's raw submissions
 * are. A command the replay excludes is not queued, as there.
 * <p>
 * The outgoing turn carries no player byte: the network session and the replay name the local player as the issuer, so the submitting player
 * must be the local player.
 */
public final class LocalCommandSink implements CommandSink {
    private final @NotNull LiveGame liveGame;
    private final @NotNull Player localPlayer;

    private final @NotNull CommandOutput output;

    /**
     * Creates a {@link LocalCommandSink} instance.
     *
     * @param liveGame    the live game whose outgoing turn receives the commands
     * @param localPlayer the engine player of the local player's slot
     */
    public LocalCommandSink(@NotNull LiveGame liveGame, @NotNull Player localPlayer) {
        this.liveGame    = liveGame;
        this.localPlayer = localPlayer;

        this.output = new CommandOutput();
    }

    /**
     * Submits one command of a player: encodes it and queues it into the outgoing turn.
     *
     * @param <C>     the command class
     * @param player  the issuing player, the local player
     * @param type    the command type
     * @param command the command
     * @throws IllegalArgumentException if the player is not the local player
     */
    @Override
    public <C> void submit(@NotNull Player player, @NotNull CommandType<C> type, @NotNull C command) {
        if (player != this.localPlayer) {
            throw Exceptions.newIllegalArgumentException("Only the local player {0} submits here, not {1}.", this.localPlayer, player);
        }

        this.output.clear();
        type.write(command, this.output);
        this.liveGame.submit(type.getId(), this.output.toByteArray());
    }
}
