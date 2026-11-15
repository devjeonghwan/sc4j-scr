package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommand;
import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests the debug and frame commands of the chat line.
 */
final class DebugCommandsTest {
    private static final long REPLAY_FRAMES = 20_000;
    private static final long START_FRAME = 3000;
    private static final long FRAMES_PER_MINUTE = 1429;
    private static final long FRAMES_PER_HALF_MINUTE = 715;
    /**
     * The seek target that {@link DebugCommands#parseSeekTarget} returns for an invalid target.
     */
    private static final long INVALID_SEEK_TARGET = -1;
    private static final @NotNull String NETWORK_GAME_REFUSAL = "Not available in a network game.";
    /**
     * The widest help line, the width of the message area.
     */
    private static final int HELP_WIDTH = 72;
    /**
     * The reply lines of one page in the message area before its footer.
     */
    private static final int REPLY_PAGE_LINES = 9;

    @Test
    void debug_command_switches_layers_and_debug_view() {
        DebugCommandScenario target = new DebugCommandScenario();

        Assertions.assertEquals("Debug view shown.", DebugCommands.run("/debug", target));
        Assertions.assertEquals("Debug view hidden.", DebugCommands.run("/debug", target), "no argument toggles the view (was F12)");

        DebugCommands.run("/debug info", target);

        Assertions.assertTrue(target.isDebugView());
        Assertions.assertEquals("Debug regions on.", DebugCommands.run("/debug regions on", target));
        Assertions.assertTrue(target.getOverlay().isEnabled(DebugOverlay.Layer.REGIONS));
        Assertions.assertEquals("Debug regions off.", DebugCommands.run("/debug regions", target), "a layer word alone toggles it");
        Assertions.assertEquals("All debug layers off.", DebugCommands.run("/debug none", target));
        Assertions.assertTrue(target.getOverlay().getEnabledLayers().isEmpty());
        Assertions.assertEquals("Debug vision on for player 3.", DebugCommands.run("/debug vision 3", target));
        Assertions.assertEquals(List.of("vision 3"), target.actions);
        Assertions.assertTrue(DebugCommands.run("/debug vision 9", target).startsWith("Use /debug vision"));

        DebugCommands.run("/debug off", target);

        Assertions.assertFalse(target.isDebugView());

        DebugCommands.run("/debug creep", target);

        Assertions.assertTrue(target.isDebugView(), "switching a layer shows the view");
        Assertions.assertTrue(DebugCommands.run("/debug nonsense", target).startsWith("Unknown argument: nonsense."));
    }

    /**
     * {@code /debug page} replaces Ctrl+F12 and shows the view.
     */
    @Test
    void debug_page_command_shows_next_panel_page() {
        DebugCommandScenario target = new DebugCommandScenario();

        Assertions.assertEquals("Debug panel page: performance.", DebugCommands.run("/debug page", target));
        Assertions.assertTrue(target.isDebugView());
        Assertions.assertEquals("Debug panel page: camera.", DebugCommands.run("/debug page", target));
    }

    /**
     * {@code /debug lighting} replaces F7: no view or next cycles, a name selects, lights is an alias.
     */
    @Test
    void debug_lighting_command_selects_and_cycles_lighting_views() {
        DebugCommandScenario target = new DebugCommandScenario();

        Assertions.assertEquals("Lighting view: lights.", DebugCommands.run("/debug lighting", target));
        Assertions.assertEquals("Lighting view: lighting.", DebugCommands.run("/debug lights next", target));
        Assertions.assertEquals("Lighting view: normals.", DebugCommands.run("/debug lighting NORMALS", target));
        Assertions.assertEquals(LightingView.NORMALS, target.lightingView);
        Assertions.assertTrue(DebugCommands.run("/debug lighting glow", target).startsWith("Unknown lighting view: glow. Use off, lights,"));

        target.lightingView = LightingView.AMOUNT;

        Assertions.assertEquals("Lighting view: off.", DebugCommands.run("/debug lighting", target), "the cycle wraps");
    }

    @Test
    void step_and_seek_commands_run_frame_tools_and_reject_bad_targets() {
        DebugCommandScenario target = new DebugCommandScenario();

        for (String command : new String[]{"/step", "/seek 4:12", "/seek 123", "/seek start"}) {
            DebugCommands.run(command, target);
        }

        Assertions.assertEquals(List.of("step", "seek 6000", "seek 123", "seek 0"), target.actions);
        Assertions.assertTrue(DebugCommands.run("/seek", target).startsWith("Use /seek"));
        Assertions.assertTrue(DebugCommands.run("/seek soon", target).startsWith("Unknown seek target: soon."));
        Assertions.assertTrue(DebugCommands.run("hello", target).startsWith("Unknown command"));
    }

