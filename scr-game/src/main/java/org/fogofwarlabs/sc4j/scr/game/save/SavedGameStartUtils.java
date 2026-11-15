package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.data.format.dat.TechdataDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UpgradesDat;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.game.map.MapStringSection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.save.section.GameInformationSection;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.GameStructureView;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Builds the replay header and scenario used to start a saved game.
 * Saved dimensions, terrain, player records and strings supply the startup data; {@link SaveGameUtils#read}
 * replaces the remaining default state after startup.
 */
public final class SavedGameStartUtils {
    /**
     * The scenario version of a Brood War game (SC:R, {@code STRx} allowed).
     */
    private static final int VERSION_EXPANSION = 206;
    /**
     * The scenario version of an original game (SC:R).
     */
    private static final int VERSION_ORIGINAL = 64;

    private static final int GAME_CAMPAIGN_MISSION = 0x154;
    private static final int GAME_FORCE_FLAGS = 0xE4C8;

    private static final int FORCE_SIZE = 20;
    private static final int VCOD_SIZE = 1040;
    private static final int UNIT_SERIAL = 0;
    private static final int UNIT_X = 4;
    private static final int UNIT_Y = 6;
    private static final int UNIT_TYPE = 8;
    private static final int UNIT_OWNER = 16;
    private static final int SPRP_SIZE = 4;

    private static final int EXPANSION_UNIT_SETTINGS_SIZE = 4168;
    private static final int EXPANSION_UPGRADE_SETTINGS_SIZE = 794;
    private static final int EXPANSION_TECHNOLOGY_SETTINGS_SIZE = 396;
    private static final int ORIGINAL_UNIT_SETTINGS_SIZE = 4048;
    private static final int ORIGINAL_UPGRADE_SETTINGS_SIZE = 598;
    private static final int ORIGINAL_TECHNOLOGY_SETTINGS_SIZE = 216;

    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Builds the replay header of the saved game: the engine BYTE (the game structure's expansion byte), the frame count,
     * the campaign mission WORD, the seed, the game information, the player records, the player colors and the melee unit
     * players (the human and computer slots).
     *
     * @param file the saved game
     * @param seed the seed DWORD the header records
     * @return the {@link ReplayHeaderBuilder#HEADER_SIZE}-byte header
     */
    public static byte @NotNull [] buildHeader(@NotNull SaveGameFile file, int seed) {
        ByteBuffer header = ByteBuffer.allocate(ReplayHeaderBuilder.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer game = ByteBuffer.wrap(file.getGameStructure()).order(ByteOrder.LITTLE_ENDIAN);
        byte[] players = file.getPlayers();

        header.put(ReplayHeaderBuilder.ENGINE_OFFSET, game.get(GameStructureView.EXPANSION_OFFSET));
        header.putInt(ReplayHeaderBuilder.FRAMES_OFFSET, 0);
        header.putShort(ReplayHeaderBuilder.CAMPAIGN_MISSION_OFFSET, game.getShort(GAME_CAMPAIGN_MISSION));
        header.putInt(ReplayHeaderBuilder.SEED_OFFSET, seed);
        header.put(GameInformationSection.HEADER_GAME_INFORMATION,
                   file.getInformation(),
                   GameInformationSection.GAME_INFORMATION,
                   GameInformationSection.GAME_INFORMATION_SIZE);
        header.put(ReplayHeaderBuilder.PLAYERS_OFFSET, players);

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            int type = players[player * ReplayHeaderBuilder.PLAYER_RECORD_SIZE + ReplayHeaderBuilder.PLAYER_TYPE_OFFSET];

            header.putInt(ReplayHeaderBuilder.COLORS_OFFSET + player * Integer.BYTES,
                          game.getInt(GameStructureView.PLAYER_COLORS_OFFSET + player * Integer.BYTES));
            header.put(ReplayHeaderBuilder.MELEE_UNIT_FLAGS_OFFSET + player,
                       (byte) (type == PlayerTypes.COMPUTER || type == PlayerTypes.HUMAN ? 1 : 0));
        }

        return header.array();
    }

    /**
     * Builds the scenario the saved game starts from.
     *
     * @param file the saved game
     * @return the scenario ({@code .chk}) bytes
     */
    public static byte @NotNull [] buildScenario(@NotNull SaveGameFile file) {
        ByteBuffer game = ByteBuffer.wrap(file.getGameStructure()).order(ByteOrder.LITTLE_ENDIAN);
        byte[] players = file.getPlayers();
        boolean expansion = game.get(GameStructureView.EXPANSION_OFFSET) != 0;
        int width = game.getShort(GameStructureView.MAP_SIZE_OFFSET) & WORD_MASK;
        int height = game.getShort(GameStructureView.MAP_SIZE_OFFSET + Short.BYTES) & WORD_MASK;
        MapStringSection strings = SaveGameUtils.require(file.getStringSection(), "string section");
        ByteArrayOutputStream scenario = new ByteArrayOutputStream();

        int version = expansion ? VERSION_EXPANSION : VERSION_ORIGINAL;

        putSection(scenario, ChkFile.VERSION_SECTION, createWords(version));
        putSection(scenario, ChkFile.DIMENSIONS_SECTION, createWords(width, height));

        int tileset = game.getShort(GameStructureView.TILESET_OFFSET) & WORD_MASK;

        putSection(scenario, ChkFile.TILESET_SECTION, createWords(tileset));
        putSection(scenario, ChkFile.OWNER_SECTION, createOwners(players));
        putSection(scenario, ChkFile.RACE_SECTION, createRaces(players));
        putSection(scenario, ChkFile.TERRAIN_SECTION, file.readMapTiles(width, height));
        putSection(scenario, strings.isExtended() ? ChkFile.EXTENDED_STRING_SECTION : ChkFile.STRING_SECTION, strings.toByteArray());
        putSection(scenario, ChkFile.SCENARIO_PROPERTIES_SECTION, new byte[SPRP_SIZE]);
        putSection(scenario, ChkFile.FORCE_SECTION, createForces(game));
        putSection(scenario, ChkFile.VERIFICATION_CODE_SECTION, new byte[VCOD_SIZE]);

        byte[] locations = new byte[TriggerContext.LOCATION_COUNT * TriggerContext.LOCATION_RECORD_SIZE];

        game.get(TriggerContext.LOCATIONS_OFFSET, locations);
        putSection(scenario, ChkFile.LOCATION_SECTION, locations);
        putSection(scenario, ChkFile.UNIT_PROPERTIES_SECTION, new byte[TriggerContext.UNIT_PROPERTIES_SIZE]);
        putSection(scenario, ChkFile.UNIT_SECTION, createStartLocations(game));
        putSection(scenario, ChkFile.THINGY_SECTION, new byte[0]);
        putSection(scenario, ChkFile.MASK_SECTION, new byte[width * height]);
        putSection(scenario, ChkFile.TRIGGER_SECTION, new byte[0]);

        if (expansion) {
            putSection(scenario, ChkFile.EXPANSION_UNIT_SETTINGS_SECTION, createDefaultFlags(UnitsDat.UNIT_COUNT, EXPANSION_UNIT_SETTINGS_SIZE));
            putSection(scenario, ChkFile.EXPANSION_UPGRADE_SETTINGS_SECTION,
                       createDefaultFlags(UpgradesDat.UPGRADE_COUNT, EXPANSION_UPGRADE_SETTINGS_SIZE));
            putSection(scenario, ChkFile.EXPANSION_TECHNOLOGY_SETTINGS_SECTION,
                       createDefaultFlags(TechdataDat.TECH_COUNT, EXPANSION_TECHNOLOGY_SETTINGS_SIZE));
            putSection(scenario, ChkFile.UNIT_AVAILABILITY_SECTION, createAvailability());
            putSection(scenario, ChkFile.EXPANSION_UPGRADE_LEVELS_SECTION, createPlayerDefaults(UpgradesDat.UPGRADE_COUNT));
            putSection(scenario, ChkFile.EXPANSION_TECHNOLOGY_AVAILABILITY_SECTION, createPlayerDefaults(TechdataDat.TECH_COUNT));
            putSection(scenario, ChkFile.COLORS_SECTION, createColors(game));
        } else {
            putSection(scenario, ChkFile.UNIT_SETTINGS_SECTION, createDefaultFlags(UnitsDat.UNIT_COUNT, ORIGINAL_UNIT_SETTINGS_SIZE));
            putSection(scenario, ChkFile.UPGRADE_SETTINGS_SECTION,
                       createDefaultFlags(PlayerUpgrades.CLASSIC_UPGRADE_COUNT, ORIGINAL_UPGRADE_SETTINGS_SIZE));
            putSection(scenario, ChkFile.TECHNOLOGY_SETTINGS_SECTION,
                       createDefaultFlags(PlayerTechnology.CLASSIC_TECHNOLOGY_COUNT, ORIGINAL_TECHNOLOGY_SETTINGS_SIZE));
            putSection(scenario, ChkFile.UNIT_AVAILABILITY_SECTION, createAvailability());
            putSection(scenario, ChkFile.UPGRADE_LEVELS_SECTION, createPlayerDefaults(PlayerUpgrades.CLASSIC_UPGRADE_COUNT));
            putSection(scenario, ChkFile.TECHNOLOGY_AVAILABILITY_SECTION, createPlayerDefaults(PlayerTechnology.CLASSIC_TECHNOLOGY_COUNT));
        }

        return scenario.toByteArray();
    }

    private static void putSection(@NotNull ByteArrayOutputStream scenario, @NotNull String tag, byte @NotNull [] payload) {
        scenario.writeBytes(tag.getBytes(StandardCharsets.US_ASCII));
        scenario.writeBytes(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());
        scenario.writeBytes(payload);
    }

    /**
     * Builds a section payload of little-endian WORDs.
     *
     * @param values the WORD values in section order, each in its low 16 bits
     * @return the section payload
     */
    private static byte @NotNull [] createWords(int @NotNull ... values) {
        ByteBuffer words = ByteBuffer.allocate(values.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int value : values) {
            words.putShort((short) value);
        }

        return words.array();
    }

    /**
     * Builds the map owner types of the saved players: a human slot for a human, a computer slot for a computer.
     *
     * @param players the saved player records
     * @return the {@code OWNR} section
     */
    private static byte @NotNull [] createOwners(byte @NotNull [] players) {
        byte[] owners = new byte[PlayerTable.PLAYER_SLOTS];

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            int type = players[player * ReplayHeaderBuilder.PLAYER_RECORD_SIZE + ReplayHeaderBuilder.PLAYER_TYPE_OFFSET];

            owners[player] = (byte) (type == PlayerTypes.HUMAN
                                     ? PlayerTypes.EITHER_PREFER_HUMAN
                                     : type == PlayerTypes.COMPUTER ? PlayerTypes.EITHER_PREFER_COMPUTER : type);
        }

        return owners;
    }

    /**
     * Builds the map races of the saved players.
     *
     * @param players the saved player records
     * @return the {@code SIDE} section
     */
    private static byte @NotNull [] createRaces(byte @NotNull [] players) {
        byte[] races = new byte[PlayerTable.PLAYER_SLOTS];

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            races[player] = players[player * ReplayHeaderBuilder.PLAYER_RECORD_SIZE + ReplayHeaderBuilder.PLAYER_RACE_OFFSET];
        }

        return races;
    }

    /**
     * Builds the forces: the player forces of the game structure (stored plus one) and the force flags; no force names.
     *
     * @param game the game structure
     * @return the {@code FORC} section
     */
    private static byte @NotNull [] createForces(@NotNull ByteBuffer game) {
        byte[] forces = new byte[FORCE_SIZE];

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            forces[player] = (byte) Math.max((game.get(GameStructureView.PLAYER_FORCES_OFFSET + player) & BYTE_MASK) - 1, 0);
        }

        for (int force = 0; force < PlayerTable.FORCE_COUNT; force++) {
            forces[FORCE_SIZE - PlayerTable.FORCE_COUNT + force] = game.get(GAME_FORCE_FLAGS + force);
        }

        return forces;
    }

    /**
     * Builds the start locations of the game structure as start location units of their players.
     *
     * @param game the game structure
     * @return the {@code UNIT} section
     */
    private static byte @NotNull [] createStartLocations(@NotNull ByteBuffer game) {
        ByteBuffer units = ByteBuffer.allocate(PlayerTable.PLAYABLE_PLAYER_SLOTS * ChkUnit.ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int count = 0;

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            int positionX = game.getShort(GameStructureView.START_LOCATIONS_OFFSET + player * Integer.BYTES) & WORD_MASK;
            int positionY = game.getShort(GameStructureView.START_LOCATIONS_OFFSET + player * Integer.BYTES + Short.BYTES) & WORD_MASK;

            if (positionX == 0 && positionY == 0) {
                continue;
            }

            int record = count++ * ChkUnit.ENTRY_SIZE;

            units.putInt(record + UNIT_SERIAL, count);
            units.putShort(record + UNIT_X, (short) positionX);
            units.putShort(record + UNIT_Y, (short) positionY);
            units.putShort(record + UNIT_TYPE, (short) ScrUnitIds.SPECIAL_START_LOCATION);
            units.put(record + UNIT_OWNER, (byte) player);
        }

        return Arrays.copyOf(units.array(), count * ChkUnit.ENTRY_SIZE);
    }

    /**
     * Builds a settings section whose leading use-default flags are all set.
     *
     * @param count the number of entries
     * @param size  the section size
     * @return the settings section
     */
    private static byte @NotNull [] createDefaultFlags(int count, int size) {
        byte[] section = new byte[size];

        Arrays.fill(section, 0, count, (byte) 1);

        return section;
    }

    /**
     * Builds the unit availability: every unit available globally and every player using the global availability.
     *
     * @return the {@code PUNI} section
     */
    private static byte @NotNull [] createAvailability() {
        byte[] section = new byte[(2 * PlayerTable.PLAYER_SLOTS + 1) * UnitsDat.UNIT_COUNT];

        Arrays.fill(section, PlayerTable.PLAYER_SLOTS * UnitsDat.UNIT_COUNT, section.length, (byte) 1);

        return section;
    }

    /**
     * Builds a player upgrade or technology section whose players all use the global defaults.
     *
     * @param count the number of upgrades or technologies
     * @return the player settings section
     */
    private static byte @NotNull [] createPlayerDefaults(int count) {
        byte[] section = new byte[PlayerTable.SETTINGS_ROWS * count];

        Arrays.fill(section, PlayerTable.SETTINGS_USE_DEFAULT_ROW * count, section.length, (byte) 1);

        return section;
    }

    /**
     * Builds the player colors of the game structure.
     *
     * @param game the game structure
     * @return the {@code COLR} section
     */
    private static byte @NotNull [] createColors(@NotNull ByteBuffer game) {
        byte[] colors = new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int player = 0; player < colors.length; player++) {
            colors[player] = (byte) game.getInt(GameStructureView.PLAYER_COLORS_OFFSET + player * Integer.BYTES);
        }

        return colors;
    }


    private SavedGameStartUtils() {
    }
}
