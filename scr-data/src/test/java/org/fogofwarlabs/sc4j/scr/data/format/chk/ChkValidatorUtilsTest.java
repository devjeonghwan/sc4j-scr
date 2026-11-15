package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Tests {@link ChkValidatorUtils} against synthetic protected scenarios.
 */
final class ChkValidatorUtilsTest {
    private static final int SECTION_HEADER_SIZE = 8;
    private static final int MAXIMUM_TERRAIN_SIZE = 0x20000;
    private static final int VERSION_ORIGINAL = 59;
    private static final int VERSION_BROOD_WAR = 205;
    private static final int VERSION_REMASTERED_BROOD_WAR = 206;
    private static final int UNIT_ENTRY_SIZE = 36;
    private static final int UNKNOWN_VERSION = 100;
    private static final int MAP_SIZE = 64;
    private static final int OVERSIZED_WIDTH = 512;
    private static final int GARBAGE_WIDTH = 0xF650;
    private static final int GARBAGE_HEIGHT = 0x42B2;
    private static final int TILE_WORD_SIZE = 2;
    private static final int CLASSIC_TILESET = 4;
    private static final int EXPANSION_TILESET = 7;

    private static final int PLAYER_SECTION_SIZE = 12;
    private static final int SCENARIO_PROPERTIES_SIZE = 4;
    private static final int MAXIMUM_FORCE_SIZE = 20;
    private static final int VCOD_SIZE = 1040;
    private static final int TRIGGER_SIZE = 2400;
    private static final int COLORS_SIZE = 8;

    private static void assertRejected(byte @NotNull [] scenario, @NotNull String reason) {
        IOException rejection = Assertions.assertThrows(IOException.class,
                                                        () -> ChkValidatorUtils.validate(ChkFile.parseTolerant(scenario), false, true));

        Assertions.assertTrue(rejection.getMessage().contains(reason), rejection.getMessage());
    }

    private static byte @NotNull [] createMeleeScenario(int version, byte @NotNull [] @NotNull [] extra) {
        ByteArrayOutputStream scenario = new ByteArrayOutputStream();

        scenario.writeBytes(createSection("VER ", createShortArray(version)));
        scenario.writeBytes(createSection("DIM ", createShortArray(MAP_SIZE, MAP_SIZE)));
        scenario.writeBytes(createSection("ERA ", createShortArray(CLASSIC_TILESET)));
        scenario.writeBytes(createSection("OWNR", new byte[PLAYER_SECTION_SIZE]));
        scenario.writeBytes(createSection("SIDE", new byte[PLAYER_SECTION_SIZE]));
        scenario.writeBytes(createSection("STR ", new byte[]{0, 0}));
        scenario.writeBytes(createSection("SPRP", new byte[SCENARIO_PROPERTIES_SIZE]));
        scenario.writeBytes(createSection("FORC", new byte[MAXIMUM_FORCE_SIZE]));
        scenario.writeBytes(createSection("VCOD", new byte[VCOD_SIZE]));
        scenario.writeBytes(createSection("TRIG", new byte[0]));
        scenario.writeBytes(createSection("MTXM", new byte[MAP_SIZE * MAP_SIZE * TILE_WORD_SIZE]));
        scenario.writeBytes(createSection("THG2", new byte[0]));
        scenario.writeBytes(createSection("UNIT", new byte[0]));
        scenario.writeBytes(createSection("COLR", new byte[COLORS_SIZE]));

        for (byte[] section : extra) {
            scenario.writeBytes(section);
        }

        return scenario.toByteArray();
    }

    private static byte @NotNull [] createScenario(byte @NotNull [] @NotNull ... sections) {
        ByteArrayOutputStream scenario = new ByteArrayOutputStream();

        for (byte[] section : sections) {
            scenario.writeBytes(section);
        }

        return scenario.toByteArray();
    }

    private static byte @NotNull [] createSection(@NotNull String name, byte @NotNull [] payload) {
        return ByteBuffer.allocate(SECTION_HEADER_SIZE + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                         .put(name.getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).put(payload).array();
    }

    private static byte @NotNull [] createShortArray(int @NotNull ... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int value : values) {
            buffer.putShort((short) value);
        }

        return buffer.array();
    }


    @Test
    void validate_should_accept_minimal_melee_scenario() throws IOException {
        ChkValidatorUtils.validate(ChkFile.parseTolerant(createMeleeScenario(VERSION_REMASTERED_BROOD_WAR, new byte[0][])), false, true);
        ChkValidatorUtils.validate(ChkFile.parseTolerant(createMeleeScenario(VERSION_BROOD_WAR, new byte[0][])), false, true);
    }

    @Test
    void validate_should_reject_any_invalid_duplicate_of_a_handled_section() {
        // Every DIM section is dispatched; a garbage copy fails the load even when a valid one follows.
        byte[] scenario = createMeleeScenario(VERSION_BROOD_WAR,
                                              new byte[][]{createSection("DIM ", createShortArray(GARBAGE_WIDTH, GARBAGE_HEIGHT))});

        IOException rejection = Assertions.assertThrows(IOException.class,
                                                        () -> ChkValidatorUtils.validate(ChkFile.parseTolerant(scenario), false, true));

        Assertions.assertTrue(rejection.getMessage().contains("DIM"));
    }

