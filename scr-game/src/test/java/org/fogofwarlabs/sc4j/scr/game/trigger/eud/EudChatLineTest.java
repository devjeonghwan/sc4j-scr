package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerActionIds;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerConditionIds;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerModifiers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks that the triggers of a Use Map Settings network game recognize a typed chat answer by comparing the chat line memory with the sender's
 * name in the player table memory, for ASCII and Korean names, and that a wrong answer is not recognized.
 */
@RequiresInstallation
final class EudChatLineTest {
    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String TRIGGER_SECTION = "TRIG";
    private static final int SEED = 0x1234;

    private static final int LOCAL_SLOT = 0;
    private static final int REMOTE_SLOT = 1;
    private static final int MELEE_FORCE = 1;

    /**
     * The classic address of the player table and the size of its records.
     */
    private static final int PLAYER_TABLE = 0x57EEE0;
    /**
     * The classic address of the message lines and the size of a line.
     */
    private static final int MESSAGE_LINES = 0x640B60;
    /**
     * The classic address of the death table, the base of the extended Deaths operands.
     */
    private static final int DEATH_TABLE = 0x58A364;
    /**
     * The lines the three chat messages of a game take in turn: the first message of a game writes line 10, the next ones lines 0 and 1.
     */
    private static final int FIRST_MESSAGE_LINE = 10;
    private static final int SECOND_MESSAGE_LINE = 0;
    private static final int THIRD_MESSAGE_LINE = 1;

    /**
     * The color code a chat line holds after the colon: the local player's own text and another player's text.
     */
    private static final byte OWN_TEXT_CODE = 7;
    private static final byte OTHER_TEXT_CODE = 2;

    /**
     * The unit types whose deaths of player 1 the three triggers set: the wrong answer, the received answer and the own answer.
     */
    private static final int WRONG_ANSWER_UNIT = 0;
    private static final int RECEIVED_ANSWER_UNIT = 1;
    private static final int OWN_ANSWER_UNIT = 2;

    private static final int TRIGGER_SIZE = 2400;
    private static final int CONDITION_COUNT = 16;
    private static final int CONDITION_SIZE = 20;
    private static final int CONDITION_MASK_OFFSET = 0;
    private static final int CONDITION_PLAYER_OFFSET = 4;
    private static final int CONDITION_AMOUNT_OFFSET = 8;
    private static final int CONDITION_COMPARISON_OFFSET = 14;
    private static final int CONDITION_TYPE_OFFSET = 15;
    private static final int CONDITION_EXTENSION_OFFSET = 18;
    private static final int ACTION_PLAYER_OFFSET = 16;
    private static final int ACTION_AMOUNT_OFFSET = 20;
    private static final int ACTION_UNIT_OFFSET = 24;
    private static final int ACTION_TYPE_OFFSET = 26;
    private static final int ACTION_MODIFIER_OFFSET = 27;
    private static final int FIRST_PLAYER_EXECUTION_OFFSET = 0x944;
    /**
     * The {@code 'S','C'} extension WORD that masks an extended Deaths condition by its location DWORD.
     */
    private static final short MASK_MARKER = 0x4353;
    private static final int BYTE_MASK = 0xFF;

    /**
     * The frames before the first message, between the messages and after the last one; the lines stay shown for 166 frames.
     */
    private static final int START_FRAMES = 10;
    private static final int MESSAGE_FRAMES = 4;
    private static final int CHECK_FRAMES = 120;

    /**
     * Writes masked Exactly conditions on the extended Deaths operands that cover a byte string at a classic address.
     *
     * @param record   the trigger record
     * @param first    the index of the first condition
     * @param address  the classic address of the string
     * @param expected the string bytes, terminator included
     * @return the index after the last written condition
     */
    private static int writeBytesConditions(@NotNull ByteBuffer record, int first, int address, byte @NotNull [] expected) {
        int index = first;
        int aligned = address & -Integer.BYTES;

        for (int dword = aligned; dword < address + expected.length; dword += Integer.BYTES) {
            int mask = 0;
            int amount = 0;

            for (int part = 0; part < Integer.BYTES; part++) {
                int position = dword + part - address;

                if (position >= 0 && position < expected.length) {
                    mask |= BYTE_MASK << part * Byte.SIZE;
                    amount |= (expected[position] & BYTE_MASK) << part * Byte.SIZE;
                }
            }

            int base = index * CONDITION_SIZE;

            record.putInt(base + CONDITION_MASK_OFFSET, mask);
            record.putInt(base + CONDITION_PLAYER_OFFSET, (dword - DEATH_TABLE) / Integer.BYTES);
            record.putInt(base + CONDITION_AMOUNT_OFFSET, amount);
            record.put(base + CONDITION_COMPARISON_OFFSET, (byte) TriggerModifiers.EXACTLY);
            record.put(base + CONDITION_TYPE_OFFSET, (byte) TriggerConditionIds.DEATHS);
            record.putShort(base + CONDITION_EXTENSION_OFFSET, MASK_MARKER);
            index++;
        }

        return index;
    }

