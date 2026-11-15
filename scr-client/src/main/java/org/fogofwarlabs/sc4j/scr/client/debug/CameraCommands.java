package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommand;
import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Registers camera zoom and drag-panning commands. Wheel zoom starts enabled in the replay viewer and disabled in live games; drag panning starts
 * disabled. Neither choice is saved across launches. Zoom changes animate about the pointer or the view center.
 */
public final class CameraCommands {
    /**
     * The zoom of the native scale, {@code 480} game rows in the window.
     */
    static final int NATIVE_ZOOM_PERCENT = 100;
    private static final @NotNull String PERCENT_SIGN = "%";
    private static final @NotNull String NO_GAME_VIEW = "No game view to zoom.";

    /**
     * Receives the effect of a camera command.
     */
    public interface Target {
        /**
         * @return {@code true} if the mouse wheel zooms the game view, {@code false} otherwise
         */
        boolean isWheelZoom();

        /**
         * Sets whether mouse wheel input zooms the game view.
         *
         * @param enabled whether the mouse wheel zooms the game view
         */
        void setWheelZoom(boolean enabled);

        /**
         * @return {@code true} if a middle or right drag pans the camera, {@code false} otherwise
         */
        boolean isDragPan();

        /**
         * Sets whether middle or right dragging pans the camera.
         *
         * @param enabled whether a middle or right drag pans the camera
         */
        void setDragPan(boolean enabled);

        /**
         * @return {@code true} if a game view is shown for zooming, {@code false} otherwise
         */
        boolean hasGameView();

        /**
         * @return the game view's zoom in percent of the native scale
         */
        int getZoomPercent();

        /**
         * Sets the game view's zoom about its center; the camera keeps the zoom within its limits.
         *
         * @param percent the zoom in percent of the native scale
         */
        void setZoomPercent(int percent);
    }

    /**
     * Registers {@code /zoom} and {@code /pan}.
     *
     * @param registry the command registry
     * @param target   where the commands act
     */
    public static void register(@NotNull ConsoleCommandRegistry registry, @NotNull Target target) {
        registry.register(ConsoleCommand.of("/zoom", "[on|off|reset|<percent>]", "zoom the game view; on enables the mouse-wheel zoom")
                                        .describe("SC:R's game view has no zoom and its mouse wheel does nothing there; this " +
                                                  "client feature is on in the replay viewer and off in live games until " +
                                                  "/zoom on or off chooses, and is not saved. Zoom changes animate smoothly. " +
                                                  "The message history keeps its native wheel scrolling either way.")
                                        .whenEmpty("show whether the wheel zoom is on and the current zoom", arguments -> {
                                            String wheel = "Wheel zoom " + (target.isWheelZoom() ? "on" : "off");

                                            return target.hasGameView() ? wheel + ", zoom " + target.getZoomPercent() + "%." : wheel + ".";
                                        })
                                        .subcommand(ConsoleCommand.ON, "", "the mouse wheel over the game view zooms about the pointer", arguments -> {
                                            target.setWheelZoom(true);

                                            return "Wheel zoom on.";
                                        })
                                        .subcommand(ConsoleCommand.OFF, "", "turn the wheel zoom off and return to 100%", arguments -> {
                                            target.setWheelZoom(false);

                                            return "Wheel zoom off." + resetZoom(target);
                                        })
                                        .subcommand("reset",
                                                    "",
                                                    "return to the native scale, 100%",
                                                    arguments -> target.hasGameView() ? resetZoom(target).trim() : NO_GAME_VIEW)
                                        .otherwise("<percent>",
                                                   "zoom to that percent of the native scale, for example 150; the camera limits apply",
                                                   arguments -> {
                                                       String value = arguments[0];
                                                       String digits = value.endsWith(PERCENT_SIGN)
                                                                       ? value.substring(0, value.length() - PERCENT_SIGN.length())
                                                                       : value;
                                                       int percent;

                                                       try {
                                                           percent = Integer.parseInt(digits);
                                                       } catch (NumberFormatException exception) {
                                                           percent = 0;
                                                       }

                                                       if (percent <= 0) {
                                                           return "Use /zoom on, off, reset or a positive percent such as 150.";
                                                       }

                                                       if (!target.hasGameView()) {
                                                           return NO_GAME_VIEW;
                                                       }

                                                       target.setZoomPercent(percent);

                                                       return "Zoom " + target.getZoomPercent() + "%.";
                                                   })
                                        .availability("live games (network games too: the camera is local) and the replay viewer; " +
                                                      "a percent or reset needs a game view.")
                                        .example("/zoom on")
                                        .example("/zoom 150"));
        registry.register(ConsoleCommand.of("/pan", "[on|off]", "drag the camera with the middle or right mouse button")
                                        .describe("A client feature, off at start and not saved. With the game console shown, " +
                                                  "only the middle button drags: the right button keeps its native orders.")
                                        .whenEmpty("show whether drag panning is on",
                                                   arguments -> "Drag panning " + (target.isDragPan() ? "on." : "off."))
                                        .subcommand(ConsoleCommand.ON, "", "a middle or right drag over the game view pans the camera", arguments -> {
                                            target.setDragPan(true);

                                            return "Drag panning on.";
                                        })
                                        .subcommand(ConsoleCommand.OFF, "", "the middle and right buttons do not pan", arguments -> {
                                            target.setDragPan(false);

                                            return "Drag panning off.";
                                        })
                                        .availability("live games (network games too: the camera is local) and the replay viewer.")
                                        .example("/pan on"));
    }

    /**
     * Resets the game view to the native scale when a game view is shown.
     *
     * @param target where the command acts
     * @return {@code " Zoom 100%."} after resetting the zoom, or an empty string without a game view
     */
    private static @NotNull String resetZoom(@NotNull Target target) {
        if (!target.hasGameView()) {
            return "";
        }

        target.setZoomPercent(NATIVE_ZOOM_PERCENT);

        return " Zoom " + target.getZoomPercent() + "%.";
    }

    /**
     * Creates a {@link CameraCommands} instance.
     */
    private CameraCommands() {
    }
}
