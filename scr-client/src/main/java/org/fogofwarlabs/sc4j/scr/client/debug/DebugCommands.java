package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommand;
import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Registers the client's debug and frame commands of the chat line in a {@link ConsoleCommandRegistry}: {@code /debug} (the debug view, its
 * overlay layers, its panel pages and the HD lighting views), {@code /step} and {@code /seek}.
 */
public final class DebugCommands {
    /**
     * The refusal of a command that would move a network game's lockstep simulation.
     */
    public static final @NotNull String NETWORK_GAME_REFUSAL = "Not available in a network game.";
    /**
     * The refusal of a replay viewer command in a live game.
     */
    public static final @NotNull String LIVE_GAME_REFUSAL = "Not available in a live game; it works in the replay viewer.";
    /**
     * The lines of the game's message area that a paged reply leaves free: its footer and a piece the message layout wraps.
     */
    private static final int RESERVED_REPLY_LINES = 2;
    /**
     * The reply lines of one page in the game's message area, which shows {@link MessageLines#ROTATING_LINES} rotating lines.
     */
    static final int REPLY_PAGE_LINES = MessageLines.ROTATING_LINES - RESERVED_REPLY_LINES;
    /**
     * The milliseconds of one game frame at Fastest, the clock of {@code m:ss} times.
     */
    static final long FRAME_MILLISECONDS = GameSpeed.FASTEST_DURATION;
    /**
     * The result of {@link #parseSeekTarget} for a value that is no seek target.
     */
    static final long INVALID_TARGET = -1;
    private static final long MILLISECONDS_PER_SECOND = 1000;
    private static final long SECONDS_PER_MINUTE = 60;
    /**
     * The highest player slot of a player argument.
     */
    private static final int MAXIMUM_PLAYER = PlayerTable.PLAYABLE_PLAYER_SLOTS - 1;

    private static final @NotNull String NEXT = "next";
    private static final @NotNull String SEEK_START = "start";
    private static final @NotNull String MINUTES_SUFFIX = "m";
    private static final @NotNull String SECONDS_SUFFIX = "s";
    private static final @NotNull String LIST_SEPARATOR = ", ";
    private static final char TIME_SEPARATOR = ':';
    private static final char FORWARD_SIGN = '+';
    private static final char BACKWARD_SIGN = '-';

    /**
     * Receives the effect of a debug command.
     */
    public interface Target {
        /**
         * @return {@code true} if the debug view is shown, {@code false} otherwise
         */
        boolean isDebugView();

        /**
         * Sets whether the development panel and debug overlays are shown.
         *
         * @param shown whether the debug view (development panel and overlays) is shown
         */
        void setDebugView(boolean shown);

        /**
         * @return the overlay whose layers the commands switch
         */
        @NotNull DebugOverlay getOverlay();

        /**
         * Sets the player whose vision the vision layer shows.
         *
         * @param player the player whose vision the vision layer shows
         */
        void setVisionPlayer(int player);

        /**
         * @return the selected HD lighting debug view
         */
        @NotNull LightingView getLightingView();

        /**
         * Sets the selected HD lighting debug view.
         *
         * @param view the view
         * @return the message describing the effect
         */
        @NotNull String setLightingView(@NotNull LightingView view);

        /**
         * @return {@code true} if the session is a network game, whose lockstep simulation the frame tools must not move, {@code false} otherwise
         */
        boolean isNetworkGame();

        /**
         * @return {@code true} if a replay plays, the only session {@code /seek} can move, {@code false} otherwise
         */
        boolean isReplay();

        /**
         * @return the last completed game frame
         */
        long getFrame();

        /**
         * Shows the next page of the debug panel.
         *
         * @return the page now shown
         */
        @NotNull DebugPanel.Page showNextDebugPage();

        /**
         * Pauses and runs one game frame.
         */
        void step();