    /**
     * Creates a trigger of player 1 that sets player 1's deaths of a unit type to 1 when the line holds the sender's chat answer and the sender's
     * player table record holds the sender's name.
     *
     * @param line   the line index
     * @param sender the sender's slot
     * @param name   the sender's name bytes
     * @param code   the color code after the colon
     * @param answer the answer bytes
     * @param unit   the unit type of the deaths
     * @return the trigger record
     */
    private static byte @NotNull [] createAnswerTrigger(int line, int sender, byte @NotNull [] name, byte code, byte @NotNull [] answer, int unit) {
        byte[] trigger = new byte[TRIGGER_SIZE];
        ByteBuffer record = ByteBuffer.wrap(trigger).order(ByteOrder.LITTLE_ENDIAN);
        int nameAddress = PLAYER_TABLE + sender * ReplayHeaderBuilder.PLAYER_RECORD_SIZE + ReplayHeaderBuilder.PLAYER_NAME_OFFSET;
        int next = writeBytesConditions(record, 0, nameAddress, terminate(name));

        writeBytesConditions(record, next, MESSAGE_LINES + line * MessageLines.LINE_SIZE, terminate(formatChatLine(name, code, answer)));

        int action = CONDITION_COUNT * CONDITION_SIZE;

        record.putInt(action + ACTION_PLAYER_OFFSET, 0);
        record.putInt(action + ACTION_AMOUNT_OFFSET, 1);
        record.putShort(action + ACTION_UNIT_OFFSET, (short) unit);
        record.put(action + ACTION_TYPE_OFFSET, (byte) TriggerActionIds.SET_DEATHS);
        record.put(action + ACTION_MODIFIER_OFFSET, (byte) TriggerModifiers.SET_TO);
        trigger[FIRST_PLAYER_EXECUTION_OFFSET] = 1;

        return trigger;
    }

    private static byte @NotNull [] formatChatLine(byte @NotNull [] name, byte code, byte @NotNull [] text) {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        line.writeBytes(name);
        line.write(':');
        line.write(code);
        line.write(' ');
        line.writeBytes(text);

        return line.toByteArray();
    }

    private static byte @NotNull [] terminate(byte @NotNull [] text) {
        byte[] terminated = new byte[text.length + 1];

        System.arraycopy(text, 0, terminated, 0, text.length);

        return terminated;
    }

    /**
     * Appends a TRIG section with the given trigger records to a scenario.
     *
     * @param scenario the scenario bytes
     * @param triggers the trigger records
     * @return the scenario with the triggers
     */
    private static byte @NotNull [] appendTriggers(byte @NotNull [] scenario, byte @NotNull [] @NotNull ... triggers) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuffer size = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        output.writeBytes(scenario);
        output.writeBytes(TRIGGER_SECTION.getBytes(StandardCharsets.US_ASCII));
        output.writeBytes(size.putInt(0, triggers.length * TRIGGER_SIZE).array());

        for (byte[] trigger : triggers) {
            output.writeBytes(trigger);
        }

