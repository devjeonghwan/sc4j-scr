package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.fogofwarlabs.sc4j.engine.command.CommandType;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Runs a whole recorded command as an engine command: the command type of the SC:R recorded command stream ({@link ReplaySession#getController}).
 * A recorded command names its issuer itself (the network id the native dispatcher resolves when the command runs, which may resolve to no
 * record) and may be a type byte without a native case or a malformed payload that the dispatcher keeps as a barrier; so the stream hands over
 * the whole record, and executing it runs the session's command handler on the record exactly as the native dispatcher runs a recorded
 * command ({@link ReplayCommandHandler#execute}). The submitting player is not read: the handler resolves the issuer from the record.
 * <p>
 * The payload is little-endian: the frame (eight bytes), the issuer (four bytes), the type byte and the recorded payload bytes. The type is
 * not in the native command registry; its id is {@link #RECORDED_COMMAND_ID}, past the native type bytes.
 */
public final class RecordedCommandType implements CommandType<ReplayCommand> {
    /**
     * The id of the recorded command type: one past the native type bytes, which the native command registry holds.
     */
    public static final int RECORDED_COMMAND_ID = 0x100;

    /**
     * The width of the encoded frame in bytes.
     */
    private static final int FRAME_BYTES = Long.BYTES;

    private final @NotNull Game game;
    private final @NotNull ReplayCommandHandler handler;

    /**
     * Creates a {@link RecordedCommandType} instance.
     *
     * @param game    the SC:R game the recorded commands run in
     * @param handler the handler that runs a recorded command, the native dispatcher or a session's own
     */
    public RecordedCommandType(@NotNull Game game, @NotNull ReplayCommandHandler handler) {
        this.game    = game;
        this.handler = handler;
    }

    /**
     * @return the id of the type in the mod's command stream, {@code 0} or more
     */
    @Override
    public int getId() {
        return RECORDED_COMMAND_ID;
    }

    /**
     * Reads a command from its encoded payload.
     *
     * @param input the payload of one command, positioned at its first byte
     * @return the command
     * @throws IOException if the payload is not a command of this type
     */
    @Override
    public @NotNull ReplayCommand read(@NotNull CommandInput input) throws IOException {
        long frame = input.readUnsigned(FRAME_BYTES);
        int playerId = input.readInt();
        int typeId = input.readUnsignedByte();
        byte[] payload = input.readBytes(input.getRemaining());

        return new ReplayCommand(frame, playerId, typeId, payload);
    }

    /**
     * Writes the encoded payload of a command.
     *
     * @param command the command
     * @param output  the output the payload is appended to
     */
    @Override
    public void write(@NotNull ReplayCommand command, @NotNull CommandOutput output) {
        output.writeUnsigned(command.getFrame(), FRAME_BYTES)
              .writeInt(command.getPlayerId())
              .writeByte(command.getTypeId())
              .writeBytes(command.getPayload());
    }

    /**
     * Applies a command of a player to the game.
     * <p>
     * The recorded command runs through the handler for the issuer it names; the player is not read.
     *
     * @param game    the game
     * @param player  the issuing player
     * @param command the command
     */
    @Override
    public void execute(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game, @NotNull Player player, @NotNull ReplayCommand command) {
        this.handler.execute(this.game, command);
    }
}
