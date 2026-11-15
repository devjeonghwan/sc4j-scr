package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Lists the built-in campaign missions and reads their scenarios.
 */
public final class CampaignMissions {
    /**
     * The number of mapdata.dat entries.
     */
    public static final int MISSION_COUNT = 65;

    private static final @NotNull String MAP_DATA_PATH = "arr/mapdata.dat";
    private static final @NotNull String MAP_DATA_TABLE_PATH = "arr/mapdata.tbl";
    /**
     * The directory prefix of an expansion campaign and its length.
     */
    private static final @NotNull String EXPANSION_PREFIX = "exp";
    private static final int EXPANSION_PREFIX_LENGTH = 3;
    private static final @NotNull String EXPANSION_NAME_PREFIX = "bw-";
    private static final int MINIMUM_PATH_COMPONENTS = 2;
    /**
     * The campaign directory component distance from the end of a mission path.
     */
    private static final int CAMPAIGN_COMPONENT_DISTANCE = 2;
    /**
     * The folder separator of the {@code mapdata} directories.
     */
    private static final char DIRECTORY_SEPARATOR = '\\';
    private static final @NotNull String DIRECTORY_SEPARATOR_PATTERN = "\\\\";

    /**
     * Creates a {@link CampaignMissions} instance from the installed tables.
     *
     * @param storage the game storage
     * @return the created {@link CampaignMissions} instance
     * @throws IOException if the tables cannot be read
     */
    public static @NotNull CampaignMissions load(@NotNull CascStorage storage) throws IOException {
        byte[] data = storage.readFile(MAP_DATA_PATH);
        TblFile table = TblFile.parse(storage.readFile(MAP_DATA_TABLE_PATH));
        List<String> directories = new ArrayList<>();

        for (int mission = 0; mission < MISSION_COUNT; mission++) {
            int offset = mission * Integer.BYTES;
            int index = offset + Integer.BYTES <= data.length ? LittleEndianUtils.readInt32(data, offset) : 0;

            directories.add(index > 0 && index <= table.getStringCount() ? table.getString(index - 1) : "");
        }

        return new CampaignMissions(directories);
    }

    /**
     * Reads the decimal mission number following a race prefix.
     *
     * @param file the mission file name
     * @param race the race prefix
     * @return the mission number, or {@code -1} when the prefix or number is absent
     */
    private static int getFileNumber(@NotNull String file, @NotNull String race) {
        if (!file.startsWith(race)) {
            return -1;
        }

        int end = race.length();

        while (end < file.length() && Character.isDigit(file.charAt(end))) {
            end++;
        }

        return end == race.length() ? -1 : Integer.parseInt(file.substring(race.length(), end));
    }

    private static @NotNull String @NotNull [] splitDirectory(@NotNull String directory) {
        return directory.toLowerCase(Locale.ROOT).split(DIRECTORY_SEPARATOR_PATTERN);
    }

    private final @NotNull List<String> directories;

    private CampaignMissions(@NotNull List<String> directories) {
        this.directories = List.copyOf(directories);
    }

    /**
     * @param mission the mission index 0..64
     * @return the mission directory such as {@code campaign\expterran\terran01}, or an empty string
     */
    public @NotNull String getDirectory(int mission) {
        return mission >= 0 && mission < this.directories.size() ? this.directories.get(mission) : "";
    }

    /**
     * @param mission the mission index 0..64
     * @return {@code true} if the mission belongs to an expansion campaign, {@code false} otherwise
     */
    public boolean isExpansion(int mission) {
        String[] parts = splitDirectory(this.getDirectory(mission));

        return parts.length >= MINIMUM_PATH_COMPONENTS && parts[parts.length - CAMPAIGN_COMPONENT_DISTANCE].startsWith(EXPANSION_PREFIX);
    }