        return output.toByteArray();
    }

    /**
     * Creates the Use Map Settings network setup of two humans: the local player in slot 0 and another player in slot 1.
     *
     * @param mapData    the scenario bytes
     * @param localName  the local player's name
     * @param remoteName the other player's name
     * @return the created setup
     * @throws IOException if the scenario cannot be read
     */
    private static @NotNull MeleeGameSetup createSetup(byte @NotNull [] mapData, @NotNull String localName, @NotNull String remoteName) throws IOException {
        LobbyResolution.Table table = new LobbyResolution.Table();

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            table.type[slot]         = LobbyResolution.TYPE_CLOSED;
            table.templateType[slot] = LobbyResolution.TYPE_OPEN;
            table.savedType[slot]    = LobbyResolution.TYPE_OPEN;
            table.race[slot]         = ScrRaceIds.TERRAN;
            table.templateRace[slot] = ScrRaceIds.TERRAN;
            table.team[slot]         = MELEE_FORCE;
            table.templateTeam[slot] = MELEE_FORCE;
            table.lobbyColor[slot]   = slot;
        }

        table.playerId[LOCAL_SLOT]  = LOCAL_SLOT;
        table.type[LOCAL_SLOT]      = MeleeGameSetup.TYPE_HUMAN;
        table.name[LOCAL_SLOT]      = localName;
        table.playerId[REMOTE_SLOT] = REMOTE_SLOT;
        table.type[REMOTE_SLOT]     = MeleeGameSetup.TYPE_HUMAN;
        table.name[REMOTE_SLOT]     = remoteName;

        return MeleeGameSetup.createNetwork(mapData, localName, table, new LobbyResolution.Options(true, true, 0, true, false),
                                            GameTemplate.USE_MAP_SETTINGS, GameSpeed.FASTEST, true);
    }

    private static void stepFrames(@NotNull ReplayRuntime runtime, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            LiveMapGameScenario.stepFrame(runtime, null);
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {"tester,joiner,apple", "한국닉,정답자,사과"})
    void chat_answer_triggers_compare_the_line_with_the_player_table_name(@NotNull String names, @TempDir @NotNull Path folder) throws IOException {
        String[] parts = names.split(",");
        byte[] localName = parts[0].getBytes(StandardCharsets.UTF_8);
        byte[] remoteName = parts[1].getBytes(StandardCharsets.UTF_8);
        byte[] answer = parts[2].getBytes(StandardCharsets.UTF_8);
        byte[] wrong = "x".getBytes(StandardCharsets.UTF_8);
        Path installedMap = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);
        byte[] mapData = appendTriggers(MpqArchive.open(installedMap).readFile(SCENARIO_FILE),
                                        createAnswerTrigger(FIRST_MESSAGE_LINE, LOCAL_SLOT, localName, OWN_TEXT_CODE, answer, WRONG_ANSWER_UNIT),
                                        createAnswerTrigger(SECOND_MESSAGE_LINE, REMOTE_SLOT, remoteName, OTHER_TEXT_CODE, answer, RECEIVED_ANSWER_UNIT),
                                        createAnswerTrigger(THIRD_MESSAGE_LINE, LOCAL_SLOT, localName, OWN_TEXT_CODE, answer, OWN_ANSWER_UNIT));
        Path mapPath = folder.resolve("chat-answer.chk");

        Files.write(mapPath, mapData);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LiveGame live = new LiveGame(createSetup(mapData, parts[0], parts[1]), SEED, LOCAL_SLOT);
            ReplayRuntime runtime = LiveMapGameScenario.startLive(gameData, live, mapPath);
            MessageLines lines = runtime.getGame().getInterfaceLink().getMessageLines();

            stepFrames(runtime, START_FRAMES);
            // The local player's wrong answer, another player's received answer and the local player's answer, as the chat input and the network
            // chat receiver write them.
            lines.writeChat(LOCAL_SLOT, wrong);
            stepFrames(runtime, MESSAGE_FRAMES);
            lines.writeChat(REMOTE_SLOT, answer);
            stepFrames(runtime, MESSAGE_FRAMES);
            lines.writeChat(LOCAL_SLOT, answer);
            stepFrames(runtime, CHECK_FRAMES);

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertArrayEquals(formatChatLine(localName, OWN_TEXT_CODE, wrong), lines.getLine(FIRST_MESSAGE_LINE));
            Assertions.assertArrayEquals(formatChatLine(remoteName, OTHER_TEXT_CODE, answer), lines.getLine(SECOND_MESSAGE_LINE));
            Assertions.assertArrayEquals(formatChatLine(localName, OWN_TEXT_CODE, answer), lines.getLine(THIRD_MESSAGE_LINE));

            UnitAccounting accounting = runtime.getGame().getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(accounting);
            Assertions.assertEquals(0, accounting.getScores().getDeaths(LOCAL_SLOT, WRONG_ANSWER_UNIT), "A wrong answer is not recognized.");
            Assertions.assertEquals(1, accounting.getScores().getDeaths(LOCAL_SLOT, RECEIVED_ANSWER_UNIT),
                                    "Another player's received answer is recognized by the sender's name.");
            Assertions.assertEquals(1, accounting.getScores().getDeaths(LOCAL_SLOT, OWN_ANSWER_UNIT),
                                    "The local player's answer is recognized by the local player's name.");
        }
    }
}
