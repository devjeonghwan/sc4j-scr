package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.command.CommandCard;
import org.fogofwarlabs.sc4j.scr.client.ui.command.CommandInput;
import org.fogofwarlabs.sc4j.scr.client.ui.console.ConsoleModel;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatTextScenario;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks hotkey serialization order, parsing rules and custom command-card strings and keys, which in a live game reach
 * the training command.
 */
final class HotkeyBindingsUtilsTest {
    private static final int RESEARCH_STIM = 324;
    private static final int MOVE = 664;
    private static final int STOP = 665;
    private static final int TRAIN_SCV = 592;
    private static final int LAST_RECORD = 1293;
    private static final int NATIVE_RECORD_COUNT = 230;
    private static final int LETTER_COUNT = 26;
    /**
     * A key above the ASCII range ({@code U+00C9}), stored as it is.
     */
    private static final int NON_ASCII_KEY = 0x00C9;

    private static final int FASTEST_SPEED = 6;
    private static final int GAME_SPEED_SETTING = 4;
    private static final int REPLAY_SPEED = 1;
    private static final int TRAINING_MINERALS = 1000;
    private static final int COMMAND_FRAMES = 3;
    private static final int TRAINING_FRAMES = 2;
    private static final int SETTLE_FRAMES = 60;
    private static final int SCV_TYPE = 7;

