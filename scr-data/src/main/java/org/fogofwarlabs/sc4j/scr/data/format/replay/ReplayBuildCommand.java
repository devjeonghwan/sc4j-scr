package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents the native Build command: the build order BYTE, the tile position WORDs and the unit type WORD ({@link ReplayFile#BUILD_PAYLOAD}
 * bytes).
 */
public final class ReplayBuildCommand {
    /**
     * The codec of the command.
     */
    public static final @NotNull ReplayCommandCodec<ReplayBuildCommand> CODEC = new ReplayCommandCodec<>() {
        @Override
        public @NotNull ReplayBuildCommand read(@NotNull CommandInput input) throws IOException {
            int order = input.readUnsignedByte();
            int tileX = input.readUnsignedShort();
            int tileY = input.readUnsignedShort();
            int unitType = input.readUnsignedShort();

            return new ReplayBuildCommand(order, tileX, tileY, unitType);
        }

        @Override
        public void write(@NotNull ReplayBuildCommand command, @NotNull CommandOutput output) {
            output.writeByte(command.getOrder()).writeShort(command.getTileX()).writeShort(command.getTileY()).writeShort(command.getUnitType());
        }
    };

    private final int order;
    private final int tileX;
    private final int tileY;
    private final int unitType;

    /**
     * Creates a {@link ReplayBuildCommand} instance.
     *
     * @param order    the build order BYTE
     * @param tileX    the tile x WORD
     * @param tileY    the tile y WORD
     * @param unitType the unit type WORD
     */
    public ReplayBuildCommand(int order, int tileX, int tileY, int unitType) {
        this.order    = order;
        this.tileX    = tileX;
        this.tileY    = tileY;
        this.unitType = unitType;
    }

    /**
     * @return the build order BYTE
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * @return the tile x WORD
     */
    public int getTileX() {
        return this.tileX;
    }

    /**
     * @return the tile y WORD
     */
    public int getTileY() {
        return this.tileY;
    }

    /**
     * @return the unit type WORD
     */
    public int getUnitType() {
        return this.unitType;
    }
}
