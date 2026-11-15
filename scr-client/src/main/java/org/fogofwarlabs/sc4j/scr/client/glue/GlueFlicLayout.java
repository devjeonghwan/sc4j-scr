package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads high-definition glue video placements from {@code rez/flc.json}.
 * Matching paths, including backslashes, stretch the video into the configured rectangle relative to its button.
 * Unlisted videos use their element rectangle and the clip's own size.
 */
public final class GlueFlicLayout {
    private static final @NotNull String LAYOUT_FILE = "rez/flc.json";
    private static final @NotNull String LAYOUT_KEY = "flc";
    private static final int RECTANGLE_SIZE = 4;
    private static final int RECTANGLE_X_INDEX = 0;
    private static final int RECTANGLE_Y_INDEX = 1;
    private static final int RECTANGLE_WIDTH_INDEX = 2;
    private static final int RECTANGLE_HEIGHT_INDEX = 3;

    /**
     * One entry of {@code rez/flc.json}: a rectangle relative to the button, in {@code 640x480} interface pixels.
     */
    public static final class Placement {
        private final int x;
        private final int y;

        private final int width;
        private final int height;

        private Placement(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;

            this.width  = width;
            this.height = height;
        }

        /**
         * @return the left offset from the button
         */
        public int getX() {
            return this.x;
        }

        /**
         * @return the top offset from the button
         */
        public int getY() {
            return this.y;
        }

        /**
         * @return the drawn width
         */
        public int getWidth() {
            return this.width;
        }

        /**
         * @return the drawn height
         */
        public int getHeight() {
            return this.height;
        }
    }

    /**
     * Creates a {@link GlueFlicLayout} instance without video placements: every video is drawn at its element rectangle.
     *
     * @return the created {@link GlueFlicLayout} instance
     */
    public static @NotNull GlueFlicLayout createEmpty() {
        return new GlueFlicLayout(Map.of());
    }

    /**
     * Creates a {@link GlueFlicLayout} instance from {@code rez/flc.json}.
     *
     * @param storage the installed game storage
     * @return the created {@link GlueFlicLayout} instance
     * @throws IOException if the file is missing or malformed
     */
    public static @NotNull GlueFlicLayout load(@NotNull CascStorage storage) throws IOException {
        if (!(JsonReader.parse(storage.readFile(LAYOUT_FILE)) instanceof Map<?, ?> root) || !(root.get(LAYOUT_KEY) instanceof Map<?, ?> entries)) {
            throw Exceptions.newIOException("{0}: no \"{1}\" object.", LAYOUT_FILE, LAYOUT_KEY);
        }

        Map<String, Placement> placements = new HashMap<>();

        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof String path) || !(entry.getValue() instanceof List<?> numbers) || numbers.size() != RECTANGLE_SIZE) {
                throw Exceptions.newIOException("{0}: malformed entry {1}.", LAYOUT_FILE, entry.getKey());
            }

            int[] values = new int[RECTANGLE_SIZE];

            for (int index = 0; index < RECTANGLE_SIZE; index++) {
                if (!(numbers.get(index) instanceof Number number)) {
                    throw Exceptions.newIOException("{0}: malformed entry {1}.", LAYOUT_FILE, path);
                }

                values[index] = number.intValue();
            }

            placements.put(path, new Placement(values[RECTANGLE_X_INDEX], values[RECTANGLE_Y_INDEX],
                                               values[RECTANGLE_WIDTH_INDEX], values[RECTANGLE_HEIGHT_INDEX]));
        }

        return new GlueFlicLayout(placements);
    }

    private final @NotNull Map<String, Placement> placements;

    private GlueFlicLayout(@NotNull Map<String, Placement> placements) {
        this.placements = Map.copyOf(placements);
    }

    /**
     * Finds the placement of a video path.
     *
     * @param path the video path as the dialog names it, for example {@code glue\mainmenu\SingleOn.webm}
     * @return the placement of that video, or {@code null} if it has none
     */
    public @Nullable Placement findPlacement(@NotNull String path) {
        return this.placements.get(path);
    }
}
