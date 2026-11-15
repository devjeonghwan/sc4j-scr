package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.log.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates required CHK tags, section sizes and expansion restrictions for each map version and game mode.
 */
public final class ChkValidatorUtils {
    private static final int FIRST_EXPANSION_TILESET = 5;
    private static final int PLAYER_SECTION_SIZE = 12;
    private static final int SCENARIO_PROPERTIES_SIZE = 4;
    private static final int MAXIMUM_FORCE_SIZE = 20;
    private static final int REFERENCE_VCOD_SIZE = 1040;
    private static final int TRIGGER_SIZE = 2400;
    private static final int PLAYER_COLORS_SIZE = 32;
    private static final int COLORS_SIZE = 8;

    private static final int UNIT_SETTINGS_SIZE = 4048;
    private static final int UPGRADE_SETTINGS_SIZE = 598;
    private static final int TECHNOLOGY_SETTINGS_SIZE = 216;
    private static final int UNIT_AVAILABILITY_SIZE = 5700;
    private static final int UPGRADE_LEVELS_SIZE = 1748;
    private static final int TECHNOLOGY_AVAILABILITY_SIZE = 912;
    private static final int EXPANSION_UNIT_SETTINGS_SIZE = 4168;
    private static final int EXPANSION_UPGRADE_SETTINGS_SIZE = 794;
    private static final int EXPANSION_TECHNOLOGY_SETTINGS_SIZE = 396;
    private static final int EXPANSION_UPGRADE_LEVELS_SIZE = 2318;
    private static final int EXPANSION_TECHNOLOGY_AVAILABILITY_SIZE = 1672;
    private static final int UNIT_PROPERTIES_SIZE = 1280;
    private static final int ORIGINAL_LOCATIONS_SIZE = 1280;
    private static final int LOCATIONS_SIZE = 5100;

    private static final @NotNull Logger LOGGER = Logger.of(ChkValidatorUtils.class);

    private static final @NotNull List<ListEntry> MAP_INFORMATION_LIST = List.of(
            new ListEntry(ChkFile.VERSION_SECTION, createExactRule(ChkFile.WORD_SECTION_SIZE), true),
            new ListEntry(ChkFile.DIMENSIONS_SECTION, ChkValidatorUtils::checkDimensions, true),
            new ListEntry(ChkFile.TILESET_SECTION, createExactRule(ChkFile.WORD_SECTION_SIZE), true),
            new ListEntry(ChkFile.OWNER_SECTION, createExactRule(PLAYER_SECTION_SIZE), true),
            new ListEntry(ChkFile.SCENARIO_PROPERTIES_SECTION, createExactRule(SCENARIO_PROPERTIES_SIZE), true),
            new ListEntry(ChkFile.STRING_SECTION, createStringRule(ChkFile.STRING_ENTRY_SIZE), false),
            new ListEntry(ChkFile.EXTENDED_STRING_SECTION, createStringRule(ChkFile.EXTENDED_STRING_ENTRY_SIZE), false),
            new ListEntry(ChkFile.TERRAIN_SECTION, createMaximumSizeRule(ChkFile.MAXIMUM_TERRAIN_SIZE), true),
            new ListEntry(ChkFile.UNIT_SECTION, createMultipleSizeRule(ChkUnit.ENTRY_SIZE), true)
    );

    private static final @NotNull List<ListEntry> VERSION_LIST = List.of(
            new ListEntry(ChkFile.VERSION_SECTION, createExactRule(ChkFile.WORD_SECTION_SIZE), true)
    );

