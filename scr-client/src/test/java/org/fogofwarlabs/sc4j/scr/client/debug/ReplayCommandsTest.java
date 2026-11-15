package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests the {@code /replay} command that opens, restarts and fits a replay outside live games.
 */
final class ReplayCommandsTest {
    private static final @NotNull String LIVE_GAME_REFUSAL = "Not available in a live game; it works in the replay viewer.";

    /**
     * {@code /replay} replaces O, R and F, and is refused in a live game.
     */
    @Test
    void replay_command_opens_restarts_and_fits_replay_except_in_live_game() {
        ViewerCommandScenario client = new ViewerCommandScenario();
        ConsoleCommandRegistry registry = client.createRegistry();

        Assertions.assertEquals("Choose a replay to open.", registry.run("/replay open"));
        Assertions.assertEquals("Replay restarted.", registry.run("/replay restart"));
        Assertions.assertEquals("Map fitted.", registry.run("/replay fit"));
        Assertions.assertEquals(List.of("open", "restart", "fit"), client.actions);

        client.hidden = true;

        Assertions.assertEquals("No file dialog in a hidden run.", registry.run("/replay open"));
        Assertions.assertTrue(registry.run("/replay").startsWith("Use /replay open|restart|fit"));

        client.actions.clear();
        client.live = true;

        Assertions.assertEquals(LIVE_GAME_REFUSAL, registry.run("/replay restart"));
        Assertions.assertTrue(client.actions.isEmpty());
    }
}
