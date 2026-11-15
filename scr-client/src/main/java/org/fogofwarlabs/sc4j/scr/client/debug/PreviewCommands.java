package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommand;
import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the client's own {@code /preview} command: the developer inspection of the selected map placement's image, its shown GRP frame and
 * mirroring, and the manual image script preview with its requested animation.
 */
public final class PreviewCommands {
    /**
     * The highest animation id the preview can request.
     */
    static final int MAXIMUM_ANIMATION = 255;
    private static final @NotNull String NEXT = "next";
    private static final @NotNull String PREVIOUS = "prev";
    private static final @NotNull String NO_PLACEMENT = "Select a map placement first (left click).";
    private static final @NotNull String NO_PREVIEW = "No preview open; /preview start opens one.";
    private static final @NotNull String ANIMATION_USAGE = "Use /preview anim next, prev or 0-" + MAXIMUM_ANIMATION + ".";

    /**
     * Receives the effect of a preview command.
     */
    public interface Target {
        /**
         * @return {@code true} if a live game runs and refuses inspection, {@code false} otherwise
         */
        boolean isLiveGame();

        /**
         * @return {@code true} if a map placement is selected, {@code false} otherwise
         */
        boolean hasPlacement();

        /**
         * @return {@code true} if a preview is open, {@code false} otherwise
         */
        boolean hasPreview();

        /**
         * @return the GRP frame count, or {@code 0} while unknown
         */
        int getFrameCount();

        /**
         * @return the animation the preview requests
         */
        int getRequestedAnimation();

        /**
         * Starts the image script preview of the selected placement and pauses the session; nothing starts without a selected placement.
         *
         * @return {@code true} if a preview started, {@code false} otherwise
         */
        boolean startPreview();

        /**
         * Closes the preview; the GRP frame shows again.
         */
        void closePreview();

        /**
         * Steps the shown GRP frame of the selected placement and closes the preview.
         *
         * @param delta the frames to step, negative toward the first
         * @return the shown frame
         */
        int changeFrame(int delta);

        /**
         * Mirrors the shown GRP frame and closes the preview.
         *
         * @return {@code true} if the frame is now mirrored, {@code false} otherwise
         */
        boolean toggleMirror();

        /**
         * Changes the requested animation within {@code 0..255}.
         *
         * @param delta the change
         */
        void chooseAnimation(int delta);

        /**
         * Requests the chosen animation in the open preview.
         */
        void playAnimation();

        /**
         * Runs the open preview's image script once.
         */
        void stepPreview();
    }

    /**
     * Registers {@code /preview}.
     *
     * @param registry the command registry
     * @param target   where the command acts
     */
    public static void register(@NotNull ConsoleCommandRegistry registry, @NotNull Target target) {
        registry.register(ConsoleCommand.of("/preview",
                                            "start|close|frame <+N|-N>|mirror|anim <next|prev|N>|play|step",
                                            "inspect the selected placement's image and image script")
                                        .describe("A developer inspection of the map placement selected with a left click " +
                                                  "(map inspection, --focus-unit). It draws a GRP frame or runs the image " +
                                                  "script in isolation and changes no game state.")
                                        .subcommand("start",
                                                    "",
                                                    "start or restart the image script preview of the selected placement and pause",
                                                    arguments -> {
                                                        if (!target.startPreview()) {
                                                            return NO_PLACEMENT;
                                                        }

                                                        return "Preview started, animation " + target.getRequestedAnimation() + ".";
                                                    })
                                        .subcommand("close", "", "close the preview; the GRP frame shows again", arguments -> {
                                            if (!target.hasPreview()) {
                                                return NO_PREVIEW;
                                            }

                                            target.closePreview();

                                            return "Preview closed.";
                                        })
                                        .subcommand("frame", "<+N|-N>", "step the shown GRP frame by N (closes the preview)", arguments -> {
                                            int delta;

                                            try {
                                                delta = arguments.length == 0 ? 0 : Integer.parseInt(arguments[0]);
                                            } catch (NumberFormatException exception) {
                                                delta = 0;
                                            }

                                            if (delta == 0) {
                                                return "Use /preview frame +N or -N, for example +1.";
                                            }

                                            if (!target.hasPlacement()) {
                                                return NO_PLACEMENT;
                                            }

                                            if (target.getFrameCount() == 0) {
                                                return "The frame count of the placement's image is not known yet.";
                                            }

                                            return "GRP frame " + target.changeFrame(delta) + " of " + target.getFrameCount() + ".";
                                        })
                                        .subcommand("mirror", "", "mirror the shown GRP frame (closes the preview)", arguments -> {
                                            if (!target.hasPlacement()) {
                                                return NO_PLACEMENT;
                                            }

                                            return target.toggleMirror() ? "Frame mirrored." : "Frame not mirrored.";
                                        })
                                        .subcommand("anim",
                                                    "<next|prev|N>",
                                                    "choose the animation the preview requests, 0-" + MAXIMUM_ANIMATION,
                                                    arguments -> {
                                                        int delta;

                                                        if (arguments.length == 0) {
                                                            return ANIMATION_USAGE;
                                                        } else if (arguments[0].equalsIgnoreCase(NEXT)) {
                                                            delta = 1;
                                                        } else if (arguments[0].equalsIgnoreCase(PREVIOUS)) {
                                                            delta = -1;
                                                        } else {
                                                            int animation;

                                                            try {
                                                                animation = Integer.parseInt(arguments[0]);
                                                            } catch (NumberFormatException exception) {
                                                                animation = -1;
                                                            }

                                                            if (animation < 0 || animation > MAXIMUM_ANIMATION) {
                                                                return ANIMATION_USAGE;
                                                            }

                                                            delta = animation - target.getRequestedAnimation();
                                                        }

                                                        target.chooseAnimation(delta);

                                                        return "Animation " + target.getRequestedAnimation() + " chosen; /preview play requests it.";
                                                    })
                                        .subcommand("play", "", "request the chosen animation in the open preview", arguments -> {
                                            if (!target.hasPreview()) {
                                                return NO_PREVIEW;
                                            }

                                            target.playAnimation();

                                            return "Animation " + target.getRequestedAnimation() + " requested.";
                                        })
                                        .subcommand("step", "", "run the open preview's image script once", arguments -> {
                                            if (!target.hasPreview()) {
                                                return NO_PREVIEW;
                                            }

                                            target.stepPreview();

                                            return "Preview stepped.";
                                        })
                                        .requires(() -> !target.isLiveGame(), DebugCommands.LIVE_GAME_REFUSAL)
                                        .availability("the replay viewer and map inspection; refused in a live game.")
                                        .example("/preview frame +1")
                                        .example("/preview anim 2"));
    }

    /**
     * Creates a {@link PreviewCommands} instance.
     */
    private PreviewCommands() {
    }
}