    private static final @NotNull List<ListEntry> CLASSIC_HEADER_LIST = List.of(
            new ListEntry(ChkFile.VERSION_SECTION, createExactRule(ChkFile.WORD_SECTION_SIZE), true),
            new ListEntry(ChkFile.DIMENSIONS_SECTION, ChkValidatorUtils::checkDimensions, true),
            new ListEntry(ChkFile.TILESET_SECTION, ChkValidatorUtils::checkEra, true),
            new ListEntry(ChkFile.OWNER_SECTION, createExactRule(PLAYER_SECTION_SIZE), true),
            new ListEntry(ChkFile.RACE_SECTION, createExactRule(PLAYER_SECTION_SIZE), true),
            new ListEntry(ChkFile.STRING_SECTION, createStringRule(ChkFile.STRING_ENTRY_SIZE), true),
            new ListEntry(ChkFile.SCENARIO_PROPERTIES_SECTION, createExactRule(SCENARIO_PROPERTIES_SIZE), true),
            new ListEntry(ChkFile.FORCE_SECTION, createMaximumSizeRule(MAXIMUM_FORCE_SIZE), true),
            new ListEntry(ChkFile.VERIFICATION_CODE_SECTION, createExactRule(REFERENCE_VCOD_SIZE), true),
            new ListEntry(ChkFile.TRIGGER_SECTION, createMultipleSizeRule(TRIGGER_SIZE), true),
            new ListEntry(ChkFile.PLAYER_COLORS_SECTION, createExactRule(PLAYER_COLORS_SIZE), false)
    );

    private static final @NotNull List<ListEntry> REMASTERED_HEADER_LIST = List.of(
            new ListEntry(ChkFile.VERSION_SECTION, createExactRule(ChkFile.WORD_SECTION_SIZE), true),
            new ListEntry(ChkFile.DIMENSIONS_SECTION, ChkValidatorUtils::checkDimensions, true),
            new ListEntry(ChkFile.TILESET_SECTION, ChkValidatorUtils::checkEra, true),
            new ListEntry(ChkFile.OWNER_SECTION, createExactRule(PLAYER_SECTION_SIZE), true),
            new ListEntry(ChkFile.RACE_SECTION, createExactRule(PLAYER_SECTION_SIZE), true),
            new ListEntry(ChkFile.STRING_SECTION, createStringRule(ChkFile.STRING_ENTRY_SIZE), false),
            new ListEntry(ChkFile.EXTENDED_STRING_SECTION, createStringRule(ChkFile.EXTENDED_STRING_ENTRY_SIZE), false),
            new ListEntry(ChkFile.SCENARIO_PROPERTIES_SECTION, createExactRule(SCENARIO_PROPERTIES_SIZE), true),
            new ListEntry(ChkFile.FORCE_SECTION, createMaximumSizeRule(MAXIMUM_FORCE_SIZE), true),
            new ListEntry(ChkFile.VERIFICATION_CODE_SECTION, createExactRule(REFERENCE_VCOD_SIZE), true),
            new ListEntry(ChkFile.TRIGGER_SECTION, createMultipleSizeRule(TRIGGER_SIZE), true),
            new ListEntry(ChkFile.PLAYER_COLORS_SECTION, createExactRule(PLAYER_COLORS_SIZE), false)
    );

    /**
     * Represents one entry of a section validation list.
     */
    private static final class ListEntry {
        private final @NotNull String tag;
        private final @NotNull SizeRule rule;
        private final boolean required;

        /**
         * Creates a {@link ListEntry} instance.
         *
         * @param tag      the four-character section tag
         * @param rule     the handler size rule
         * @param required whether the dispatcher requires the tag
         */
        private ListEntry(@NotNull String tag, @NotNull SizeRule rule, boolean required) {
            this.tag      = tag;
            this.rule     = rule;
            this.required = required;
        }
    }

    /**
     * Represents the size check of a section handler.
     */
    private interface SizeRule {
        /**
         * Checks one section.
         *
         * @param section   the section to check
         * @param expansion whether the game runs the expansion
         * @return the rejection reason, or {@code null} if the handler accepts the section
         */
        @Nullable String check(@NotNull ChkSection section, boolean expansion);
    }

    /**
     * Validates the version, header and game mode sections.
     *
     * @param chkFile        the parsed map
     * @param useMapSettings whether the game uses map settings; otherwise the melee lists apply
     * @param expansionGame  whether the game runs the expansion, independently of the map version
     * @throws IOException if a required section is absent or a section fails validation
     */
    public static void validate(@NotNull ChkFile chkFile, boolean useMapSettings, boolean expansionGame) throws IOException {
        validate(chkFile, useMapSettings, expansionGame, false);
    }

