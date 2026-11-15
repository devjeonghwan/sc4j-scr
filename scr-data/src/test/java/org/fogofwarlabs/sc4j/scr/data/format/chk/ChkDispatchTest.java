package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Tests bounded CHK traversal, repeated sections, string tables and THG2 routing.
 */
final class ChkDispatchTest {
    private static final int UNIT_TAG = 0x54494e55;
    private static final int THINGY_TAG = 0x32474854;

    private static final int SPRITE_FLAG = 0x1000;
    private static final int POST_CREATION_EFFECT_FLAG = 0x8000;
    private static final int NEUTRAL_OWNER = 11;
    private static final int FIRST_DOOR_TYPE = 205;
    private static final int LAST_DOOR_TYPE = 208;

    private static byte @NotNull [] createScenario(byte @NotNull [] @NotNull ... sections) {
        ByteBuffer content = ByteBuffer.allocate(Arrays.stream(sections).mapToInt(section -> section.length).sum());

        for (byte[] section : sections) {
            content.put(section);
        }

        return content.array();
    }

    private static byte @NotNull [] createSection(@NotNull String name, byte @NotNull [] payload) {
        return ByteBuffer.allocate(ChkFile.SECTION_HEADER_SIZE + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                         .put(name.getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).put(payload).array();
    }

    private static byte @NotNull [] createClassicStringTable() {
        String text = "classic";

        return ByteBuffer.allocate(4 + text.length() + 1).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 1).putShort((short) 4)
                         .put(text.getBytes(StandardCharsets.US_ASCII)).array();
    }

    private static byte @NotNull [] createExtendedStringTable(int @NotNull [] values) {
        String text = "extended";
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Integer.BYTES + text.length() + 1).order(ByteOrder.LITTLE_ENDIAN);

        for (int value : values) {
            buffer.putInt(value);
        }

