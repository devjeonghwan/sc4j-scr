package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.scr.client.glue.GlueGameRequest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.casc.SoundPathResolver;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.trigger.BriefingSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the ready room of custom maps: briefing sounds resolve from the open map archive, units without portraits
 * show no clip, and a Use Map Settings game opens the ready room without a campaign entry.
 */
final class ReadyRoomScreenTest {
    /**
     * A single player map whose briefing plays WAVs from the map archive.
     */
    private static final @NotNull String MAP = GameTestResources.MAP_ARCHIVE_NEW_AVALON;
    /**
     * A map whose briefing shows unit type {@code 202}, which has no portrait.
     */
    private static final @NotNull String NO_PORTRAIT_MAP = GameTestResources.MAP_ARCHIVE_DRAGON;
    private static final int NO_PORTRAIT = 65535;
    private static final char SCENARIO_SEPARATOR = 92;
    private static final @NotNull String SCENARIO = "staredit" + SCENARIO_SEPARATOR + "scenario.chk";
    private static final int STEP_MILLISECONDS = 50;
    private static final int BRIEFING_MILLISECONDS = 180_000;
    private static final @NotNull String UNITS_FILE = "arr/units.dat";
    /**
     * A custom map briefs as campaign mission {@code 0} without a campaign directory, from time {@code 0}.
     */
    private static final int CUSTOM_MAP_MISSION = 0;
    private static final @NotNull String CUSTOM_MAP_DIRECTORY = "";
    private static final int START_MILLISECONDS = 0;
    private static final int RANDOM_RACE = 6;
    /**
     * The locale of the briefing sounds and the player name of the custom game.
     */
    private static final @NotNull String LOCALE = "enUS";
    private static final @NotNull String PLAYER_NAME = "Tester";

    @RequiresInstallation
    @Test
    void briefing_sounds_come_from_map_archive() throws IOException {
        Path map = GameTestResources.getPath(MAP);
        MpqArchive archive = MpqArchive.open(map, true);
        ChkFile scenario = ChkFile.parseTolerant(archive.readFile(SCENARIO));

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            DatTable units = UnitsDat.createSchema().parse(storage.readFile(UNITS_FILE));
            Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];
            int localPlayer = ReadyRoomScreen.readPlayers(scenario, players);
            BriefingSession session = BriefingSession.create(scenario,
                                                             UnitsDat.createTypeTable(units),
                                                             players, localPlayer,
                                                             CUSTOM_MAP_MISSION, CUSTOM_MAP_DIRECTORY,
                                                             false,
                                                             null,
                                                             START_MILLISECONDS,
                                                             null);
            List<BriefingSession.SoundEvent> sounds = new ArrayList<>();

            for (int time = 0; time <= BRIEFING_MILLISECONDS; time += STEP_MILLISECONDS) {
                session.update(time);

                for (BriefingSession.SoundEvent event : session.pollSoundEvents()) {
                    if (!event.isStop()) {
                        sounds.add(event);
                    }
                }
            }

            session.close();

            Assertions.assertFalse(sounds.isEmpty(), "the briefing plays sounds");

            SoundPathResolver resolver = new SoundPathResolver(storage, LOCALE, SoundPathResolver.AudioSet.REMASTERED);
            boolean mapOnly = false;

            for (BriefingSession.SoundEvent event : sounds) {
                Assertions.assertNotNull(resolver.readAsset(event.getPathBytes(), archive), event.getPath());
                mapOnly |= resolver.readAsset(event.getPathBytes(), null) == null;
            }

            Assertions.assertTrue(mapOnly, "some briefing sounds exist only in the map archive");
        }
    }

    @RequiresInstallation
    @Test
    void briefing_shows_unit_without_portrait_with_absent_portrait_index() throws IOException {
        Path map = GameTestResources.getPath(NO_PORTRAIT_MAP);
        ChkFile scenario = ChkFile.parseTolerant(MpqArchive.open(map, true).readFile(SCENARIO));

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            DatTable units = UnitsDat.createSchema().parse(storage.readFile(UNITS_FILE));
            Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];
            int localPlayer = ReadyRoomScreen.readPlayers(scenario, players);
            BriefingSession session = BriefingSession.create(scenario,
                                                             UnitsDat.createTypeTable(units),
                                                             players, localPlayer,
                                                             CUSTOM_MAP_MISSION, CUSTOM_MAP_DIRECTORY,
                                                             false,
                                                             null,
                                                             START_MILLISECONDS,
                                                             null);
            boolean shown = false;

            // Show Portrait retains the absent portrait index of unit type 202; no clip opens.
            for (int time = 0; time <= BRIEFING_MILLISECONDS; time += STEP_MILLISECONDS) {
                session.update(time);
                session.pollSoundEvents();

                for (int slot = 0; slot < BriefingSession.SLOT_COUNT; slot++) {
                    shown |= session.isSlotVisible(slot) && session.getSlotPortrait(slot) == NO_PORTRAIT;
                }
            }

            session.close();

            Assertions.assertTrue(shown, "the unit without a portrait is shown with portrait 65535");
        }
    }

    @Test
    void use_map_settings_game_opens_ready_room_without_campaign_entry() throws IOException {
        Path map = GameTestResources.getPath(MAP);
        GlueGameRequest request = GlueGameRequest.createCustom(map, PLAYER_NAME, true, GameTemplate.USE_MAP_SETTINGS, RANDOM_RACE, new int[0]);
        ReadyRoomScreen room = ReadyRoomScreen.createForCustomGame(request, true);

        Assertions.assertNull(room.getEntry());
    }
}