    /**
     * Validates the version, header and game mode sections, optionally skipping rejected earlier copies.
     * <p>
     * Compatibility mode logs and skips rejected sections before the final section with the same tag.
     * The final section must pass; without compatibility mode, every section must pass.
     *
     * @param chkFile        the parsed map
     * @param useMapSettings whether the game uses map settings; otherwise the melee lists apply
     * @param expansionGame  whether the game runs the expansion
     * @param compatibility  whether rejected sections before the last one of their tag are skipped instead of rejected
     * @throws IOException if a required section is absent or a section fails validation
     */
    public static void validate(@NotNull ChkFile chkFile, boolean useMapSettings, boolean expansionGame, boolean compatibility) throws IOException {
        dispatch(chkFile, VERSION_LIST, expansionGame, compatibility);

        int version = LittleEndianUtils.readUnsigned16(chkFile.getSections(ChkFile.VERSION_SECTION).getLast().getPayload(), 0);
        boolean expansion = ChkFile.isExpansionVersion(version);
        boolean remastered = ChkFile.isRemasteredVersion(version);

        if (version != ChkFile.VERSION_ORIGINAL && version != ChkFile.VERSION_HYBRID && !expansion && !remastered) {
            throw Exceptions.newIOException("The original version table has no record for CHK version {0}.", version);
        }

        dispatch(chkFile, remastered ? REMASTERED_HEADER_LIST : CLASSIC_HEADER_LIST, expansionGame, compatibility);

        List<ListEntry> gameModeList = useMapSettings ? createMapSettingsList(version, expansion, remastered)
                                                      : createMeleeList(expansion, remastered);

        dispatch(chkFile, gameModeList, expansionGame, compatibility);
    }

    /**
     * Validates section sizes and requires a loaded string table for map list information.
     *
     * @param chkFile       the parsed map
     * @param compatibility whether rejected sections before the last one of their tag are skipped instead of rejected
     * @throws IOException if a required section is absent or a section fails validation
     */
    public static void validateMapInformation(@NotNull ChkFile chkFile, boolean compatibility) throws IOException {
        dispatch(chkFile, MAP_INFORMATION_LIST, true, compatibility);

        for (String tag : new String[]{ChkFile.STRING_SECTION, ChkFile.EXTENDED_STRING_SECTION}) {
            for (ChkSection section : chkFile.getSections(tag)) {
                int headerSize = tag.equals(ChkFile.EXTENDED_STRING_SECTION) ? ChkFile.EXTENDED_STRING_ENTRY_SIZE : ChkFile.STRING_ENTRY_SIZE;

                if (Integer.compareUnsigned(section.getDeclaredSize(), headerSize) >= 0) {
                    return;
                }
            }
        }

        throw Exceptions.newIOException("The CHK file loads no string table.");
    }

    private static @NotNull List<ListEntry> createMeleeList(boolean expansion, boolean remastered) {
        ListEntry strings = new ListEntry(ChkFile.STRING_SECTION, createStringRule(ChkFile.STRING_ENTRY_SIZE), !remastered);
        ListEntry extendedStrings = new ListEntry(ChkFile.EXTENDED_STRING_SECTION, createStringRule(ChkFile.EXTENDED_STRING_ENTRY_SIZE), false);
        ListEntry terrain = new ListEntry(ChkFile.TERRAIN_SECTION, createMaximumSizeRule(ChkFile.MAXIMUM_TERRAIN_SIZE), true);
        ListEntry thingies = new ListEntry(ChkFile.THINGY_SECTION, createMultipleSizeRule(ChkThingy.ENTRY_SIZE), true);
        ListEntry units = new ListEntry(ChkFile.UNIT_SECTION, createMultipleSizeRule(ChkUnit.ENTRY_SIZE), true);
        ListEntry colors = new ListEntry(ChkFile.COLORS_SECTION, createExactRule(COLORS_SIZE), true);

        if (remastered) {
            return expansion ? List.of(strings, extendedStrings, terrain, thingies, units, colors)
                             : List.of(strings, extendedStrings, terrain, thingies, units);
        }

        return expansion ? List.of(strings, terrain, thingies, units, colors) : List.of(strings, terrain, thingies, units);
    }

