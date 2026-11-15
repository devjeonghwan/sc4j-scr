package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves localized campaign entry names by their one-based episode identifiers.
 */
public final class CampaignMissionNames {
    private static final @NotNull String NAMES_FILE = "gluHist.json";
    private static final @NotNull String LOCALES_FOLDER = "locales/";
    private static final @NotNull String LOCALE_REZ_FOLDER = "/Assets/rez/";
    private static final @NotNull String VALUE_KEY = "Value";
    private static final int FIRST_IDENTIFIER = 1;

    /**
     * Loads the names of a locale.
     *
     * @param storage the installed game storage
     * @param locale  the locale such as {@code enUS}
     * @return the loaded names, shared by every caller with the same storage
     * @throws IOException if the file is missing or malformed
     */
    public static @NotNull CampaignMissionNames load(@NotNull CascStorage storage, @NotNull String locale) throws IOException {
        return storage.readParsed(LOCALES_FOLDER + locale + LOCALE_REZ_FOLDER + NAMES_FILE, CampaignMissionNames.class, CampaignMissionNames::parse);
    }

    private static @NotNull CampaignMissionNames parse(byte @NotNull [] contents) throws IOException {
        if (!(JsonReader.parse(contents) instanceof List<?> entries)) {
            throw Exceptions.newIOException("{0} must be a list of entries.", NAMES_FILE);
        }

        List<String> names = new ArrayList<>();

        for (Object entry : entries) {
            names.add(entry instanceof Map<?, ?> map && map.get(VALUE_KEY) instanceof String value ? StatText.decode(value) : "");
        }

        return new CampaignMissionNames(List.copyOf(names));
    }

    private final @NotNull List<String> names;

    /**
     * Creates a {@link CampaignMissionNames} instance from an immutable decoded name list.
     *
     * @param names the decoded names in identifier order
     */
    private CampaignMissionNames(@NotNull List<String> names) {
        this.names = names;
    }

    /**
     * @param identifier the one-based episode entry identifier ({@link CampaignEpisodes.Entry#getIdentifier})
     * @return the entry's name, or an empty string for an identifier without one
     */
    public @NotNull String getMissionName(int identifier) {
        int index = identifier - FIRST_IDENTIFIER;

        return index >= 0 && index < this.names.size() ? this.names.get(index) : "";
    }
}
