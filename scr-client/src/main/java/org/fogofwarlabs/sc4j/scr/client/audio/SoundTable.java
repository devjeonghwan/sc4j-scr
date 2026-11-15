package org.fogofwarlabs.sc4j.scr.client.audio;

import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads sound identifiers and length adjustments from the data pack sound table. Identifiers follow entry order; malformed entries are skipped
 * without advancing the identifier.
 */
public final class SoundTable {
    /**
     * The capacity of the sound length-adjustment table.
     */
    private static final int SOUND_COUNT = 1144;

    /**
     * The data pack path of the sound table.
     */
    private static final @NotNull String SOUND_TABLE_PATH = "rez/sfx.json";

    /**
     * The keys of the sound table: the sound effect section, its original sound set, the entries of a category and the length adjustment and
     * identifier of an entry.
     */
    private static final @NotNull String SOUND_EFFECTS_KEY = "sfx";
    private static final @NotNull String ORIGINAL_KEY = "original";
    private static final @NotNull String ENTRIES_KEY = "entries";
    private static final @NotNull String LENGTH_ADJUSTMENT_KEY = "lengthAdjust";
    private static final @NotNull String IDENTIFIER_KEY = "ID";

    /**
     * Creates a {@link SoundTable} instance from {@code rez/sfx.json}.
     *
     * @param storage the installed game storage
     * @return the created {@link SoundTable} instance
     * @throws IOException if {@code rez/sfx.json} cannot be read
     */
    public static @NotNull SoundTable load(@NotNull CascStorage storage) throws IOException {
        SoundTable table = new SoundTable();
        int index = 0;

        if (JsonReader.parse(storage.readFile(SOUND_TABLE_PATH)) instanceof Map<?, ?> root &&
            root.get(SOUND_EFFECTS_KEY) instanceof Map<?, ?> soundEffects &&
            soundEffects.get(ORIGINAL_KEY) instanceof Map<?, ?> original) {
            for (Object category : original.values()) {
                if (!(category instanceof Map<?, ?> group) || !(group.get(ENTRIES_KEY) instanceof List<?> entries)) {
                    continue;
                }

                for (Object entry : entries) {
                    if (!(entry instanceof Map<?, ?> sound) || !(sound.get(LENGTH_ADJUSTMENT_KEY) instanceof Long lengthAdjustment) ||
                        !(sound.get(IDENTIFIER_KEY) instanceof String soundName)) {
                        continue;
                    }

                    table.soundIdentifiers.putIfAbsent(soundName, index);

                    if (index < table.lengthAdjustments.length) {
                        table.lengthAdjustments[index] = (short) (long) lengthAdjustment;
                    }

                    index++;
                }
            }
        }

        return table;
    }


    private final @NotNull Map<String, Integer> soundIdentifiers;
    private final short @NotNull [] lengthAdjustments;

    /**
     * Creates a {@link SoundTable} instance.
     */
    private SoundTable() {
        this.soundIdentifiers  = new HashMap<>();
        this.lengthAdjustments = new short[SOUND_COUNT];
    }

    /**
     * @param name a sound ID of {@code sfx.json}
     * @return the sound index, or {@code 0} when unknown
     */
    public int getSoundIdentifier(@NotNull String name) {
        return this.soundIdentifiers.getOrDefault(name, 0);
    }

    /**
     * @param soundIdentifier a sound index
     * @return its length adjustment in milliseconds, or {@code 0} outside the table
     */
    public int getLengthAdjustment(int soundIdentifier) {
        return soundIdentifier >= 0 && soundIdentifier < this.lengthAdjustments.length ? this.lengthAdjustments[soundIdentifier] : 0;
    }
}
