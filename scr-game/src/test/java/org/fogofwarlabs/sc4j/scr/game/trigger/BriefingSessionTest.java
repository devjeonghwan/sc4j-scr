package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.trigger.BriefingSession.SlotAnimation;
import org.fogofwarlabs.sc4j.scr.game.trigger.BriefingSession.SoundEvent;
import org.fogofwarlabs.sc4j.scr.game.trigger.BriefingSession.TextLayout;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks briefing actions, timers, portraits and text presentation.
 */
final class BriefingSessionTest {
    private static final int UNITS_DAT_SIZE = 19876;

    private static final int PORTRAIT_FIXTURE_UNIT = ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE;
    private static final int PORTRAIT_FIXTURE_IDENTIFIER = 42;

    private static final int LOCAL_PLAYER = 0;

    private static final int CAMPAIGN_MISSION = 1;
    private static final @NotNull String CAMPAIGN_DIRECTORY = "campaign\\terran\\terran01";

    private static final int SECTION_NAME_LENGTH = 4;
    private static final int SECTION_HEADER_SIZE = SECTION_NAME_LENGTH + Integer.BYTES;
    private static final int STRING_COUNT_SIZE = Short.BYTES;
    private static final int STRING_OFFSET_SIZE = Short.BYTES;

    /**
     * Encodes ordered briefing conditions, owners and action fields.
     */
    private static final class RecordBuilder {
        private final byte @NotNull [] bytes;
        private int actionCount;

        private RecordBuilder(int conditionType, int @NotNull ... owners) {
            this.bytes       = new byte[TriggerRecordCodec.RECORD_SIZE];
            this.actionCount = 0;

            this.bytes[TriggerRecordCodec.CONDITION_TYPE_FIELD] = (byte) conditionType;

            for (int owner : owners) {
                this.bytes[TriggerRecordCodec.OWNERS_OFFSET + owner] = 1;
            }
        }

        private @NotNull RecordBuilder action(int type,
                                              int slot,
                                              int stringIndex, int soundIndex,
                                              int duration,
                                              int unitType,
                                              int modifier, int secondary) {
            int position = TriggerRecordCodec.ACTIONS_OFFSET + this.actionCount++ * TriggerRecordCodec.ACTION_SIZE;

            writeInteger(this.bytes, position + TriggerRecordCodec.ACTION_STRING_FIELD, stringIndex);
            writeInteger(this.bytes, position + TriggerRecordCodec.ACTION_SOUND_FIELD, soundIndex);
            writeInteger(this.bytes, position + TriggerRecordCodec.ACTION_DURATION_FIELD, duration);
            writeInteger(this.bytes, position + TriggerRecordCodec.ACTION_PLAYER_FIELD, slot);
            writeInteger(this.bytes, position + TriggerRecordCodec.ACTION_SECONDARY_FIELD, secondary);
            this.bytes[position + TriggerRecordCodec.ACTION_UNIT_FIELD]     = (byte) unitType;
            this.bytes[position + TriggerRecordCodec.ACTION_UNIT_FIELD + 1] = (byte) (unitType >>> Byte.SIZE);
            this.bytes[position + TriggerRecordCodec.ACTION_TYPE_FIELD]     = (byte) type;
            this.bytes[position + TriggerRecordCodec.ACTION_MODIFIER_FIELD] = (byte) modifier;

            return this;
        }

        private @NotNull RecordBuilder text(int stringIndex, int duration) {
            return this.action(BriefingActionIds.TEXT_MESSAGE, 0, stringIndex, 0, duration, 0, 0, 0);
        }

        private @NotNull RecordBuilder waitFor(int duration) {
            return this.action(BriefingActionIds.WAIT, 0, 0, 0, duration, 0, 0, 0);
        }

        private @NotNull RecordBuilder show(int slot) {
            return this.action(BriefingActionIds.SHOW_PORTRAIT, slot, 0, 0, 0, PORTRAIT_FIXTURE_UNIT, 0, 0);
        }

        private @NotNull RecordBuilder hide(int slot) {
            return this.action(BriefingActionIds.HIDE_PORTRAIT, slot, 0, 0, 0, 0, 0, 0);
        }

