package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.scr.client.glue.CharacterFile;
import org.fogofwarlabs.sc4j.scr.game.session.setup.CampaignMissions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores campaign episode entries and selects unlocked missions, cinematics and scenario continuations.
 */
public final class CampaignEpisodes {
    /**
     * The field indices of an entry of the tables below: the displayed identifier, the mission, the cinematic and the ready room race with its flags.
     */
    private static final int IDENTIFIER_FIELD = 0;
    private static final int MISSION_FIELD = 1;
    private static final int CINEMATIC_FIELD = 2;
    private static final int RACE_FIELD = 3;
    /**
     * The race bits of the race field; the bits above are entry flags.
     */
    private static final int RACE_MASK = 0xFF;

    /**
     * The table end: the terminating entry {@code {0, 0, 0, race}}.
     */
    private static final int TERMINATOR = 0;
    /**
     * The identifier of an entry the mission choice does not list.
     */
    private static final int NO_IDENTIFIER = 0;
    /**
     * The cinematic field of a mission entry.
     */
    private static final int NO_CINEMATIC = 0;
    /**
     * The next-scenario extension that selects the original episodes; other extensions select Brood War.
     */
    private static final @NotNull String ORIGINAL_SCENARIO_EXTENSION = ".SCM";
    private static final char EXTENSION_SEPARATOR = '.';

    /**
     * The original Zerg episode.
     */
    private static final int @NotNull [] @NotNull [] ZERG = {
            {32, 12, 0, 0}, {33, 13, 0, 0}, {34, 14, 0, 0}, {42, 15, 13, 0}, {35, 15, 0, 0}, {36, 16, 0, 0}, {43, 17, 14, 0}, {37, 17, 0, 0},
            {38, 18, 0, 0}, {39, 19, 0, 0}, {44, 20, 15, 0}, {40, 20, 0, 0}, {41, 21, 0, 0}, {45, 22, 16, 0}, {TERMINATOR, TERMINATOR, 0, 0}
    };
    /**
     * The original Terran episode.
     */
    private static final int @NotNull [] @NotNull [] TERRAN = {
            {1, 1, 0, 1}, {2, 2, 0, 1}, {3, 3, 0, 1}, {12, 4, 6, 1}, {4, 4, 0, 1}, {5, 5, 0, 1}, {6, 6, 0, 1}, {13, 7, 7, 1}, {7, 7, 0, 1},
            {8, 8, 0, 1}, {9, 9, 0, 1}, {14, 10, 8, 1}, {10, 10, 0, 1}, {11, 11, 0, 1}, {15, 12, 9, 1}, {TERMINATOR, TERMINATOR, 0, 1}
    };
    /**
     * The original Protoss episode.
     */
    private static final int @NotNull [] @NotNull [] PROTOSS = {
            {17, 22, 0, 2}, {18, 23, 0, 2}, {19, 24, 0, 2}, {27, 25, 20, 2}, {20, 25, 0, 2}, {21, 26, 0, 2}, {28, 27, 21, 2}, {22, 27, 0, 2},
            {23, 28, 0, 2}, {29, 29, 22, 2}, {24, 29, 0, 2}, {25, 30, 0, 2}, {26, 31, 0, 2}, {30, 32, 23, 2}, {TERMINATOR, TERMINATOR, 0, 2}
    };
    /**
     * The Brood War Zerg episode (entry id {@code 0}: the branch and bonus missions).
     */
    private static final int @NotNull [] @NotNull [] EXPANSION_ZERG = {
            {74, 49, 0, 0}, {75, 50, 0, 0}, {76, 51, 0, 0}, {77, 52, 0, 0}, {0, 53, 0, 0x100}, {0, 54, 0, 0x100}, {0, 55, 0, 0x100},
            {0, 56, 0, 0x100}, {0, 57, 0, 0x100}, {78, 58, 0, 0}, {79, 59, 0, 0}, {80, 60, 0, 0}, {81, 61, 0, 0}, {82, 62, 0, 0},
            {0, 63, 0, 0x100}, {84, 64, 0, 0}, {85, 65, 27, 0}, {TERMINATOR, TERMINATOR, 0, 0}
    };
    /**
     * The Brood War Terran episode.
     */
    private static final int @NotNull [] @NotNull [] EXPANSION_TERRAN = {
            {65, 40, 0, 1}, {66, 41, 0, 1}, {67, 42, 0, 1}, {68, 43, 0, 1}, {69, 44, 0, 1}, {70, 45, 0, 0x101}, {71, 46, 0, 1}, {72, 47, 0, 1},
            {73, 48, 0, 1}, {87, 49, 26, 1}, {TERMINATOR, TERMINATOR, 0, 1}
    };
    /**
     * The Brood War Protoss episode.
     */
    private static final int @NotNull [] @NotNull [] EXPANSION_PROTOSS = {
            {57, 32, 0, 2}, {58, 33, 0, 2}, {59, 34, 0, 2}, {60, 35, 0, 2}, {61, 36, 0, 2}, {62, 37, 0, 2}, {63, 38, 0, 2}, {64, 39, 0, 2},
            {86, 40, 25, 2}, {TERMINATOR, TERMINATOR, 0, 2}
    };

