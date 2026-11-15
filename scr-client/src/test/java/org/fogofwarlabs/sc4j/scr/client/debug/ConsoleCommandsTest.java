package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code /console} command that switches the replay console's player, controls and recorded selection outside live games.
 */
final class ConsoleCommandsTest {
    private static final @NotNull String LIVE_GAME_REFUSAL = "Not available in a live game; it works in the replay viewer.";

    /**
     * {@code /console} replaces G, V and L.
     */
    @Test
    void console_command_switches_replay_console() {
        ViewerCommandScenario client = new ViewerCommandScenario();
        ConsoleCommandRegistry registry = client.createRegistry();

        Assertions.assertEquals("Console player 1.", registry.run("/console player"));
        Assertions.assertEquals("Console player 5.", registry.run("/console player 5"));

        client.consolePlayer = 7;

        Assertions.assertEquals("Console player 0.", registry.run("/console player"), "the next player wraps");
        Assertions.assertEquals("Use /console player 0-7.", registry.run("/console player 8"));
        Assertions.assertEquals("Replay controls hidden.", registry.run("/console controls"));
        Assertions.assertEquals("Replay controls shown.", registry.run("/console controls on"));
        Assertions.assertEquals("Console follows player 0's recorded selection.", registry.run("/console follow"));

        client.live = true;

        Assertions.assertEquals(LIVE_GAME_REFUSAL, registry.run("/console player 3"));
        Assertions.assertEquals(0, client.consolePlayer);
    }
}