        private @NotNull RecordBuilder speak(int slot, int duration) {
            return this.action(BriefingActionIds.DISPLAY_SPEAKING_PORTRAIT, slot, 0, 0, duration, 0, 0, 0);
        }
    }

    private static void runHostTimeRange(@NotNull BriefingSession session, int startHostTimeMilliseconds, int endHostTimeMilliseconds) {
        for (int hostTimeMilliseconds = startHostTimeMilliseconds; hostTimeMilliseconds <= endHostTimeMilliseconds; hostTimeMilliseconds++) {
            session.update(hostTimeMilliseconds);
        }
    }

    private static @NotNull BriefingSession createSession(@NotNull String @NotNull [] strings, @NotNull RecordBuilder @NotNull ... records) throws IOException {
        return BriefingSession.create(createMap(strings, records),
                                      UnitsDat.createTypeTable(createUnits()),
                                      createPlayers(),
                                      LOCAL_PLAYER,
                                      0,
                                      "",
                                      false,
                                      null,
                                      0);
    }

    private static @NotNull ChkFile createMap(@NotNull String @NotNull [] strings, @NotNull RecordBuilder @NotNull ... records) throws IOException {
        ByteArrayOutputStream recordData = new ByteArrayOutputStream();

        for (RecordBuilder record : records) {
            recordData.writeBytes(record.bytes);
        }

        ByteArrayOutputStream stringData = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        int base = STRING_COUNT_SIZE + strings.length * STRING_OFFSET_SIZE;

        for (String string : strings) {
            offsets.add(base + stringData.size());
            stringData.writeBytes(string.getBytes(StandardCharsets.US_ASCII));
            stringData.write(0);
        }

        byte[] stringSection = new byte[base + stringData.size()];

        stringSection[0] = (byte) strings.length;

        for (int index = 0; index < offsets.size(); index++) {
            stringSection[STRING_COUNT_SIZE + index * STRING_OFFSET_SIZE]     = (byte) (int) offsets.get(index);
            stringSection[STRING_COUNT_SIZE + index * STRING_OFFSET_SIZE + 1] = (byte) (offsets.get(index) >>> Byte.SIZE);
        }

        System.arraycopy(stringData.toByteArray(), 0, stringSection, base, stringData.size());

        ByteArrayOutputStream file = new ByteArrayOutputStream();

        file.writeBytes(createSection("STR ", stringSection));
        file.writeBytes(createSection("MBRF", recordData.toByteArray()));

        return ChkFile.parse(file.toByteArray());
    }

    private static byte @NotNull [] createSection(@NotNull String name, byte @NotNull [] payload) {
        byte[] bytes = new byte[SECTION_HEADER_SIZE + payload.length];

        System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, SECTION_NAME_LENGTH);
        writeInteger(bytes, SECTION_NAME_LENGTH, payload.length);
        System.arraycopy(payload, 0, bytes, SECTION_HEADER_SIZE, payload.length);