    /**
     * One entry of an episode: its position and fields.
     */
    public static final class Entry {
        private final boolean expansion;
        private final CharacterFile.@NotNull Race episode;
        private final int index;

        /**
         * Creates a {@link Entry} instance at a position in the selected episode table.
         *
         * @param expansion whether the expansion campaign is selected
         * @param episode   the campaign race
         * @param index     the table entry index
         */
        private Entry(boolean expansion, CharacterFile.@NotNull Race episode, int index) {
            this.expansion = expansion;
            this.episode   = episode;
            this.index     = index;
        }

        /**
         * @return {@code true} if the expansion campaign is selected, {@code false} otherwise
         */
        public boolean isExpansion() {
            return this.expansion;
        }

        /**
         * @return the mission index, {@code 0} for the terminating entry
         */
        public int getMission() {
            return this.getFields()[MISSION_FIELD];
        }

        /**
         * @return the cinematic index, or {@code 0} for a mission entry
         */
        public int getCinematic() {
            return this.getFields()[CINEMATIC_FIELD];
        }

        /**
         * @return the ready room race: {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss
         */
        public int getRace() {
            return this.getFields()[RACE_FIELD] & RACE_MASK;
        }

        /**
         * @return the race whose campaign progress this entry updates
         */
        public CharacterFile.@NotNull Race getProgressRace() {
            return CharacterFile.Race.values()[this.getRace()];
        }

        /**
         * @return the entry id (the name in {@link CampaignMissionNames}), or {@code 0} for an entry the mission choice does not list
         */
        public int getIdentifier() {
            return this.getFields()[IDENTIFIER_FIELD];
        }

        /**
         * @return {@code true} if the entry is the terminating entry of the episode, {@code false} otherwise
         */
        public boolean isTerminator() {
            return this.getFields()[MISSION_FIELD] == TERMINATOR;
        }

        /**
         * @return {@code true} if the entry is the original Protoss or Brood War Zerg terminating entry, {@code false} otherwise
         */
        public boolean isEnding() {
            if (!this.isTerminator()) {
                return false;
            }

            int race = this.getRace();

            return this.expansion ? race == CharacterFile.Race.ZERG.ordinal() : race == CharacterFile.Race.PROTOSS.ordinal();
        }

        /**
         * @return the fields of this entry in its episode table
         */
        private int @NotNull [] getFields() {
            return getScenarioTable(this.expansion, this.episode)[this.index];
        }


