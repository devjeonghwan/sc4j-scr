package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Covers structured command descriptions for the opcodes {@link ReplayCommandExecutor} dispatches.
 */
final class ReplayCommandDescriptionTest {
    private static final int @NotNull [] HANDLED_FIXED_OPCODES = {
            ReplayCommandIds.KEEP_ALIVE, ReplayCommandIds.BUILD, ReplayCommandIds.VISION, ReplayCommandIds.ALLIANCE,
            ReplayCommandIds.CHEAT, ReplayCommandIds.HOTKEY, ReplayCommandIds.CANCEL_BUILD, ReplayCommandIds.CANCEL_MORPH,
            ReplayCommandIds.STOP, ReplayCommandIds.CARRIER_STOP, ReplayCommandIds.REAVER_STOP, ReplayCommandIds.RETURN_CARGO,
            ReplayCommandIds.TRAIN, ReplayCommandIds.CANCEL_TRAIN, ReplayCommandIds.CLOAK, ReplayCommandIds.DECLOAK,
            ReplayCommandIds.UNIT_MORPH, ReplayCommandIds.UNSIEGE, ReplayCommandIds.SIEGE, ReplayCommandIds.TRAIN_FIGHTER,
            ReplayCommandIds.UNLOAD_ALL, ReplayCommandIds.UNLOAD, ReplayCommandIds.MERGE_ARCHON, ReplayCommandIds.HOLD_POSITION,
            ReplayCommandIds.BURROW, ReplayCommandIds.UNBURROW, ReplayCommandIds.CANCEL_NUKE, ReplayCommandIds.LIFT_OFF,
            ReplayCommandIds.TECH, ReplayCommandIds.CANCEL_TECH, ReplayCommandIds.UPGRADE, ReplayCommandIds.CANCEL_UPGRADE,
            ReplayCommandIds.CANCEL_ADDON, ReplayCommandIds.BUILDING_MORPH, ReplayCommandIds.STIM, ReplayCommandIds.LEAVE_GAME,
            ReplayCommandIds.MINIMAP_PING, ReplayCommandIds.MERGE_DARK_ARCHON, ReplayCommandIds.CHAT, ReplayCommandIds.UNLOAD_121
    };

    /**
     * The cheat flags of Operation CWAL and Show Me The Money.
     */
    private static final int OPERATION_CWAL_AND_MONEY_CHEAT_FLAGS = 0x12;
    /**
     * A cheat flag without a cheat name.
     */
    private static final int UNNAMED_CHEAT_FLAG = 0x20;
    /**
     * The cheat flag of the stored cheat hash whose text is not known, and the cheat flag of the Noglues cheat.
     */
    private static final int UNIDENTIFIED_CHEAT_FLAG = 0x100000;
    private static final int NOGLUES_CHEAT_FLAG = 0x20000000;

    private static final @NotNull String CHAT_TEXT = "안녕 show me the money";
    private static final @NotNull String CHAT_CHARSET = "x-windows-949";

    @Test
    void describe_should_report_hotkey_recall_group() {
        ReplayCommandDescription description = ReplayCommandDescription.describe(new ReplayCommand(90, 0, ReplayCommandIds.HOTKEY, new byte[]{1, 3}));

        Assertions.assertEquals("Hotkey recall", description.getName());
        Assertions.assertEquals(List.of("Group 3"), description.getDetails());
    }

    @Test
    void describe_should_accept_every_handled_fixed_opcode_at_its_payload_length() {
        for (int opcode : HANDLED_FIXED_OPCODES) {
            int length = ReplayCommandDescription.getPayloadLength(opcode);

            Assertions.assertTrue(length >= 0, "length for " + opcode);

            ReplayCommandDescription description = ReplayCommandDescription.describe(new ReplayCommand(0, 0, opcode, new byte[length]));

            Assertions.assertFalse(description.getName().startsWith("Unsupported"), "opcode " + opcode);
            Assertions.assertFalse(description.getName().startsWith("Malformed"), "opcode " + opcode);
        }
    }

    @Test
    void describe_should_report_build_order_unit_type_and_tile() {
        byte[] payload = {6, 10, 0, 20, 1, (byte) 106, 0};
        ReplayCommandDescription description = ReplayCommandDescription.describe(new ReplayCommand(0, 0, ReplayCommandIds.BUILD, payload));

        Assertions.assertEquals("Build", description.getName());
        Assertions.assertEquals(List.of("Order 6, unit type 106", "Tile 10, 276"), description.getDetails());
    }

    @Test
    void describe_should_parse_selection_and_target_with_shared_parsers() {
        ReplayCommandDescription selection = ReplayCommandDescription.describe(new ReplayCommand(0, 0, ReplayCommandIds.SELECT, new byte[]{1, 5, 0}));

        Assertions.assertEquals("Select", selection.getName());
        Assertions.assertEquals(List.of("1 handles"), selection.getDetails());

        byte[] target = {32, 0, 64, 0, 0, 0, (byte) 0xE4, 0, 0};
        ReplayCommandDescription rightClick = ReplayCommandDescription.describe(new ReplayCommand(0, 0, ReplayCommandIds.RIGHT_CLICK, target));

        Assertions.assertEquals("Right click", rightClick.getName());
        Assertions.assertEquals("Position 32, 64", rightClick.getDetails().get(0));
    }

    @Test
    void describe_should_name_cheat_flags_but_keep_cheat_words_in_chat_as_text() {
        ReplayCommand cheat = new ReplayCommand(63, 0, ReplayCommandIds.CHEAT, new byte[]{OPERATION_CWAL_AND_MONEY_CHEAT_FLAGS, 0, 0, 0});

        Assertions.assertEquals(OPERATION_CWAL_AND_MONEY_CHEAT_FLAGS, ReplayCommandDescription.getCheatFlags(cheat));
        Assertions.assertEquals(List.of("operation cwal", "show me the money"), ReplayCommandDescription.describe(cheat).getDetails());
        Assertions.assertEquals(List.of("bit 0x20"), ReplayCommandDescription.getCheatNames(UNNAMED_CHEAT_FLAG));

        byte[] chat = new byte[1 + ReplayFile.CHAT_MESSAGE_SIZE];

        chat[0] = 1;

        byte[] text = CHAT_TEXT.getBytes(Charset.forName(CHAT_CHARSET));

        System.arraycopy(text, 0, chat, 1, text.length);

        ReplayCommandDescription description = ReplayCommandDescription.describe(new ReplayCommand(0, 1, ReplayCommandIds.CHAT, chat));

        Assertions.assertEquals("Chat", description.getName());
        Assertions.assertEquals(List.of("Sender 1", CHAT_TEXT), description.getDetails());
    }

    @Test
    void get_cheat_names_should_report_the_cheat_bit_without_a_known_text_as_hexadecimal() {
        Assertions.assertEquals(List.of("noglues", "bit 0x100000"),
                                ReplayCommandDescription.getCheatNames(UNIDENTIFIED_CHEAT_FLAG | NOGLUES_CHEAT_FLAG));
    }

    @Test
    void describe_should_report_unsupported_opcodes_and_malformed_payloads() {
        ReplayCommand unsupported = new ReplayCommand(0, 0, ReplayCommandIds.MAKE_GAME_PUBLIC, new byte[0]);

        Assertions.assertTrue(ReplayCommandDescription.describe(unsupported).getName().startsWith("Unsupported"));

        ReplayCommand malformed = new ReplayCommand(0, 0, ReplayCommandIds.HOTKEY, new byte[1]);

        Assertions.assertTrue(ReplayCommandDescription.describe(malformed).getName().startsWith("Malformed"));
    }
}