    @Test
    void validate_should_relax_only_earlier_copies_in_compatibility_mode() throws IOException {
        // A garbage DIM before the real one: native rejects the map, the compatibility mode keeps the last copy.
        byte[] garbageFirst = createScenario(createSection("DIM ", createShortArray(GARBAGE_WIDTH, GARBAGE_HEIGHT)),
                                             createMeleeScenario(VERSION_BROOD_WAR, new byte[0][]));

        Assertions.assertThrows(IOException.class, () -> ChkValidatorUtils.validate(ChkFile.parseTolerant(garbageFirst), false, true));
        ChkValidatorUtils.validate(ChkFile.parseTolerant(garbageFirst), false, true, true);

        // The copy the game reads must still pass.
        byte[] garbageLast = createMeleeScenario(VERSION_BROOD_WAR,
                                                 new byte[][]{createSection("DIM ", createShortArray(GARBAGE_WIDTH, GARBAGE_HEIGHT))});

        Assertions.assertThrows(IOException.class, () -> ChkValidatorUtils.validate(ChkFile.parseTolerant(garbageLast), false, true, true));
    }

    @Test
    void validate_map_information_should_apply_map_list_rules() throws IOException {
        // The map list accepts STRx for every version but needs a loaded string table.
        ChkValidatorUtils.validateMapInformation(
                ChkFile.parseTolerant(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("STRx", new byte[]{0, 0, 0, 0})})),
                false
        );

        byte[] withoutStrings = createScenario(createSection("VER ", createShortArray(VERSION_BROOD_WAR)),
                                               createSection("DIM ", createShortArray(MAP_SIZE, MAP_SIZE)),
                                               createSection("ERA ", createShortArray(CLASSIC_TILESET)),
                                               createSection("OWNR", new byte[PLAYER_SECTION_SIZE]),
                                               createSection("SPRP", new byte[SCENARIO_PROPERTIES_SIZE]),
                                               createSection("MTXM", new byte[MAP_SIZE * MAP_SIZE * TILE_WORD_SIZE]),
                                               createSection("UNIT", new byte[0]),
                                               createSection("STR ", new byte[1]));

        IOException rejection = Assertions.assertThrows(IOException.class,
                                                        () -> ChkValidatorUtils.validateMapInformation(ChkFile.parseTolerant(withoutStrings), false));

        Assertions.assertTrue(rejection.getMessage().contains("string table"));

        byte[] oversized = createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("DIM ", createShortArray(OVERSIZED_WIDTH, MAP_SIZE))});

        Assertions.assertThrows(IOException.class, () -> ChkValidatorUtils.validateMapInformation(ChkFile.parseTolerant(oversized), true));
    }

    @Test
    void validate_should_ignore_unlisted_and_short_string_sections() throws IOException {
        // A negative-size ISOM section is never dispatched; a 1-byte STR is below the offset-table header.
        byte[] scenario = createMeleeScenario(VERSION_REMASTERED_BROOD_WAR, new byte[][]{
                createSection("STR ", new byte[1]),
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                          .put("ISOM".getBytes(StandardCharsets.US_ASCII)).putInt(-854297418).array()
        });

        ChkValidatorUtils.validate(ChkFile.parseTolerant(scenario), false, true);
    }

    @Test
    void validate_should_apply_section_handler_size_rules() {
        assertRejected(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("FORC", new byte[MAXIMUM_FORCE_SIZE + 1])}), "FORC");
        assertRejected(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("UNIT", new byte[UNIT_ENTRY_SIZE + 1])}), "UNIT");
        assertRejected(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("VCOD", new byte[VCOD_SIZE - 1])}), "VCOD");
        assertRejected(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("TRIG", new byte[TRIGGER_SIZE + 1])}), "TRIG");
        assertRejected(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("MTXM", new byte[MAXIMUM_TERRAIN_SIZE + 1])}), "MTXM");
        Assertions.assertDoesNotThrow(() -> ChkValidatorUtils.validate(
                ChkFile.parseTolerant(createMeleeScenario(VERSION_BROOD_WAR, new byte[][]{createSection("FORC", new byte[8])})),
                false, true
        ));
    }

    @Test
    void validate_should_require_expansion_game_for_expansion_tilesets() throws IOException {
        byte[] scenario = createMeleeScenario(VERSION_ORIGINAL, new byte[][]{createSection("ERA ", createShortArray(EXPANSION_TILESET))});

        Assertions.assertThrows(IOException.class, () -> ChkValidatorUtils.validate(ChkFile.parseTolerant(scenario), false, false));
        ChkValidatorUtils.validate(ChkFile.parseTolerant(scenario), false, true);
    }

    @Test
    void validate_should_reject_missing_required_sections_and_unknown_version() {
        assertRejected(createScenario(createSection("VER ", createShortArray(VERSION_BROOD_WAR))), "required");
        assertRejected(createMeleeScenario(UNKNOWN_VERSION, new byte[0][]), "version");
    }
}
