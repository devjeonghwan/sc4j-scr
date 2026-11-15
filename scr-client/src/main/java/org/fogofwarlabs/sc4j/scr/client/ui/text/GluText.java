package org.fogofwarlabs.sc4j.scr.client.ui.text;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds localized keyed interface strings and their file order.
 * The installed tables include {@code gluAll.json}, {@code network.json} and {@code RC3.json}.
 */
public final class GluText {
    /**
     * The {@code gluAll} keys of the game speed names, indexed by game speed {@code 0..6}.
     */
    public static final @NotNull List<String> GAME_SPEED_KEYS = List.of("gameSLOWEST", "gameSLOWER", "gameSLOW", "gameNORMAL",
                                                                        "gameFAST", "gameFASTER", "gameFASTEST");

    private static final @NotNull String REZ_FOLDER = "rez/";

    /**
     * Loads the strings of a locale.
     *
     * @param storage the installed game storage
     * @param locale  the locale such as {@code enUS}
     * @return the loaded strings
     * @throws IOException if the file is missing or malformed
     */
    public static @NotNull GluText load(@NotNull CascStorage storage, @NotNull String locale) throws IOException {
        return load(storage, locale, "gluAll.json");
    }

    /**
     * Loads a keyed string file of a locale, for example {@code RC3.json} (the dialog control texts such as {@code stat_f10-1}).
     *
     * @param storage the installed game storage
     * @param locale  the locale such as {@code enUS}
     * @param file    the file name under {@code rez}
     * @return the loaded strings, shared by every caller with the same storage
     * @throws IOException if the file is missing or malformed
     */
    public static @NotNull GluText load(@NotNull CascStorage storage, @NotNull String locale, @NotNull String file) throws IOException {
        // Loaded once per storage: the glue screens, the console and the message lines share one instance.
        // The localized resolver (with locales/%s/assets/%s): the locale, then enUS, then the unlocalized file.
        String path = new AssetResolver(storage, AssetResolver.AssetSet.HD, "", locale).resolveLocalized(REZ_FOLDER + file);

        if (path == null) {
            throw Exceptions.newIOException("Missing localized {0}{1} for {2}", REZ_FOLDER, file, locale);
        }

        return storage.readParsed(path, GluText.class, contents -> {
            if (!(JsonReader.parse(contents) instanceof List<?> entries)) {
                throw Exceptions.newIOException("{0} must be a list of entries.", file);
            }

            Map<String, String> values = new HashMap<>();
            List<String> ordered = new ArrayList<>();

            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map && map.get("Key") instanceof String key && map.get("Value") instanceof String value) {
                    String decoded = StatText.decode(value);

                    values.put(key, decoded);
                    ordered.add(decoded);
                }
            }

            return new GluText(values, ordered);
        });
    }

    private final @NotNull Map<String, String> values;
    private final @NotNull List<String> ordered;

    private GluText(@NotNull Map<String, String> values, @NotNull List<String> ordered) {
        this.values  = values;
        this.ordered = ordered;
    }

    /**
     * Reads the requested entry.
     *
     * @param key the string key
     * @return the string, or an empty string for an unknown key
     */
    public @NotNull String get(@NotNull String key) {
        return this.values.getOrDefault(key, "");
    }

    /**
     * Reads a string by its one-based file position.
     *
     * @param number the one-based position
     * @return the string, or an empty string outside the table
     */
    public @NotNull String getByNumber(int number) {
        return number < 1 || number > this.ordered.size() ? "" : this.ordered.get(number - 1);
    }

    /**
     * Tests whether a text key is present.
     *
     * @param key the string key
     * @return {@code true} if the table has the key, {@code false} otherwise
     */
    public boolean contains(@NotNull String key) {
        return this.values.containsKey(key);
    }
}
