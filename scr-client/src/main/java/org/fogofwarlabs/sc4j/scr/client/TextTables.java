package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Holds localized network, status, menu and dialog text tables for one client locale.
 */
public final class TextTables {
    /**
     * The table of the keyed network texts.
     */
    private static final @NotNull String NETWORK_TABLE = "network.json";
    /**
     * The table of the dialog control texts.
     */
    private static final @NotNull String CONTROL_TABLE = "RC3.json";

    private static final @NotNull String PLAYER_LEFT_KEY = "strPLAYER_LEFT";
    private static final @NotNull String PLAYER_DROPPED_KEY = "strPLAYER_WAS_DROPPED";
    private static final @NotNull String ELIMINATED_NAMED_KEY = "strELIMINATED_s";
    private static final @NotNull String ELIMINATED_NUMBERED_KEY = "strELIMINATED_d";

    /**
     * Creates a {@link TextTables} instance by loading its locale tables.
     *
     * @param storage the game storage
     * @param locale  the requested locale
     * @return the created {@link TextTables} instance
     * @throws IOException if a table is missing or malformed
     */
    public static @NotNull TextTables load(@NotNull CascStorage storage, @NotNull String locale) throws IOException {
        return new TextTables(locale,
                              GluText.load(storage, locale, NETWORK_TABLE),
                              StatText.load(storage, locale),
                              GluText.load(storage, locale),
                              GluText.load(storage, locale, CONTROL_TABLE));
    }

    /**
     * Treats an empty localized text as a missing one.
     *
     * @param text the looked-up text, empty when the table lacks it
     * @return the text, or {@code null} when it is empty
     */
    private static @Nullable String toNullableText(@NotNull String text) {
        return text.isEmpty() ? null : text;
    }

    /**
     * Encodes a localized text for the message lines, treating an empty text as a missing one.
     *
     * @param text the looked-up text, empty when the table lacks it
     * @return the UTF-8 bytes of the text, or {@code null} when it is empty
     */
    private static byte @Nullable [] encodeText(@NotNull String text) {
        return text.isEmpty() ? null : text.getBytes(StandardCharsets.UTF_8);
    }

    private final @NotNull String locale;
    private final @NotNull GluText network;
    private final @NotNull StatText statText;
    private final @NotNull GluText gluText;
    private final @NotNull GluText control;

    /**
     * Creates a {@link TextTables} instance.
     *
     * @param locale   the locale of the loaded tables
     * @param network  the keyed network texts
     * @param statText the numbered status texts
     * @param gluText  the menu texts
     * @param control  the dialog control texts
     */
    private TextTables(@NotNull String locale,
                       @NotNull GluText network, @NotNull StatText statText, @NotNull GluText gluText, @NotNull GluText control) {
        this.locale   = locale;
        this.network  = network;
        this.statText = statText;
        this.gluText  = gluText;
        this.control  = control;
    }

    /**
     * @return the locale of the tables
     */
    public @NotNull String getLocale() {
        return this.locale;
    }

    /**
     * @return the keyed texts of {@code network.json}
     */
    public @NotNull GluText getNetwork() {
        return this.network;
    }

    /**
     * @return the numbered {@code stat_txt} strings
     */
    public @NotNull StatText getStatText() {
        return this.statText;
    }

    /**
     * @return the keyed glue texts of {@code gluAll.json}
     */
    public @NotNull GluText getGluText() {
        return this.gluText;
    }

    /**
     * @return the dialog control texts of {@code RC3.json}
     */
    public @NotNull GluText getControl() {
        return this.control;
    }


    /**
     * Installs localized status, departure and elimination formats into the message lines.
     *
     * @param lines the game message lines
     */
    public void install(@NotNull MessageLines lines) {
        String left = this.network.get(PLAYER_LEFT_KEY);
        String dropped = this.network.get(PLAYER_DROPPED_KEY);

        lines.setDepartureFormats(toNullableText(left), toNullableText(dropped));

        String named = this.network.get(ELIMINATED_NAMED_KEY);
        String numbered = this.network.get(ELIMINATED_NUMBERED_KEY);

        lines.setEliminationFormats(toNullableText(named), toNullableText(numbered));

        lines.setStatusTexts(new MessageLines.StatusTexts() {
            /**
             * Resolves a localized keyed text.
             *
             * @param key the string key
             * @return the localized text bytes, or {@code null} when the key is unknown
             */
            @Override
            public byte @Nullable [] resolveKeyedText(@NotNull String key) {
                return encodeText(TextTables.this.network.get(key));
            }

            /**
             * Resolves a localized numbered text.
             *
             * @param textIdentifier the 1-based {@code stat_txt} id
             * @return the localized text bytes, or {@code null} when the id is unknown
             */
            @Override
            public byte @Nullable [] resolveNumberedText(int textIdentifier) {
                return encodeText(TextTables.this.statText.getString(textIdentifier));
            }
        });
    }
}
