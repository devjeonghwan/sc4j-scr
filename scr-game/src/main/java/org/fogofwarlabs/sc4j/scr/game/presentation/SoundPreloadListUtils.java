package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Builds race sound preload lists from the original sound groups in file order. Each list contains the generic group followed by the race group and
 * two terminating zero words.
 */
public final class SoundPreloadListUtils {
    /**
     * The number of zero words that terminate a list.
     */
    private static final int TERMINATING_WORD_COUNT = 2;

    /**
     * Builds the three lists.
     *
     * @param soundJson the contents of {@code rez/sfx.json}
     * @return the little-endian WORD bytes of the lists, indexed by race (Zerg {@code 0}, Terran {@code 1}, Protoss {@code 2})
     * @throws IOException if the parsed root, its {@code sfx} value or its {@code sfx.original} value is not a JSON object
     */
    public static byte @NotNull [] @NotNull [] build(byte @NotNull [] soundJson) throws IOException {
        if (!(JsonReader.parse(soundJson) instanceof Map<?, ?> root) || !(root.get("sfx") instanceof Map<?, ?> soundGroups) ||
            !(soundGroups.get("original") instanceof Map<?, ?> original)) {
            throw Exceptions.newIOException("rez/sfx.json has no sfx.original groups.");
        }

        int[] generic = collect(original, "generic");
        byte[][] lists = new byte[ScrRaceIds.PLAYABLE_COUNT][];

        lists[ScrRaceIds.ZERG]    = concatenate(generic, collect(original, "zerg"));
        lists[ScrRaceIds.TERRAN]  = concatenate(generic, collect(original, "terran"));
        lists[ScrRaceIds.PROTOSS] = concatenate(generic, collect(original, "protoss"));

        return lists;
    }

    /**
     * Collects the sound indices of one group of {@code rez/sfx.json}: the positions of its entries in the entry order of all groups.
     *
     * @param original the parsed {@code rez/sfx.json} groups
     * @param group    the group name
     * @return the sound indices of the group
     * @throws IOException if a group up to the requested one has no entry list, or the group is missing
     */
    private static int @NotNull [] collect(@NotNull Map<?, ?> original, @NotNull String group) throws IOException {
        int index = 0;

        for (Map.Entry<?, ?> category : original.entrySet()) {
            if (!(category.getValue() instanceof Map<?, ?> values) || !(values.get("entries") instanceof List<?> entries)) {
                throw Exceptions.newIOException("rez/sfx.json group {0} has no entries.", category.getKey());
            }

            if (group.equals(category.getKey())) {
                int[] indices = new int[entries.size()];

                for (int position = 0; position < indices.length; position++) {
                    indices[position] = index + position;
                }

                return indices;
            }

            index += entries.size();
        }

        throw Exceptions.newIOException("rez/sfx.json has no group {0}.", group);
    }

    private static byte @NotNull [] concatenate(int @NotNull [] generic, int @NotNull [] race) {
        // The two trailing zero WORDs stay zero.
        byte[] bytes = new byte[(generic.length + race.length + TERMINATING_WORD_COUNT) * Short.BYTES];
        int offset = 0;

        for (int[] part : new int[][]{generic, race}) {
            for (int sound : part) {
                bytes[offset]     = (byte) sound;
                bytes[offset + 1] = (byte) (sound >>> Byte.SIZE);
                offset += Short.BYTES;
            }
        }

        return bytes;
    }

    private SoundPreloadListUtils() {
    }
}
