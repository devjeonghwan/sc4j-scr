package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses campaign establishing-shot scripts into pages with inherited display, fade, background and text-color settings.
 */
public final class EstablishingShot {
    /**
     * No location tag on the page: no text.
     */
    public static final int NO_LOCATION = 0;

    /**
     * The establishing-shot script names by campaign mission index; entry {@code 0} is unused.
     */
    private static final @NotNull String @NotNull [] NAMES = {
            "", "EstT0t", "EstT01", "EstT02", "EstT03", "EstT04", "EstT05", "EstT06", "EstT08", "EstT09", "EstT11", "EstT12",
            "EstZ01", "EstZ02", "EstZ03", "EstZ04", "EstZ05", "EstZ06", "EstZ07", "EstZ08", "EstZ09", "EstZ10",
            "EstP01", "EstP02", "EstP03", "EstP04", "EstP05", "EstP06", "EstP07", "EstP08", "EstP09", "EstP10",
            "EstP01x", "EstP02x", "EstP03x", "EstP04x", "EstP05x", "EstP06x", "EstP07x", "EstP08x",
            "EstT01x", "EstT02x", "EstT03x", "EstT04x", "EstT05ax", "EstT05bx", "EstT06x", "EstT07x", "EstT08x",
            "EstZ01x", "EstZ02x", "EstZ03x", "EstZ04x", "EstZ04x", "EstZ04x", "EstZ04x", "EstZ04x", "EstZ04x", "EstZ05x", "EstZ06x", "EstZ07x",
            "EstZ08x", "EstZ09x", "EstZ09bx", "EstZ10x"
    };

    /**
     * The mission whose victory plays a finale script.
     */
    private static final int FINALE_MISSION = 63;
    private static final @NotNull String FINALE_NAME = "FinZ09bx";

    private static final int DEFAULT_DISPLAY_TIME = 5000;
    private static final int DEFAULT_FADE_SPEED = 1;
    private static final @NotNull String DEFAULT_BACKGROUND = "glue\\palpa\\Blank.pcx";
    private static final @NotNull String DEFAULT_FONT_COLOR = "glue\\palpa\\tfont.pcx";

    private static final @NotNull String TAG_START = "</";
    private static final char TAG_END = '>';
    private static final char TAG_VALUE_SEPARATOR = ' ';
    private static final @NotNull String @NotNull [] LOCATIONS = {"SCREENLEFT", "SCREENRIGHT", "SCREENTOP", "SCREENBOTTOM", "SCREENCENTER",
                                                                  "SCREENLOWERLEFT", "TEXTBOX"};
    private static final int @NotNull [] LOCATION_CONTROLS = {1, 2, 3, 4, 5, 6, 8};

    /**
     * The page tags of the script.
     */
    private static final @NotNull String DISPLAY_TIME_TAG = "DISPLAYTIME";
    private static final @NotNull String FADE_SPEED_TAG = "FADESPEED";
    private static final @NotNull String BACKGROUND_TAG = "BACKGROUND";
    private static final @NotNull String FONT_COLOR_TAG = "FONTCOLOR";
    private static final @NotNull String VIDEO_TAG = "VIDEO";
    private static final @NotNull String VIDEO_AUTO_PROCEED_TAG = "VIDEOAUTOPROCEED";
    private static final @NotNull String SOUND_TAG = "SOUND";
    private static final @NotNull String PAGE_TAG = "PAGE";

    private static final @NotNull String BYTE_ORDER_MARK = "﻿";
    private static final @NotNull String CARRIAGE_RETURN = "\r";
    private static final @NotNull String LINE_SEPARATOR = "\n";
    private static final char LINE_FEED = '\n';

    /**
     * One page of the script.
     */
    public static final class Page {
        private int displayTime;
        private int fadeSpeed;
        private @NotNull String background;
        private @NotNull String fontColor;

        private @Nullable String video;
        private boolean autoProceed;
        private @Nullable String sound;
        private int location;
        private final @NotNull StringBuilder text;

        /**
         * Creates a {@link Page} instance inheriting display and palette settings from the preceding page.
         *
         * @param previous the preceding page, or {@code null} for script defaults
         */
        private Page(@Nullable Page previous) {
            this.displayTime = previous == null ? DEFAULT_DISPLAY_TIME : previous.displayTime;
            this.fadeSpeed   = previous == null ? DEFAULT_FADE_SPEED : previous.fadeSpeed;
            this.background  = previous == null ? DEFAULT_BACKGROUND : previous.background;
            this.fontColor   = previous == null ? DEFAULT_FONT_COLOR : previous.fontColor;

            this.video       = null;
            this.autoProceed = false;
            this.sound       = null;
            this.location    = NO_LOCATION;
            this.text        = new StringBuilder();
        }