    /**
     * Returns a short launch name: the campaign ({@code bw-} for the expansion directories) and the in-game episode, which is
     * the rank of the file number within the campaign directory plus its part letter. The installed original Terran files
     * skip numbers ({@code terran08} is the seventh mission, {@code terran-7}); {@code campaign\expterran\terran05a} is
     * {@code bw-terran-5a}; directories without a number keep their file name ({@code terran-tutorial}, {@code bw-zerg-bonus}).
     *
     * @param mission the mission index 0..64
     * @return the launch name, or an empty string for a mission without a directory
     */
    public @NotNull String getName(int mission) {
        String[] parts = splitDirectory(this.getDirectory(mission));

        if (parts.length < MINIMUM_PATH_COMPONENTS) {
            return "";
        }

        String campaign = parts[parts.length - CAMPAIGN_COMPONENT_DISTANCE];
        boolean expansion = campaign.startsWith(EXPANSION_PREFIX);
        String race = expansion ? campaign.substring(EXPANSION_PREFIX_LENGTH) : campaign;
        String episode = parts[parts.length - 1];
        int number = getFileNumber(episode, race);

        if (number >= 0) {
            TreeSet<Integer> numbers = new TreeSet<>();

            for (int other = 1; other < this.directories.size(); other++) {
                String[] otherParts = splitDirectory(this.directories.get(other));

                if (otherParts.length >= MINIMUM_PATH_COMPONENTS && otherParts[otherParts.length - CAMPAIGN_COMPONENT_DISTANCE].equals(campaign)) {
                    int otherNumber = getFileNumber(otherParts[otherParts.length - 1], race);

                    if (otherNumber >= 0) {
                        numbers.add(otherNumber);
                    }
                }
            }

            String suffix = episode.substring(race.length()).replaceFirst("^[0-9]+", "");

            episode = (numbers.headSet(number).size() + 1) + suffix;
        }

        return (expansion ? EXPANSION_NAME_PREFIX : "") + race + "-" + episode;
    }


    /**
     * Finds a mission by its directory name, for example {@code expterran\terran01} or {@code campaign\terran\terran01}.
     *
     * @param name the directory or its tail
     * @return the mission index, or {@code -1}
     */
    public int find(@NotNull String name) {
        String key = name.replace('/', DIRECTORY_SEPARATOR).toLowerCase(Locale.ROOT);

        for (int mission = 1; mission < this.directories.size(); mission++) {
            String directory = this.directories.get(mission).toLowerCase(Locale.ROOT);

            if (!directory.isEmpty() && (directory.equals(key) || directory.endsWith(DIRECTORY_SEPARATOR + key))) {
                return mission;
            }
        }

        return -1;
    }

    /**
     * Finds the next mission whose file name begins with the scenario name, ignoring case.
     *
     * @param name the scenario name without its extension
     * @param from the first mission index to test
     * @return the mission index, or {@code -1} when no later entry matches
     */
    public int findFileName(@NotNull String name, int from) {
        for (int mission = Math.max(0, from); mission < this.directories.size(); mission++) {
            String directory = this.directories.get(mission);
            String file = directory.substring(directory.lastIndexOf(DIRECTORY_SEPARATOR) + 1);

            if (file.equals(directory)) {
                continue;
            }

            if (file.length() >= name.length() && file.regionMatches(true, 0, name, 0, name.length())) {
                return mission;
            }
        }

        return -1;
    }

    /**
     * Resolves a launch argument: a mission index ({@code 40}), a launch name ({@link #getName}, for example
     * {@code bw-terran-1}) or a directory or its tail ({@link #find}).
     *
     * @param argument the argument
     * @return the mission index, or {@code -1}
     */
    public int resolve(@NotNull String argument) {
        String key = argument.trim().toLowerCase(Locale.ROOT);

        if (!key.isEmpty() && key.chars().allMatch(Character::isDigit)) {
            int mission = Integer.parseInt(key);

            return mission > 0 && mission < this.directories.size() && !this.directories.get(mission).isEmpty() ? mission : -1;
        }

        for (int mission = 1; mission < this.directories.size(); mission++) {
            if (!this.directories.get(mission).isEmpty() && this.getName(mission).equals(key)) {
                return mission;
            }
        }

        return this.find(key);
    }

    /**
     * Reads a mission scenario in the given locale.
     *
     * @param storage the game storage
     * @param mission the mission index
     * @param locale  the text locale such as {@code koKR}, or {@code null} for enUS
     * @return the scenario bytes
     * @throws IOException if no copy exists
     */
    public byte @NotNull [] readScenario(@NotNull CascStorage storage, int mission, @Nullable String locale) throws IOException {
        String directory = this.getDirectory(mission);

        if (directory.isEmpty()) {
            throw Exceptions.newIOException("Campaign mission {0} has no directory.", mission);
        }

        String asset = directory + "\\" + ChkFile.ARCHIVE_ENTRY;

        for (String prefix : new String[]{"", "SD\\"}) {
            if (locale != null && storage.hasFile("locales\\" + locale + "\\Assets\\" + prefix + asset)) {
                return storage.readFile("locales\\" + locale + "\\Assets\\" + prefix + asset);
            }

            if (storage.hasFile("locales\\enUS\\Assets\\" + prefix + asset)) {
                return storage.readFile("locales\\enUS\\Assets\\" + prefix + asset);
            }

            if (storage.hasFile(prefix + asset)) {
                return storage.readFile(prefix + asset);
            }
        }

        throw Exceptions.newIOException("Campaign mission {0} has no scenario: {1}", mission, asset);
    }
}