        return bytes;
    }

    private static @NotNull DatTable createUnits() throws IOException {
        DatTable units = UnitsDat.createSchema().parse(new byte[UNITS_DAT_SIZE]);
        int[] portraits = new int[UnitsDat.UNIT_COUNT];

        portraits[PORTRAIT_FIXTURE_UNIT] = PORTRAIT_FIXTURE_IDENTIFIER;

        return units.createWithColumns(Map.of("Portrait", portraits));
    }

    private static @NotNull Player @NotNull [] createPlayers() {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int slot = 0; slot < players.length; slot++) {
            players[slot] = new Player(slot, slot, PlayerTypes.HUMAN, 1, 1);
        }

        return players;
    }

    private static void writeInteger(byte @NotNull [] bytes, int offset, int value) {
        for (int index = 0; index < Integer.BYTES; index++) {
            bytes[offset + index] = (byte) (value >>> (index * Byte.SIZE));
        }
    }


    @Test
    void wait_holds_briefing_until_its_host_timer_fires() throws IOException {
        BriefingSession session = createSession(new String[]{"after"},
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).waitFor(500).text(1, 1000));

        // The step first fires at 101; its 500 ms wait remains pending through 601.
        runHostTimeRange(session, 1, 101);

        Assertions.assertTrue(session.isWaiting());

        runHostTimeRange(session, 102, 601);

        Assertions.assertTrue(session.isWaiting());

        runHostTimeRange(session, 602, 602);

        Assertions.assertFalse(session.isWaiting());

        // The steps run at 101 + 101 * n; the resumed Wait succeeds at 606 and the text follows in the same step.
        runHostTimeRange(session, 603, 605);

        Assertions.assertNull(session.getTransmissionText());
        Assertions.assertFalse(session.update(606));
        Assertions.assertEquals("after", session.getTransmissionText());
        Assertions.assertEquals(606, session.getTransmissionTextStartMillis());
        Assertions.assertEquals(1000, session.getTransmissionTextDuration());

        runHostTimeRange(session, 607, 1606);

        Assertions.assertEquals("after", session.getTransmissionText());

        runHostTimeRange(session, 1607, 1607);

        Assertions.assertNull(session.getTransmissionText());
    }

    @Test
    void due_timers_fire_in_due_order_within_one_update() throws IOException {
        BriefingSession late = createSession(new String[]{"after"},
                                             new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).waitFor(500).text(1, 1000));

        late.update(101);
        // The step (due 201) precedes the wait (due 601): the step still sees the wait, then the wait ends.
        late.update(1000);

        Assertions.assertFalse(late.isWaiting());
        Assertions.assertNull(late.getTransmissionText());

        late.update(1101);

        Assertions.assertEquals("after", late.getTransmissionText());

        BriefingSession early = createSession(new String[]{"after"},
                                              new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).waitFor(50).text(1, 1000));

        early.update(101);
        // The wait (due 151) precedes the step (due 201): the step resumes the Wait in the same pump.
        early.update(300);

        Assertions.assertEquals("after", early.getTransmissionText());
    }

    @Test
    void records_without_mission_briefing_first_condition_finish_without_actions() throws IOException {
        BriefingSession session = createSession(new String[]{"skipped", "shown"},
                                                new RecordBuilder(TriggerConditionIds.ALWAYS, LOCAL_PLAYER).text(1, 1000),
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).text(2, 1000));

        Assertions.assertTrue(session.update(101));
        Assertions.assertNull(session.getTransmissionText());
        Assertions.assertFalse(session.update(202));
        Assertions.assertEquals("shown", session.getTransmissionText());
    }

    @Test
    void portraits_show_speak_and_hide_on_schedule() throws IOException {
        BriefingSession session = createSession(new String[0],
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).show(1)
                                                                                                                     .speak(1, 1000)
                                                                                                                     .waitFor(2000)
                                                                                                                     .hide(1));

        Assertions.assertFalse(session.isSlotVisible(1));
        Assertions.assertEquals(-1, session.getSlotUnit(1));

        runHostTimeRange(session, 1, 101);

        Assertions.assertTrue(session.isSlotVisible(1));
        Assertions.assertEquals(PORTRAIT_FIXTURE_UNIT, session.getSlotUnit(1));
        Assertions.assertEquals(PORTRAIT_FIXTURE_IDENTIFIER, session.getSlotPortrait(1));
        Assertions.assertTrue(session.isSlotSpeaking(1));
        Assertions.assertEquals(SlotAnimation.TALKING, session.getSlotAnimation(1));
        Assertions.assertFalse(session.isSlotVisible(0));

        runHostTimeRange(session, 102, 1101);

        Assertions.assertTrue(session.isSlotSpeaking(1));

        runHostTimeRange(session, 1102, 1102);

        Assertions.assertFalse(session.isSlotSpeaking(1));
        Assertions.assertEquals(SlotAnimation.IDLE, session.getSlotAnimation(1));

        // The wait ends after 2101; the next step at 101 + 101 * 20 = 2121 hides the slot.
        runHostTimeRange(session, 1103, 2120);

        Assertions.assertTrue(session.isSlotVisible(1));

        runHostTimeRange(session, 2121, 2121);

        Assertions.assertFalse(session.isSlotVisible(1));
        Assertions.assertEquals(PORTRAIT_FIXTURE_IDENTIFIER, session.getSlotPortrait(1));
    }

    @Test
    void speaking_on_hidden_slot_lasts_until_show_clears_it() throws IOException {
        BriefingSession session = createSession(new String[0],
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).speak(0, 100)
                                                                                                                     .waitFor(500)
                                                                                                                     .show(0));

        runHostTimeRange(session, 1, 101);

        Assertions.assertTrue(session.isSlotSpeaking(0));
        Assertions.assertFalse(session.isSlotVisible(0));

        runHostTimeRange(session, 102, 605);

        Assertions.assertTrue(session.isSlotSpeaking(0));

        runHostTimeRange(session, 606, 606);

        Assertions.assertTrue(session.isSlotVisible(0));
        Assertions.assertFalse(session.isSlotSpeaking(0));

        runHostTimeRange(session, 607, 1000);

        Assertions.assertFalse(session.isSlotSpeaking(0));
    }

    @Test
    void transmission_shows_text_for_its_duration_and_waits_for_modified_duration() throws IOException {
        RecordBuilder record = new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER)
                .show(0)
                .action(BriefingActionIds.TRANSMISSION, 0, 1, 2, 3000, 0, TriggerModifiers.ADD, 500)
                .text(3, 100);
        BriefingSession session = BriefingSession.create(createMap(new String[]{"Hello", "tx.wav", "done"}, record),
                                                         UnitsDat.createTypeTable(createUnits()),
                                                         createPlayers(),
                                                         LOCAL_PLAYER,
                                                         CAMPAIGN_MISSION,
                                                         CAMPAIGN_DIRECTORY,
                                                         false,
                                                         null,
                                                         0);

        List<SoundEvent> initial = session.pollSoundEvents();

        Assertions.assertEquals(1, initial.size());
        Assertions.assertTrue(initial.getFirst().isStop());

        runHostTimeRange(session, 1, 101);

        List<SoundEvent> sounds = session.pollSoundEvents();

        Assertions.assertEquals(1, sounds.size());
        Assertions.assertFalse(sounds.getFirst().isStop());
        Assertions.assertTrue(sounds.getFirst().isTransmission());
        Assertions.assertEquals(CAMPAIGN_DIRECTORY + "\\tx.wav", sounds.getFirst().getPath());
        Assertions.assertEquals("Hello", session.getTransmissionText());
        Assertions.assertEquals(3000, session.getTransmissionTextDuration());
        Assertions.assertTrue(session.isSlotSpeaking(0));
        Assertions.assertTrue(session.isWaiting());

        runHostTimeRange(session, 102, 3101);

        Assertions.assertEquals("Hello", session.getTransmissionText());

        runHostTimeRange(session, 3102, 3102);

        Assertions.assertNull(session.getTransmissionText());

        runHostTimeRange(session, 3103, 3601);

        Assertions.assertTrue(session.isSlotSpeaking(0));
        Assertions.assertTrue(session.isWaiting());

        runHostTimeRange(session, 3602, 3602);

        Assertions.assertFalse(session.isSlotSpeaking(0));
        Assertions.assertFalse(session.isWaiting());

        // The next step, at 101 + 101 * 35 = 3636, resumes the Transmission and shows the following text.
        runHostTimeRange(session, 3603, 3635);

        Assertions.assertNull(session.getTransmissionText());

        runHostTimeRange(session, 3636, 3636);

        Assertions.assertEquals("done", session.getTransmissionText());
    }

    @Test
    void play_wav_uses_map_string_outside_campaigns() throws IOException {
        BriefingSession session = createSession(new String[]{"sound\\briefing.wav"},
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER)
                                                        .action(BriefingActionIds.PLAY_WAV, 0, 0, 1, 0, 0, 0, 0));

        session.pollSoundEvents();
        session.update(101);

        List<SoundEvent> sounds = session.pollSoundEvents();

        Assertions.assertEquals(1, sounds.size());
        Assertions.assertEquals("sound\\briefing.wav", sounds.getFirst().getPath());
        Assertions.assertFalse(sounds.getFirst().isTransmission());
    }

    @Test
    void portrait_video_ticks_every_fifty_milliseconds_in_timer_list_order() throws IOException {
        RecordBuilder record = new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).waitFor(10000);
        BriefingSession session = BriefingSession.create(createMap(new String[]{"x"}, record),
                                                         UnitsDat.createTypeTable(createUnits()),
                                                         createPlayers(),
                                                         LOCAL_PLAYER,
                                                         0,
                                                         "",
                                                         false,
                                                         null,
                                                         0);

        List<Integer> ticks = new ArrayList<>();

        session.setPortraitVideo((slot, portrait, talking, hostMillis) -> ticks.add(slot));
        session.update(50);

        Assertions.assertTrue(ticks.isEmpty(), "The portrait timer fires only after more than the interval.");

        session.update(51);

        Assertions.assertEquals(List.of(3, 2, 1, 0), ticks, "A timer armed later goes before those due at the same time.");

        ticks.clear();
        session.update(102);

        Assertions.assertEquals(4, ticks.size());
    }

    @Test
    void mission_objectives_and_skip_tutorial_apply_in_campaigns_and_clear_on_skip() throws IOException {
        RecordBuilder record = new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER)
                .action(BriefingActionIds.MISSION_OBJECTIVES, 0, 1, 0, 0, 0, 0, 0)
                .action(BriefingActionIds.SKIP_TUTORIAL_ENABLED, 0, 0, 0, 0, 0, 0, 0)
                .text(2, 10000)
                .waitFor(10000);
        String[] strings = {"Destroy the base.", "Welcome"};
        BriefingSession campaign = BriefingSession.create(createMap(strings, record),
                                                          UnitsDat.createTypeTable(createUnits()),
                                                          createPlayers(),
                                                          LOCAL_PLAYER,
                                                          CAMPAIGN_MISSION,
                                                          CAMPAIGN_DIRECTORY,
                                                          false,
                                                          null,
                                                          0);

        Assertions.assertNull(campaign.getMissionObjectives());

        campaign.update(101);

        Assertions.assertEquals("Destroy the base.", campaign.getMissionObjectives());
        Assertions.assertEquals(1, campaign.getMissionObjectivesStringIndex());
        Assertions.assertTrue(campaign.isSkipTutorialEnabled());

        BriefingSession custom = BriefingSession.create(createMap(strings, record),
                                                        UnitsDat.createTypeTable(createUnits()),
                                                        createPlayers(),
                                                        LOCAL_PLAYER,
                                                        0,
                                                        "",
                                                        false,
                                                        null,
                                                        0);

        custom.update(101);

        Assertions.assertFalse(custom.isSkipTutorialEnabled());

        BriefingSession multiplayer = BriefingSession.create(createMap(strings, record),
                                                             UnitsDat.createTypeTable(createUnits()),
                                                             createPlayers(),
                                                             LOCAL_PLAYER,
                                                             CAMPAIGN_MISSION,
                                                             CAMPAIGN_DIRECTORY,
                                                             true,
                                                             null,
                                                             0);

        multiplayer.update(101);

        Assertions.assertFalse(multiplayer.isSkipTutorialEnabled());

        campaign.pollSoundEvents();
        campaign.skip();

        List<SoundEvent> sounds = campaign.pollSoundEvents();

        Assertions.assertEquals(1, sounds.size());
        Assertions.assertTrue(sounds.getFirst().isStop());
        Assertions.assertNull(campaign.getTransmissionText());
        Assertions.assertNull(campaign.getMissionObjectives());
        Assertions.assertFalse(campaign.update(20000));
        Assertions.assertTrue(campaign.isWaiting());
    }

    @Test
    void replay_restarts_briefing_from_first_record() throws IOException {
        BriefingSession session = createSession(new String[]{"a", "b"},
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).text(1, 10000)
                                                                                                                     .waitFor(5000)
                                                                                                                     .text(2, 1000)
                                                                                                                     .show(0));

        session.update(101);

        Assertions.assertEquals("a", session.getTransmissionText());
        Assertions.assertTrue(session.isWaiting());

        session.replay();

        Assertions.assertFalse(session.isWaiting());
        Assertions.assertNull(session.getTransmissionText());
        Assertions.assertTrue(session.isRunning());

        session.update(202);

        Assertions.assertEquals("a", session.getTransmissionText());
        Assertions.assertEquals(202, session.getTransmissionTextStartMillis());
        Assertions.assertTrue(session.isWaiting());
    }

    @Test
    void owners_register_copies_for_local_player_all_players_and_its_force() throws IOException {
        BriefingSession session = createSession(new String[]{"one", "two", "three", "four"},
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, 1).text(1, 10000),
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING,
                                                                  LOCAL_PLAYER,
                                                                  TriggerPlayerSelectors.ALL_PLAYERS)
                                                        .text(2, 10000),
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, TriggerPlayerSelectors.FORCE_2)
                                                        .text(3, 10000),
                                                new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, TriggerPlayerSelectors.FORCE_1)
                                                        .text(4, 10000));

        Assertions.assertFalse(session.isEmpty());
        Assertions.assertTrue(session.update(101));
        Assertions.assertEquals("two", session.getTransmissionText());
        Assertions.assertTrue(session.update(202));
        Assertions.assertEquals("two", session.getTransmissionText());
        Assertions.assertEquals(202, session.getTransmissionTextStartMillis());
        Assertions.assertFalse(session.update(303));
        Assertions.assertEquals("four", session.getTransmissionText());
    }

    @Test
    void long_transmission_text_scrolls_after_delay_and_needs_text_layout() throws IOException {
        TextLayout layout = new TextLayout() {
            /**
             * @return the control height: bottom minus top, signed WORDs
             * @implSpec Returns the fixed box height for the scrolling fixture.
             */
            @Override
            public int getBoxHeight() {
                return 40;
            }


            /**
             * Measures the text height used by scroll timing.
             *
             * @param text the text shown
             * @return the wrapped text height in pixels, a signed WORD
             * @implSpec Returns the fixed measured height for the scrolling fixture.
             */
            @Override
            public int measureTextHeight(@NotNull String text) {
                return 100;
            }
        };
        BriefingSession session = BriefingSession.create(createMap(new String[]{"long"},
                                                                   new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER)
                                                                           .text(1, 8000)),
                                                         UnitsDat.createTypeTable(createUnits()),
                                                         createPlayers(),
                                                         LOCAL_PLAYER,
                                                         0,
                                                         "",
                                                         false,
                                                         layout,
                                                         0);

        // Height 100 over box 40: divisor 100 - 41 / 2 = 80, step (8000 - 4000) / 80 = 50 after the 4000 ms delay.
        runHostTimeRange(session, 1, 4152);

        Assertions.assertEquals(0, session.getTransmissionScrollOffset());

        runHostTimeRange(session, 4153, 4153);

        Assertions.assertEquals(1, session.getTransmissionScrollOffset());

        runHostTimeRange(session, 4154, 4204);

        Assertions.assertEquals(2, session.getTransmissionScrollOffset());

        runHostTimeRange(session, 4205, 8101);

        Assertions.assertEquals("long", session.getTransmissionText());

        runHostTimeRange(session, 8102, 8102);

        Assertions.assertNull(session.getTransmissionText());

        BriefingSession unmeasured = createSession(new String[]{"long"},
                                                   new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).text(1, 8000));

        Assertions.assertThrows(UnsupportedOperationException.class, unmeasured::getTransmissionScrollOffset);
    }

    @Test
    void empty_briefing_has_no_current_record() throws IOException {
        BriefingSession session = createSession(new String[0]);

        Assertions.assertTrue(session.isEmpty());
        Assertions.assertFalse(session.update(101));
    }

    @Test
    void unknown_actions_invalid_slots_and_truncated_records_throw() throws IOException {
        BriefingSession pastTable = createSession(new String[0],
                                                  new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER)
                                                          .action(BriefingActionIds.COUNT, 0, 0, 0, 0, 0, 0, 0));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> pastTable.update(101));

        BriefingSession badSlot = createSession(new String[0], new RecordBuilder(TriggerConditionIds.MISSION_BRIEFING, LOCAL_PLAYER).hide(4));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> badSlot.update(101));

        byte[] truncated = createSection("MBRF", new byte[TriggerRecordCodec.RECORD_SIZE - 1]);

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> BriefingSession.create(ChkFile.parse(truncated),
                                                             UnitsDat.createTypeTable(createUnits()),
                                                             createPlayers(),
                                                             LOCAL_PLAYER,
                                                             0,
                                                             "",
                                                             false,
                                                             null,
                                                             0));
    }
}