    private static @NotNull List<ListEntry> createMapSettingsList(int version, boolean expansion, boolean remastered) {
        List<ListEntry> head = remastered
                               ? List.of(new ListEntry(ChkFile.STRING_SECTION, createStringRule(ChkFile.STRING_ENTRY_SIZE), false),
                                         new ListEntry(ChkFile.EXTENDED_STRING_SECTION, createStringRule(ChkFile.EXTENDED_STRING_ENTRY_SIZE), false))
                               : List.of(new ListEntry(ChkFile.STRING_SECTION, createStringRule(ChkFile.STRING_ENTRY_SIZE), true));
        List<ListEntry> terrain = List.of(new ListEntry(ChkFile.TERRAIN_SECTION, createMaximumSizeRule(ChkFile.MAXIMUM_TERRAIN_SIZE), true),
                                          new ListEntry(ChkFile.THINGY_SECTION, createMultipleSizeRule(ChkThingy.ENTRY_SIZE), true),
                                          new ListEntry(ChkFile.MASK_SECTION, createReadableRule(), true));
        List<ListEntry> classicSettings = List.of(
                new ListEntry(ChkFile.UNIT_SETTINGS_SECTION, createExactRule(UNIT_SETTINGS_SIZE), true),
                new ListEntry(ChkFile.UPGRADE_SETTINGS_SECTION, createExactRule(UPGRADE_SETTINGS_SIZE), true),
                new ListEntry(ChkFile.TECHNOLOGY_SETTINGS_SECTION, createExactRule(TECHNOLOGY_SETTINGS_SIZE), true),
                new ListEntry(ChkFile.UNIT_AVAILABILITY_SECTION, createExactRule(UNIT_AVAILABILITY_SIZE), true),
                new ListEntry(ChkFile.UPGRADE_LEVELS_SECTION, createExactRule(UPGRADE_LEVELS_SIZE), true),
                new ListEntry(ChkFile.TECHNOLOGY_AVAILABILITY_SECTION, createExactRule(TECHNOLOGY_AVAILABILITY_SIZE), true),
                new ListEntry(ChkFile.EXPANSION_UNIT_SETTINGS_SECTION, createExactRule(EXPANSION_UNIT_SETTINGS_SIZE), false),
                new ListEntry(ChkFile.EXPANSION_UPGRADE_SETTINGS_SECTION, createExactRule(EXPANSION_UPGRADE_SETTINGS_SIZE), false),
                new ListEntry(ChkFile.EXPANSION_TECHNOLOGY_SETTINGS_SECTION, createExactRule(EXPANSION_TECHNOLOGY_SETTINGS_SIZE), false),
                new ListEntry(ChkFile.EXPANSION_UPGRADE_LEVELS_SECTION, createExactRule(EXPANSION_UPGRADE_LEVELS_SIZE), false),
                new ListEntry(ChkFile.EXPANSION_TECHNOLOGY_AVAILABILITY_SECTION, createExactRule(EXPANSION_TECHNOLOGY_AVAILABILITY_SIZE), false)
        );
        List<ListEntry> expansionSettings = List.of(
                new ListEntry(ChkFile.EXPANSION_UNIT_SETTINGS_SECTION, createExactRule(EXPANSION_UNIT_SETTINGS_SIZE), true),
                new ListEntry(ChkFile.EXPANSION_UPGRADE_SETTINGS_SECTION, createExactRule(EXPANSION_UPGRADE_SETTINGS_SIZE), true),
                new ListEntry(ChkFile.EXPANSION_TECHNOLOGY_SETTINGS_SECTION, createExactRule(EXPANSION_TECHNOLOGY_SETTINGS_SIZE), true),
                new ListEntry(ChkFile.UNIT_AVAILABILITY_SECTION, createExactRule(UNIT_AVAILABILITY_SIZE), true),
                new ListEntry(ChkFile.EXPANSION_UPGRADE_LEVELS_SECTION, createExactRule(EXPANSION_UPGRADE_LEVELS_SIZE), true),
                new ListEntry(ChkFile.EXPANSION_TECHNOLOGY_AVAILABILITY_SECTION, createExactRule(EXPANSION_TECHNOLOGY_AVAILABILITY_SIZE), true)
        );
        List<ListEntry> tail = List.of(
                new ListEntry(ChkFile.UNIT_SECTION, createMultipleSizeRule(ChkUnit.ENTRY_SIZE), true),
                new ListEntry(ChkFile.UNIT_PROPERTIES_SECTION, createExactRule(UNIT_PROPERTIES_SIZE), true),
                new ListEntry(ChkFile.LOCATION_SECTION,
                              createExactRule(version == ChkFile.VERSION_ORIGINAL ? ORIGINAL_LOCATIONS_SIZE : LOCATIONS_SIZE),
                              true),
                new ListEntry(ChkFile.TRIGGER_SECTION, createMultipleSizeRule(TRIGGER_SIZE), true)
        );
        List<ListEntry> entries = new ArrayList<>(head);

        entries.addAll(terrain);
        entries.addAll(expansion ? expansionSettings : classicSettings);
        entries.addAll(tail);

        if (expansion) {
            entries.add(new ListEntry(ChkFile.COLORS_SECTION, createExactRule(COLORS_SIZE), true));
        }

        return entries;
    }