        /**
         * @return the display time in milliseconds; {@code 0} waits for a click
         */
        public int getDisplayTime() {
            return this.displayTime;
        }

        /**
         * @return the fade speed byte
         */
        public int getFadeSpeed() {
            return this.fadeSpeed;
        }

        /**
         * @return the background picture path, for example {@code glue\palpa\ProtossA.pcx}
         */
        public @NotNull String getBackground() {
            return this.background;
        }

        /**
         * @return the text color file path, for example {@code glue\palpa\tfont.pcx}
         */
        public @NotNull String getFontColor() {
            return this.fontColor;
        }

        /**
         * @return the clip of control {@code 7}, or {@code null}
         */
        public @Nullable String getVideo() {
            return this.video;
        }

        /**
         * @return {@code true} if the next page follows when the clip ends, {@code false} otherwise
         */
        public boolean isAutoProceed() {
            return this.autoProceed;
        }

        /**
         * @return the sound played with the page, or {@code null}
         */
        public @Nullable String getSound() {
            return this.sound;
        }

        /**
         * @return the text control id ({@code 1..6}, {@code 8}), or {@link #NO_LOCATION}
         */
        public int getLocation() {
            return this.location;
        }

        /**
         * @return the page text, lines separated by line feeds, without the trailing line breaks
         */
        public @NotNull String getText() {
            return this.text.toString().stripTrailing();
        }
    }

    /**
     * Returns the establishing-shot script played before a mission's ready room.
     *
     * @param mission the campaign mission index
     * @return the script name of the mission, or {@code null} for none
     */
    public static @Nullable String getName(int mission) {
        return mission > 0 && mission < NAMES.length ? NAMES[mission] : null;
    }

    /**
     * Returns the finale script played after a mission is won.
     *
     * @param mission the campaign mission index
     * @return the script name, or {@code null} when the mission has none
     */
    public static @Nullable String getFinaleName(int mission) {
        return mission == FINALE_MISSION ? FINALE_NAME : null;
    }

    /**
     * Creates a {@link EstablishingShot} instance by parsing a script.
     *
     * @param script the file text
     * @return the created {@link EstablishingShot} instance with the parsed pages
     */
    public static @NotNull EstablishingShot parse(@NotNull String script) {
        List<Page> pages = new ArrayList<>();
        Page page = new Page(null);
        boolean inText = false;

        for (String rawLine : script.replace(BYTE_ORDER_MARK, "").replace(CARRIAGE_RETURN, "").split(LINE_SEPARATOR, -1)) {
            String line = rawLine.strip();

            if (!line.startsWith(TAG_START)) {
                if (inText) {
                    page.text.append(rawLine).append(LINE_FEED);
                }

                continue;
            }

            int endIndex = line.indexOf(TAG_END);
            String tag = line.substring(TAG_START.length(), endIndex < 0 ? line.length() : endIndex);
            int spaceIndex = tag.indexOf(TAG_VALUE_SEPARATOR);
            String name = (spaceIndex < 0 ? tag : tag.substring(0, spaceIndex)).toUpperCase(Locale.ROOT);
            String value = spaceIndex < 0 ? "" : tag.substring(spaceIndex + 1).strip();

            switch (name) {
                case DISPLAY_TIME_TAG -> page.displayTime = parseNumber(value, page.displayTime);
                case FADE_SPEED_TAG -> page.fadeSpeed = parseNumber(value, page.fadeSpeed);
                case BACKGROUND_TAG -> page.background = value;
                case FONT_COLOR_TAG -> page.fontColor = value;
                case VIDEO_TAG -> page.video = value;
                case VIDEO_AUTO_PROCEED_TAG -> {
                    page.video       = value;
                    page.autoProceed = true;
                }
                case SOUND_TAG -> page.sound = value;
                case PAGE_TAG -> {
                    pages.add(page);
                    page   = new Page(page);
                    inText = false;
                }
                default -> {
                    for (int index = 0; index < LOCATIONS.length; index++) {
                        if (LOCATIONS[index].equals(name)) {
                            page.location = LOCATION_CONTROLS[index];
                            inText        = true;
                        }
                    }
                }
            }
        }

        return new EstablishingShot(pages);
    }

    private static int parseNumber(@NotNull String value, int fallback) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private final @NotNull List<Page> pages;

    /**
     * Creates a {@link EstablishingShot} instance retaining the parsed page sequence.
     *
     * @param pages the completed pages
     */
    private EstablishingShot(@NotNull List<Page> pages) {
        this.pages = List.copyOf(pages);
    }

    /**
     * @return the pages in order
     */
    public @NotNull List<Page> getPages() {
        return this.pages;
    }
}
