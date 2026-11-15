package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandSink;
import org.fogofwarlabs.sc4j.engine.command.CommandType;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the commands submitted to it in the native command dispatch of one dispatcher ({@link ReplayCommandExecutor#createSink}): the engine
 * {@link CommandSink} of the SC:R simulation. A command runs at once, in submission order, for the record of the submitting player, exactly as
 * a recorded command of its type and payload whose issuer resolves to that record runs ({@link ReplayCommandExecutor#execute}); the issuer
 * resolution and the payload checks of recorded commands stay with the recorded stream.
 */
public final class ScrCommandDispatchSink implements CommandSink {
    private final @NotNull ReplayCommandExecutor executor;
    private final @NotNull Game game;

    /**
     * Creates a {@link ScrCommandDispatchSink} instance.
     *
     * @param executor the dispatcher whose command types and cases run the commands
     * @param game     the SC:R game the commands run in
     */
    ScrCommandDispatchSink(@NotNull ReplayCommandExecutor executor, @NotNull Game game) {
        this.executor = executor;
        this.game     = game;
    }

    /**
     * Submits one command of a player.
     * <p>
     * The command runs at once in the native dispatch of its type, for the player's record and its command player.
     *
     * @param <C>     the command class
     * @param player  the issuing player
     * @param type    the command type
     * @param command the command
     * @throws IllegalArgumentException if the type is not a command type of the dispatcher
     * @throws IllegalStateException    if the player has no record
     */
    @Override
    public <C> void submit(@NotNull Player player, @NotNull CommandType<C> type, @NotNull C command) {
        ScrCommandType<?> registered = this.executor.getCommandTypes().find(type.getId());

        if (registered != type) {
            throw Exceptions.newIllegalArgumentException("The command type {0} is not a type of this dispatcher.", type.getId());
        }

        org.fogofwarlabs.sc4j.scr.game.player.Player record = this.game.getPlayerTable().getPlayer(player.getIndex());

        if (record == null) {
            throw Exceptions.newIllegalStateException("The command issuer {0} has no player record.", player);
        }

        @SuppressWarnings("unchecked")
        ScrCommandType<C> scrType = (ScrCommandType<C>) registered;

        this.executor.dispatch(this.game, player, record.getCommandPlayerIndex(), scrType, command);
    }
}