        /**
         * Creates a {@link Entry} instance for the next position of the episode when this entry is not the terminator.
         *
         * @return the created {@link Entry} instance (the terminating entry after the last one), or {@code null} after the terminating entry
         */
        public @Nullable Entry createNextEntry() {
            int nextIndex = this.index + 1;

            return nextIndex < getScenarioTable(this.expansion, this.episode).length ? new Entry(this.expansion, this.episode, nextIndex) : null;
        }
    }

    /**
     * Creates a {@link Entry} instance at the first position of the requested campaign episode.
     *
     * @param expansion {@code true} for Brood War
     * @param episode   the episode
     * @return the created {@link Entry} instance
     */
    public static @NotNull Entry createFirstEntry(boolean expansion, CharacterFile.@NotNull Race episode) {
        return new Entry(expansion, episode, 0);
    }

    /**
     * Lists entries with identifiers and mission indices at most the progress, in table order, including cinematics.
     *
     * @param expansion {@code true} for Brood War
     * @param episode   the chosen race
     * @param progress  the selected character's completed mission index for the episode
     * @return the unlocked entries
     */
    public static @NotNull List<Entry> getUnlocked(boolean expansion, CharacterFile.@NotNull Race episode, int progress) {
        int[][] entries = getScenarioTable(expansion, episode);
        List<Entry> unlocked = new ArrayList<>();

        for (int index = 0; index < entries.length; index++) {
            if (entries[index][MISSION_FIELD] <= progress && entries[index][IDENTIFIER_FIELD] != NO_IDENTIFIER) {
                unlocked.add(new Entry(expansion, episode, index));
            }
        }

        return unlocked;
    }

    /**
     * Finds the first mission entry matching the scenario name prefix in the episodes selected by its extension.
     *
     * @param scenario the next scenario string
     * @param missions the {@code mapdata} missions
     * @return the mission entry, or {@code null} when the name has no extension or no mission matches (the cursor stays)
     */
    public static @Nullable Entry findScenario(@NotNull String scenario, @NotNull CampaignMissions missions) {
        int dotIndex = scenario.lastIndexOf(EXTENSION_SEPARATOR);

        if (dotIndex < 0 || dotIndex == scenario.length() - 1) {
            return null;
        }

        boolean expansion = !scenario.substring(dotIndex).equalsIgnoreCase(ORIGINAL_SCENARIO_EXTENSION);
        String stem = scenario.substring(0, dotIndex);

        for (int mission = missions.findFileName(stem, 0); mission >= 0; mission = missions.findFileName(stem, mission + 1)) {
            Entry entry = findMissionEntry(expansion, mission);

            if (entry != null) {
                return entry;
            }
        }

        return null;
    }

    /**
     * Finds the mission entry of a mission index.
     *
     * @param expansion {@code true} for Brood War
     * @param mission   the mission index
     * @return the entry, or {@code null} if no episode plays that mission
     */
    public static @Nullable Entry findMissionEntry(boolean expansion, int mission) {
        for (CharacterFile.Race episode : CharacterFile.Race.values()) {
            int[][] entries = getScenarioTable(expansion, episode);

            for (int index = 0; index < entries.length; index++) {
                if (entries[index][MISSION_FIELD] == mission && entries[index][MISSION_FIELD] != TERMINATOR &&
                    entries[index][CINEMATIC_FIELD] == NO_CINEMATIC) {
                    return new Entry(expansion, episode, index);
                }
            }
        }

        return null;
    }

    private static int @NotNull [] @NotNull [] getScenarioTable(boolean expansion, CharacterFile.@NotNull Race episode) {
        return switch (episode) {
            case ZERG -> expansion ? EXPANSION_ZERG : ZERG;
            case TERRAN -> expansion ? EXPANSION_TERRAN : TERRAN;
            case PROTOSS -> expansion ? EXPANSION_PROTOSS : PROTOSS;
        };
    }

    /**
     * Creates a {@link CampaignEpisodes} instance.
     */
    private CampaignEpisodes() {
    }
}