        return buffer.put(text.getBytes(StandardCharsets.US_ASCII)).array();
    }


    @Test
    void parse_tolerant_should_index_header_before_testing_payload_extent() throws IOException {
        ChkFile file = ChkFile.parseTolerant(HexFormat.of().parseHex("554e495463000000"));

        Assertions.assertTrue(file.hasSection("UNIT"));
        Assertions.assertEquals(1, file.getSections().size());

        ChkSection section = file.getSections().getFirst();

        Assertions.assertEquals(0, section.getHeaderOffset());
        Assertions.assertEquals(8, section.getPayloadOffset());
        Assertions.assertEquals(99, section.getDeclaredSize());
        Assertions.assertFalse(section.hasCompletePayload());
        Assertions.assertThrows(IllegalStateException.class, file::getUnits);
        Assertions.assertThrows(IllegalStateException.class, () -> file.getSection("UNIT"));
    }

    @Test
    void parse_tolerant_should_follow_signed_backtracking_in_discovery_order() throws IOException {
        ByteBuffer content = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);

        content.putInt(0x504d554a).putInt(24);
        content.putInt(0x544e4157).putInt(0);
        content.putInt(0x4c494154).putInt(64);
        content.position(32);
        content.putInt(0x4b434142).putInt(-32);

        ChkFile file = ChkFile.parseTolerant(content.array());

        Assertions.assertEquals(List.of(0, 32, 8, 16), file.getSections().stream().map(ChkSection::getHeaderOffset).toList());
        Assertions.assertEquals(List.of("JUMP", "BACK", "WANT", "TAIL"), file.getSectionNames());
        Assertions.assertEquals(-32, file.getSections().get(1).getDeclaredSize());
        Assertions.assertEquals(0, file.requireSection("WANT").length);
        Assertions.assertThrows(IllegalStateException.class, () -> file.requireSection("BACK"));
        Assertions.assertThrows(IOException.class, () -> ChkFile.parse(content.array()));
    }

    @Test
    void parse_tolerant_should_retain_oversized_unknown_tail_and_ignore_short_header_tail() throws IOException {
        ChkFile oversized = ChkFile.parseTolerant(HexFormat.of().parseHex("554e4954000000004a554e4b63000000"));

        Assertions.assertEquals(List.of(0, 8), oversized.getSections().stream().map(ChkSection::getHeaderOffset).toList());
        Assertions.assertEquals(0, oversized.getUnits().size());
        Assertions.assertFalse(oversized.getSections().getLast().hasCompletePayload());

        ChkFile shortTail = ChkFile.parseTolerant(HexFormat.of().parseHex("554e495400000000616263"));

        Assertions.assertEquals(1, shortTail.getSections().size());
        Assertions.assertEquals(0, shortTail.getUnits().size());
    }

    @Test
    void parse_tolerant_should_stop_at_cycles_and_headers_before_the_buffer() throws IOException {
        // The original loops forever on a cycle and reads heap memory for a header before the buffer; both stop here.
        ChkFile cycle = ChkFile.parseTolerant(HexFormat.of().parseHex("4c4f4f50f8ffffff"));

        Assertions.assertEquals(1, cycle.getSections().size());

        for (int nextHeaderOffset = -8; nextHeaderOffset < 0; nextHeaderOffset++) {
            byte[] content = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4b434142).putInt(nextHeaderOffset - 8).array();

            Assertions.assertEquals(1, ChkFile.parseTolerant(content).getSections().size());
        }
    }

    @Test
    void get_strings_should_select_last_accepted_string_section_with_strx_after_str() throws IOException {
        // Version 206 dispatches STR sections, then STRx sections; a section below its offset-table header is ignored.
        byte[] remasteredVersion = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                                             .putShort((short) ChkFile.VERSION_REMASTERED_BROOD_WAR).array();

        byte[] strings = createScenario(createSection("VER ", remasteredVersion),
                                        createSection("STR ", createClassicStringTable()),
                                        createSection("STRx", createExtendedStringTable(new int[]{1, 8})),
                                        createSection("STRx", new byte[3]));

        Assertions.assertEquals(List.of("extended"), ChkFile.parseTolerant(strings).getStrings());

        // Version 205 has no STRx in its section lists.
        byte[] classicVersion = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort((short) ChkFile.VERSION_BROOD_WAR).array();

        byte[] classic = createScenario(createSection("VER ", classicVersion), createSection("STR ", createClassicStringTable()),
                                        createSection("STRx", createExtendedStringTable(new int[]{1, 8})));

        Assertions.assertEquals(List.of("classic"), ChkFile.parseTolerant(classic).getStrings());

        // No string section: an empty table instead of a failure.
        List<String> expectedStrings = List.of();
        byte[] emptyStringsVersion = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                                               .putShort((short) ChkFile.VERSION_REMASTERED_BROOD_WAR).array();

        Assertions.assertEquals(expectedStrings, ChkFile.parseTolerant(createScenario(createSection("VER ", emptyStringsVersion))).getStrings());
    }

    @Test
    void get_strings_should_stop_at_last_offset_entry_inside_the_section() throws IOException {
        // The count claims 1000 strings. The 9-byte section fits three offset entries: 6, 99 (past the section) and the
        // bytes "ab" read as offset 0x6261 (past the section); later entries would end outside it.
        ByteBuffer table = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);

        table.putShort((short) 1000).putShort((short) 6).putShort((short) 99).put("ab".getBytes()).put((byte) 0);

        Assertions.assertEquals(List.of("ab", "", ""), ChkFile.parseTolerant(createScenario(createSection("STR ", table.array()))).getStrings());
    }

    @Test
    void parse_tolerant_should_stop_when_next_header_end_falls_below_buffer_lower_bound() throws IOException {
        ChkFile file = ChkFile.parseTolerant(HexFormat.of().parseHex("4241434befffffff"));

        Assertions.assertEquals(1, file.getSections().size());
        Assertions.assertEquals(-17, file.getSections().getFirst().getDeclaredSize());
        Assertions.assertFalse(file.getSections().getFirst().hasCompletePayload());
    }

    @Test
    void get_units_should_decode_every_unit_section_and_reject_incomplete_records() throws IOException {
        ByteBuffer content = ByteBuffer.allocate(44 * 2 + 8).order(ByteOrder.LITTLE_ENDIAN);

        content.putInt(UNIT_TAG).putInt(ChkUnit.ENTRY_SIZE).putInt(7);
        content.position(44);
        content.putInt(THINGY_TAG).putInt(0);
        content.putInt(UNIT_TAG).putInt(ChkUnit.ENTRY_SIZE).putInt(9);

        ChkFile file = ChkFile.parseTolerant(content.array());

        Assertions.assertEquals(List.of(0, 52), file.getSections("UNIT").stream().map(ChkSection::getHeaderOffset).toList());
        Assertions.assertEquals(List.of(7L, 9L), file.getUnits().stream().map(ChkUnit::getSerial).toList());
        Assertions.assertEquals(9, ByteBuffer.wrap(file.requireSection("UNIT")).order(ByteOrder.LITTLE_ENDIAN).getInt());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> file.getSections().clear());

        ChkFile malformed = ChkFile.parseTolerant(HexFormat.of().parseHex("554e49540100000000"));

        Assertions.assertThrows(IllegalStateException.class, malformed::getUnits);
    }

    @Test
    void get_thingies_should_decode_repeated_sections_without_applying_creation_outcomes() throws IOException {
        ByteBuffer content = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);

        content.putInt(THINGY_TAG).putInt(ChkThingy.ENTRY_SIZE).putShort((short) 207).putShort((short) 0xffff).putShort((short) 0x8000);
        content.put((byte) 255).put((byte) 0xff).putShort((short) 0x8000);
        content.putInt(THINGY_TAG).putInt(ChkThingy.ENTRY_SIZE).putShort((short) 208).putShort((short) 2).putShort((short) 3);
        content.put((byte) 255).put((byte) 0).putShort((short) 0x9000);

        List<ChkThingy> thingies = ChkFile.parseTolerant(content.array()).getThingies();

        Assertions.assertEquals(List.of(8, 26), thingies.stream().map(ChkThingy::getSourceOffset).toList());

        ChkThingy unit = thingies.getFirst();

        Assertions.assertEquals(207, unit.getTypeIdentifier());
        Assertions.assertEquals(65535, unit.getHorizontal());
        Assertions.assertEquals(32768, unit.getVertical());
        Assertions.assertEquals(255, unit.getOwner());
        Assertions.assertEquals(11, unit.getCreationOwner());
        Assertions.assertTrue(unit.requestsUnitCreation(false));
        Assertions.assertTrue(unit.hasPostCreationEffect());

        ChkThingy sprite = thingies.getLast();

        Assertions.assertTrue(sprite.isSprite());
        Assertions.assertEquals(255, sprite.getCreationOwner());
        Assertions.assertFalse(sprite.requestsUnitCreation(true));
        Assertions.assertFalse(sprite.hasPostCreationEffect());

        ChkFile malformed = ChkFile.parseTolerant(HexFormat.of().parseHex("544847320100000000"));

        Assertions.assertThrows(IllegalStateException.class, malformed::getThingies);
    }

    @Test
    void thingy_creation_owner_and_unit_request_follow_type_owner_and_flag_gates() {
        for (int typeIdentifier : new int[]{0, 204, 205, 206, 207, 208, 209, 0xffff}) {
            for (int owner : new int[]{0, 10, 11, 12, 255}) {
                for (int flags : new int[]{0, 0x1000, 0x8000, 0x9000}) {
                    ChkThingy thingy = new ChkThingy(8, typeIdentifier, 65535, 32768, owner, flags);
                    boolean sprite = (flags & SPRITE_FLAG) != 0;
                    int creationOwner = !sprite && typeIdentifier >= FIRST_DOOR_TYPE && typeIdentifier <= LAST_DOOR_TYPE ? NEUTRAL_OWNER : owner;

                    Assertions.assertEquals(creationOwner, thingy.getCreationOwner());
                    Assertions.assertEquals(!sprite, thingy.requestsUnitCreation(true));
                    Assertions.assertEquals(!sprite && creationOwner == NEUTRAL_OWNER, thingy.requestsUnitCreation(false));
                    Assertions.assertEquals(!sprite && (flags & POST_CREATION_EFFECT_FLAG) != 0, thingy.hasPostCreationEffect());
                }
            }
        }
    }
}