    private static void dispatch(@NotNull ChkFile chkFile, @NotNull List<ListEntry> entries, boolean expansion, boolean compatibility) throws IOException {
        // Every required tag must be present before any handler runs.
        for (ListEntry entry : entries) {
            if (entry.required && !chkFile.hasSection(entry.tag)) {
                throw Exceptions.newIOException("The CHK file misses the required section {0}.", entry.tag.strip());
            }
        }

        // Each list entry visits every section with its tag in file order.
        for (ListEntry entry : entries) {
            List<ChkSection> sections = chkFile.getSections(entry.tag);

            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                ChkSection section = sections.get(sectionIndex);
                String rejection = entry.rule.check(section, expansion);

                if (rejection == null) {
                    continue;
                }

                String message = "The original " + entry.tag.strip() + " handler rejects the section at offset " + section.getHeaderOffset() +
                                 ": " + rejection + ".";

                if (!compatibility || sectionIndex == sections.size() - 1) {
                    throw Exceptions.newIOException("{0}", message);
                }

                LOGGER.info("MAP COMPATIBILITY: relaxed rule \"every " + entry.tag.strip() + " section must pass\"; skipped: " + message);
            }
        }
    }

    private static @Nullable String checkDimensions(@NotNull ChkSection section, boolean expansion) {
        String rejection = createExactRule(ChkFile.DIMENSIONS_SIZE).check(section, expansion);

        if (rejection != null) {
            return rejection;
        }

        byte[] payload = section.getPayload();

        if (LittleEndianUtils.readUnsigned16(payload, 0) > ChkFile.MAXIMUM_DIMENSION ||
            LittleEndianUtils.readUnsigned16(payload, ChkFile.DIMENSION_HEIGHT_OFFSET) > ChkFile.MAXIMUM_DIMENSION) {
            return "a dimension exceeds 256";
        }

        return null;
    }

    private static @Nullable String checkEra(@NotNull ChkSection section, boolean expansion) {
        String rejection = createExactRule(ChkFile.WORD_SECTION_SIZE).check(section, expansion);

        if (rejection != null) {
            return rejection;
        }

        if ((LittleEndianUtils.readUnsigned16(section.getPayload(), 0) & ChkFile.TILESET_MASK) >= FIRST_EXPANSION_TILESET && !expansion) {
            return "an expansion tileset outside an expansion game";
        }

        return null;
    }

    private static @NotNull SizeRule createExactRule(int size) {
        return (section, expansion) -> section.getDeclaredSize() != size
                                       ? "the size is not " + size
                                       : section.hasCompletePayload() ? null : "the payload is outside the buffer";
    }

    private static @NotNull SizeRule createMaximumSizeRule(int size) {
        return (section, expansion) -> Integer.compareUnsigned(section.getDeclaredSize(), size) > 0
                                       ? "the size exceeds " + size
                                       : section.hasCompletePayload() ? null : "the payload is outside the buffer";
    }

    private static @NotNull SizeRule createMultipleSizeRule(int size) {
        return (section, expansion) -> Integer.remainderUnsigned(section.getDeclaredSize(), size) != 0
                                       ? "the size is not a multiple of " + size
                                       : section.hasCompletePayload() ? null : "the payload is outside the buffer";
    }

    private static @NotNull SizeRule createReadableRule() {
        return (section, expansion) -> section.hasCompletePayload() ? null : "the payload is outside the buffer";
    }

    private static @NotNull SizeRule createStringRule(int headerSize) {
        return (section, expansion) -> Integer.compareUnsigned(section.getDeclaredSize(), headerSize) < 0 || section.hasCompletePayload()
                                       ? null
                                       : "the payload is outside the buffer";
    }


    private ChkValidatorUtils() {
    }
}
