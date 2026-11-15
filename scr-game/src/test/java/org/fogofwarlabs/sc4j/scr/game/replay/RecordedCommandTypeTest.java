package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandOutput;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests that a {@link RecordedCommandType} writes back what it reads and runs a whole recorded command through its handler, whatever player
 * the stream submits it as.
 */
final class RecordedCommandTypeTest {
    private static final int SEED = 0x2026_1106;
    private static final long FRAME = 0x1_2345_6789L;
    private static final int ISSUER = 0x83;
    private static final int TYPE_ID = 0x14;

    @Test
    void read_should_return_the_written_record_and_execute_should_run_it_through_the_handler() throws IOException {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1], new int[1]), new LcgRandom(SEED));
        List<ReplayCommand> handled = new ArrayList<>();
        RecordedCommandType type = new RecordedCommandType(game, (currentGame, command) -> {
            Assertions.assertSame(game, currentGame);

            handled.add(command);
        });
        ReplayCommand record = new ReplayCommand(FRAME, ISSUER, TYPE_ID, new byte[]{1, 2, 3});
        CommandOutput output = new CommandOutput();

        type.write(record, output);

        ReplayCommand read = type.read(new CommandInput(output.toByteArray()));

        Assertions.assertEquals(FRAME, read.getFrame());
        Assertions.assertEquals(ISSUER, read.getPlayerId());
        Assertions.assertEquals(TYPE_ID, read.getTypeId());
        Assertions.assertArrayEquals(record.getPayload(), read.getPayload());
        Assertions.assertEquals(RecordedCommandType.RECORDED_COMMAND_ID, type.getId());

        type.execute(game.getEngineGame(), game.getEngineGame().getPlayer(PlayerTable.NEUTRAL_PLAYER_ID), read);

        Assertions.assertEquals(List.of(read), handled);
    }
}