    private static void stepFrames(@NotNull ClientSession session, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            session.stepFrame(false);
        }
    }

    private static @Nullable Unit findCommandCenter(@NotNull Game game, int player) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getOwnerIndex() == player &&
                pool.getUnit(index).getUnitId() == ScrUnitIds.TERRAN_COMMAND_CENTER) {
                return pool.getUnit(index);
            }
        }

        return null;
    }

    @Test
    void binding_table_holds_native_names_in_order() {
        Assertions.assertEquals(NATIVE_RECORD_COUNT, HotkeyBindingsUtils.COUNT);
        Assertions.assertEquals(RESEARCH_STIM, HotkeyBindingsUtils.getIdentifier(0));
        Assertions.assertEquals("STR_RSRCH_STIM", HotkeyBindingsUtils.getName(0));
        Assertions.assertEquals(LAST_RECORD, HotkeyBindingsUtils.getIdentifier(HotkeyBindingsUtils.COUNT - 1));
        Assertions.assertEquals("STR_MAKE_P_DTEMPLAR", HotkeyBindingsUtils.getName(HotkeyBindingsUtils.COUNT - 1));
    }

    @Test
    void format_should_write_table_order_and_skip_missing_keys() {
        Map<Integer, Integer> keys = new HashMap<>();

        keys.put(STOP, (int) 's');
        keys.put(MOVE, (int) 'm');
        keys.put(RESEARCH_STIM, 0);
        keys.put(TRAIN_SCV, (int) 'ㅅ');

        Assertions.assertEquals("STR_MAKE_T_SCV=ㅅ\nSTR_MOVE=m\nSTR_STOP=s\n", HotkeyBindingsUtils.format(keys));
    }

    @Test
    void parse_should_round_trip_every_formatted_record() {
        Map<Integer, Integer> keys = new HashMap<>();

        for (int index = 0; index < HotkeyBindingsUtils.COUNT; index++) {
            keys.put(HotkeyBindingsUtils.getIdentifier(index), (int) 'a' + index % LETTER_COUNT);
        }

        keys.put(MOVE, (int) ';');
        keys.put(STOP, (int) 'é');

        String text = HotkeyBindingsUtils.format(keys);

        Assertions.assertEquals(NATIVE_RECORD_COUNT, text.split("\n").length);
        Assertions.assertEquals(keys, HotkeyBindingsUtils.parse(text));
        // The settings file holds the string as UTF-8 text.
        Assertions.assertEquals(text, new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    @Test
    void parse_should_lower_case_ascii_and_skip_empty_unknown_and_malformed_lines() {
        Map<Integer, Integer> keys = HotkeyBindingsUtils.parse("STR_MOVE=Q\nSTR_STOP=\nSTR_UNKNOWN=x\nno separator\n" +
                                                               "STR_MAKE_T_SCV=�\nSTR_RSRCH_STIM=k=j");

        // Upper-case ASCII is stored lower-cased; an empty value, an unknown name, a line without '=' and
        // U+FFFD are skipped; the value's first code point is the key.
        Assertions.assertEquals(Map.of(MOVE, (int) 'q', RESEARCH_STIM, (int) 'k'), keys);
        Assertions.assertEquals(NON_ASCII_KEY, HotkeyBindingsUtils.normalize(NON_ASCII_KEY));
        Assertions.assertEquals('a', HotkeyBindingsUtils.normalize('A'));
    }

    @Test
    void custom_binding_changes_command_card_hotkey_and_tooltip() throws IOException {
        StatText text = StatTextScenario.createStatText(STOP, identifier -> switch (identifier) {
            case MOVE -> "m\\u0007\\u0003M\\u0001ove";
            case STOP -> "s\\u0007\\u0003S\\u0001top";
            default -> "";
        });
        Map<Integer, Integer> overrides = HotkeyBindingsUtils.parse(HotkeyBindingsUtils.format(Map.of(MOVE, (int) ';')));
        HotkeyProfile profile = new HotkeyProfile(text, HotkeyProfile.DEVICE, overrides);

        Assertions.assertEquals(';', profile.getHotkey(MOVE));
        Assertions.assertEquals(";\u0007Move [\u0003;\u0001]", profile.getString(MOVE));
        // The stop button keeps its default key.
        Assertions.assertEquals('s', profile.getHotkey(STOP));
        Assertions.assertEquals((int) ';', profile.getKey(MOVE));
        Assertions.assertEquals("Move", profile.getNameText(MOVE));
        Assertions.assertFalse(profile.getNameText(RESEARCH_STIM).isEmpty());
        Assertions.assertTrue(profile.getNameText(RESEARCH_STIM).startsWith("## STR 324 TOO SHORT"));
    }

    @RequiresInstallation
    @Test
    void custom_binding_trains_scv_with_bound_key_instead_of_default() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 "bw-terran-1", "Tester", FASTEST_SPEED, ClientSessionScenario.GAME_SEED,
                                                                 "enUS")) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();
            stepFrames(session, SETTLE_FRAMES);

            Game game = session.getGame();
            int player = session.getLocalPlayer();

            game.getPlayerTable().requirePlayer(player).setMinerals(TRAINING_MINERALS);

            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));
            WorldInterface world = new WorldInterface(console, (owner, opcode, payload) -> session.submitLocalCommand(owner, opcode, payload), true);
            CommandInput input = world.getCommandInput();
            Map<Integer, Integer> overrides = HotkeyBindingsUtils.parse("STR_MAKE_T_SCV=;\n");
            HotkeyProfile profile = new HotkeyProfile(StatText.load(session.getStorage(), "enUS"), HotkeyProfile.DEVICE, overrides);

            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Unit commandCenter = findCommandCenter(game, player);

            Assertions.assertNotNull(commandCenter);
            input.select(commandCenter);
            stepFrames(session, COMMAND_FRAMES);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(-1, input.findHotkeySlot(profile, 's'), "the default key no longer trains an SCV");
            Assertions.assertTrue(input.findHotkeySlot(profile, ';') >= 0, "the bound key finds the SCV button");
            Assertions.assertTrue(profile.getString(TRAIN_SCV).endsWith(" [\u0003;\u0001]"), "the tooltip names the bound key");
            Assertions.assertEquals(CommandInput.Result.COMMAND, input.pressHotkey(profile, ';', false));
            stepFrames(session, TRAINING_FRAMES);
            Assertions.assertEquals(SCV_TYPE, commandCenter.getBuildQueueUnitType(commandCenter.getBuildQueueIndex()), "an SCV trains");
        }
    }
}
