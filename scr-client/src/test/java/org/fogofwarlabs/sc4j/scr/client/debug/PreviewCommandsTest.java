package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests the {@code /preview} command that inspects the image and GRP frames of the selected map placement outside live games.
 */
final class PreviewCommandsTest {
    private static final @NotNull String LIVE_GAME_REFUSAL = "Not available in a live game; it works in the replay viewer.";

    /**
     * {@code /preview} replaces I, Backspace, [ ], M, J, K, Enter and T.
     */
    @Test
    void preview_command_inspects_placement_image() {
        ViewerCommandScenario client = new ViewerCommandScenario();
        ConsoleCommandRegistry registry = client.createRegistry();

        Assertions.assertEquals("No preview open; /preview start opens one.", registry.run("/preview play"));
        Assertions.assertEquals("Animation 1 chosen; /preview play requests it.", registry.run("/preview anim next"));
        Assertions.assertEquals("Animation 0 chosen; /preview play requests it.", registry.run("/preview anim prev"));
        Assertions.assertEquals("Animation 12 chosen; /preview play requests it.", registry.run("/preview anim 12"));
        Assertions.assertTrue(registry.run("/preview anim 256").startsWith("Use /preview anim"));
        Assertions.assertEquals("Preview started, animation 12.", registry.run("/preview start"));
        Assertions.assertEquals("Animation 12 requested.", registry.run("/preview play"));
        Assertions.assertEquals("Preview stepped.", registry.run("/preview step"));
        Assertions.assertEquals(List.of("play 12", "step"), client.actions);
        Assertions.assertEquals("Preview closed.", registry.run("/preview close"));
        Assertions.assertEquals("GRP frame 1 of 17.", registry.run("/preview frame +1"));
        Assertions.assertEquals("GRP frame 0 of 17.", registry.run("/preview frame -1"));
        Assertions.assertTrue(registry.run("/preview frame").startsWith("Use /preview frame"));
        Assertions.assertEquals("Frame mirrored.", registry.run("/preview mirror"));

        client.placement = false;

        Assertions.assertEquals("Select a map placement first (left click).", registry.run("/preview start"));
        Assertions.assertEquals("Select a map placement first (left click).", registry.run("/preview mirror"));

        client.live = true;

        Assertions.assertEquals(LIVE_GAME_REFUSAL, registry.run("/preview start"));
    }
}
