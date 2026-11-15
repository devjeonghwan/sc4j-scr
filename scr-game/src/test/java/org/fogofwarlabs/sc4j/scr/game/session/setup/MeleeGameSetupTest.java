package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks resolved lobby records and header bytes against recorded replays, and the lobby race resolution with its global-generator gate.
 */
final class MeleeGameSetupTest {
    private static final int TEMPLATE_OPTIONS_START = 0x40;
    private static final int TEMPLATE_OPTIONS_END = 0x61;
    private static final int HEADER_TEMPLATE_OFFSET = 0x40;
    private static final int PLAYER_RECORDS_SIZE = ReplayHeaderBuilder.PLAYER_COUNT * ReplayHeaderBuilder.PLAYER_RECORD_SIZE;

    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String TEMPLATE_DIRECTORY = "locales/enUS/Assets/Templates/";
    private static final @NotNull String RECORDED_PLAYER_NAME = "asd";
    private static final @NotNull String PLAYER_NAME = "tester";

    private static final int SEED = 12345;
    /**
     * The Zerg, Terran and Protoss races.
     */
    private static final int ZERG_RACE = 0;
    private static final int TERRAN_RACE = 1;
    private static final int PROTOSS_RACE = 2;

    /**
     * Resolves the recorded lobby case and compares every relevant header byte.
     *
     * @param name          the replay resource ({@link GameTestResources})
     * @param humanRace     the human lobby race
     * @param computerRaces the computer races in lobby order
     * @throws IOException if the fixture or installed map cannot be read
     */
    private static void checkRecording(@NotNull String name, int humanRace, int @NotNull [] computerRaces) throws IOException {
        Path path = GameTestResources.getPath(name);
        byte[] file = Files.readAllBytes(path);
        ReplayFile replay = ReplayFile.parse(file);
        ReplayHeader header = replay.getHeader();
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        MeleeGameSetup setup = new MeleeGameSetup(mapData, RECORDED_PLAYER_NAME, humanRace, 0, computerRaces, header.getSpeed());
        LiveGame game = new LiveGame(setup, (int) header.getRandomSeed());
        byte[] recorded = ReplayFile.readHeaderSection(file);
        byte[] written = ReplayFile.readHeaderSection(game.writeReplay(header.getFrames(), 0));

        for (int slot = 0; slot < ReplayHeaderBuilder.PLAYER_COUNT; slot++) {
            int offset = ReplayHeaderBuilder.PLAYERS_OFFSET + slot * ReplayHeaderBuilder.PLAYER_RECORD_SIZE;
            boolean computer = recorded[offset + ReplayHeaderBuilder.PLAYER_TYPE_OFFSET] == PlayerTypes.COMPUTER;

            for (int index = 0; index < ReplayHeaderBuilder.PLAYER_RECORD_SIZE; index++) {
                if (computer && index >= ReplayHeaderBuilder.PLAYER_NAME_OFFSET) {
                    continue;
                }

                Assertions.assertEquals(recorded[offset + index], written[offset + index], name + " player record " + slot + " byte " + index);
            }
        }

        for (int index = 0; index < recorded.length; index++) {
            if (index >= ReplayHeaderBuilder.PLAYERS_OFFSET && index < ReplayHeaderBuilder.PLAYERS_OFFSET + PLAYER_RECORDS_SIZE) {
                continue;
            }

            Assertions.assertEquals(recorded[index], written[index], name + " header byte 0x" + Integer.toHexString(index));
        }
    }

    @Test
    void written_header_matches_recorded_random_computer_race_game() throws IOException {
        checkRecording(GameTestResources.REPLAY_TERRAN_VS_COMPUTER, ScrRaceIds.TERRAN, new int[]{ScrRaceIds.RANDOM});
    }

    @Test
    void written_header_matches_recorded_fixed_race_game() throws IOException {
        checkRecording(GameTestResources.REPLAY_ZVZ_COMPUTER_SHORT, ScrRaceIds.ZERG, new int[]{ScrRaceIds.ZERG});
    }

    @Test
    void written_header_matches_recorded_four_player_game() throws IOException {
        checkRecording(GameTestResources.REPLAY_FREE_FOR_ALL_COMPUTERS, ScrRaceIds.TERRAN,
                       new int[]{ScrRaceIds.PROTOSS, ScrRaceIds.TERRAN, ScrRaceIds.ZERG});
    }

    @RequiresInstallation
    @Test
    void header_templates_equal_installed_melee_and_use_map_settings_templates() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            String[] templates = {"Melee(1).got", "Use Map Settings(1).got"};
            boolean[] useMapSettings = {false, true};

            for (int index = 0; index < templates.length; index++) {
                byte[] template = storage.readFile(TEMPLATE_DIRECTORY + templates[index]);
                MeleeGameSetup setup = useMapSettings[index]
                                       ? MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, GameSpeed.FASTEST)
                                       : new MeleeGameSetup(mapData,
                                                            PLAYER_NAME, ScrRaceIds.TERRAN, 0,
                                                            new int[]{ScrRaceIds.ZERG},
                                                            GameSpeed.FASTEST);
                byte[] header = ReplayFile.readHeaderSection(new LiveGame(setup, 1).writeReplay(0, 0));

                for (int offset = TEMPLATE_OPTIONS_START; offset < TEMPLATE_OPTIONS_END; offset++) {
                    Assertions.assertEquals(template[offset], header[HEADER_TEMPLATE_OFFSET + offset],
                                            templates[index] + " byte 0x" + Integer.toHexString(offset));
                }
            }
        }
    }

    @Test
    void resolve_lobby_race_should_draw_random_race_from_global_generator_and_close_gate() {
        LcgRandom global = new LcgRandom(0);

        global.setEnabled(0);

        int race = MeleeGameSetup.resolveLobbyRace(global, ScrRaceIds.RANDOM);

        Assertions.assertTrue(race >= ZERG_RACE && race <= PROTOSS_RACE, "a random race resolves to a race");
        Assertions.assertNotEquals(0, global.getState(), "the draw advances the global state from its initial 0");
        Assertions.assertEquals(0, global.getEnabled(), "the gate closes after the resolution");

        // The same history gives the same race: the global state is the only input.
        LcgRandom again = new LcgRandom(0);

        again.setEnabled(0);

        Assertions.assertEquals(race, MeleeGameSetup.resolveLobbyRace(again, ScrRaceIds.RANDOM));
    }

    @Test
    void resolve_lobby_race_should_draw_nothing_for_a_chosen_race() {
        LcgRandom global = new LcgRandom(SEED);

        global.setEnabled(0);

        Assertions.assertEquals(TERRAN_RACE, MeleeGameSetup.resolveLobbyRace(global, ScrRaceIds.TERRAN));
        Assertions.assertEquals(SEED, global.getState());
        Assertions.assertEquals(0, global.getEnabled());
    }
}