    /**
     * The relative seeks replace the period, comma and Home keys.
     */
    @Test
    void seek_command_seeks_relative_to_current_frame_and_clamps_to_replay() {
        DebugCommandScenario target = new DebugCommandScenario();

        Assertions.assertEquals("Seeking to frame " + (START_FRAME + FRAMES_PER_MINUTE) + ".", DebugCommands.run("/seek +1m", target));
        Assertions.assertEquals("Seeking to frame " + (START_FRAME - FRAMES_PER_MINUTE) + ".", DebugCommands.run("/seek -1m", target));
        Assertions.assertEquals("Seeking to frame " + (START_FRAME - FRAMES_PER_HALF_MINUTE) + ".", DebugCommands.run("/seek -30s", target));
        Assertions.assertEquals("Seeking to frame 0.", DebugCommands.run("/seek -10m", target), "never before frame 0");
        Assertions.assertEquals("Seeking to frame " + REPLAY_FRAMES + ".", DebugCommands.run("/seek 99:00", target), "clamped to the replay");
        Assertions.assertEquals(3100, DebugCommands.parseSeekTarget("+100", 3000));
        Assertions.assertEquals(DebugCommands.parseSeekTarget("1:00", 0), DebugCommands.parseSeekTarget("60s", 0));
        Assertions.assertEquals(INVALID_SEEK_TARGET, DebugCommands.parseSeekTarget("+", 0));
        Assertions.assertEquals(INVALID_SEEK_TARGET, DebugCommands.parseSeekTarget("1:-5", 0));
    }

    /**
     * In single player the frame tools run; in a network game they are refused locally and move nothing.
     */
    @Test
    void frame_tools_run_in_single_player_and_are_refused_in_network_game() {
        DebugCommandScenario single = new DebugCommandScenario();

        Assertions.assertEquals("Stepped to frame 3001.", DebugCommands.run("/step", single));

        single.replay = false;

        Assertions.assertEquals("Seeking needs a replay in the replay viewer.", DebugCommands.run("/seek 123", single));
        Assertions.assertEquals(List.of("step"), single.actions);

        DebugCommandScenario network = new DebugCommandScenario();

        network.network = true;

        Assertions.assertEquals(NETWORK_GAME_REFUSAL, DebugCommands.run("/step", network));
        Assertions.assertEquals(NETWORK_GAME_REFUSAL, DebugCommands.run("/seek 123", network));
        Assertions.assertTrue(network.actions.isEmpty());

        // The /debug command is local only and works in both.
        DebugCommands.run("/debug", network);

        Assertions.assertTrue(network.isDebugView());
    }

    /**
     * The {@code /debug} help names every layer, the panel sections and every lighting view.
     */
    @Test
    void debug_help_documents_every_layer_and_lighting_view() {
        String help = DebugCommands.run("/help debug", new DebugCommandScenario());
        // A wrapped entry continues on the next line after the continuation indent.
        String flat = help.replace("\n      ", " ");

        for (DebugOverlay.Layer layer : DebugOverlay.Layer.values()) {
            Assertions.assertTrue(help.contains("/debug " + layer.getName() + " [on|off") && flat.contains(layer.getDescription()), layer.getName());
        }

        for (LightingView view : LightingView.values()) {
            Assertions.assertTrue(help.contains("  " + view.getName() + " - "), view.getName());
        }

        for (String text : new String[]{"/debug - show or hide", "/debug page", "/debug all", "/debug none", "/debug off",
                                        "Performance", "Game state", "Where:", "Example: /debug regions"}) {
            Assertions.assertTrue(help.contains(text), text);
        }
    }

    /**
     * Every command's help fits the message area and says where it works with an example.
     */
    @Test
    void help_documents_every_command_within_message_width() {
        ViewerCommandScenario client = new ViewerCommandScenario();
        ConsoleCommandRegistry registry = client.createRegistry();

        DebugCommands.register(registry, new DebugCommandScenario());

        Assertions.assertEquals(List.of("/zoom", "/pan", "/replay", "/console", "/preview", "/debug", "/step", "/seek"),
                                registry.getCommands().stream().map(ConsoleCommand::getName).toList());

        for (String line : registry.getHelpLines()) {
            Assertions.assertTrue(line.length() <= HELP_WIDTH, line);
        }

        for (ConsoleCommand command : registry.getCommands()) {
            List<String> help = registry.getCommandHelpLines(command.getName());

            Assertions.assertTrue(help.getFirst().startsWith(command.getName() + " "), command.getName());
            Assertions.assertTrue(help.stream().anyMatch(line -> line.startsWith("Where: ")), command.getName());
            Assertions.assertTrue(help.stream().anyMatch(line -> line.startsWith("Example: " + command.getName())), command.getName());

            for (String line : help) {
                Assertions.assertTrue(line.length() <= HELP_WIDTH, command.getName() + ": " + line);
            }
        }
    }

    @Test
    void create_registry_should_page_replies_for_message_area() {
        ConsoleCommandRegistry registry = DebugCommands.createRegistry();

        Assertions.assertTrue(registry.run("/help more").endsWith("shows " + REPLY_PAGE_LINES + " lines at a time."));
    }
}