        /**
         * Requests a seek of the replay.
         *
         * @param frame the game frame to seek to
         * @return the requested frame after clamping it to the replay
         */
        long seek(long frame);
    }

    /**
     * Registers {@code /debug}, {@code /step} and {@code /seek}.
     *
     * @param registry the command registry
     * @param target   where the commands act
     */
    public static void register(@NotNull ConsoleCommandRegistry registry, @NotNull Target target) {
        registry.register(createDebugCommand(target));
        registry.register(ConsoleCommand.of("/step", "", "pause and run one game frame")
                                        .whenEmpty("pause the game or replay and run exactly one frame", arguments -> {
                                            target.step();

                                            return "Stepped to frame " + target.getFrame() + ".";
                                        })
                                        .requires(() -> !target.isNetworkGame(), NETWORK_GAME_REFUSAL)
                                        .availability("single player games and the replay viewer; refused in a network game, " +
                                                      "whose players run in lockstep.")
                                        .example("/step"));
        registry.register(ConsoleCommand.of("/seek", "<time|frame|start>", "move the replay to a game time or frame")
                                        .otherwise("<target>", "seek to the target; a leading + or - is relative to the current frame", arguments -> {
                                            String value = arguments[0];
                                            long frame = parseSeekTarget(value, target.getFrame());

                                            if (frame == INVALID_TARGET) {
                                                return "Unknown seek target: " + value + ". Use /seek m:ss, frame, start, Nm, Ns, +T or -T.";
                                            }

                                            return "Seeking to frame " + target.seek(frame) + ".";
                                        })
                                        .section("Targets:")
                                        .item("start", "the first frame")
                                        .item("m:ss", "a game time at Fastest (42 ms a frame), for example 4:12")
                                        .item("Nm, Ns", "N minutes or N seconds, for example 1m or 30s")
                                        .item("N", "a frame number")
                                        .item("+T, -T", "T after or before the current frame, for example +1m or -30s")
                                        .requires(() -> !target.isNetworkGame(), NETWORK_GAME_REFUSAL)
                                        .requires(target::isReplay, "Seeking needs a replay in the replay viewer.")
                                        .availability("the replay viewer only; the target is clamped to the replay's length.")
                                        .example("/seek 4:12")
                                        .example("/seek -1m"));
    }

    /**
     * Creates a {@link ConsoleCommandRegistry} instance without commands that pages its replies for the game's message area
     * ({@link #REPLY_PAGE_LINES} lines a page).
     *
     * @return the created {@link ConsoleCommandRegistry} instance
     */
    public static @NotNull ConsoleCommandRegistry createRegistry() {
        return new ConsoleCommandRegistry(REPLY_PAGE_LINES);
    }

    /**
     * Runs one prompt line against the debug and frame commands alone.
     *
     * @param line   the typed line
     * @param target where the command acts
     * @return a message describing the effect or the error
     */
    public static @NotNull String run(@NotNull String line, @NotNull Target target) {
        ConsoleCommandRegistry registry = createRegistry();

        register(registry, target);

        return registry.run(line);
    }

    /**
     * Parses a player slot argument of the debug and console commands.
     *
     * @param value the argument text
     * @return the player slot from {@code 0} to {@code 7}, or {@code -1} when the text is not such a slot
     */
    static int parsePlayer(@NotNull String value) {
        try {
            int player = Integer.parseInt(value);

            return player >= 0 && player <= MAXIMUM_PLAYER ? player : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * Parses a seek target: {@code start}, a frame number, {@code m:ss} game time, {@code Nm} minutes or {@code Ns} seconds (times at Fastest,
     * {@link #FRAME_MILLISECONDS} per frame); a leading {@code +} or {@code -} makes it relative to the current frame, never before frame {@code 0}.
     *
     * @param value        the typed value
     * @param currentFrame the current frame of a relative target
     * @return the frame, or {@link #INVALID_TARGET} if the value is none of the forms
     */
    static long parseSeekTarget(@NotNull String value, long currentFrame) {
        String target = value.toLowerCase(Locale.ROOT);

        if (target.equals(SEEK_START)) {
            return 0;
        }

        if (target.isEmpty()) {
            return INVALID_TARGET;
        }

        char sign = target.charAt(0);

        if (sign != FORWARD_SIGN && sign != BACKWARD_SIGN) {
            return parseFrames(target);
        }

        long frames = parseFrames(target.substring(1));

        if (frames == INVALID_TARGET) {
            return INVALID_TARGET;
        }

        return Math.max(0, sign == BACKWARD_SIGN ? currentFrame - frames : currentFrame + frames);
    }

    private static long parseFrames(@NotNull String value) {
        if (value.endsWith(MINUTES_SUFFIX)) {
            long minutes = parseCount(value.substring(0, value.length() - MINUTES_SUFFIX.length()));

            return minutes == INVALID_TARGET ? INVALID_TARGET : convertToFrames(minutes * SECONDS_PER_MINUTE);
        }

        if (value.endsWith(SECONDS_SUFFIX)) {
            long seconds = parseCount(value.substring(0, value.length() - SECONDS_SUFFIX.length()));

            return seconds == INVALID_TARGET ? INVALID_TARGET : convertToFrames(seconds);
        }

        int separator = value.indexOf(TIME_SEPARATOR);

        if (separator < 0) {
            return parseCount(value);
        }

        long minutes = parseCount(value.substring(0, separator));
        long seconds = parseCount(value.substring(separator + 1));

        if (minutes == INVALID_TARGET || seconds == INVALID_TARGET) {
            return INVALID_TARGET;
        }

        return convertToFrames(minutes * SECONDS_PER_MINUTE + seconds);
    }

    /**
     * Parses a non-negative decimal count.
     *
     * @param value the typed count
     * @return the non-negative decimal count, or {@link #INVALID_TARGET}
     */
    private static long parseCount(@NotNull String value) {
        try {
            long count = Long.parseLong(value);

            return count < 0 ? INVALID_TARGET : count;
        } catch (NumberFormatException exception) {
            return INVALID_TARGET;
        }
    }

    private static long convertToFrames(long seconds) {
        return (seconds * MILLISECONDS_PER_SECOND + FRAME_MILLISECONDS - 1) / FRAME_MILLISECONDS;
    }

    private static @NotNull ConsoleCommand createDebugCommand(@NotNull Target target) {
        StringJoiner pages = new StringJoiner(LIST_SEPARATOR);

        for (DebugPanel.Page page : DebugPanel.Page.values()) {
            pages.add(page.name().toLowerCase(Locale.ROOT));
        }

        ConsoleCommand command = ConsoleCommand.of("/debug",
                                                   "[info|off|all|none|page|<layer> [on|off]|lighting [<view>]]",
                                                   "the debug view, its overlay layers and the HD lighting views");

        command.describe("The debug view is the development panel on the left of the game screen with the enabled " +
                         "overlay layers drawn over the map; path, collision and orders are on at start. A layer word " +
                         "alone toggles that layer, on or off sets it, and switching a layer also shows the view.")
               .whenEmpty("show or hide the debug view", arguments -> {
                   target.setDebugView(!target.isDebugView());

                   return "Debug view " + (target.isDebugView() ? "shown." : "hidden.");
               })
               .subcommand("info|on", "", "show the debug view", arguments -> {
                   target.setDebugView(true);

                   return "Debug view shown.";
               })
               .subcommand(ConsoleCommand.OFF, "", "hide the debug view; the layers keep their state", arguments -> {
                   target.setDebugView(false);

                   return "Debug view hidden.";
               })
               .subcommand("all", "", "turn every layer on and show the view", arguments -> setAllLayers(true, target))
               .subcommand("none", "", "turn every layer off and show the panel alone", arguments -> setAllLayers(false, target))
               .subcommand("page", "", "show the next panel page: " + pages, arguments -> {
                   DebugPanel.Page page = target.showNextDebugPage();

                   target.setDebugView(true);

                   return "Debug panel page: " + page.name().toLowerCase(Locale.ROOT) + ".";
               });

        for (DebugOverlay.Layer layer : DebugOverlay.Layer.values()) {
            boolean vision = layer == DebugOverlay.Layer.VISION;

            command.subcommand(layer.getName(),
                               vision ? "[on|off|<player>]" : "[on|off]",
                               layer.getDescription() + (vision ? "; a player 0-7 selects whose vision (default 0)" : ""),
                               arguments -> switchLayer(layer, arguments, target));
        }

        command.subcommand("lighting|lights",
                           "[<view>|next]",
                           "select an HD lighting view; no view or next selects the next one",
                           arguments -> selectLightingView(arguments, target))
               .section("Panel sections (/debug page shows one at a time):")
               .item("Performance", "frame rate, frame and update times, graphics, lighting view, pacing, HD texture memory, errors")
               .item("Camera / map", "map name, size and tileset, camera position and zoom, view size")
               .item("Selection", "selected count; the first unit's type, owner, position, hit points, shields, energy")
               .item("Orders / paths", "its orders, order target and path slot; the enabled layers")
               .item("Game state", "frame and game time, random state, seed, speed, unit count, playback status, the next replay command")
               .item("Progress strip", "in a replay, a strip across the top; clicking it seeks")
               .section("Lighting views (HD graphics with HD lighting on):");

        for (LightingView view : LightingView.values()) {
            command.item(view.getName(), view.getDescription());
        }

        return command.availability("everywhere, network games included: the view is local and changes no game state.")
                      .example("/debug regions")
                      .example("/debug lighting normals");
    }

    private static @NotNull String setAllLayers(boolean enabled, @NotNull Target target) {
        for (DebugOverlay.Layer layer : DebugOverlay.Layer.values()) {
            target.getOverlay().setEnabled(layer, enabled);
        }

        target.setDebugView(true);

        return enabled ? "All debug layers on." : "All debug layers off.";
    }

    private static @NotNull String switchLayer(DebugOverlay.@NotNull Layer layer, @NotNull String @NotNull [] arguments, @NotNull Target target) {
        DebugOverlay overlay = target.getOverlay();
        String reply = "";
        Boolean enabled = arguments.length == 0 ? Boolean.valueOf(!overlay.isEnabled(layer)) : ConsoleCommand.parseSwitch(arguments[0]);

        if (enabled != null) {
            overlay.setEnabled(layer, enabled);
        } else if (layer == DebugOverlay.Layer.VISION && parsePlayer(arguments[0]) >= 0) {
            target.setVisionPlayer(parsePlayer(arguments[0]));
            overlay.setEnabled(layer, true);
            reply = " for player " + parsePlayer(arguments[0]);
        } else {
            return "Use /debug " + layer.getName() + (layer == DebugOverlay.Layer.VISION ? " [on|off|0-7]." : " [on|off].");
        }

        target.setDebugView(true);

        return "Debug " + layer.getName() + (overlay.isEnabled(layer) ? " on" : " off") + reply + ".";
    }

    private static @NotNull String selectLightingView(@NotNull String @NotNull [] arguments, @NotNull Target target) {
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase(NEXT)) {
            return target.setLightingView(target.getLightingView().getNextView());
        }

        LightingView view = LightingView.find(arguments[0]);

        if (view == null) {
            StringJoiner names = new StringJoiner(LIST_SEPARATOR);

            for (LightingView known : LightingView.values()) {
                names.add(known.getName());
            }

            return "Unknown lighting view: " + arguments[0] + ". Use " + names + ".";
        }

        return target.setLightingView(view);
    }

    /**
     * Creates a {@link DebugCommands} instance.
     */
    private DebugCommands() {
    }
}
